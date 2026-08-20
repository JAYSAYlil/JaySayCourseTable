package com.jaysay.coursetable.data.parser

import com.jaysay.coursetable.data.model.Course
import com.jaysay.coursetable.util.TimeUtils

/**
 * 文本粘贴导入：支持把网页课表复制为文本粘贴进来。
 *
 * 每行一条课程，空白/逗号/顿号/分号分隔，顺序为：
 * `星期 节次 课程名 [教室] [教师] [周次]`
 * 示例：
 * - `周一 1-2节 高等数学 教1-101 张老师 1-16周`
 * - `星期二 第3-4节 大学英语 外语楼201 李老师 1,3,5,7周`
 * - `周三 5节 体育 田径场 王老师 1-8周(单)`
 * 周次缺省为整学期（1-20）；教师/教室可省略。
 */
object TextScheduleParser {

    data class ParseResult(
        val courses: List<Course>,
        val errors: List<String>
    )

    private const val MAX_REPORTED_ERRORS = 20
    private const val DEFAULT_TOTAL_WEEKS = 20

    fun parse(text: String, totalWeeks: Int = DEFAULT_TOTAL_WEEKS): ParseResult {
        val safeTotalWeeks = totalWeeks.coerceIn(1, 30)
        val courses = mutableListOf<Course>()
        val errors = mutableListOf<String>()
        text.lines().forEachIndexed { index, raw ->
            val line = raw.trim()
            if (line.isBlank() || line.startsWith("#")) return@forEachIndexed
            runCatching { parseLine(line, safeTotalWeeks) }
                .onSuccess { course -> if (course != null) courses.add(course) }
                .onFailure { error ->
                    if (errors.size < MAX_REPORTED_ERRORS) {
                        errors.add("第${index + 1}行：${error.message ?: "格式不正确"}")
                    }
                }
        }
        // 不在解析阶段按时段去重：同一时段可能确实存在不同课程，交给导入预览展示冲突。
        return ParseResult(courses, errors)
    }

    private fun parseLine(line: String, totalWeeks: Int): Course? {
        var rest = line

        // 周次：整体提取 "1-16周" / "1,3,5,7周" / "2-8周(单)"，避免枚举逗号被字段分隔符切散
        val weekMatch = Regex("\\d[\\d\\s,，、~～\\-至]*周(?:\\([单双]\\)|（[单双]）)?").find(line)
        val weeks = weekMatch?.value?.let { TimeUtils.parseWeeks(it) } ?: (1..totalWeeks).toList()
        if (weeks.isEmpty()) error("无法识别上课周次")
        if (weekMatch != null) rest = rest.replace(weekMatch.value, " ")

        // 节次：提取 "1-2节" / "第3-4节" / "5节"
        val periodMatch = Regex("第?\\d+\\s*-\\s*\\d+节|第?\\d+节").find(rest)
            ?: error("缺少节次信息（如 1-2节）")
        val periodToken = periodMatch.value
        val range = Regex("(\\d+)\\s*-\\s*(\\d+)").find(periodToken)
        val startPeriod = range?.groupValues?.get(1)?.toInt()
            ?: Regex("(\\d+)").find(periodToken)?.groupValues?.get(1)?.toInt()
            ?: error("无法识别节次")
        val endPeriod = range?.groupValues?.get(2)?.toInt() ?: startPeriod
        if (startPeriod !in 1..30 || endPeriod !in startPeriod..30) {
            error("节次必须在 1-30 之间")
        }
        rest = rest.replace(periodMatch.value, " ")

        // 星期：支持 "周一/星期一/1"
        val tokens = tokenize(rest)
        val dayTokenIndex = tokens.indexOfFirst { TimeUtils.parseDayOfWeekOrNull(it) != null }
        val day = tokens.getOrNull(dayTokenIndex)?.let(TimeUtils::parseDayOfWeekOrNull)
            ?: error("无法识别星期（如 周一、星期一、1）")

        // 剩余 token 按顺序：课程名、教室、教师
        val remaining = tokens.filterIndexed { index, _ -> index != dayTokenIndex }
        val courseName = remaining.getOrNull(0)?.trim() ?: error("缺少课程名")
        val second = remaining.getOrNull(1)?.trim().orEmpty()
        val third = remaining.getOrNull(2)?.trim().orEmpty()
        val onlyOptionalFieldIsTeacher = third.isEmpty() && looksLikeTeacher(second)
        val classroom = if (onlyOptionalFieldIsTeacher) "" else second
        val teacher = if (onlyOptionalFieldIsTeacher) second else third

        return Course(
            courseId = "",
            courseName = courseName,
            classNumber = "",
            department = "",
            credits = 0f,
            weeks = weeks,
            dayOfWeek = day,
            startPeriod = startPeriod,
            endPeriod = endPeriod,
            teacher = teacher,
            classroom = classroom,
            courseType = "",
            courseCategory = "",
            isOnline = false,
            assessmentMethod = "",
            seriesId = ""
        )
    }

    private fun tokenize(text: String): List<String> =
        text.split(Regex("[\\s,，、;；]+")).filter { it.isNotBlank() }

    private fun looksLikeTeacher(value: String): Boolean =
        value.endsWith("老师") || value.endsWith("教授") || value.endsWith("讲师")
}
