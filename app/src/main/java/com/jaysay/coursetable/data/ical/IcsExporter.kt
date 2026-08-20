package com.jaysay.coursetable.data.ical

import com.jaysay.coursetable.data.model.ScheduleDateResolver
import com.jaysay.coursetable.data.repository.TableData
import com.jaysay.coursetable.util.TimeUtils
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * iCal（.ics）导出：把课表课程展开为逐周 VEVENT（本地时间，不带时区后缀），
 * 纯本地生成，无网络请求；供系统日历/其他设备导入。
 */
object IcsExporter {

    private const val PRODUCT_ID = "-//JaySayCourseTable//CN//"
    private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val stampFormat: DateTimeFormatter = DateTimeFormatter
        .ofPattern("yyyyMMdd'T'HHmmss'Z'")
        .withZone(ZoneOffset.UTC)

    fun export(table: TableData, generatedAt: Instant = Instant.now()): String {
        val lines = mutableListOf(
            "BEGIN:VCALENDAR",
            "VERSION:2.0",
            "PRODID:$PRODUCT_ID",
            "CALSCALE:GREGORIAN",
            "METHOD:PUBLISH",
            "X-WR-CALNAME:${escape(table.name)}"
        )
        val stamp = stampFormat.format(generatedAt)

        val semesterStart = TimeUtils.semesterWeekStartOrNull(table.semesterStart)
        val regularEnd = semesterStart?.plusDays(table.totalWeeks * 7L - 1L)
        val exceptionDates = table.dateExceptions.mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }
        val exceptionEnd = exceptionDates.maxOrNull()
        val exportEnd = listOfNotNull(regularEnd, exceptionEnd).maxOrNull()
        val exportStart = listOfNotNull(semesterStart, exceptionDates.minOrNull()).minOrNull()
        if (exportStart != null && exportEnd != null) {
            generateSequence(exportStart) { it.plusDays(1) }.takeWhile { !it.isAfter(exportEnd) }.forEach { date ->
                ScheduleDateResolver.coursesOn(
                    table.courses, table.semesterStart, table.totalWeeks,
                    table.excludedWeeks.toSet(), table.dateExceptions, date
                ).forEach courseLoop@ { resolved ->
                val course = resolved.course
                val start = table.periods.getOrNull(course.startPeriod - 1)?.start
                    ?.let(TimeUtils::parseMinuteOfDay) ?: return@courseLoop
                val end = table.periods.getOrNull(course.endPeriod - 1)?.end
                    ?.let(TimeUtils::parseMinuteOfDay) ?: return@courseLoop
                if (end <= start) return@courseLoop
                val datePrefix = date.format(dateFormat) + "T"
                lines += "BEGIN:VEVENT"
                lines += "UID:${escape(course.seriesKey)}-${date.format(dateFormat)}@jaysay-coursetable"
                lines += "DTSTAMP:$stamp"
                lines += "DTSTART:$datePrefix${start.asIcsTime()}"
                lines += "DTEND:$datePrefix${end.asIcsTime()}"
                lines += "SUMMARY:${escape(course.courseName)}"
                if (course.teacher.isNotBlank()) {
                    lines += "DESCRIPTION:${escape("教师：${course.teacher}")}"
                }
                if (course.classroom.isNotBlank()) {
                    lines += "LOCATION:${escape(course.classroom)}"
                }
                lines += "TRANSP:OPAQUE"
                lines += "END:VEVENT"
            }
            }
        }
        lines += "END:VCALENDAR"
        return lines.flatMap(::foldLine).joinToString(separator = "\r\n", postfix = "\r\n")
    }

    /** RFC 5545 文本字段转义：逗号、分号、反斜杠、换行。 */
    private fun escape(value: String): String = value
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\r", "")
        .replace("\n", "\\n")

    /** RFC 5545 内容行最多 75 个 UTF-8 字节，后续行以一个空格继续。 */
    private fun foldLine(line: String): List<String> {
        if (line.toByteArray(Charsets.UTF_8).size <= 75) return listOf(line)
        val result = mutableListOf<String>()
        var content = StringBuilder()
        var bytes = 0
        var continuation = false
        line.codePoints().forEach { codePoint ->
            val token = String(Character.toChars(codePoint))
            val tokenBytes = token.toByteArray(Charsets.UTF_8).size
            val limit = if (continuation) 74 else 75
            if (bytes + tokenBytes > limit && content.isNotEmpty()) {
                result += (if (continuation) " " else "") + content.toString()
                content = StringBuilder()
                bytes = 0
                continuation = true
            }
            content.append(token)
            bytes += tokenBytes
        }
        if (content.isNotEmpty()) result += (if (continuation) " " else "") + content.toString()
        return result
    }

    private fun Int.asIcsTime(): String = "%02d%02d00".format(this / 60, this % 60)
}
