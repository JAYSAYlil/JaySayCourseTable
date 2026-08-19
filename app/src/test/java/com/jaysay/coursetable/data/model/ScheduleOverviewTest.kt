package com.jaysay.coursetable.data.model

import com.jaysay.coursetable.data.preferences.PeriodTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.LocalDate

class ScheduleOverviewTest {
    private val monday = LocalDate.parse("2026-02-23")
    private val periods = listOf(
        PeriodTime("08:00", "08:45"),
        PeriodTime("08:55", "09:40"),
        PeriodTime("10:10", "10:55")
    )
    private val courses = listOf(
        course("高等数学", "张老师", "A101", 1, 1),
        course("大学英语", "李老师", "B202", 2, 2)
    )

    @Test
    fun reportsBeforeFirstClass() {
        val result = agenda(7 * 60 + 30)
        assertEquals(TodayAgendaPhase.BEFORE_FIRST, result.phase)
        assertEquals("高等数学", result.next?.course?.courseName)
    }

    @Test
    fun reportsClassInProgressWithEndExclusiveBoundary() {
        assertEquals(TodayAgendaPhase.IN_CLASS, agenda(8 * 60 + 20).phase)
        assertEquals(TodayAgendaPhase.BETWEEN_CLASSES, agenda(8 * 60 + 45).phase)
    }

    @Test
    fun reportsBreakAndNextClass() {
        val result = agenda(8 * 60 + 50)
        assertEquals(TodayAgendaPhase.BETWEEN_CLASSES, result.phase)
        assertEquals("大学英语", result.next?.course?.courseName)
    }

    @Test
    fun reportsFinishedAfterLastClass() {
        val result = agenda(12 * 60)
        assertEquals(TodayAgendaPhase.FINISHED, result.phase)
        assertNull(result.next)
    }

    @Test
    fun reportsNoCourseDayAndOutsideSemester() {
        val noCourse = TodayAgendaCalculator.calculate(courses, periods, monday.toString(), 20, monday.plusDays(1), 8 * 60)
        val outside = TodayAgendaCalculator.calculate(courses, periods, monday.toString(), 1, monday.plusDays(7), 8 * 60)
        assertEquals(TodayAgendaPhase.NO_COURSES, noCourse.phase)
        assertEquals(TodayAgendaPhase.OUTSIDE_SEMESTER, outside.phase)
    }

    @Test
    fun invalidPeriodTimeDoesNotCrashAndIsReported() {
        val result = TodayAgendaCalculator.calculate(
            listOf(course("异常课程", "教师", "教室", 1, 1)),
            listOf(PeriodTime("错误", "08:45")),
            monday.toString(),
            20,
            monday,
            8 * 60
        )
        assertEquals(TodayAgendaPhase.INVALID_TIME, result.phase)
        assertEquals(1, result.invalidCourseCount)
    }

    @Test
    fun searchMatchesNameTeacherAndClassroomAndCanClear() {
        assertEquals(listOf(courses[0]), CourseSearch.filter(courses, "数学"))
        assertEquals(listOf(courses[1]), CourseSearch.filter(courses, "李老师"))
        assertEquals(listOf(courses[0]), CourseSearch.filter(courses, "高等 A101"))
        assertSame(courses, CourseSearch.filter(courses, "  "))
    }

    private fun agenda(minute: Int) = TodayAgendaCalculator.calculate(
        courses, periods, monday.toString(), 20, monday, minute
    )

    private fun course(name: String, teacher: String, classroom: String, start: Int, end: Int) = Course(
        courseId = name,
        courseName = name,
        classNumber = "测试班",
        department = "测试学院",
        credits = 2f,
        weeks = listOf(1),
        dayOfWeek = 1,
        startPeriod = start,
        endPeriod = end,
        teacher = teacher,
        classroom = classroom,
        courseType = "必修",
        courseCategory = "测试",
        isOnline = false,
        assessmentMethod = "考试"
    )
}
