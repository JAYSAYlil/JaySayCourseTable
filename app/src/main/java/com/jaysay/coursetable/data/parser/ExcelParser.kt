package com.jaysay.coursetable.data.parser

import android.content.Context
import android.net.Uri
import com.jaysay.coursetable.data.model.Course
import com.jaysay.coursetable.util.TimeUtils
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.FormulaEvaluator
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.Locale

object ExcelParser {
    private val requiredColumns = listOf("课程号", "课程名", "上课星期", "开始节次", "结束节次", "上课周次")
    private val headerAliases = mapOf(
        "课程编号" to "课程号", "课程代码" to "课程号",
        "课程名称" to "课程名",
        "星期" to "上课星期", "上课日期" to "上课星期",
        "起始节次" to "开始节次", "开始小节" to "开始节次",
        "终止节次" to "结束节次", "结束小节" to "结束节次",
        "周次" to "上课周次", "教学周" to "上课周次",
        "教师" to "上课教师", "任课教师" to "上课教师",
        "教室" to "教室名称", "上课教室" to "教室名称"
    )

    data class ParseResult(
        val courses: List<Course>,
        val errors: List<String>
    )

    fun parse(context: Context, uri: Uri): ParseResult {
        val stream = context.contentResolver.openInputStream(uri)
            ?: return ParseResult(emptyList(), listOf("无法打开文件"))
        // POI 会把整本工作簿载入内存，与备份导入对齐设置大小护栏，避免超大文件导致 OOM。
        return BoundedInputStream(stream, MAX_FILE_BYTES).use(::parse)
    }

    /** 独立于 Android Uri 的入口，便于自动化测试真实 Excel。 */
    fun parse(inputStream: InputStream): ParseResult {
        return try {
            WorkbookFactory.create(inputStream).use { workbook ->
                if (workbook.numberOfSheets == 0) return ParseResult(emptyList(), listOf("文件中没有工作表"))
                val sheet = workbook.getSheetAt(0)
                val formatter = DataFormatter(Locale.CHINA)
                val evaluator = workbook.creationHelper.createFormulaEvaluator()
                val header = findHeader(sheet, formatter, evaluator)
                    ?: return ParseResult(emptyList(), listOf("前 10 行中未找到课表表头"))
                val missing = requiredColumns.filterNot(header.columns::containsKey)
                if (missing.isNotEmpty()) {
                    return ParseResult(emptyList(), missing.map { "缺少必要列：$it" })
                }

                val courses = mutableListOf<Course>()
                val errors = mutableListOf<String>()
                val lastRow = minOf(sheet.lastRowNum, MAX_ROWS + header.rowIndex)
                for (rowIndex in (header.rowIndex + 1)..lastRow) {
                    val row = sheet.getRow(rowIndex) ?: continue
                    if (row.physicalNumberOfCells == 0) continue
                    parseRow(row, header.columns, formatter, evaluator)
                        .onSuccess { course -> if (course != null) courses.add(course) }
                        .onFailure { error ->
                            if (errors.size < MAX_REPORTED_ERRORS) {
                                errors.add("第${rowIndex + 1}行：${error.message ?: "格式不正确"}")
                            }
                        }
                }
                if (sheet.lastRowNum > lastRow) errors.add("文件超过 $MAX_ROWS 行，超出部分未读取")
                ParseResult(courses.distinctBy { it.uniqueKey }, errors)
            }
        } catch (error: BoundedInputStream.FileTooLargeException) {
            ParseResult(emptyList(), listOf("文件超过 $MAX_FILE_MB MB，未读取"))
        } catch (error: Exception) {
            ParseResult(emptyList(), listOf("文件解析失败：${error.message ?: error.javaClass.simpleName}"))
        }
    }

    /**
     * 限制总读取字节数的流：超出上限立即抛错，避免把整本超大工作簿读进内存后才失败。
     */
    private class BoundedInputStream(
        input: InputStream,
        private val maxBytes: Int
    ) : FilterInputStream(input) {
        private var count = 0

        class FileTooLargeException : IOException()

        override fun read(): Int {
            val value = super.read()
            if (value >= 0 && ++count > maxBytes) throw FileTooLargeException()
            return value
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = super.read(b, off, len)
            if (n > 0) {
                count += n
                if (count > maxBytes) throw FileTooLargeException()
            }
            return n
        }
    }

