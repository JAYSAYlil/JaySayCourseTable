package com.jaysay.coursetable.data.parser

import com.jaysay.coursetable.data.model.Course
import com.jaysay.coursetable.data.model.CourseSeriesIds
import com.jaysay.coursetable.util.TimeUtils
import java.nio.charset.StandardCharsets
import java.util.UUID

data class TextColumnMapping(
    val courseName: Int,
    val dayOfWeek: Int,
    val periods: Int,
    val weeks: Int,
    val teacher: Int? = null,
    val classroom: Int? = null
) {
    val requiredColumnCount: Int
        get() = listOfNotNull(courseName, dayOfWeek, periods, weeks, teacher, classroom).maxOrNull()?.plus(1) ?: 0
}

/** 对制表符、竖线或逗号分隔文本按用户指定列进行解析。列下标从 0 开始。 */
object MappedTextScheduleParser {
    fun parse(text: String, mapping: TextColumnMapping, totalWeeks: Int): TextScheduleParser.ParseResult {
        require(totalWeeks in 1..30) { "学期周数无效" }
        require(mapping.requiredColumnCount in 1..50) { "列映射无效" }
        val courses = mutableListOf<Course>()
        val errors = mutableListOf<String>()
        text.lineSequence().map(String::trim).filter(String::isNotEmpty).take(5_001).forEachIndexed { index, line ->
            if (index >= 5_000) {
                errors += "内容超过 5000 行，后续已忽略"
                return@forEachIndexed
            }
            val columns = split(line)
            if (columns.size < mapping.requiredColumnCount) {
                errors += "第 ${index + 1} 行列数不足"
                return@forEachIndexed
            }
            val name = columns[mapping.courseName].trim()
            val day = parseDay(columns[mapping.dayOfWeek])
            val periodRange = parsePeriods(columns[mapping.periods])
            val weeks = TimeUtils.parseWeeks(columns[mapping.weeks]).filter { it in 1..totalWeeks }
            if (name.isBlank() || day == null || periodRange == null || weeks.isEmpty()) {
                errors += "第 ${index + 1} 行的课程名、星期、节次或周次无效"
                return@forEachIndexed
            }
            val seed = "$index|${columns.joinToString("|")}".toByteArray(StandardCharsets.UTF_8)
            val id = UUID.nameUUIDFromBytes(seed).toString()
            courses += Course(
                courseId = id,
                courseName = name,
                classNumber = "",
                department = "",
                credits = 0f,
                weeks = weeks.distinct().sorted(),
                dayOfWeek = day,
                startPeriod = periodRange.first,
                endPeriod = periodRange.last,
                teacher = mapping.teacher?.let(columns::get).orEmpty().trim(),
                classroom = mapping.classroom?.let(columns::get).orEmpty().trim(),
                courseType = "",
                courseCategory = "",
                isOnline = false,
                assessmentMethod = "",
                seriesId = CourseSeriesIds.idForSeed(id)
            )
        }
        return TextScheduleParser.ParseResult(courses, errors)
    }

    fun previewColumns(firstNonBlankLine: String): List<String> = split(firstNonBlankLine).map(String::trim)

    private fun split(line: String): List<String> = when {
        '\t' in line -> line.split('\t')
        '|' in line -> line.split('|')
        '，' in line -> line.split('，')
        else -> line.split(',')
    }

    private fun parseDay(value: String): Int? {
        val compact = value.trim().replace("星期", "周")
        val names = mapOf("一" to 1, "二" to 2, "三" to 3, "四" to 4, "五" to 5, "六" to 6, "日" to 7, "天" to 7)
        names.entries.firstOrNull { compact.contains(it.key) }?.let { return it.value }
        return compact.filter(Char::isDigit).toIntOrNull()?.takeIf { it in 1..7 }
    }

    private fun parsePeriods(value: String): IntRange? {
        val numbers = Regex("\\d+").findAll(value).map { it.value.toInt() }.toList()
        val start = numbers.getOrNull(0) ?: return null
        val end = numbers.getOrNull(1) ?: start
        return if (start in 1..30 && end in start..30) start..end else null
    }
}
