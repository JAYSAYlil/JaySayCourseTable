package com.jaysay.coursetable.ui.screen

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jaysay.coursetable.data.model.Course
import com.jaysay.coursetable.data.model.CourseImportAnalyzer
import com.jaysay.coursetable.ui.theme.JaySayTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImportConfirmScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun conflictIsVisibleAndNotSelectedUntilUserChoosesIt() {
        val existing = course("EXISTING", "现有课程", day = 1)
        val preview = CourseImportAnalyzer.analyze(
            existing = listOf(existing),
            imported = listOf(
                course("SAFE", "安全课程", day = 2),
                course("CONFLICT", "冲突课程", day = 1),
                existing
            )
        )
        composeRule.setContent {
            JaySayTheme {
                ImportConfirmScreen(preview, listOf("完全虚构的测试提示"), onConfirm = {}, onCancel = {})
            }
        }

        composeRule.onNodeWithText("新增 1 · 合并 0 · 冲突 1 · 重复 1 · 提示 1").assertExists()
        composeRule.onNodeWithText("导入 1 条").assertExists()
        composeRule.onNodeWithTag("import-preview-list").performScrollToIndex(1)
        composeRule.onNodeWithTag("import-filter-conflict").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("import-preview-list").performScrollToIndex(3)
        composeRule.onNodeWithText("冲突课程").assertExists()
        composeRule.onNodeWithTag("import-item-1").performClick()
        composeRule.onNodeWithText("导入 2 条").assertExists()
    }

    private fun course(id: String, name: String, day: Int) = Course(
        courseId = id,
        courseName = name,
        classNumber = "01",
        department = "虚构院系",
        credits = 2f,
        weeks = listOf(1, 2),
        dayOfWeek = day,
        startPeriod = 1,
        endPeriod = 2,
        teacher = "虚构教师",
        classroom = "虚构教室",
        courseType = "必修",
        courseCategory = "专业课",
        isOnline = false,
        assessmentMethod = "考试"
    )
}
