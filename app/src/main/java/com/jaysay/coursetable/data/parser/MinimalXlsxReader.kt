package com.jaysay.coursetable.data.parser

import org.w3c.dom.Element
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
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
import javax.xml.parsers.SAXParserFactory
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * 手写的最小 xlsx（OOXML）读取器，用于替代 Apache POI 以缩小 APK 体积。
 *
 * 仅覆盖课表导入所需的能力：
 * - 定位 xl/workbook.xml 中第一个可见工作表（经 workbook.xml.rels 解析 r:id → target）。
 * - 单元格取值：sharedStrings / inlineStr / 公式缓存(str) / 布尔 / 数值。
 * - 数值按 General 格式输出（整数不带小数点，科学计数展开为普通数字）。
 * - 日期单元格按 Excel 1900 序列数（含闰日 bug 修正）转 "yyyy/M/d [HH:mm]"。
 *
 * ZIP 元数据使用 DOM，目标工作表使用 SAX 流式解析，Android 与 JVM 单测均可运行。
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
        var date1904 = false
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
                    val workbookInfo = resolveWorkbookInfo(workbookBytes!!, relsBytes!!)
                    targetPath = workbookInfo.sheetPath
                    date1904 = workbookInfo.date1904
                    worksheetBytes.keys.retainAll { it == targetPath }
                }
                entry = zip.nextEntry
            }
        }

        val notValidXlsx = InvalidXlsxException("文件解析失败：不是有效的 xlsx 文件")
        val workbookInfo = if (targetPath == null) {
            val workbook = workbookBytes ?: throw notValidXlsx
            val rels = relsBytes ?: throw notValidXlsx
            resolveWorkbookInfo(workbook, rels)
        } else {
            WorkbookInfo(requireNotNull(targetPath), date1904)
        }
        val sheetPath = workbookInfo.sheetPath
        val sheetBytes = worksheetBytes[sheetPath] ?: throw notValidXlsx

        val sharedStrings = sharedBytes?.let { parseSharedStrings(parseXml(it)) } ?: emptyList()
        val styles = stylesBytes?.let { Styles.parse(parseXml(it)) } ?: Styles.EMPTY
        // 工作表通常是整个 xlsx 中最大的 XML 部件，使用 SAX 不构建完整 DOM，
        // 将低端设备上的峰值内存控制在“共享字符串/样式 + 当前单元格”范围内。
        return parseSheetStreaming(sheetBytes, sharedStrings, styles, workbookInfo.date1904)
    }

    // ---------- 部件定位 ----------

    private data class WorkbookInfo(val sheetPath: String, val date1904: Boolean)

    /** 解析 workbook.xml 第一个可见 sheet 的 r:id，并经 rels 换算成 zip 内的部件路径。 */
    private fun resolveWorkbookInfo(workbookBytes: ByteArray, relsBytes: ByteArray): WorkbookInfo {
        val workbook = parseXml(workbookBytes)
        val sheets = workbook.getElementsByTagName("sheet")
        if (sheets.length == 0) throw InvalidXlsxException("文件中没有工作表")
        val sheet = (0 until sheets.length)
            .asSequence()
            .map { sheets.item(it) as Element }
            .firstOrNull { it.getAttribute("state").lowercase() !in setOf("hidden", "veryhidden") }
            ?: sheets.item(0) as Element
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

        val sheetPath = when {
            target.startsWith("/") -> target.trimStart('/')
            target.startsWith("../") -> "xl/" + target.removePrefix("../")
            else -> "xl/$target"
        }
        val workbookPr = workbook.getElementsByTagName("workbookPr").item(0) as? Element
        val date1904 = workbookPr?.getAttribute("date1904")
            ?.let { it == "1" || it.equals("true", ignoreCase = true) }
            ?: false
        return WorkbookInfo(sheetPath, date1904)
    }

    // ---------- 工作表网格 ----------

    private fun parseSheet(
        root: Element,
        sharedStrings: List<String>,
        styles: Styles,
        date1904: Boolean
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
                    formatNumeric(raw, cell.getAttribute("s").toIntOrNull(), styles, date1904)
                } else {
                    raw
                }
                if (value.isNotEmpty()) cells[columnIndex] = value
            }
            if (cells.isNotEmpty()) rows[rowIndex] = cells
        }
        applyMergedCells(root, rows)
        return SheetGrid(rows)
    }

    /** SAX 流式读取工作表，避免为数万行课程表构造庞大的 DOM 树。 */
    private fun parseSheetStreaming(
        bytes: ByteArray,
        sharedStrings: List<String>,
        styles: Styles,
        date1904: Boolean
    ): SheetGrid {
        val rows = TreeMap<Int, TreeMap<Int, String>>()
        val merges = ArrayList<String>()
        val parser = SAXParserFactory.newInstance().apply {
            isNamespaceAware = true
            try { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) } catch (_: Exception) {}
            try { setFeature("http://xml.org/sax/features/external-general-entities", false) } catch (_: Exception) {}
            try { setFeature("http://xml.org/sax/features/external-parameter-entities", false) } catch (_: Exception) {}
            try { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) } catch (_: Exception) {}
        }.newSAXParser()
        parser.xmlReader.contentHandler = object : DefaultHandler() {
            private var rowIndex = -1
            private var nextRowNumber = 1
            private var cellColumn = -1
            private var nextColumnIndex = 0
            private var cellType = ""
            private var cellStyle: Int? = null
            private var cellRaw = StringBuilder()
            private var inlineText = StringBuilder()
            private var capture: String? = null
            private var rowCells = TreeMap<Int, String>()

            override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes) {
                when (qName.substringAfterLast(':')) {
                    "row" -> {
                        val number = attributes.getValue("r")?.toIntOrNull() ?: nextRowNumber
                        rowIndex = number - 1
                        nextRowNumber = number + 1
                        rowCells = TreeMap()
                        nextColumnIndex = 0
                    }
                    "c" -> {
                        val ref = attributes.getValue("r").orEmpty()
                        cellColumn = if (ref.isEmpty()) nextColumnIndex else columnIndex(ref) ?: nextColumnIndex
                        nextColumnIndex = cellColumn + 1
                        cellType = attributes.getValue("t").orEmpty()
                        cellStyle = attributes.getValue("s")?.toIntOrNull()
                        cellRaw = StringBuilder()
                        inlineText = StringBuilder()
                    }
                    "v", "t" -> if (cellColumn >= 0) capture = qName.substringAfterLast(':')
                    "mergeCell" -> attributes.getValue("ref")?.let { if (merges.size < MAX_MERGE_RANGES) merges += it }
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                when (capture) {
                    "v" -> cellRaw.append(ch, start, length)
                    "t" -> inlineText.append(ch, start, length)
                }
            }

            override fun endElement(uri: String?, localName: String?, qName: String) {
                when (qName.substringAfterLast(':')) {
                    "v", "t" -> capture = null
                    "c" -> {
                        val raw = when (cellType) {
                            "s" -> cellRaw.toString().trim().toIntOrNull()?.let { sharedStrings.getOrElse(it) { "" } }.orEmpty()
                            "inlineStr" -> inlineText.toString()
                            "b" -> if (cellRaw.toString().trim() in setOf("1", "true", "TRUE")) "TRUE" else "FALSE"
                            else -> cellRaw.toString()
                        }
                        val value = if (cellType.isEmpty() || cellType == "n") {
                            formatNumeric(raw, cellStyle, styles, date1904)
                        } else raw
                        if (value.isNotEmpty()) {
                            if (rowCells.size >= MAX_CELLS_PER_ROW) throw InvalidXlsxException("文件解析失败：单行单元格过多")
                            rowCells[cellColumn] = value
                        }
                        cellColumn = -1
                        capture = null
                    }
                    "row" -> if (rowCells.isNotEmpty()) {
                        if (rows.size >= MAX_ROWS) throw InvalidXlsxException("文件解析失败：工作表行数过多")
                        rows[rowIndex] = rowCells
                    }
                }
            }
        }
        parser.xmlReader.parse(InputSource(ByteArrayInputStream(bytes)))
        // SAX 不保留文档树，合并区域先缓存引用，再复用同一套有界展开逻辑。
        applyMergedCells(merges, rows)
        return SheetGrid(rows)
    }

    /** 将合并区域左上角的值复制到其余格，兼容合并表头和分组表头。 */
    private fun applyMergedCells(root: Element, rows: TreeMap<Int, TreeMap<Int, String>>) {
        val mergeNodes = root.getElementsByTagName("mergeCell")
        val refs = (0 until mergeNodes.length.coerceAtMost(MAX_MERGE_RANGES))
            .mapNotNull { (mergeNodes.item(it) as? Element)?.getAttribute("ref") }
        applyMergedCells(refs, rows)
    }

    private fun applyMergedCells(refs: List<String>, rows: TreeMap<Int, TreeMap<Int, String>>) {
        for (ref in refs) {
            val parts = ref.split(":", limit = 2)
            if (parts.size != 2) continue
            val start = parseCellRef(parts[0]) ?: continue
            val end = parseCellRef(parts[1]) ?: continue
            val rowStart = minOf(start.first, end.first)
            val rowEnd = maxOf(start.first, end.first)
            val columnStart = minOf(start.second, end.second)
            val columnEnd = maxOf(start.second, end.second)
            if (rowEnd - rowStart > MAX_MERGE_SPAN || columnEnd - columnStart > MAX_MERGE_SPAN) continue
            val value = rows[rowStart]?.get(columnStart)?.takeIf(String::isNotEmpty) ?: continue
            for (rowIndex in rowStart..rowEnd) {
                val row = rows.getOrPut(rowIndex) { TreeMap() }
                for (columnIndex in columnStart..columnEnd) row.putIfAbsent(columnIndex, value)
            }
        }
    }

    private fun parseCellRef(ref: String): Pair<Int, Int>? {
        val match = CELL_REF.matchEntire(ref.trim()) ?: return null
        val column = columnIndex(match.groupValues[1]) ?: return null
        val row = match.groupValues[2].toIntOrNull()?.minus(1) ?: return null
        return row to column
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

    private fun formatNumeric(raw: String, styleIndex: Int?, styles: Styles, date1904: Boolean): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        val numFmtId = styles.numFmtId(styleIndex)
        if (numFmtId == 49) return trimmed // "@" 文本格式，原样输出
        if (numFmtId in BUILTIN_DATE_FORMAT_IDS || styles.isCustomDateFormat(numFmtId)) {
            return formatDateValue(trimmed, date1904) ?: trimmed
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
    private fun formatDateValue(raw: String, date1904: Boolean): String? {
        val serial = raw.toDoubleOrNull() ?: return null
        if (serial < 0.0 || serial >= 2958466.0) return null // 超出 9999-12-31
        val whole = floor(serial).toLong()
        val days = whole - if (!date1904 && whole > 59) 1L else 0L
        // 1899-12-31 到 1904-01-01 相差 1461 天；1904 系统的序列 0 即 1904-01-01。
        val dateSystemOffset = if (date1904) 1461L else 0L
        var date = LocalDate.of(1899, 12, 31).plusDays(days + dateSystemOffset)
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
            // Android 的 DocumentBuilderFactory 不实现 XInclude，直接设置会抛出
            // UnsupportedOperationException，导致所有 xlsx 在真机上解析失败。
            // XInclude 默认关闭，因此不要调用这个 Android 不支持的可选 API。
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

    private const val MAX_MERGE_RANGES = 1_000
    private const val MAX_MERGE_SPAN = 100
    private const val MAX_ROWS = 50_000
    private const val MAX_CELLS_PER_ROW = 2_000
    private val CELL_REF = Regex("^([A-Za-z]+)(\\d+)$")
}
