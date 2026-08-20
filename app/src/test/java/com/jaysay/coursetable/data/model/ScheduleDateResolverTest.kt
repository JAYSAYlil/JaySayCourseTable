package com.jaysay.coursetable.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ScheduleDateResolverTest {
    private fun course(series: String, name: String, day: Int = 1) = Course(
        courseId = series, courseName = name, classNumber = "", department = "", credits = 0f,
        weeks = listOf(1, 2), dayOfWeek = day, startPeriod = 1, endPeriod = 2, teacher = "",
        classroom = "", courseType = "", courseCategory = "", isOnline = false,
        assessmentMethod = "", seriesId = series
    )

    @Test fun dayOffAndMakeupAreAppliedTogether() {
        val regular = course("regular", "高数")
        val makeup = course("makeup", "补课", day = 5)
        val date = LocalDate.parse("2026-09-07")
        val exceptions = listOf(
            ScheduleDateException(date = date.toString(), type = ScheduleExceptionType.DAY_OFF, title = "校庆"),
            ScheduleDateException(date = date.toString(), type = ScheduleExceptionType.MAKEUP, makeupCourse = makeup)
        )
        val result = ScheduleDateResolver.coursesOn(listOf(regular), "2026-09-07", 20, emptySet(), exceptions, date)
        assertEquals(listOf("补课"), result.map { it.course.courseName })
        assertTrue(result.single().isMakeup)
    }

    @Test fun singleCourseCancellationDoesNotRemoveOtherCourses() {
        val first = course("first", "高数")
        val second = course("second", "英语")
        val date = LocalDate.parse("2026-09-07")
        val result = ScheduleDateResolver.coursesOn(
            listOf(first, second), "2026-09-07", 20, emptySet(),
            listOf(ScheduleDateException(date = date.toString(), type = ScheduleExceptionType.COURSE_CANCELLED,
                courseSeriesKey = first.seriesKey)), date
        )
        assertEquals(listOf("英语"), result.map { it.course.courseName })
    }
}
