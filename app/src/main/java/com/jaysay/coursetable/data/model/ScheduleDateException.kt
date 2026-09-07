package com.jaysay.coursetable.data.model

import com.jaysay.coursetable.util.TimeUtils
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

enum class ScheduleExceptionType { DAY_OFF, COURSE_CANCELLED, MAKEUP }

/** 具体日期的校历例外，可表示整日停课、单课停课或补课。 */
data class ScheduleDateException(
    val id: String = UUID.randomUUID().toString(),
    val date: String,
    val type: ScheduleExceptionType,
    val courseSeriesKey: String? = null,
    val makeupCourse: Course? = null,
    val title: String = ""
)

data class ResolvedDateCourse(val course: Course, val week: Int, val date: LocalDate, val isMakeup: Boolean)

object ScheduleDateResolver {
    fun coursesOn(
        courses: List<Course>,
        semesterStart: String,
        totalWeeks: Int,
        excludedWeeks: Set<Int>,
        exceptions: List<ScheduleDateException>,
        date: LocalDate
    ): List<ResolvedDateCourse> {
        val start = TimeUtils.semesterWeekStartOrNull(semesterStart) ?: return emptyList()
        val dayOffset = ChronoUnit.DAYS.between(start, date)
        val dateKey = date.toString()
        val week = TimeUtils.semesterWeekOrNull(semesterStart, totalWeeks, date)
            ?: return makeupOnly(exceptions, date, dateKey)
        val scheduleDay = (dayOffset % 7 + 1).toInt()
        var isDayOff = false
        val cancelledSeries = mutableSetOf<String>()
        val makeup = mutableListOf<ResolvedDateCourse>()
        exceptions.forEach { item ->
            if (item.date != dateKey) return@forEach
            when (item.type) {
                ScheduleExceptionType.DAY_OFF -> isDayOff = true
                ScheduleExceptionType.COURSE_CANCELLED -> item.courseSeriesKey?.let(cancelledSeries::add)
                ScheduleExceptionType.MAKEUP -> item.makeupCourse?.let {
                    makeup += ResolvedDateCourse(it, 0, date, true)
                }
            }
        }
        val regular = if (isDayOff || week in excludedWeeks) emptyList() else courses.asSequence()
            .filter { week in it.weeks && it.dayOfWeek == scheduleDay && it.seriesKey !in cancelledSeries }
            .map { ResolvedDateCourse(it, week, date, false) }
            .toList()
        val resolved = ArrayList<ResolvedDateCourse>(regular.size + makeup.size)
        resolved.addAll(regular)
        resolved.addAll(makeup)
        resolved.sortWith(compareBy<ResolvedDateCourse> { it.course.startPeriod }.thenBy { it.course.endPeriod })
        return resolved
    }

    fun normalize(
        exceptions: List<ScheduleDateException>,
        maxItems: Int = 500
    ): List<ScheduleDateException> = exceptions.asSequence().mapNotNull { item ->
        val date = runCatching { LocalDate.parse(item.date) }.getOrNull() ?: return@mapNotNull null
        when (item.type) {
            ScheduleExceptionType.DAY_OFF -> item.copy(
                date = date.toString(), courseSeriesKey = null, makeupCourse = null, title = item.title.trim().take(80)
            )
            ScheduleExceptionType.COURSE_CANCELLED -> item.courseSeriesKey?.takeIf(String::isNotBlank)?.let {
                item.copy(date = date.toString(), courseSeriesKey = it, makeupCourse = null, title = item.title.trim().take(80))
            }
            ScheduleExceptionType.MAKEUP -> item.makeupCourse?.let {
                item.copy(date = date.toString(), courseSeriesKey = it.seriesKey, makeupCourse = it, title = item.title.trim().take(80))
            }
        }
    }.distinctBy(ScheduleDateException::id).sortedBy(ScheduleDateException::date).take(maxItems).toList()

    private fun makeupOnly(
        exceptions: List<ScheduleDateException>,
        date: LocalDate,
        dateKey: String = date.toString()
    ): List<ResolvedDateCourse> =
        exceptions.asSequence()
            .filter { it.date == dateKey && it.type == ScheduleExceptionType.MAKEUP }
            .mapNotNull { it.makeupCourse }
            .map { ResolvedDateCourse(it, 0, date, true) }
            .toList()
}
