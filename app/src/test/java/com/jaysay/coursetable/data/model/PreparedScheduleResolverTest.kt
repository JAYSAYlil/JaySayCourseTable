package com.jaysay.coursetable.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PreparedScheduleResolverTest {
    private fun course(i: Int) = Course("c$i", "虚构课程$i", "", "", 0f,
        (1..20).toList(), i % 7 + 1, i % 8 + 1, i % 8 + 2,
        "", "", "", "", false, "", seriesId = "series${i % 10}")

    @Test fun indexedQueriesMatchDirectResolverIncludingExceptionsAndSplitRecords() {
        val start = LocalDate.of(2030, 9, 2)
        val courses = (0..29).map(::course) + listOf(course(0).copy(weeks = listOf(2), classroom = "调课"))
        val exceptions = (-3..32).flatMap { offset ->
            val date = start.plusDays(offset.toLong()).toString()
            listOf(
                ScheduleDateException(date = date, type = ScheduleExceptionType.MAKEUP, makeupCourse = course(4)),
                ScheduleDateException(date = date, type = ScheduleExceptionType.COURSE_CANCELLED, courseSeriesKey = "series0")
            ) + if (offset % 5 == 0) listOf(ScheduleDateException(date = date, type = ScheduleExceptionType.DAY_OFF)) else emptyList()
        }
        for (semester in listOf("2030-09-02", "2030-09-05", "invalid")) {
            for (totalWeeks in listOf(0, 3, 20)) {
                val prepared = ScheduleDateResolver.prepare(courses, semester, totalWeeks, setOf(2), exceptions)
                for (offset in -5..145) {
                    val date = start.plusDays(offset.toLong())
                    assertEquals("$semester / $totalWeeks / $date",
                        ScheduleDateResolver.coursesOn(courses, semester, totalWeeks, setOf(2), exceptions, date),
                        prepared.coursesOn(date))
                }
            }
        }
    }

    @Test fun batchReadsSourceCollectionsOnceRatherThanOncePerDate() {
        val courses = CountingList((0 until 140).map(::course))
        val start = LocalDate.of(2030, 9, 2)
        val exceptions = CountingList((0 until 140).map {
            ScheduleDateException(date = start.plusDays(it.toLong()).toString(), type = ScheduleExceptionType.COURSE_CANCELLED, courseSeriesKey = "missing")
        })
        val dates = (0 until 140).map { start.plusDays(it.toLong()) }
        val baseline = dates.map { ScheduleDateResolver.coursesOn(courses, start.toString(), 20, emptySet(), exceptions, it) }
        assertEquals(19_600, courses.reads)
        assertEquals(19_600, exceptions.reads)
        courses.reads = 0
        exceptions.reads = 0
        val prepared = ScheduleDateResolver.prepare(courses, start.toString(), 20, emptySet(), exceptions)
        assertEquals(baseline, dates.map(prepared::coursesOn))
        assertEquals(140, courses.reads)
        assertEquals(140, exceptions.reads)
    }

    @Test fun freshIndexReflectsEditedWeeksWithoutGlobalStaleCache() {
        val date = LocalDate.of(2030, 9, 2)
        val courses = mutableListOf(course(0))
        val before = ScheduleDateResolver.prepare(courses, date.toString(), 20, emptySet(), emptyList())
        courses[0] = courses[0].copy(weeks = listOf(2))
        val after = ScheduleDateResolver.prepare(courses, date.toString(), 20, emptySet(), emptyList())
        assertEquals(1, before.coursesOn(date).size)
        assertTrue(after.coursesOn(date).isEmpty())
        assertEquals(listOf(2), after.coursesOn(date.plusWeeks(1)).single().course.weeks)
    }

    private class CountingList<T>(private val values: List<T>) : AbstractList<T>() {
        var reads = 0
        override val size get() = values.size
        override fun get(index: Int): T { reads++; return values[index] }
    }
}
