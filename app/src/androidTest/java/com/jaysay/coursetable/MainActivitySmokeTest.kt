package com.jaysay.coursetable

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
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
}
