package com.jaysay.coursetable.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseImportAnalyzerTest {
    @Test
    fun classifiesNewMergeAndDuplicateItems() {
        val existing = course(id = "BASE", weeks = listOf(1, 2))
        val preview = CourseImportAnalyzer.analyze(
            existing = listOf(existing),
            imported = listOf(
                existing,
                existing.copy(weeks = listOf(2, 3)),
                course(id = "NEW", day = 2, weeks = listOf(1, 2))
            )
        )

        assertEquals(
            listOf(ImportItemStatus.DUPLICATE, ImportItemStatus.MERGE, ImportItemStatus.NEW),
            preview.items.map(ImportPreviewItem::status)
        )
        assertFalse(preview.items[0].selectedByDefault)
        assertTrue(preview.items[1].selectedByDefault)
        assertTrue(preview.items[2].selectedByDefault)
    }

    @Test
    fun detectsConflictWithExistingCourseOnlyWhenWeeksAndPeriodsOverlap() {
        val existing = course(id = "EXISTING", name = "现有课程", weeks = listOf(1, 2), start = 1, end = 2)
        val conflicting = course(id = "INCOMING", name = "导入课程", weeks = listOf(2, 3), start = 2, end = 3)
        val safeDifferentWeek = course(id = "SAFE", weeks = listOf(4), start = 1, end = 2)

        val preview = CourseImportAnalyzer.analyze(listOf(existing), listOf(conflicting, safeDifferentWeek))

        val conflict = preview.items[0]
        assertEquals(ImportItemStatus.CONFLICT, conflict.status)
        assertFalse(conflict.selectedByDefault)
        assertEquals("现有课程", conflict.conflicts.single().otherCourseName)
        assertEquals(listOf(2), conflict.conflicts.single().overlappingWeeks)
        assertEquals(2, conflict.conflicts.single().startPeriod)
        assertEquals(2, conflict.conflicts.single().endPeriod)
        assertEquals(ImportItemStatus.NEW, preview.items[1].status)
    }

    @Test
    fun detectsConflictBetweenTwoImportedCourses() {
        val first = course(id = "A", name = "课程A", weeks = listOf(1, 2), start = 3, end = 4)
        val second = course(id = "B", name = "课程B", weeks = listOf(2), start = 4, end = 5)

        val preview = CourseImportAnalyzer.analyze(emptyList(), listOf(first, second))

        assertEquals(listOf(ImportItemStatus.CONFLICT, ImportItemStatus.CONFLICT), preview.items.map { it.status })
        assertEquals("课程B", preview.items[0].conflicts.single().otherCourseName)
        assertEquals("课程A", preview.items[1].conflicts.single().otherCourseName)
    }

    @Test
    fun manualConflictLookupCanExcludeEditedCourse() {
        val old = course(id = "EDIT", weeks = listOf(1, 2))
        val other = course(id = "OTHER", weeks = listOf(2), start = 2, end = 3)
        val updated = old.copy(courseName = "编辑后", startPeriod = 2, endPeriod = 2)

        val conflicts = CourseImportAnalyzer.findConflicts(
            existing = listOf(old, other),
            candidate = updated,
            excludedUniqueKeys = setOf(old.uniqueKey)
        )

        assertEquals(listOf("Course-OTHER"), conflicts.map { it.otherCourseName })
    }

    @Test(timeout = 5_000)
    fun analyzesTwoThousandRowsWithoutQuadraticScan() {
        val imported = (0 until 2_000).map { index ->
            course(
                id = "LOAD-$index",
                weeks = listOf(index % 30 + 1),
                day = index % 7 + 1,
                start = index % 12 + 1,
                end = index % 12 + 1
            )
        }

        val preview = CourseImportAnalyzer.analyze(emptyList(), imported)

        assertEquals(2_000, preview.items.size)
        assertTrue(preview.items.all { it.status == ImportItemStatus.NEW || it.status == ImportItemStatus.CONFLICT })
    }

    private fun course(
        id: String,
        name: String = "Course-$id",
        weeks: List<Int>,
        day: Int = 1,
        start: Int = 1,
        end: Int = 2
    ) = Course(
        courseId = id,
        courseName = name,
        classNumber = "01",
        department = "Synthetic Department",
        credits = 2f,
        weeks = weeks,
        dayOfWeek = day,
        startPeriod = start,
        endPeriod = end,
        teacher = "Synthetic Teacher",
        classroom = "Synthetic Room",
        courseType = "Required",
        courseCategory = "Major",
        isOnline = false,
        assessmentMethod = "Exam"
    )
}
