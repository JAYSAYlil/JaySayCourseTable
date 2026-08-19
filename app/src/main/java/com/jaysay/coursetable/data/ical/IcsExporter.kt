package com.jaysay.coursetable.data.ical

import com.jaysay.coursetable.data.model.Course
import com.jaysay.coursetable.data.preferences.PeriodTime
import com.jaysay.coursetable.data.repository.TableData
import com.jaysay.coursetable.util.TimeUtils
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * iCal（.ics）导出：把课表课程展开为逐周 VEVENT（本地时间，不带时区后缀），
 * 纯本地生成，无网络请求；供系统日历/其他设备导入。
 */
object IcsExporter {

    private const val PRODUCT_ID = "-//JaySayCourseTable//CN//"
    private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

    fun export(table: TableData): String = buildString {
        appendLine("BEGIN:VCALENDAR")
        appendLine("VERSION:2.0")
        appendLine("PRODID:$PRODUCT_ID")
        appendLine("CALSCALE:GREGORIAN")
        appendLine("METHOD:PUBLISH")
        append("X-WR-CALNAME:").appendLine(escape(table.name))

        table.courses.forEach { course ->
            course.weeks.forEach { week ->
                if (week in table.excludedWeeks) return@forEach
                val date = runCatching { LocalDate.parse(table.semesterStart) }
                    .getOrNull()?.plusDays((week - 1).toLong() * 7 + (course.dayOfWeek - 1))
                    ?: return@forEach
                val start = table.periods.getOrNull(course.startPeriod - 1)?.start ?: "00:00"
                val end = table.periods.getOrNull(course.endPeriod - 1)?.end ?: start
                appendLine("BEGIN:VEVENT")
                appendLine("UID:${course.seriesId}-$week@jaysay-coursetable")
                append("DTSTART:").appendLine(date.format(dateFormat) + "T" + start.replace(":", "") + "00")
                append("DTEND:").appendLine(date.format(dateFormat) + "T" + end.replace(":", "") + "00")
                append("SUMMARY:").appendLine(escape(course.courseName))
                if (course.teacher.isNotBlank()) {
                    append("DESCRIPTION:").appendLine(escape("教师：${course.teacher}"))
                }
                if (course.classroom.isNotBlank()) {
                    append("LOCATION:").appendLine(escape(course.classroom))
                }
                appendLine("TRANSP:OPAQUE")
                appendLine("END:VEVENT")
            }
        }
        append("END:VCALENDAR")
    }

    /** RFC 5545 文本字段转义：逗号、分号、反斜杠、换行。 */
    private fun escape(value: String): String = value
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\n", "\\n")
}
