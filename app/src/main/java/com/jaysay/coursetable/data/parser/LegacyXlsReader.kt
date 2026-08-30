package com.jaysay.coursetable.data.parser

import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.FormulaEvaluator
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.InputStream
import java.util.Locale

/**
 * 旧版 .xls（BIFF8/OLE2 复合文档）读取。
 * POI（仅 core，不含 ooxml）只在命中 OLE2 魔数后启用；.xlsx 仍由 MinimalXlsxReader 处理，
 * 两条路径的产出统一为 [MinimalXlsxReader.SheetGrid]，上层解析逻辑完全共享。
 */
internal object LegacyXlsReader {
    fun read(input: InputStream): MinimalXlsxReader.SheetGrid {
        val workbook = WorkbookFactory.create(input)
        workbook.use { wb ->
            if (wb.numberOfSheets == 0) {
                throw MinimalXlsxReader.InvalidXlsxException("文件中没有工作表")
            }
            val sheet = wb.getSheetAt(0)
            val formatter = DataFormatter(Locale.CHINA)
            val evaluator = wb.creationHelper.createFormulaEvaluator()
            val rows = mutableMapOf<Int, Map<Int, String>>()
            for (rowIndex in 0..sheet.lastRowNum) {
                val row = sheet.getRow(rowIndex) ?: continue
                if (row.physicalNumberOfCells == 0) continue
                val cells = mutableMapOf<Int, String>()
                for (cell in row) {
                    val text = formatter.formatCellValue(cell, evaluator).trim()
                    if (text.isNotEmpty()) cells[cell.columnIndex] = text
                }
                if (cells.isNotEmpty()) rows[rowIndex] = cells
            }
            return MinimalXlsxReader.SheetGrid.fromRows(rows)
        }
    }
}
