package com.jaysay.coursetable.data.model

import com.jaysay.coursetable.util.TimeUtils
import java.time.LocalDate

/** 同一份学期安排状态供课表、设置页与小组件展示，避免各界面各自解释规则。 */
data class AcademicCalendarDayStatus(
    val date: LocalDate,
    val week: Int?,
    val weekLabel: String?,
    val suspendedWeek: Boolean,
    val dayOff: Boolean,
    val dayOffTitle: String?,
    val cancelledCount: Int,
    val makeupCount: Int
) {
    val dateAdjustmentCount: Int get() = (if (dayOff) 1 else 0) + cancelledCount + makeupCount
    val hasDateAdjustment: Boolean get() = dateAdjustmentCount > 0
    val hasCalendarContext: Boolean
        get() = suspendedWeek || !weekLabel.isNullOrBlank() || hasDateAdjustment
}

data class AcademicCalendarWeekStatus(
    val week: Int,
    val label: String?,
    val suspended: Boolean,
    val dayOffCount: Int,
    val cancelledCount: Int,
    val makeupCount: Int
) {
    val dateAdjustmentCount: Int get() = dayOffCount + cancelledCount + makeupCount
    val hasCalendarContext: Boolean
        get() = suspended || !label.isNullOrBlank() || dateAdjustmentCount > 0
}

object AcademicCalendarStatusResolver {
    fun day(
        date: LocalDate,
        semesterStart: String,
        totalWeeks: Int,
        excludedWeeks: Set<Int>,
        exceptions: List<ScheduleDateException>,
        weekLabels: Map<Int, String>
    ): AcademicCalendarDayStatus {
        val week = TimeUtils.semesterWeekOrNull(semesterStart, totalWeeks, date)
        val dateKey = date.toString()
        var dayOff: ScheduleDateException? = null
        var cancelledCount = 0
        var makeupCount = 0
        exceptions.forEach { item ->
            if (item.date != dateKey) return@forEach
            when (item.type) {
                ScheduleExceptionType.DAY_OFF -> if (dayOff == null) dayOff = item
                ScheduleExceptionType.COURSE_CANCELLED -> cancelledCount += 1
                ScheduleExceptionType.MAKEUP -> makeupCount += 1
            }
        }
        return AcademicCalendarDayStatus(
            date = date,
            week = week,
            weekLabel = week?.let(weekLabels::get)?.trim()?.takeIf(String::isNotEmpty),
            suspendedWeek = week != null && week in excludedWeeks,
            dayOff = dayOff != null,
            dayOffTitle = dayOff?.title?.trim()?.takeIf(String::isNotEmpty),
            cancelledCount = cancelledCount,
            makeupCount = makeupCount
        )
    }

    fun week(
        week: Int,
        semesterStart: String,
        totalWeeks: Int,
        excludedWeeks: Set<Int>,
        exceptions: List<ScheduleDateException>,
        weekLabels: Map<Int, String>
    ): AcademicCalendarWeekStatus {
        val start = TimeUtils.semesterWeekStartOrNull(semesterStart)
        val dates = if (start != null && week in 1..totalWeeks) {
            (0L..6L).map { start.plusDays((week - 1L) * 7L + it).toString() }.toSet()
        } else emptySet()
        var dayOffCount = 0
        var cancelledCount = 0
        var makeupCount = 0
        exceptions.forEach { item ->
            if (item.date !in dates) return@forEach
            when (item.type) {
                ScheduleExceptionType.DAY_OFF -> dayOffCount += 1
                ScheduleExceptionType.COURSE_CANCELLED -> cancelledCount += 1
                ScheduleExceptionType.MAKEUP -> makeupCount += 1
            }
        }
        return AcademicCalendarWeekStatus(
            week = week,
            label = weekLabels[week]?.trim()?.takeIf(String::isNotEmpty),
            suspended = week in excludedWeeks,
            dayOffCount = dayOffCount,
            cancelledCount = cancelledCount,
            makeupCount = makeupCount
        )
    }
}
