package com.jaysay.coursetable.data.parser

import org.junit.Assert.assertTrue
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

    private fun createSyntheticWorkbook(workbook: Workbook): ByteArray = workbook.use {
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

        val dataRow = sheet.createRow(3)
        listOf("DEMO-001", "示例课程", "星期一", "1", "2", "1-4周", "示例教师", "示例教室")
            .forEachIndexed { index, value -> dataRow.createCell(index).setCellValue(value) }

        ByteArrayOutputStream().use { output ->
            it.write(output)
            output.toByteArray()
        }
    }
}
