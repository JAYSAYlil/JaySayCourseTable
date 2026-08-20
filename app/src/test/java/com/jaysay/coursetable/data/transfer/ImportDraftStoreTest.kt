package com.jaysay.coursetable.data.transfer

import com.jaysay.coursetable.data.model.Course
import com.jaysay.coursetable.data.parser.ExcelParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Files

class ImportDraftStoreTest {
    private val course = Course(
        courseId = "id", courseName = "线性代数", classNumber = "", department = "", credits = 0f,
        weeks = listOf(1, 2), dayOfWeek = 2, startPeriod = 3, endPeriod = 4, teacher = "教师",
        classroom = "A101", courseType = "", courseCategory = "", isOnline = false,
        assessmentMethod = "", seriesId = "series"
    )

    @Test fun draftSurvivesReloadAndCanBeCleared() {
        val directory = Files.createTempDirectory("import-draft-test").toFile()
        try {
            val store = ImportDraftStore.forTest(directory.resolve("draft.json"))
            val draft = ExcelParser.ParseResult(listOf(course), listOf("第2行需要确认"))
            store.save(draft)
            assertEquals(draft, store.load())
            store.clear()
            assertNull(store.load())
        } finally {
            directory.deleteRecursively()
        }
    }
}
