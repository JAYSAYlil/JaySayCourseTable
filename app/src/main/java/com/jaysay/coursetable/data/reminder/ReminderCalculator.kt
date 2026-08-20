package com.jaysay.coursetable.data.reminder

import com.jaysay.coursetable.data.model.Course
import com.jaysay.coursetable.data.model.TodayAgendaCalculator
import com.jaysay.coursetable.data.preferences.PeriodTime
import com.jaysay.coursetable.util.TimeUtils
import java.time.LocalDate
import java.time.LocalDateTime

/** 某一节课在某一周的具体上课实例（日期 + 起止分钟）。 */
data class CourseInstance(
    val course: Course,
    val week: Int,
    val date: LocalDate,
    val startMinute: Int,
    val endMinute: Int
)

/**
 * 上课提醒的纯 Kotlin 计算：把课程/周次/节次时间换算成具体日期与时刻，
 * 不依赖 Android 或系统时钟，便于 JVM 边界测试。
 */
object ReminderCalculator {

    /**
     * 计算某课表在指定周的所有课程实例。
     * 停课周直接返回空；节次时间解析失败的课程跳过。
     */
    fun courseInstances(
        courses: List<Course>,
        semesterStart: String,
        periods: List<PeriodTime>,
        week: Int,
        excludedWeeks: Set<Int> = emptySet()
    ): List<CourseInstance> {
        if (week in excludedWeeks) return emptyList()
        val start = runCatching { LocalDate.parse(semesterStart) }.getOrNull() ?: return emptyList()
        return courses
            .filter { week in it.weeks }
            .mapNotNull { course ->
                val startPeriod = periods.getOrNull(course.startPeriod - 1) ?: return@mapNotNull null
                val endPeriod = periods.getOrNull(course.endPeriod - 1) ?: return@mapNotNull null
                val startMinute = TimeUtils.parseMinuteOfDay(startPeriod.start) ?: return@mapNotNull null
                val endMinute = TimeUtils.parseMinuteOfDay(endPeriod.end) ?: return@mapNotNull null
                if (endMinute <= startMinute) return@mapNotNull null
                val date = start.plusDays((week - 1).toLong() * 7 + (course.dayOfWeek - 1))
                CourseInstance(course, week, date, startMinute, endMinute)
            }
            .sortedWith(compareBy<CourseInstance> { it.date }.thenBy { it.startMinute })
    }

    /**
     * 计算从 [fromDate] 开始、严格落在未来 [days] 个自然日内的课程实例。
     * 用日期边界过滤，避免跨周时把第 8～14 天的课程提前排进本轮闹钟窗口。
     */
    fun upcomingInstances(
        courses: List<Course>,
        semesterStart: String,
        totalWeeks: Int,
        periods: List<PeriodTime>,
        fromDate: LocalDate,
        days: Long,
        excludedWeeks: Set<Int> = emptySet()
    ): List<CourseInstance> {
        if (days <= 0) return emptyList()
        val lastDate = fromDate.plusDays(days - 1)
        val weeks = (0 until days).mapNotNull { offset ->
            TodayAgendaCalculator.semesterWeek(
                semesterStart = semesterStart,
                totalWeeks = totalWeeks,
                date = fromDate.plusDays(offset)
            )
        }.distinct()
        return weeks.flatMap { week ->
            courseInstances(courses, semesterStart, periods, week, excludedWeeks)
        }.filter { it.date in fromDate..lastDate }
            .sortedWith(compareBy<CourseInstance> { it.date }.thenBy { it.startMinute })
    }

    /** 提醒触发时刻 = 上课开始时刻 - 提前分钟数（允许跨零点，由调用方过滤过去的时刻）。 */
    fun reminderAt(instance: CourseInstance, advanceMinutes: Int): LocalDateTime =
        instance.date.atTime(instance.startMinute / 60, instance.startMinute % 60)
            .minusMinutes(advanceMinutes.toLong())
}
