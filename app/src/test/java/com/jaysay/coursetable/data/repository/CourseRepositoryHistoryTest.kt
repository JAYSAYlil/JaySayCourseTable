package com.jaysay.coursetable.data.repository

import com.jaysay.coursetable.data.history.CourseSnapshotDiff
import com.jaysay.coursetable.data.model.Course
import com.jaysay.coursetable.data.storage.DataCorruptionException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.file.Files

class CourseRepositoryHistoryTest {
    @Test
    fun automaticallySnapshotsOldStateAndRestoresIt() = runBlocking {
        val filesDir = Files.createTempDirectory("course-repository-history").toFile()
        val repository = CourseRepository(filesDir)
        val original = listOf(table(course("math", "高等数学", "A101")))
        val changed = listOf(
            table(
                course("math", "高等数学", "A202"),
                course("english", "大学英语", "B101")
            )
        )

        repository.saveAllTables(original)
        assertEquals(0, repository.listSnapshots().size)
        repository.saveAllTables(changed)

        val snapshot = repository.listSnapshots().single()
        assertEquals(1, snapshot.courseCount)
        assertEquals(
            CourseSnapshotDiff(addedCourses = 0, modifiedCourses = 1, deletedCourses = 1),
            repository.previewSnapshot(snapshot.id)
        )

        assertEquals(original, repository.restoreSnapshot(snapshot.id))
        assertEquals(original, repository.loadAllTables())
        assertEquals(2, repository.listSnapshots().size)
    }

    @Test
    fun savingUnchangedStateDoesNotCreateRedundantSnapshot() = runBlocking {
        val filesDir = Files.createTempDirectory("course-repository-noop").toFile()
        val repository = CourseRepository(filesDir)
        val tables = listOf(table(course("math", "高等数学", "A101")))

        repository.saveAllTables(tables)
        repository.saveAllTables(tables)

        assertEquals(0, repository.listSnapshots().size)
    }

    @Test
    fun snapshotsAndRestoresLegacyTopLevelArrayJson() = runBlocking {
        val filesDir = Files.createTempDirectory("course-repository-legacy").toFile()
        val legacy = listOf(table(course("legacy", "旧课程", "旧教室")))
        filesDir.resolve("tables.json").writeText(TableDataJson.toJson(legacy).toString(), Charsets.UTF_8)
        val repository = CourseRepository(filesDir)

        repository.saveAllTables(listOf(table(course("new", "新课程", "新教室"))))
        val snapshot = repository.listSnapshots().single()
        repository.restoreSnapshot(snapshot.id)

        assertEquals(legacy, repository.loadAllTables())
    }

    @Test
    fun ordinarySaveDoesNotOverwriteCorruptProtectedData() {
        val filesDir = Files.createTempDirectory("course-repository-protection").toFile()
        filesDir.resolve("tables.json").writeText("broken-primary", Charsets.UTF_8)
        filesDir.resolve("tables.json.bak").writeText("broken-backup", Charsets.UTF_8)
        val repository = CourseRepository(filesDir)

        assertThrows(DataCorruptionException::class.java) {
            runBlocking { repository.saveAllTables(listOf(table(course("new", "新课程", "教室")))) }
        }
        assertEquals("broken-primary", filesDir.resolve("tables.json").readText(Charsets.UTF_8))
        assertEquals("broken-backup", filesDir.resolve("tables.json.bak").readText(Charsets.UTF_8))
        assertEquals(emptyList<Any>(), filesDir.resolve("course_history").listFiles().orEmpty().toList())
    }

    @Test
    fun validatedHistoryRestoreCanRecoverCorruptPrimaryAndBackup() = runBlocking {
        val filesDir = Files.createTempDirectory("course-repository-recovery").toFile()
        val repository = CourseRepository(filesDir)
        val original = listOf(table(course("math", "高等数学", "A101")))
        repository.saveAllTables(original)
        repository.saveAllTables(listOf(table(course("english", "大学英语", "B101"))))
        val snapshot = repository.listSnapshots().single()
        filesDir.resolve("tables.json").writeText("broken-primary", Charsets.UTF_8)
        filesDir.resolve("tables.json.bak").writeText("broken-backup", Charsets.UTF_8)

        repository.restoreSnapshot(snapshot.id)

        assertEquals(original, repository.loadAllTables())
    }

    private fun table(vararg courses: Course) = TableData.placeholder().copy(
        semesterStart = "2026-09-01",
        courses = courses.toList()
    )

    private fun course(seriesId: String, name: String, classroom: String) = Course(
        courseId = seriesId,
        courseName = name,
        classNumber = "",
        department = "",
        credits = 0f,
        weeks = listOf(1, 2),
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
