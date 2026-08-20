package com.jaysay.coursetable

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mainScreenExposesCoreActionsAndCanOpenAddDialog() {
        composeRule.waitUntil(timeoutMillis = 8_000) {
            composeRule.onAllNodesWithTag("course-table-screen").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("course-table-screen").assertExists()
        composeRule.onNodeWithTag("import-course-button").assertExists()
        composeRule.onNodeWithTag("add-course-button").performClick()
        composeRule.onNodeWithText("添加课程").assertExists()
    }

    @Test
    fun manualConflictPromptStaysAboveAddDialogAndCanReturnToEditing() {
        composeRule.waitUntil(timeoutMillis = 8_000) {
            composeRule.onAllNodesWithTag("course-table-screen").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("add-course-button").performClick()
        composeRule.onNodeWithTag("course-name-input").performTextInput("虚构课程甲")
        composeRule.onNodeWithTag("course-save-button").performClick()
        composeRule.waitForIdle()
        if (composeRule.onAllNodesWithText("检测到课程冲突").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithText("仍然保存").performClick()
        }
        composeRule.waitUntil(timeoutMillis = 8_000) {
            composeRule.onAllNodesWithTag("course-edit-dialog").fetchSemanticsNodes().isEmpty()
        }

        composeRule.onNodeWithTag("add-course-button").performClick()
        composeRule.onNodeWithTag("course-name-input").performTextInput("虚构课程乙")
        composeRule.onNodeWithTag("course-save-button").performClick()

        composeRule.onNodeWithText("检测到课程冲突").assertExists()
        composeRule.onNodeWithText("返回修改").performClick()
        composeRule.onNodeWithText("添加课程").assertExists()
        composeRule.onNodeWithTag("course-name-input").assertExists()
    }

    @Test
    fun selectedViewModeSurvivesActivityRecreation() {
        composeRule.waitUntil(timeoutMillis = 8_000) {
            composeRule.onAllNodesWithTag("course-table-screen").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("view-mode-button").performClick()
        composeRule.onNodeWithTag("view-mode-work_week").performClick()
        composeRule.waitUntil(timeoutMillis = 8_000) {
            composeRule.onAllNodesWithText("五天").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.activityRule.scenario.recreate()

        composeRule.waitUntil(timeoutMillis = 8_000) {
            composeRule.onAllNodesWithText("五天").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun settingsScreenSurvivesActivityRecreation() {
        composeRule.waitUntil(timeoutMillis = 8_000) {
            composeRule.onAllNodesWithTag("course-table-screen").fetchSemanticsNodes().isNotEmpty()
        }

        // 进入设置页（窄屏入口在“更多操作”菜单中，宽屏直接可见）
        if (composeRule.onAllNodesWithTag("more-actions-button").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithTag("more-actions-button").performClick()
            composeRule.onNodeWithText("设置").performClick()
        } else {
            composeRule.onNodeWithContentDescription("设置").performClick()
        }
        composeRule.waitUntil(timeoutMillis = 8_000) {
            composeRule.onAllNodesWithText("外观模式").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.activityRule.scenario.recreate()

        // 导航位置必须保留，重建后仍停留在设置页而非跳回主界面
        composeRule.waitUntil(timeoutMillis = 8_000) {
            composeRule.onAllNodesWithText("外观模式").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun customBackgroundControlsAreReachableFromSettings() {
        composeRule.waitUntil(timeoutMillis = 8_000) {
            composeRule.onAllNodesWithTag("course-table-screen").fetchSemanticsNodes().isNotEmpty()
        }
        if (composeRule.onAllNodesWithTag("more-actions-button").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithTag("more-actions-button").performClick()
            composeRule.onNodeWithText("设置").performClick()
        } else {
            composeRule.onNodeWithContentDescription("设置").performClick()
        }

        composeRule.onNodeWithTag("choose-custom-background")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("课表背景").assertExists()
    }

    @Test
    fun systemBackFromAgendaReturnsToScheduleInsteadOfExiting() {
        composeRule.waitUntil(timeoutMillis = 8_000) {
            composeRule.onAllNodesWithTag("course-table-screen").fetchSemanticsNodes().isNotEmpty()
        }
        if (composeRule.onAllNodesWithTag("more-actions-button").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithTag("more-actions-button").performClick()
            composeRule.onNodeWithText("日程列表").performClick()
        } else {
            composeRule.onNodeWithContentDescription("日程列表").performClick()
        }
        composeRule.onNodeWithText("日程列表").assertIsDisplayed()

        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }

        composeRule.waitUntil(timeoutMillis = 8_000) {
            composeRule.onAllNodesWithTag("course-table-screen").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun courseDetailBackRestoresTheExactScheduleScrollPosition() {
        composeRule.waitUntil(timeoutMillis = 8_000) {
            composeRule.onAllNodesWithTag("course-table-screen").fetchSemanticsNodes().isNotEmpty()
        }
        val courseName = "返回定位-${System.nanoTime()}"
        addCourse(courseName, startPeriod = "10", endPeriod = "11")

        val cardDescription = "$courseName，点击查看详情"
        composeRule.onNodeWithContentDescription(cardDescription).performScrollTo()
        composeRule.waitForIdle()
        val before = composeRule.onNodeWithTag("schedule-scroll")
            .fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue("测试课程应使课表产生有效滚动", before > 0f)

        composeRule.onNodeWithContentDescription(cardDescription).performClick()
        composeRule.onNodeWithText("课程详情").assertIsDisplayed()
        composeRule.activityRule.scenario.recreate()
        composeRule.onNodeWithText("课程详情").assertIsDisplayed()
        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitUntil(timeoutMillis = 8_000) {
            composeRule.onAllNodesWithTag("course-table-screen").fetchSemanticsNodes().isNotEmpty()
        }

        val after = composeRule.onNodeWithTag("schedule-scroll")
            .fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue("返回课程详情后应保持原滚动位置：before=$before, after=$after", kotlin.math.abs(before - after) < 1f)
    }

    @Test
    fun courseDetailReturnsToAgendaAndEditCancelReturnsToDetail() {
        composeRule.waitUntil(timeoutMillis = 8_000) {
            composeRule.onAllNodesWithTag("course-table-screen").fetchSemanticsNodes().isNotEmpty()
        }
        val courseName = "日程返回-${System.nanoTime()}"
        addCourse(courseName, startPeriod = "1", endPeriod = "2")

        if (composeRule.onAllNodesWithTag("more-actions-button").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithTag("more-actions-button").performClick()
            composeRule.onNodeWithText("日程列表").performClick()
        } else {
            composeRule.onNodeWithContentDescription("日程列表").performClick()
        }
        composeRule.onNodeWithText("日程列表").assertIsDisplayed()
        composeRule.onAllNodes(hasContentDescription(courseName, substring = true))[0]
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText("课程详情").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("编辑").performClick()
        composeRule.onNodeWithText("编辑课程").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("关闭").performClick()
        composeRule.onNodeWithText("课程详情").assertIsDisplayed()
        composeRule.activityRule.scenario.recreate()
        composeRule.onNodeWithText("课程详情").assertIsDisplayed()

        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.onNodeWithText("日程列表").assertIsDisplayed()
    }

    @Test
    fun settingsRestoresScrollPositionAfterReturningFromHistoryAndSuspendedWeeksScrolls() {
        composeRule.waitUntil(timeoutMillis = 8_000) {
            composeRule.onAllNodesWithTag("course-table-screen").fetchSemanticsNodes().isNotEmpty()
        }
        if (composeRule.onAllNodesWithTag("more-actions-button").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithTag("more-actions-button").performClick()
            composeRule.onNodeWithText("设置").performClick()
        } else {
            composeRule.onNodeWithContentDescription("设置").performClick()
        }

        composeRule.onNodeWithTag("excluded-weeks-setting").performScrollTo().performClick()
        composeRule.onNodeWithTag("excluded-weeks-list").performScrollToNode(hasText("第 20 周"))
        composeRule.onNodeWithText("第 20 周").assertIsDisplayed()
        composeRule.onNodeWithText("取消").performClick()

        composeRule.onNodeWithText("本机历史版本").performScrollTo().performClick()
        composeRule.onNodeWithText("历史版本").assertIsDisplayed()
        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.onNodeWithText("本机历史版本").assertIsDisplayed()
    }

    @Test
    fun pastedScheduleTextSurvivesActivityRecreation() {
        composeRule.waitUntil(timeoutMillis = 8_000) {
            composeRule.onAllNodesWithTag("course-table-screen").fetchSemanticsNodes().isNotEmpty()
        }
        if (composeRule.onAllNodesWithTag("more-actions-button").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithTag("more-actions-button").performClick()
            composeRule.onNodeWithText("设置").performClick()
        } else {
            composeRule.onNodeWithContentDescription("设置").performClick()
        }
        composeRule.onNodeWithText("粘贴导入课程").performScrollTo().performClick()
        composeRule.onNodeWithTag("paste-import-input")
            .performTextInput("周一 1-2节 虚构课程 1-16周")

        composeRule.activityRule.scenario.recreate()

        val restoredText = composeRule.onNodeWithTag("paste-import-input")
            .fetchSemanticsNode().config[SemanticsProperties.EditableText].text
        assertTrue(restoredText.contains("虚构课程"))
    }

    private fun addCourse(courseName: String, startPeriod: String, endPeriod: String) {
        composeRule.onNodeWithTag("add-course-button").performClick()
        composeRule.onNodeWithTag("course-name-input").performTextInput(courseName)
        composeRule.onNodeWithTag("course-start-period-input")
            .performScrollTo()
            .performTextReplacement(startPeriod)
        composeRule.onNodeWithTag("course-end-period-input")
            .performTextReplacement(endPeriod)
        composeRule.onNodeWithTag("course-save-button").performClick()
        composeRule.waitForIdle()
        if (composeRule.onAllNodesWithText("检测到课程冲突").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithText("仍然保存").performClick()
        }
        composeRule.waitUntil(timeoutMillis = 8_000) {
            composeRule.onAllNodesWithTag("course-edit-dialog").fetchSemanticsNodes().isEmpty()
        }
    }
}
