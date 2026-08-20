package com.jaysay.coursetable.data.model

import com.jaysay.coursetable.data.preferences.PeriodTime
import com.jaysay.coursetable.util.TimeUtils
import java.time.LocalDate

/** 日程列表的日期分区。只包含从查询起始日开始、实际有课的日期。 */
enum class AgendaSection(val label: String) {
    TODAY("今天"),
    TOMORROW("明天"),
    THIS_WEEK("本周"),
    UPCOMING("后续")
}

/** 一门课在某个具体日期的一次上课实例。时间不合法时仍保留实例，方便用户发现设置问题。 */
data class AgendaCourseInstance(
    val date: LocalDate,
    val week: Int,
    val course: Course,
    val startMinute: Int?,
    val endMinute: Int?,
    val timeLabel: String,
    val periodLabel: String
)

/** 同一天的日程；无课日期不会生成此模型。 */
data class AgendaDateGroup(
    val section: AgendaSection,
    val date: LocalDate,
    val week: Int,
    val courses: List<AgendaCourseInstance>
)

/**
 * 日程列表的纯计算模型。
 *
 * 课程日期始终按“学期开始日 + (周次 - 1) * 7 + 星期序号 - 1”计算，而非假定开学日
 * 必然是周一；因此导入了非周一开学日期的课表也能得到与课表网格一致的日期。
 */
object AgendaListCalculator {
    fun calculate(
        courses: List<Course>,
        periods: List<PeriodTime>,
        semesterStart: String,
        totalWeeks: Int,
        fromDate: LocalDate,
        excludedWeeks: Set<Int> = emptySet(),
        exceptions: List<ScheduleDateException> = emptyList(),
        searchQuery: String = ""
    ): List<AgendaDateGroup> {
        if (totalWeeks <= 0) return emptyList()
        val start = TimeUtils.semesterWeekStartOrNull(semesterStart) ?: return emptyList()
        val visibleCourseKeys = CourseSearch.filter(
            courses + exceptions.mapNotNull(ScheduleDateException::makeupCourse), searchQuery
        ).map(Course::uniqueKey).toSet()
        val lastRegularDate = start.plusDays(totalWeeks * 7L - 1L)
        val lastExceptionDate = exceptions.mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }.maxOrNull()
        val end = maxOf(lastRegularDate, lastExceptionDate ?: lastRegularDate)
        if (end < fromDate) return emptyList()

        val instances = generateSequence(fromDate) { it.plusDays(1) }
            .takeWhile { !it.isAfter(end) }
            .flatMap { date ->
                ScheduleDateResolver.coursesOn(courses, semesterStart, totalWeeks, excludedWeeks, exceptions, date)
                    .asSequence()
                    .filter { it.course.uniqueKey in visibleCourseKeys }
                    .map { instanceFor(it.course, it.week, date, periods) }
            }
            .sortedWith(
                compareBy<AgendaCourseInstance> { it.date }
                    .thenBy { it.startMinute ?: Int.MAX_VALUE }
                    .thenBy { it.endMinute ?: Int.MAX_VALUE }
                    .thenBy { it.course.courseName }
                    .thenBy { it.course.courseId }
            )
            .toList()

        val endOfCurrentWeek = fromDate.plusDays((7 - fromDate.dayOfWeek.value).toLong())
        return instances.groupBy { it.date }.map { (date, dayCourses) ->
            AgendaDateGroup(
                section = sectionFor(date, fromDate, endOfCurrentWeek),
                date = date,
                week = dayCourses.first().week,
                courses = dayCourses
            )
        }
    }

    private fun instanceFor(
        course: Course,
        week: Int,
        date: LocalDate,
        periods: List<PeriodTime>
    ): AgendaCourseInstance {
        val start = periods.getOrNull(course.startPeriod - 1)?.start
        val end = periods.getOrNull(course.endPeriod - 1)?.end
        val startMinute = start?.let(TimeUtils::parseMinuteOfDay)
        val endMinute = end?.let(TimeUtils::parseMinuteOfDay)
        val validTime = startMinute != null && endMinute != null && endMinute > startMinute
        return AgendaCourseInstance(
            date = date,
            week = week,
            course = course,
            startMinute = startMinute.takeIf { validTime },
            endMinute = endMinute.takeIf { validTime },
            timeLabel = if (validTime) "$start - $end" else "时间未设置",
            periodLabel = TimeUtils.formatPeriodRange(course.startPeriod, course.endPeriod)
        )
    }

    private fun sectionFor(date: LocalDate, fromDate: LocalDate, endOfCurrentWeek: LocalDate): AgendaSection = when {
        date == fromDate -> AgendaSection.TODAY
        date == fromDate.plusDays(1) -> AgendaSection.TOMORROW
        date <= endOfCurrentWeek -> AgendaSection.THIS_WEEK
        else -> AgendaSection.UPCOMING
    }
}
