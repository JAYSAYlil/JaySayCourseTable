package com.jaysay.coursetable.data.history

import com.jaysay.coursetable.data.model.Course
import com.jaysay.coursetable.data.repository.TableData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.file.Files

class CourseHistoryStoreTest {
    @Test
    fun retainsOnlyTenNewestSnapshots() {
        val root = Files.createTempDirectory("course-history-limit").toFile()
        var time = 1000L
        var suffix = 0
        val store = CourseHistoryStore(
            directory = root,
            clock = { time++ },
            suffixFactory = { "%08x".format(suffix++) },
            minSnapshotIntervalMillis = 0L
        )

        repeat(12) { index ->
            val tables = listOf(TableData.placeholder("课表$index"))
            store.createSnapshot(encodeForTest(tables), tables)
        }

        val snapshots = store.listSnapshots(::decodeForTest)
        assertEquals(10, snapshots.size)
        assertEquals(1011L, snapshots.first().createdAtMillis)
        assertEquals(1002L, snapshots.last().createdAtMillis)
    }

    @Test
    fun coalescesSnapshotsWithinEditingWindowUnlessForced() {
        val root = Files.createTempDirectory("course-history-coalesce").toFile()
        var time = 10_000L
        var suffix = 0
        val store = CourseHistoryStore(
            directory = root,
            clock = { time },
            suffixFactory = { "%08x".format(suffix++) },
            minSnapshotIntervalMillis = 1_000L
        )
        val first = listOf(TableData.placeholder("第一版"))
        val second = listOf(TableData.placeholder("第二版"))
        val third = listOf(TableData.placeholder("第三版"))

        store.createSnapshot(encodeForTest(first), first)
        time += 100
        store.createSnapshot(encodeForTest(second), second)
        assertEquals(1, store.listSnapshots(::decodeForTest).size)
        time += 1_000
        store.createSnapshot(encodeForTest(third), third, force = true)
        assertEquals(2, store.listSnapshots(::decodeForTest).size)
    }

    @Test
    fun comparesAddedModifiedAndDeletedCoursesInRestoreDirection() {
        val unchanged = course("unchanged", "高等数学")
        val beforeEdit = course("edited", "大学英语", classroom = "A101")
        val afterEdit = beforeEdit.copy(classroom = "A202")
        val onlyCurrent = course("current-only", "物理")
        val onlyTarget = course("target-only", "化学")

        val diff = CourseSnapshotDiffer.compare(
            current = listOf(TableData.placeholder().copy(courses = listOf(unchanged, afterEdit, onlyCurrent))),
            target = listOf(TableData.placeholder().copy(courses = listOf(unchanged, beforeEdit, onlyTarget)))
        )

        assertEquals(CourseSnapshotDiff(addedCourses = 1, modifiedCourses = 1, deletedCourses = 1), diff)
    }

    @Test
    fun rejectsSnapshotIdThatCouldEscapeHistoryDirectory() {
        val root = Files.createTempDirectory("course-history-id").toFile()
        val store = CourseHistoryStore(root)

        assertThrows(IllegalArgumentException::class.java) {
            store.loadSnapshot("../tables", ::decodeForTest)
        }
    }

    private fun encodeForTest(tables: List<TableData>): String = tables.joinToString(
        prefix = "[", postfix = "]"
    ) { "{\"name\":\"${it.name}\",\"courses\":[]}" }

    private fun decodeForTest(content: String): List<TableData> =
        Regex("\\\"name\\\":\\\"([^\\\"]+)\\\"").findAll(content)
            .map { TableData.placeholder(it.groupValues[1]) }
            .toList()
            .also { require(it.isNotEmpty()) }

    private fun course(seriesId: String, name: String, classroom: String = "教室") = Course(
        courseId = seriesId,
        courseName = name,
        classNumber = "",
        department = "",
        credits = 0f,
        weeks = listOf(1),
        dayOfWeek = 1,
        startPeriod = 1,
        endPeriod = 2,
        teacher = "教师",
        classroom = classroom,
        courseType = "",
        courseCategory = "",
        isOnline = false,
        assessmentMethod = "",
        seriesId = seriesId
    )
}
