package com.jaysay.coursetable.data.ical

import com.jaysay.coursetable.data.model.Course
import com.jaysay.coursetable.data.preferences.PeriodTime
import com.jaysay.coursetable.data.repository.TableData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IcsExporterTest {

    private val course = Course(
        courseId = "id-1",
        courseName = "高等数学,（A）",
        classNumber = "",
        department = "",
        credits = 3f,
        weeks = listOf(1, 2),
        dayOfWeek = 1,
        startPeriod = 1,
        endPeriod = 1,
        teacher = "张老师",
        classroom = "教1-101",
        courseType = "",
        courseCategory = "",
        isOnline = false,
        assessmentMethod = "",
        seriesId = "series-1"
    )

    @Test
    fun exportsCalendarWithCourseEvents() {
        val table = TableData(
            name = "我的课表",
            courses = listOf(course),
            periods = listOf(PeriodTime("08:00", "08:45")),
            semesterStart = "2026-02-23", // 周一
            totalWeeks = 20
        )
        val ics = IcsExporter.export(table)
        assertTrue(ics.startsWith("BEGIN:VCALENDAR"))
        assertTrue(ics.contains("END:VCALENDAR"))
        assertTrue(ics.contains("BEGIN:VEVENT"))
        assertEquals(2, Regex("BEGIN:VEVENT").findAll(ics).count())
        // 第 1 周周一 = 2026-02-23；第 2 周周一 = 2026-03-02
        assertTrue(ics.contains("DTSTART:20260223T080000"))
        assertTrue(ics.contains("DTSTART:20260302T080000"))
        assertTrue(ics.contains("DTEND:20260223T084500"))
        // 逗号转义，防止破坏 iCal 字段
        assertTrue(ics.contains("SUMMARY:高等数学\\,（A）"))
        assertTrue(ics.contains("LOCATION:教1-101"))
    }

    @Test
    fun skipsExcludedWeeks() {
        val table = TableData(
            name = "我的课表",
            courses = listOf(course),
            periods = listOf(PeriodTime("08:00", "08:45")),
            semesterStart = "2026-02-23",
            totalWeeks = 20,
            excludedWeeks = listOf(2)
        )
        val ics = IcsExporter.export(table)
        assertEquals(1, Regex("BEGIN:VEVENT").findAll(ics).count())
        assertTrue(ics.contains("DTSTART:20260223T080000"))
        assertTrue(!ics.contains("DTSTART:20260302T080000"))
    }
}
