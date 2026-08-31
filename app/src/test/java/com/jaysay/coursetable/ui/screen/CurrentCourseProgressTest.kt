package com.jaysay.coursetable.ui.screen

import com.jaysay.coursetable.data.model.Course
import com.jaysay.coursetable.data.preferences.PeriodTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrentCourseProgressTest {
    private val periods = listOf(
        PeriodTime("08:00", "08:45"),
        PeriodTime("08:55", "09:40"),
        PeriodTime("10:00", "10:45")
    )

    private val course = Course(
        courseId = "progress-test",
        courseName = "测试课程",
        classNumber = "",
        department = "",
        credits = 0f,
        weeks = listOf(1),
        dayOfWeek = 1,
        startPeriod = 1,
        endPeriod = 2,
        teacher = "",
        classroom = "",
        courseType = "",
        courseCategory = "",
        isOnline = false,
        assessmentMethod = "",
        customColor = null,
        notes = "",
        seriesId = "progress-test"
    )

    @Test
    fun visibleOnlyInsideAnOccupiedTeachingPeriod() {
        assertNull(currentCourseProgressPosition(7 * 60 + 59, periods, listOf(course)))
        assertNull(currentCourseProgressPosition(8 * 60 + 50, periods, listOf(course)))
        assertNull(currentCourseProgressPosition(10 * 60 + 10, periods, listOf(course)))
        assertNull(currentCourseProgressPosition(8 * 60 + 20, periods, emptyList()))

        val firstPeriod = currentCourseProgressPosition(8 * 60 + 20, periods, listOf(course))
        assertEquals(0, firstPeriod?.first)
        assertTrue(firstPeriod != null && firstPeriod.second in 0f..1f)

        val secondPeriod = currentCourseProgressPosition(9 * 60 + 10, periods, listOf(course))
        assertEquals(1, secondPeriod?.first)
        assertTrue(secondPeriod != null && secondPeriod.second in 0f..1f)
    }
}
