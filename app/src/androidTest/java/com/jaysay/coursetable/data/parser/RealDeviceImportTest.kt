package com.jaysay.coursetable.data.parser

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.io.File
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 真机导入链路回归：把真实学校课表文件推送到应用外部目录后，
 * 走 parse(context, uri) 完整路径（SAF/文件流 + 魔数分流 + 解析）。
 * 测试数据由脚本推送到 getExternalFilesDir(testdata)，仓库不提交用户数据。
 */
@RunWith(AndroidJUnit4::class)
class RealDeviceImportTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun parsesRealSchoolXlsxFromDevicePath() {
        val dir = File(context.getExternalFilesDir(null), "testdata")
        val names = listOf("大三上课表.xlsx", "大二上课表.xlsx", "大二下课表.xlsx")
        val missing = names.filterNot { File(dir, it).isFile }
        if (missing.size == names.size) return // 未推送测试数据时跳过

        for (name in names) {
            val file = File(dir, name)
            if (!file.isFile) continue
            val result = ExcelParser.parse(context, Uri.fromFile(file))
            assertTrue("$name errors=${result.errors}", result.courses.isNotEmpty())
        }
    }

    @Test
    fun parsesSyntheticXlsThroughContextPath() {
        val bytes = ByteArrayOutputStream().let { output ->
            HSSFWorkbook().use { workbook ->
                val header = workbook.createSheet("课表").createRow(0)
                listOf("课程号", "课程名", "上课星期", "开始节次", "结束节次", "上课周次")
                    .forEachIndexed { index, title -> header.createCell(index).setCellValue(title) }
                val data = workbook.getSheetAt(0).createRow(1)
                data.createCell(0).setCellValue("T001")
                data.createCell(1).setCellValue("高等数学")
                data.createCell(2).setCellValue("星期一")
                data.createCell(3).setCellValue("第1小节")
                data.createCell(4).setCellValue("第2小节")
                data.createCell(5).setCellValue("1-8周")
                workbook.write(output)
            }
            output.toByteArray()
        }
        val file = File(context.cacheDir, "synthetic.xls")
        file.writeBytes(bytes)

        val result = ExcelParser.parse(context, Uri.fromFile(file))
        file.delete()
        assertTrue("errors=${result.errors}", result.courses.size == 1)
        assertEquals("高等数学", result.courses.first().courseName)
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8), result.courses.first().weeks)
    }
}
