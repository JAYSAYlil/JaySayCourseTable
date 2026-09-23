package com.jaysay.coursetable.data.parser

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Bounded, coordinate-preserving text view of the first visible worksheet. */
object WorkbookTextExtractor {
    const val MAX_FILE_BYTES = 20 * 1024 * 1024
    const val MAX_ROWS = 500
    const val MAX_CELLS = 5_000
    const val MAX_TEXT_CHARS = 48_000

    data class CellText(val row: Int, val column: Int, val text: String)
    data class Extracted(val cells: List<CellText>, val promptText: String)

    fun extract(input: InputStream): Extracted {
        val bytes = readBounded(input)
        val grid = ByteArrayInputStream(bytes).use { source ->
            val pushback = java.io.PushbackInputStream(source, 8)
            val header = ByteArray(8)
            var read = 0
            while (read < header.size) {
                val count = pushback.read(header, read, header.size - read)
                if (count < 0) break
                read += count
            }
            if (read > 0) pushback.unread(header, 0, read)
            val isXlsx = read >= 2 && header[0] == 'P'.code.toByte() && header[1] == 'K'.code.toByte()
            val isXls = read >= 4 && header.copyOfRange(0, 4).contentEquals(
                byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11.toByte(), 0xE0.toByte())
            )
            when {
                isXlsx -> MinimalXlsxReader.read(pushback)
                isXls -> LegacyXlsReader.read(pushback)
                else -> throw IllegalArgumentException("请选择有效的 .xls 或 .xlsx 文件")
            }
        }
        require(grid.lastRowNum + 1 <= MAX_ROWS) { "工作表超过 $MAX_ROWS 行，请先拆分文件" }
        val cells = buildList {
            for (rowIndex in 0..grid.lastRowNum) {
                grid.row(rowIndex).orEmpty().forEach { (column, value) ->
                    if (value.isNotBlank()) {
                        require(size < MAX_CELLS) { "工作表超过 $MAX_CELLS 个非空单元格，请先拆分文件" }
                        require(value.length <= 2_000) { "单元格 R${rowIndex + 1}C${column + 1} 内容过长，请先拆分文件" }
                        add(CellText(rowIndex + 1, column + 1, value))
                    }
                }
            }
        }
        require(cells.isNotEmpty()) { "所选工作表没有可读取的文字" }
        val text = cells.joinToString("\n") { "R${it.row}C${it.column}: ${it.text}" }
        require(text.length <= MAX_TEXT_CHARS) { "工作表文本超过 $MAX_TEXT_CHARS 字符，请先拆分文件" }
        return Extracted(cells, text)
    }

    private fun readBounded(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8_192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= MAX_FILE_BYTES) { "文件超过 20 MB，未读取" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}
