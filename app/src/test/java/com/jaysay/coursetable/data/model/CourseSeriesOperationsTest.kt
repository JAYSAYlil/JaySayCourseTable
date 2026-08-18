package com.jaysay.coursetable.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseSeriesOperationsTest {
    @Test
    fun legacyWeekSplitsReceiveSameSeriesAndDeleteAllWorksFirstTime() {
        val migrated = CourseSeriesIds.ensure(
            listOf(course(weeks = (1..9).toList()), course(weeks = listOf(10)))
        )

        assertEquals(migrated[0].seriesKey, migrated[1].seriesKey)
        assertTrue(CourseSeriesOperations.deleteAll(migrated, migrated[0].seriesKey).isEmpty())
    }

    @Test
    fun legacySplitMovedToAnotherDayIsStillRecognized() {
        val migrated = CourseSeriesIds.ensure(
            listOf(course(weeks = (1..9).toList()), course(weeks = listOf(10), day = 3))
        )

        assertEquals(migrated[0].seriesKey, migrated[1].seriesKey)
    }

    @Test
    fun overlappingMeetingsOfSameCourseRemainSeparateSeries() {
        val migrated = CourseSeriesIds.ensure(
            listOf(course(weeks = (1..10).toList()), course(weeks = (1..10).toList(), day = 3))
        )

        assertNotEquals(migrated[0].seriesKey, migrated[1].seriesKey)
    }

    @Test
    fun applyAllReplacesEverySplitWithOneUnifiedCourse() {
        val split = CourseSeriesIds.ensure(
            listOf(course(weeks = (1..9).toList()), course(weeks = listOf(10)))
        )
        val replacement = split[0].copy(classroom = "新教室", weeks = (1..10).toList())

        val result = CourseSeriesOperations.replaceAll(split, split[0].seriesKey, replacement)

        assertEquals(1, result.size)
        assertEquals((1..10).toList(), result.single().weeks)
        assertEquals("新教室", result.single().classroom)
        assertEquals(split[0].seriesKey, result.single().seriesKey)
    }

    @Test
    fun targetedUndoPreservesUnrelatedChanges() {
        val selected = course(weeks = listOf(1, 2), seriesId = "selected")
        val unrelated = course(id = "C002", name = "另一门课", weeks = listOf(1), seriesId = "other")
        val laterAddition = course(id = "C003", name = "后来新增", weeks = listOf(1), seriesId = "later")
        val before = listOf(selected, unrelated)
        val after = CourseSeriesOperations.deleteWeek(before, "selected", 1)
        val undo = CourseSeriesUndo.capture(before, after, "selected")

        val restored = undo.restore(after + laterAddition)

        assertEquals(listOf(selected, unrelated, laterAddition), restored)
    }

    private fun course(
        id: String = "C001",
        name: String = "课程",
        weeks: List<Int>,
        day: Int = 1,
        seriesId: String = ""
    ) = Course(
        courseId = id,
        courseName = name,
        classNumber = "01",
        department = "学院",
        credits = 2f,
        weeks = weeks,
        dayOfWeek = day,
        startPeriod = 1,
        endPeriod = 2,
        teacher = "教师",
        classroom = "教室",
        courseType = "必修",
        courseCategory = "专业",
        isOnline = false,
        assessmentMethod = "考试",
        seriesId = seriesId
    )
}
