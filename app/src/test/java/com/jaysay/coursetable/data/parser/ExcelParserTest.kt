package com.jaysay.coursetable.data.parser

import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class ExcelParserTest {
    @Test
    fun parsesSyntheticXlsxWithoutExternalFiles() {
        val bytes = createSyntheticWorkbook(XSSFWorkbook())
        val result = ByteArrayInputStream(bytes).use(ExcelParser::parse)

        assertTrue("No valid courses; errors=${result.errors}", result.courses.isNotEmpty())
        assertTrue("Unexpected row errors: ${result.errors}", result.errors.isEmpty())
        val course = result.courses.single()
        assertTrue(course.courseId == "DEMO-001")
        assertTrue(course.courseName == "示例课程")
        assertTrue(course.teacher == "示例教师")
        assertTrue(course.classroom == "示例教室")
        assertTrue(course.weeks == listOf(1, 2, 3, 4))
    }

    @Test
    fun parsesLegacyXlsWorkbook() {
        val bytes = createSyntheticWorkbook(HSSFWorkbook())
        val result = ByteArrayInputStream(bytes).use(ExcelParser::parse)

        assertTrue("Unexpected errors: ${result.errors}", result.errors.isEmpty())
        assertEquals(1, result.courses.size)
    }

    @Test
    fun parsesOneThousandSyntheticRows() {
        val bytes = createSyntheticWorkbook(XSSFWorkbook(), rowCount = 1_000)

        val result = ByteArrayInputStream(bytes).use(ExcelParser::parse)

        assertTrue("Unexpected row errors: ${result.errors}", result.errors.isEmpty())
        assertEquals(1_000, result.courses.size)
        assertEquals("DEMO-1000", result.courses.last().courseId)
    }

    @Test
    fun formatsIntegerPeriodsFromNumericCells() {
        val bytes = XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("数值节次")
            writeHeaders(sheet.createRow(0))
            val row = sheet.createRow(1)
            row.createCell(0).setCellValue("NUM-001")
            row.createCell(1).setCellValue("数值课程")
            row.createCell(2).setCellValue("星期三")
            row.createCell(3).setCellValue(3.0)
            row.createCell(4).setCellValue(5.0)
            row.createCell(5).setCellValue("1-4周")
            workbook.toBytes()
        }

        val result = ByteArrayInputStream(bytes).use(ExcelParser::parse)

        assertTrue("Unexpected row errors: ${result.errors}", result.errors.isEmpty())
        val course = result.courses.single()
        // 整数不能被读成 "3.0"（节次解析取数字位，"3.0" 会变成 30）。
        assertEquals(3, course.startPeriod)
        assertEquals(5, course.endPeriod)
    }

    @Test
    fun usesCachedFormulaValues() {
        val bytes = XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("公式课程")
            writeHeaders(sheet.createRow(0))
            val row = sheet.createRow(1)
            row.createCell(0).setCellValue("F-001")
            val nameCell = row.createCell(1)
            nameCell.setCellFormula("\"公式课程\"")
            row.createCell(2).setCellValue("星期五")
            val startCell = row.createCell(3)
            startCell.setCellFormula("1+2")
            val endCell = row.createCell(4)
            endCell.setCellFormula("2+2")
            row.createCell(5).setCellValue("1-2周")
            val evaluator = workbook.creationHelper.createFormulaEvaluator()
            listOf(nameCell, startCell, endCell).forEach(evaluator::evaluateFormulaCell)
            workbook.toBytes()
        }

        val result = ByteArrayInputStream(bytes).use(ExcelParser::parse)

        assertTrue("Unexpected row errors: ${result.errors}", result.errors.isEmpty())
        val course = result.courses.single()
        assertEquals("公式课程", course.courseName)
        assertEquals(3, course.startPeriod)
        assertEquals(4, course.endPeriod)
    }

    private fun writeHeaders(headerRow: Row) {
        val headers = listOf(
            "课程代码",
            "课程名称",
            "星期",
            "起始节次",
            "终止节次",
            "教学周",
            "教师",
            "教室"
        )
        headers.forEachIndexed { index, value -> headerRow.createCell(index).setCellValue(value) }
    }

    private fun createSyntheticWorkbook(workbook: Workbook, rowCount: Int = 1): ByteArray = workbook.use {
        val sheet = it.createSheet("示例课表")
        sheet.createRow(0).createCell(0).setCellValue("完全虚构的自动化测试数据")
        writeHeaders(sheet.createRow(2))

        repeat(rowCount) { rowIndex ->
            val number = rowIndex + 1
            val suffix = if (rowCount == 1) "" else number.toString()
            val dataRow = sheet.createRow(3 + rowIndex)
            listOf(
                "DEMO-${number.toString().padStart(3, '0')}",
                "示例课程$suffix",
                "星期${listOf("一", "二", "三", "四", "五", "六", "日")[rowIndex % 7]}",
                "${rowIndex % 10 + 1}",
                "${rowIndex % 10 + 1}",
                "1-4周",
                "示例教师",
                "示例教室$suffix"
            ).forEachIndexed { index, value -> dataRow.createCell(index).setCellValue(value) }
        }

        it.toBytes()
    }

    private fun Workbook.toBytes(): ByteArray = ByteArrayOutputStream().use { output ->
        write(output)
        output.toByteArray()
    }
}
