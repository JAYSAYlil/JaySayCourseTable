package com.jaysay.coursetable.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CourseMergerTest {
    @Test
    fun mergesWeeksAndPreservesUserFields() {
        val existing = course(weeks = listOf(1, 2), notes = "keep", color = 0x123456)
        val incoming = course(weeks = listOf(2, 3, 4))

        val result = CourseMerger.mergeImported(listOf(existing), listOf(incoming))

        assertEquals(0, result.added)
        assertEquals(1, result.merged)
        assertEquals(0, result.skipped)
        assertEquals(listOf(1, 2, 3, 4), result.courses.single().weeks)
        assertEquals("keep", result.courses.single().notes)
        assertEquals(0x123456, result.courses.single().customColor)
    }

    @Test
    fun repeatedImportIsIdempotent() {
        val item = course(weeks = listOf(1, 2, 3))
        val result = CourseMerger.mergeImported(listOf(item), listOf(item, item))

        assertEquals(1, result.courses.size)
        assertEquals(0, result.added)
        assertEquals(0, result.merged)
        assertEquals(2, result.skipped)
    }

    private fun course(weeks: List<Int>, notes: String = "", color: Int? = null) = Course(
        courseId = "C001",
        courseName = "Course",
        classNumber = "01",
        department = "Department",
        credits = 2f,
        weeks = weeks,
        dayOfWeek = 1,
        startPeriod = 1,
        endPeriod = 2,
        teacher = "Teacher",
        classroom = "Room",
        courseType = "Required",
        courseCategory = "Major",
        isOnline = false,
        assessmentMethod = "Exam",
        customColor = color,
        notes = notes
    )
}
