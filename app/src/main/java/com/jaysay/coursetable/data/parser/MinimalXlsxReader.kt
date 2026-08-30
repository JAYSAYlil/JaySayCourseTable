package com.jaysay.coursetable.data.parser

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.TreeMap
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * 手写的最小 xlsx（OOXML）读取器，用于替代 Apache POI 以缩小 APK 体积。
 *
 * 仅覆盖课表导入所需的能力：
 * - 定位 xl/workbook.xml 中第一个工作表（经 workbook.xml.rels 解析 r:id → target）。
 * - 单元格取值：sharedStrings / inlineStr / 公式缓存(str) / 布尔 / 数值。
 * - 数值按 General 格式输出（整数不带小数点，科学计数展开为普通数字）。
 * - 日期单元格按 Excel 1900 序列数（含闰日 bug 修正）转 "yyyy/M/d [HH:mm]"。
 *
 * 全程只用 JDK 内置的 ZipInputStream 与 DOM 解析器，Android 与 JVM 单测均可运行。
 */
object MinimalXlsxReader {

    /** 结构性错误：[message] 即面向用户的最终文案，由调用方原样展示。 */
    class InvalidXlsxException(message: String) : IOException(message)

    /** 压缩炸弹防护：解压累计字节数超过上限时抛出。 */
    class DecompressionLimitExceededException :
        IOException("解压数据超过 ${MAX_DECOMPRESSED_BYTES / (1024 * 1024)}MB 限制")

    /** 解析后的工作表网格：行/列均为 0 基，与 POI 索引语义对齐；空白单元格不保留。 */
    class SheetGrid internal constructor(private val rows: TreeMap<Int, TreeMap<Int, String>>) {
        /** 最后一个存在内容的行号（0 基）；空表返回 -1。 */
        val lastRowNum: Int get() = rows.keys.lastOrNull() ?: -1

        /** 返回指定行的 列号→文本 映射；行不存在返回 null。 */
        fun row(rowIndex: Int): Map<Int, String>? = rows[rowIndex]

        companion object {
            /** 供 .xls 路径（LegacyXlsReader）把 POI 行列转成统一网格。 */
            internal fun fromRows(source: Map<Int, Map<Int, String>>): SheetGrid {
                val tree = TreeMap<Int, TreeMap<Int, String>>()
                source.forEach { (rowIndex, cells) ->
                    val row = TreeMap<Int, String>()
                    cells.forEach { (columnIndex, value) -> row[columnIndex] = value }
                    tree[rowIndex] = row
                }
                return SheetGrid(tree)
            }
        }
    }

    private const val MAX_DECOMPRESSED_BYTES = 64L * 1024 * 1024
    private const val REL_NAMESPACE =
        "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private val WORKSHEET_ENTRY = Regex("^xl/worksheets/[^/]+\\.xml$")

    /** Excel 内置日期数字格式的 id 集合。 */
    private val BUILTIN_DATE_FORMAT_IDS: Set<Int> =
        (14..22).toSet() + (27..36).toSet() + (45..47).toSet() + (50..58).toSet()

    fun read(input: InputStream): SheetGrid {
        var workbookBytes: ByteArray? = null
        var relsBytes: ByteArray? = null
        var sharedBytes: ByteArray? = null
        var stylesBytes: ByteArray? = null
        val worksheetBytes = HashMap<String, ByteArray>()
        var targetPath: String? = null
        val decompressed = DecompressedCounter()

        ZipInputStream(input.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name.trimStart('/')
                val isDirectory = entry.isDirectory
                val isWorksheet = !isDirectory && WORKSHEET_ENTRY.matches(name)
                val wanted = !isDirectory && (
                    name == "xl/workbook.xml" ||
                        name == "xl/_rels/workbook.xml.rels" ||
                        name == "xl/sharedStrings.xml" ||
                        name == "xl/styles.xml" ||
                        (isWorksheet && (targetPath == null || name == targetPath))
                    )

                if (wanted) {
                    val bytes = readEntry(zip, decompressed)
                    when (name) {
                        "xl/workbook.xml" -> workbookBytes = bytes
                        "xl/_rels/workbook.xml.rels" -> relsBytes = bytes
                        "xl/sharedStrings.xml" -> sharedBytes = bytes
                        "xl/styles.xml" -> stylesBytes = bytes
                        else -> worksheetBytes[name] = bytes
                    }
                } else {
                    drainEntry(zip, decompressed)
                }

                // 拿到 workbook + rels 后立刻解析目标表路径，丢弃此前误缓冲的其他工作表。
                if (targetPath == null && workbookBytes != null && relsBytes != null) {
                    targetPath = resolveFirstSheetPath(workbookBytes!!, relsBytes!!)
                    worksheetBytes.keys.retainAll { it == targetPath }
                }
                entry = zip.nextEntry
            }
        }

