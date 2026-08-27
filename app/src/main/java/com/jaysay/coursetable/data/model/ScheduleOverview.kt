package com.jaysay.coursetable.data.model

import com.jaysay.coursetable.data.preferences.PeriodTime
import com.jaysay.coursetable.util.TimeUtils
import java.time.LocalDate
import java.util.Locale

enum class TodayAgendaPhase {
    OUTSIDE_SEMESTER,
    NO_COURSES,
    BEFORE_FIRST,
    IN_CLASS,
    BETWEEN_CLASSES,
    FINISHED,
    INVALID_TIME
}

data class AgendaCourseSlot(
    val course: Course,
    val startMinute: Int,
    val endMinute: Int
)

data class TodayAgenda(
    val phase: TodayAgendaPhase,
    val week: Int? = null,
    val current: AgendaCourseSlot? = null,
    val next: AgendaCourseSlot? = null,
    val remainingCount: Int = 0,
    val invalidCourseCount: Int = 0
)

/** 今日课程状态的纯 Kotlin 计算，不依赖 Compose 或系统时钟，便于覆盖边界测试。 */
object TodayAgendaCalculator {
    fun calculate(
        courses: List<Course>,
        periods: List<PeriodTime>,
        semesterStart: String,
        totalWeeks: Int,
        date: LocalDate,
        minuteOfDay: Int,
        excludedWeeks: Set<Int> = emptySet(),
        exceptions: List<ScheduleDateException> = emptyList()
    ): TodayAgenda {
        val week = semesterWeek(semesterStart, totalWeeks, date)
        val todayCourses = ScheduleDateResolver.coursesOn(
            courses, semesterStart, totalWeeks, excludedWeeks, exceptions, date
        ).map(ResolvedDateCourse::course)
        if (week == null && todayCourses.isEmpty()) return TodayAgenda(TodayAgendaPhase.OUTSIDE_SEMESTER)
        if (todayCourses.isEmpty()) return TodayAgenda(TodayAgendaPhase.NO_COURSES, week = week)

        var invalidCount = 0
        val slots = todayCourses.mapNotNull { course ->
            val start = periods.getOrNull(course.startPeriod - 1)?.start?.let(TimeUtils::parseMinuteOfDay)
            val end = periods.getOrNull(course.endPeriod - 1)?.end?.let(TimeUtils::parseMinuteOfDay)
            if (start == null || end == null || end <= start) {
                invalidCount += 1
                null
            } else {
                AgendaCourseSlot(course, start, end)
            }
        }.sortedWith(compareBy<AgendaCourseSlot> { it.startMinute }.thenBy { it.endMinute })

        if (slots.isEmpty()) {
            return TodayAgenda(
                phase = TodayAgendaPhase.INVALID_TIME,
                week = week,
                invalidCourseCount = invalidCount
            )
        }

        val now = minuteOfDay.coerceIn(0, 23 * 60 + 59)
        val current = slots.firstOrNull { now >= it.startMinute && now < it.endMinute }
        val next = slots.firstOrNull { it.startMinute > now }
        val phase = when {
            current != null -> TodayAgendaPhase.IN_CLASS
            now < slots.first().startMinute -> TodayAgendaPhase.BEFORE_FIRST
            next != null -> TodayAgendaPhase.BETWEEN_CLASSES
            else -> TodayAgendaPhase.FINISHED
        }
        return TodayAgenda(
            phase = phase,
            week = week,
            current = current,
            next = if (current == null) next else slots.firstOrNull { it.startMinute >= current.endMinute },
            remainingCount = slots.count { it.endMinute > now },
            invalidCourseCount = invalidCount
        )
    }

    fun semesterWeek(semesterStart: String, totalWeeks: Int, date: LocalDate): Int? =
        // 委托到 TimeUtils 的唯一周次公式，避免语义漂移。
        TimeUtils.semesterWeekOrNull(semesterStart, totalWeeks, date)
}

/** 本地过滤只改变显示集合；空查询直接返回原列表实例。 */
object CourseSearch {
    fun filter(courses: List<Course>, query: String): List<Course> {
        val terms = query.trim().lowercase(Locale.ROOT).split(Regex("\\s+")).filter(String::isNotBlank)
        if (terms.isEmpty()) return courses
        return courses.filter { course ->
            val searchable = listOf(course.courseName, course.teacher, course.classroom)
                .joinToString("\n")
                .lowercase(Locale.ROOT)
            terms.all(searchable::contains)
        }
    }
}
