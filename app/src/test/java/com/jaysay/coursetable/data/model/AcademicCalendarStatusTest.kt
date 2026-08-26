package com.jaysay.coursetable.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AcademicCalendarStatusTest {
    @Test
    fun dayStatusCombinesSuspendedWeekLabelAndDateAdjustments() {
        val date = LocalDate.parse("2026-08-26")
        val status = AcademicCalendarStatusResolver.day(
            date = date,
            semesterStart = "2026-08-17",
            totalWeeks = 20,
            excludedWeeks = setOf(2),
            exceptions = listOf(
                ScheduleDateException(date = date.toString(), type = ScheduleExceptionType.DAY_OFF, title = "校庆"),
                ScheduleDateException(date = date.toString(), type = ScheduleExceptionType.MAKEUP, makeupCourse = course())
            ),
            weekLabels = mapOf(2 to "实践周")
        )

        assertEquals(2, status.week)
        assertEquals("实践周", status.weekLabel)
        assertTrue(status.suspendedWeek)
        assertTrue(status.dayOff)
        assertEquals("校庆", status.dayOffTitle)
        assertEquals(2, status.dateAdjustmentCount)
        assertTrue(status.hasCalendarContext)
    }

    @Test
    fun weekStatusCountsOnlyDatesInsideSelectedWeek() {
        val status = AcademicCalendarStatusResolver.week(
            week = 2,
            semesterStart = "2026-08-17",
            totalWeeks = 20,
            excludedWeeks = emptySet(),
            exceptions = listOf(
                ScheduleDateException(date = "2026-08-24", type = ScheduleExceptionType.DAY_OFF),
                ScheduleDateException(date = "2026-08-27", type = ScheduleExceptionType.COURSE_CANCELLED, courseSeriesKey = "series"),
                ScheduleDateException(date = "2026-08-31", type = ScheduleExceptionType.DAY_OFF)
            ),
            weekLabels = emptyMap()
        )

        assertEquals(2, status.dateAdjustmentCount)
        assertEquals(1, status.dayOffCount)
        assertEquals(1, status.cancelledCount)
        assertFalse(status.suspended)
    }

    private fun course() = Course(
        courseId = "id", courseName = "补课", classNumber = "", department = "", credits = 0f,
        weeks = listOf(1), dayOfWeek = 1, startPeriod = 1, endPeriod = 2,
        teacher = "", classroom = "", courseType = "", courseCategory = "",
        isOnline = false, assessmentMethod = "", seriesId = "series"
    )
}