        val notValidXlsx = InvalidXlsxException("文件解析失败：不是有效的 xlsx 文件")
        val sheetPath = targetPath ?: run {
            val workbook = workbookBytes ?: throw notValidXlsx
            val rels = relsBytes ?: throw notValidXlsx
            resolveFirstSheetPath(workbook, rels)
        }
        val sheetBytes = worksheetBytes[sheetPath] ?: throw notValidXlsx

        val sharedStrings = sharedBytes?.let { parseSharedStrings(parseXml(it)) } ?: emptyList()
        val styles = stylesBytes?.let { Styles.parse(parseXml(it)) } ?: Styles.EMPTY
        return parseSheet(parseXml(sheetBytes), sharedStrings, styles)
    }

    // ---------- 部件定位 ----------

    /** 解析 workbook.xml 第一个 sheet 的 r:id，并经 rels 换算成 zip 内的部件路径。 */
    private fun resolveFirstSheetPath(workbookBytes: ByteArray, relsBytes: ByteArray): String {
        val workbook = parseXml(workbookBytes)
        val sheets = workbook.getElementsByTagName("sheet")
        if (sheets.length == 0) throw InvalidXlsxException("文件中没有工作表")
        val sheet = sheets.item(0) as Element
        val relId = sheet.getAttribute("r:id").ifEmpty {
            sheet.getAttributeNS(REL_NAMESPACE, "id")
        }.ifEmpty { throw InvalidXlsxException("文件解析失败：不是有效的 xlsx 文件") }

        val rels = parseXml(relsBytes)
        val relationships = rels.getElementsByTagName("Relationship")
        var target: String? = null
        for (i in 0 until relationships.length) {
            val rel = relationships.item(i) as Element
            if (rel.getAttribute("Id") == relId) {
                target = rel.getAttribute("Target")
                break
            }
        }
        target ?: throw InvalidXlsxException("文件解析失败：不是有效的 xlsx 文件")

        return when {
            target.startsWith("/") -> target.trimStart('/')
            target.startsWith("../") -> "xl/" + target.removePrefix("../")
            else -> "xl/$target"
        }
    }

    // ---------- 工作表网格 ----------

    private fun parseSheet(
        root: Element,
        sharedStrings: List<String>,
        styles: Styles
    ): SheetGrid {
        val rows = TreeMap<Int, TreeMap<Int, String>>()
        val sheetData = root.getElementsByTagName("sheetData")
        if (sheetData.length == 0) return SheetGrid(rows)
        val rowNodes = (sheetData.item(0) as Element).getElementsByTagName("row")

        // <row r="..."> 的 r 是 1 基行号；转成与 POI 一致的 0 基索引。
        var nextRowNumber = 1
        for (i in 0 until rowNodes.length) {
            val rowElement = rowNodes.item(i) as Element
            val rowNumber = rowElement.getAttribute("r").toIntOrNull() ?: nextRowNumber
            val rowIndex = rowNumber - 1
            nextRowNumber = rowNumber + 1

            val cells = TreeMap<Int, String>()
            var nextColumnIndex = 0
            val cellNodes = rowElement.getElementsByTagName("c")
            for (j in 0 until cellNodes.length) {
                val cell = cellNodes.item(j) as Element
                val ref = cell.getAttribute("r")
                val columnIndex = if (ref.isEmpty()) nextColumnIndex else columnIndex(ref) ?: nextColumnIndex
                nextColumnIndex = columnIndex + 1

                val type = cell.getAttribute("t")
                val raw = when (type) {
                    "s" -> {
                        val index = firstChildText(cell, "v")?.trim()?.toIntOrNull()
                        if (index == null) "" else sharedStrings.getOrElse(index) { "" }
                    }
                    "inlineStr" -> {
                        val isElement = firstChildElement(cell, "is")
                        if (isElement == null) "" else richText(isElement)
                    }
                    "b" -> when (firstChildText(cell, "v")?.trim()) {
                        "1", "true", "TRUE" -> "TRUE"
                        else -> "FALSE"
                    }
                    else -> firstChildText(cell, "v") ?: ""
                }
                val value = if (type.isEmpty() || type == "n") {
                    formatNumeric(raw, cell.getAttribute("s").toIntOrNull(), styles)
                } else {
                    raw
                }
                if (value.isNotEmpty()) cells[columnIndex] = value
            }
            if (cells.isNotEmpty()) rows[rowIndex] = cells
        }
        return SheetGrid(rows)
    }

    /** "B3" → 列号 1（0 基）。 */
    private fun columnIndex(ref: String): Int? {
        var column = 0
        var sawLetter = false
        for (ch in ref) {
            when {
                ch in 'A'..'Z' -> { column = column * 26 + (ch - 'A' + 1); sawLetter = true }
                ch in 'a'..'z' -> { column = column * 26 + (ch - 'a' + 1); sawLetter = true }
                ch.isDigit() -> {}
                else -> return null
            }
        }
        return if (sawLetter) column - 1 else null
    }

    // ---------- 数字与日期格式化 ----------

    private fun formatNumeric(raw: String, styleIndex: Int?, styles: Styles): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        val numFmtId = styles.numFmtId(styleIndex)
        if (numFmtId == 49) return trimmed // "@" 文本格式，原样输出
        if (numFmtId in BUILTIN_DATE_FORMAT_IDS || styles.isCustomDateFormat(numFmtId)) {
            return formatDateValue(trimmed) ?: trimmed
        }
        return formatGeneral(trimmed)
    }

    /** General 格式：整数不带小数点，科学计数展开为普通数字；非数字原样返回。 */
    private fun formatGeneral(raw: String): String = try {
        BigDecimal(raw).stripTrailingZeros().toPlainString()
    } catch (_: NumberFormatException) {
        raw
    }

    /**
     * Excel 1900 日期系统序列数 → "yyyy/M/d"，序列数带时间部分时追加 " HH:mm"。
     * 兼容 Excel 的 1900-02-29 闰日 bug：序列数 > 59 时减 1。
     */
    private fun formatDateValue(raw: String): String? {
        val serial = raw.toDoubleOrNull() ?: return null
        if (serial < 0.0 || serial >= 2958466.0) return null // 超出 9999-12-31
        val whole = floor(serial).toLong()
        val days = whole - if (whole > 59) 1L else 0L
        var date = LocalDate.of(1899, 12, 31).plusDays(days)
        var minutes = ((serial - whole) * 1440.0).roundToInt()
        if (minutes >= 1440) {
            date = date.plusDays((minutes / 1440).toLong())
            minutes %= 1440
        }
        val dateText = date.format(DateTimeFormatter.ofPattern("yyyy/M/d"))
        return if (minutes > 0) {
            "$dateText %02d:%02d".format(minutes / 60, minutes % 60)
        } else {
            dateText
        }
    }

    // ---------- 样式表 ----------

    /** styles.xml 的最小模型：仅保留 cellXfs 的 numFmtId 与自定义格式串。 */
    private class Styles private constructor(
        private val xfNumFmtIds: IntArray,
        private val customFormats: Map<Int, String>
    ) {
        fun numFmtId(styleIndex: Int?): Int =
            if (styleIndex == null) 0 else xfNumFmtIds.getOrElse(styleIndex) { 0 }

        fun isCustomDateFormat(numFmtId: Int): Boolean =
            customFormats[numFmtId]?.let(::looksLikeDateFormat) == true

        companion object {
            val EMPTY = Styles(IntArray(0), emptyMap())

            fun parse(root: Element): Styles {
                val custom = mutableMapOf<Int, String>()
                val numFmts = root.getElementsByTagName("numFmt")
                for (i in 0 until numFmts.length) {
                    val element = numFmts.item(i) as Element
                    val id = element.getAttribute("numFmtId").toIntOrNull() ?: continue
                    custom[id] = element.getAttribute("formatCode")
                }
                val xfs = mutableListOf<Int>()
                val cellXfs = root.getElementsByTagName("cellXfs")
                if (cellXfs.length > 0) {
                    val children = (cellXfs.item(0) as Element).getElementsByTagName("xf")
                    for (i in 0 until children.length) {
                        xfs += (children.item(i) as Element)
                            .getAttribute("numFmtId").toIntOrNull() ?: 0
                    }
                }
                return Styles(xfs.toIntArray(), custom)
            }
        }
    }

    /** 自定义格式串去掉引号/方括号/转义片段后含 y/m/d/h 即视为日期格式。 */
    private fun looksLikeDateFormat(formatCode: String): Boolean {
        val stripped = formatCode
            .replace(Regex("\"[^\"]*\""), "")
            .replace(Regex("'[^']*'"), "")
            .replace(Regex("\\[[^\\]]*\\]"), "")
            .replace(Regex("\\\\."), "")
            .lowercase()
        return stripped.any { it in "ymdh" }
    }

    // ---------- 共享字符串 ----------

    private fun parseSharedStrings(root: Element): List<String> {
        val items = mutableListOf<String>()
        val nodes = root.getElementsByTagName("si")
        for (i in 0 until nodes.length) {
            items += richText(nodes.item(i) as Element)
        }
        return items
    }

    /** 拼接 <si>/<is> 内的文本：直接 <t> 与富文本 run <r><t>；跳过拼音 <rPh>。 */
    private fun richText(container: Element): String {
        val sb = StringBuilder()
        val children = container.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i) as? Element ?: continue
            when (node.tagName) {
                "t" -> sb.append(node.textContent)
                "r" -> firstChildElement(node, "t")?.let { sb.append(it.textContent) }
            }
        }
        return sb.toString()
    }

    // ---------- DOM 辅助 ----------

    private fun parseXml(bytes: ByteArray): Element {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isXIncludeAware = false
            isExpandEntityReferences = false
            try { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) } catch (_: Exception) {}
            try { setFeature("http://xml.org/sax/features/external-general-entities", false) } catch (_: Exception) {}
            try { setFeature("http://xml.org/sax/features/external-parameter-entities", false) } catch (_: Exception) {}
            try { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) } catch (_: Exception) {}
        }
        val builder = factory.newDocumentBuilder()
        val document = builder.parse(ByteArrayInputStream(bytes))
        return document.documentElement
    }

    private fun firstChildElement(parent: Element, tagName: String): Element? {
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i) as? Element ?: continue
            if (node.tagName == tagName) return node
        }
        return null
    }

    private fun firstChildText(parent: Element, tagName: String): String? =
        firstChildElement(parent, tagName)?.textContent

    // ---------- zip 读取与压缩炸弹防护 ----------

    private class DecompressedCounter {
        var total = 0L

        fun add(bytes: Int) {
            total += bytes
            if (total > MAX_DECOMPRESSED_BYTES) throw DecompressionLimitExceededException()
        }
    }

    private fun readEntry(zip: ZipInputStream, counter: DecompressedCounter): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val n = zip.read(buffer)
            if (n < 0) break
            if (n > 0) {
                counter.add(n)
                output.write(buffer, 0, n)
            }
        }
        return output.toByteArray()
    }

    private fun drainEntry(zip: ZipInputStream, counter: DecompressedCounter) {
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val n = zip.read(buffer)
            if (n < 0) break
            if (n > 0) counter.add(n)
        }
    }
}
