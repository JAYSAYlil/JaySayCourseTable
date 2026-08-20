package com.jaysay.coursetable.data.model

import com.jaysay.coursetable.data.preferences.PeriodTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AgendaListCalculatorTest {
    private val periods = listOf(
        PeriodTime("08:00", "08:45"),
        PeriodTime("08:55", "09:40"),
        PeriodTime("10:10", "10:55")
    )

    @Test
    fun groupsTodayTomorrowThisWeekAndUpcomingAndSkipsEmptyDays() {
        val start = LocalDate.parse("2026-02-23") // Monday
        val result = AgendaListCalculator.calculate(
            courses = listOf(
                course("today", "今天的课", day = 1, weeks = listOf(1)),
                course("tomorrow", "明天的课", day = 2, weeks = listOf(1)),
                course("this-week", "周五的课", day = 5, weeks = listOf(1)),
                course("upcoming", "下周的课", day = 1, weeks = listOf(2))
            ),
            periods = periods,
            semesterStart = start.toString(),
            totalWeeks = 2,
            fromDate = start
        )

        assertEquals(
            listOf(AgendaSection.TODAY, AgendaSection.TOMORROW, AgendaSection.THIS_WEEK, AgendaSection.UPCOMING),
            result.map { it.section }
        )
        assertEquals(listOf("今天的课", "明天的课", "周五的课", "下周的课"), result.map { it.courses.single().course.courseName })
        assertEquals(start.plusDays(7), result.last().date)
    }

    @Test
    fun calculatesDatesRelativeToSemesterStartEvenWhenItIsNotMonday() {
        val wednesdayStart = LocalDate.parse("2026-02-25")
        val result = AgendaListCalculator.calculate(
            courses = listOf(course("relative", "相对开学日", day = 1, weeks = listOf(1, 2))),
            periods = periods,
            semesterStart = wednesdayStart.toString(),
            totalWeeks = 2,
            fromDate = wednesdayStart
        )

        assertEquals(listOf(wednesdayStart, wednesdayStart.plusDays(7)), result.map { it.date })
        assertEquals(listOf(1, 2), result.map { it.week })
    }

    @Test
    fun excludesSuspendedWeeksAndFiltersByNameTeacherAndClassroom() {
        val start = LocalDate.parse("2026-02-23")
        val result = AgendaListCalculator.calculate(
            courses = listOf(
                course("first", "线性代数", teacher = "王老师", classroom = "A101", weeks = listOf(1)),
                course("second", "大学物理", teacher = "李老师", classroom = "B202", weeks = listOf(2)),
                course("third", "大学物理", teacher = "李老师", classroom = "B202", weeks = listOf(3))
            ),
            periods = periods,
            semesterStart = start.toString(),
            totalWeeks = 3,
            fromDate = start,
            excludedWeeks = setOf(2),
            searchQuery = "李老师 B202"
        )

        assertEquals(1, result.size)
        assertEquals(3, result.single().week)
        assertEquals("大学物理", result.single().courses.single().course.courseName)
    }

    @Test
    fun keepsCoursesWithInvalidPeriodSettingsVisibleAndPlacesThemAfterTimedCourses() {
        val start = LocalDate.parse("2026-02-23")
        val result = AgendaListCalculator.calculate(
            courses = listOf(
                course("invalid", "异常时间", startPeriod = 4, endPeriod = 4),
                course("valid", "正常时间", startPeriod = 1, endPeriod = 2)
            ),
            periods = periods,
            semesterStart = start.toString(),
            totalWeeks = 1,
            fromDate = start
        )

        val instances = result.single().courses
        assertEquals(listOf("正常时间", "异常时间"), instances.map { it.course.courseName })
        assertEquals("08:00 - 09:40", instances.first().timeLabel)
        assertEquals("时间未设置", instances.last().timeLabel)
        assertTrue(instances.last().startMinute == null)
    }

    @Test
    fun ignoresMalformedSemesterStartAndPastInstances() {
        assertTrue(
            AgendaListCalculator.calculate(
                courses = listOf(course("one", "课程")),
                periods = periods,
                semesterStart = "not-a-date",
                totalWeeks = 1,
                fromDate = LocalDate.parse("2026-02-23")
            ).isEmpty()
        )
        assertTrue(
            AgendaListCalculator.calculate(
                courses = listOf(course("one", "课程", weeks = listOf(1))),
                periods = periods,
                semesterStart = "2026-02-23",
                totalWeeks = 1,
                fromDate = LocalDate.parse("2026-03-02")
            ).isEmpty()
        )
    }

    private fun course(
        id: String,
        name: String,
        day: Int = 1,
        weeks: List<Int> = listOf(1),
        startPeriod: Int = 1,
        endPeriod: Int = 2,
        teacher: String = "测试教师",
        classroom: String = "测试教室"
    ) = Course(
        courseId = id,
        courseName = name,
        classNumber = "测试班",
        department = "测试学院",
        credits = 2f,
        weeks = weeks,
        dayOfWeek = day,
        startPeriod = startPeriod,
        endPeriod = endPeriod,
        teacher = teacher,
        classroom = classroom,
        courseType = "必修",
        courseCategory = "测试",
        isOnline = false,
        assessmentMethod = "考试"
    )
}
