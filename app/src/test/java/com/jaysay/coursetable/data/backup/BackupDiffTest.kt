package com.jaysay.coursetable.data.backup

import com.jaysay.coursetable.data.model.Course
import com.jaysay.coursetable.data.repository.TableData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupDiffTest {
    private fun course(series: String, name: String, room: String = "A101") = Course(
        courseId = series, courseName = name, classNumber = "", department = "", credits = 0f,
        weeks = listOf(1), dayOfWeek = 1, startPeriod = 1, endPeriod = 2, teacher = "",
        classroom = room, courseType = "", courseCategory = "", isOnline = false,
        assessmentMethod = "", seriesId = series
    )

    @Test fun reportsAddedRemovedAndChangedCourses() {
        val current = listOf(TableData("本学期", listOf(course("keep", "高数"), course("gone", "英语"))))
        val incoming = listOf(TableData("本学期", listOf(course("keep", "高数", "B202"), course("new", "物理"))))
        val diff = BackupDiff.between(current, incoming)
        assertEquals(1, diff.coursesAdded)
        assertEquals(1, diff.coursesRemoved)
        assertEquals(1, diff.coursesChanged)
        assertTrue(diff.hasChanges)
    }
}
