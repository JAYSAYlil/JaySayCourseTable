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
        if (dayOffset < 0) return makeupOnly(exceptions, date)
        val week = (dayOffset / 7 + 1).toInt()
        if (week !in 1..totalWeeks) return makeupOnly(exceptions, date)
        val scheduleDay = (dayOffset % 7 + 1).toInt()
        val dayExceptions = exceptions.filter { it.date == date.toString() }
        val isDayOff = dayExceptions.any { it.type == ScheduleExceptionType.DAY_OFF }
        val cancelledSeries = dayExceptions.asSequence()
            .filter { it.type == ScheduleExceptionType.COURSE_CANCELLED }
            .mapNotNull(ScheduleDateException::courseSeriesKey)
            .toSet()
        val regular = if (isDayOff || week in excludedWeeks) emptyList() else courses.asSequence()
            .filter { week in it.weeks && it.dayOfWeek == scheduleDay && it.seriesKey !in cancelledSeries }
            .map { ResolvedDateCourse(it, week, date, false) }
            .toList()
        return (regular + makeupOnly(dayExceptions, date)).sortedWith(
            compareBy<ResolvedDateCourse> { it.course.startPeriod }.thenBy { it.course.endPeriod }
        )
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

    private fun makeupOnly(exceptions: List<ScheduleDateException>, date: LocalDate): List<ResolvedDateCourse> =
        exceptions.asSequence()
            .filter { it.date == date.toString() && it.type == ScheduleExceptionType.MAKEUP }
            .mapNotNull { it.makeupCourse }
            .map { ResolvedDateCourse(it, 0, date, true) }
            .toList()
}
