package com.jaysay.coursetable.data.transfer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jaysay.coursetable.data.parser.ExcelParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.zip.ZipInputStream

/**
 * “下载 Excel 导入模板”回归：确认内置模板资源随安装包打包，
 * 且是结构完整的 xlsx（ZIP + workbook 内容类型），保证导出后能被表格软件打开。
 */
@RunWith(AndroidJUnit4::class)
class ExcelTemplateAssetTest {

    @Test
    fun importTemplateAssetIsBundledAndIsValidXlsx() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bytes = context.assets.open("import_template.xlsx").use { it.readBytes() }

        assertTrue("模板资源应为非空", bytes.isNotEmpty())
        // xlsx 本质是 ZIP：文件头固定为 PK
        assertEquals(0x50, bytes[0].toInt() and 0xFF)
        assertEquals(0x4B, bytes[1].toInt() and 0xFF)
        assertEquals(0x03, bytes[2].toInt() and 0xFF)
        assertEquals(0x04, bytes[3].toInt() and 0xFF)

        val entryNames = ZipInputStream(bytes.inputStream()).use { zip ->
            val names = mutableListOf<String>()
            while (true) {
                val entry = zip.nextEntry ?: break
                names.add(entry.name)
            }
            names
        }
        assertTrue("模板应包含 [Content_Types].xml", "[Content_Types].xml" in entryNames)
        assertTrue("模板应包含工作簿文件", entryNames.any { it.endsWith("workbook.xml") })
    }

    @Test
    fun bundledXlsxIsParsedByAndroidXmlImplementation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val result = context.assets.open("import_template.xlsx").use(ExcelParser::parse)

        // 空模板没有课程是预期的，但必须成功解析到表头；旧故障会在这里返回
        // “This parser does not support specification ...”。
        assertTrue("Android xlsx parser errors=${result.errors}", result.errors.isEmpty())
    }
}
