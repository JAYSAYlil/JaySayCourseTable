package com.jaysay.coursetable.data.ical

import com.jaysay.coursetable.data.model.Course
import com.jaysay.coursetable.data.model.ScheduleDateException
import com.jaysay.coursetable.data.model.ScheduleExceptionType
import com.jaysay.coursetable.data.preferences.PeriodTime
import com.jaysay.coursetable.data.repository.TableData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

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
        val ics = IcsExporter.export(table, Instant.parse("2026-02-01T00:00:00Z"))
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
        assertTrue(ics.contains("DTSTAMP:20260201T000000Z"))
        assertTrue(ics.endsWith("\r\n"))
        assertTrue(!ics.replace("\r\n", "").contains("\n"))
    }

    @Test
    fun usesLastPeriodEndAndFoldsLongUtf8Lines() {
        val longCourse = course.copy(
            courseName = "超长课程名称".repeat(12),
            endPeriod = 2
        )
        val table = TableData(
            name = "我的课表",
            courses = listOf(longCourse),
            periods = listOf(PeriodTime("08:00", "08:45"), PeriodTime("08:55", "09:40")),
            semesterStart = "2026-02-23",
            totalWeeks = 20
        )

        val ics = IcsExporter.export(table, Instant.EPOCH)

        assertTrue(ics.contains("DTEND:20260223T094000"))
        assertTrue(ics.split("\r\n").any { it.startsWith(" ") })
        assertTrue(ics.split("\r\n").filter { it.isNotEmpty() }.all {
            it.toByteArray(Charsets.UTF_8).size <= 75
        })
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

    @Test
    fun skipsCourseWeeksOutsideCurrentSemester() {
        val table = TableData(
            name = "我的课表",
            courses = listOf(course.copy(weeks = listOf(1, 17))),
            periods = listOf(PeriodTime("08:00", "08:45")),
            semesterStart = "2026-02-23",
            totalWeeks = 16
        )

        val ics = IcsExporter.export(table)

        assertEquals(1, Regex("BEGIN:VEVENT").findAll(ics).count())
    }

    @Test
    fun skipsEventsWithInvalidOrReversedTimes() {
        val table = TableData(
            name = "我的课表",
            courses = listOf(course),
            periods = listOf(PeriodTime("09:00", "08:00")),
            semesterStart = "2026-02-23",
            totalWeeks = 20
        )

        val ics = IcsExporter.export(table)

        assertEquals(0, Regex("BEGIN:VEVENT").findAll(ics).count())
    }

    @Test
    fun appliesCancelledDayAndMakeupToCalendar() {
        val table = TableData(
            name = "我的课表",
            courses = listOf(course.copy(weeks = listOf(1))),
            periods = listOf(PeriodTime("08:00", "08:45")),
            semesterStart = "2026-02-23",
            totalWeeks = 1,
            dateExceptions = listOf(
                ScheduleDateException(
                    id = "cancel", date = "2026-02-23", type = ScheduleExceptionType.COURSE_CANCELLED,
                    courseSeriesKey = course.seriesKey
                ),
                ScheduleDateException(
                    id = "makeup", date = "2026-02-24", type = ScheduleExceptionType.MAKEUP,
                    courseSeriesKey = course.seriesKey, makeupCourse = course
                )
            )
        )

        val ics = IcsExporter.export(table)

        assertEquals(1, Regex("BEGIN:VEVENT").findAll(ics).count())
        assertTrue(!ics.contains("DTSTART:20260223T080000"))
        assertTrue(ics.contains("DTSTART:20260224T080000"))
    }
}
