package com.jaysay.coursetable.data.reminder

import com.jaysay.coursetable.data.model.Course
import com.jaysay.coursetable.data.preferences.PeriodTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReminderCalculatorTest {

    private val periods = listOf(
        PeriodTime("08:00", "08:45"),
        PeriodTime("08:55", "09:40"),
        PeriodTime("10:10", "10:55")
    )

    private fun course(
        name: String,
        weeks: List<Int>,
        dayOfWeek: Int,
        startPeriod: Int = 1,
        endPeriod: Int = 1,
        seriesId: String = "s-$name"
    ) = Course(
        courseId = "id-$name",
        courseName = name,
        classNumber = "",
        department = "",
        credits = 0f,
        weeks = weeks,
        dayOfWeek = dayOfWeek,
        startPeriod = startPeriod,
        endPeriod = endPeriod,
        teacher = "张老师",
        classroom = "教101",
        courseType = "",
        courseCategory = "",
        isOnline = false,
        assessmentMethod = "",
        seriesId = seriesId
    )

    @Test
    fun computesDateAndMinutesForWeek() {
        val instances = ReminderCalculator.courseInstances(
            courses = listOf(course("高数", weeks = listOf(1, 3), dayOfWeek = 1, startPeriod = 1, endPeriod = 1)),
            semesterStart = "2026-02-23", // 周一
            periods = periods,
            week = 1
        )
        assertEquals(1, instances.size)
        val instance = instances.single()
        assertEquals(LocalDate.of(2026, 2, 23), instance.date)
        assertEquals(8 * 60, instance.startMinute)
        assertEquals(8 * 60 + 45, instance.endMinute)
        assertEquals(1, instance.week)
    }

    @Test
    fun filtersWeeksWithoutCourses() {
        val instances = ReminderCalculator.courseInstances(
            courses = listOf(course("高数", weeks = listOf(1), dayOfWeek = 1)),
            semesterStart = "2026-02-23",
            periods = periods,
            week = 2
        )
        assertTrue(instances.isEmpty())
    }

    @Test
    fun excludesExcludedWeeks() {
        val instances = ReminderCalculator.courseInstances(
            courses = listOf(course("高数", weeks = listOf(1, 3), dayOfWeek = 1)),
            semesterStart = "2026-02-23",
            periods = periods,
            week = 3,
            excludedWeeks = setOf(3)
        )
        assertTrue(instances.isEmpty())
    }

    @Test
    fun skipsCoursesWithInvalidPeriodTimes() {
        val bad = course("坏时间", weeks = listOf(1), dayOfWeek = 1)
        val instances = ReminderCalculator.courseInstances(
            courses = listOf(bad),
            semesterStart = "2026-02-23",
            periods = listOf(PeriodTime("not-a-time", "09:00")),
            week = 1
        )
        assertTrue(instances.isEmpty())
    }

    @Test
    fun reminderAtSubtractsAdvanceMinutes() {
        val instance = ReminderCalculator.courseInstances(
            courses = listOf(course("高数", weeks = listOf(1), dayOfWeek = 1, startPeriod = 1)),
            semesterStart = "2026-02-23",
            periods = periods,
            week = 1
        ).single()
        // 08:00 上课，提前 10 分钟 → 07:50
        assertEquals(
            LocalDate.of(2026, 2, 23).atTime(7, 50),
            ReminderCalculator.reminderAt(instance, 10)
        )
        // 提前 30 分钟 → 07:30
        assertEquals(
            LocalDate.of(2026, 2, 23).atTime(7, 30),
            ReminderCalculator.reminderAt(instance, 30)
        )
    }
}
