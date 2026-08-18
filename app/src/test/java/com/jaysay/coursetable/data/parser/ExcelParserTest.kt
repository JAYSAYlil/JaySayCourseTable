package com.jaysay.coursetable.data.parser

import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class ExcelParserTest {
    @Test
    fun parsesSyntheticXlsAndXlsxWithoutExternalFiles() {
        listOf(HSSFWorkbook(), XSSFWorkbook()).forEach { workbook ->
            val bytes = createSyntheticWorkbook(workbook)
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
    }

    @Test
    fun parsesOneThousandSyntheticRows() {
        val bytes = createSyntheticWorkbook(XSSFWorkbook(), rowCount = 1_000)

        val result = ByteArrayInputStream(bytes).use(ExcelParser::parse)

        assertTrue("Unexpected row errors: ${result.errors}", result.errors.isEmpty())
        assertEquals(1_000, result.courses.size)
        assertEquals("DEMO-1000", result.courses.last().courseId)
    }

    private fun createSyntheticWorkbook(workbook: Workbook, rowCount: Int = 1): ByteArray = workbook.use {
        val sheet = it.createSheet("示例课表")
        sheet.createRow(0).createCell(0).setCellValue("完全虚构的自动化测试数据")
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
        val headerRow = sheet.createRow(2)
        headers.forEachIndexed { index, value -> headerRow.createCell(index).setCellValue(value) }

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

        ByteArrayOutputStream().use { output ->
            it.write(output)
            output.toByteArray()
        }
    }
}