    private data class Header(val rowIndex: Int, val columns: Map<String, Int>)

    private fun findHeader(
        sheet: org.apache.poi.ss.usermodel.Sheet,
        formatter: DataFormatter,
        evaluator: FormulaEvaluator
    ): Header? {
        var best: Header? = null
        var bestMatches = 0
        for (rowIndex in 0..minOf(sheet.lastRowNum, 9)) {
            val row = sheet.getRow(rowIndex) ?: continue
            val columns = mutableMapOf<String, Int>()
            row.forEach { cell ->
                val raw = formatter.formatCellValue(cell, evaluator)
                val canonical = canonicalHeader(raw)
                if (canonical.isNotEmpty()) columns.putIfAbsent(canonical, cell.columnIndex)
            }
            val matches = requiredColumns.count(columns::containsKey)
            if (matches > bestMatches) {
                best = Header(rowIndex, columns)
                bestMatches = matches
            }
            if (matches == requiredColumns.size) return Header(rowIndex, columns)
        }
        return best?.takeIf { bestMatches >= 3 }
    }

    private fun parseRow(
        row: Row,
        columns: Map<String, Int>,
        formatter: DataFormatter,
        evaluator: FormulaEvaluator
    ): Result<Course?> = runCatching {
        fun value(key: String): String = columns[key]?.let { column ->
            row.getCell(column)?.let { formatter.formatCellValue(it, evaluator).trim() }
        }.orEmpty()

        val courseId = value("课程号")
        val courseName = value("课程名")
        if (courseId.isBlank() && courseName.isBlank()) return@runCatching null
        require(courseId.isNotBlank()) { "课程号为空" }
        require(courseName.isNotBlank()) { "课程名为空" }

        val day = TimeUtils.parseDayOfWeekOrNull(value("上课星期"))
            ?: error("无法识别上课星期“${value("上课星期")}”")
        val start = TimeUtils.parsePeriodOrNull(value("开始节次"))
            ?: error("无法识别开始节次“${value("开始节次")}”")
        val end = TimeUtils.parsePeriodOrNull(value("结束节次"))
            ?: error("无法识别结束节次“${value("结束节次")}”")
        require(end >= start) { "结束节次不能早于开始节次" }
        require(end <= MAX_PERIOD) { "节次不能超过 $MAX_PERIOD" }

        val weekText = value("上课周次")
        val weeks = TimeUtils.parseWeeks(weekText)
        require(weeks.isNotEmpty()) { "无法识别上课周次“$weekText”" }
        require(weeks.all { it in 1..MAX_WEEK }) { "周次必须在 1-$MAX_WEEK 之间" }

        Course(
            courseId = courseId,
            courseName = courseName,
            classNumber = value("课序号"),
            department = value("开课单位"),
            credits = value("学分").toFloatOrNull() ?: 0f,
            weeks = weeks,
            dayOfWeek = day,
            startPeriod = start,
            endPeriod = end,
            teacher = value("上课教师"),
            classroom = value("教室名称"),
            courseType = value("课程性质"),
            courseCategory = value("课程类别"),
            isOnline = value("是否线上教学").let { it == "是" || it.equals("true", true) },
            assessmentMethod = value("重修重考")
        )
    }

    private fun canonicalHeader(value: String): String {
        val normalized = value.replace("\uFEFF", "").replace(Regex("\\s+"), "").trim()
        return headerAliases[normalized] ?: normalized
    }

    private const val MAX_ROWS = 5_000
    private const val MAX_REPORTED_ERRORS = 20
    private const val MAX_WEEK = 30
    private const val MAX_PERIOD = 30
    private const val MAX_FILE_MB = 20
    private const val MAX_FILE_BYTES = MAX_FILE_MB * 1024 * 1024
}
