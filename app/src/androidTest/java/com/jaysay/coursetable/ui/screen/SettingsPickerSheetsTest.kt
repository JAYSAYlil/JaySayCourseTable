package com.jaysay.coursetable.ui.screen

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jaysay.coursetable.data.preferences.AppPreferences
import com.jaysay.coursetable.data.repository.TableData
import com.jaysay.coursetable.ui.theme.JaySayTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 设置页的日期和节次时间必须使用应用内纯白底部面板，而不是系统弹窗。 */
@RunWith(AndroidJUnit4::class)
class SettingsPickerSheetsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun semesterDateOpensCustomCalendarSheet() {
        setSettingsContent()

        composeRule.onNodeWithText("开学日期").performScrollTo().performClick()
        composeRule.onNodeWithTag("semester-date-picker-sheet").assertExists()
        composeRule.onNodeWithTag("date-picker-next-month").assertExists()
        composeRule.onNodeWithText("取消").performClick()
        composeRule.onNodeWithTag("semester-date-picker-sheet").assertDoesNotExist()
    }

    @Test
    fun periodTimeOpensCustomWheelSheet() {
        setSettingsContent()

        composeRule.onNodeWithTag("settings-search-field").performTextInput("节次")
        composeRule.onNodeWithText("08:00").performScrollTo().performClick()
        composeRule.onNodeWithTag("period-time-picker-sheet").assertExists()
        composeRule.onNodeWithTag("time-picker-hour-wheel").assertExists()
        composeRule.onNodeWithTag("time-picker-minute-wheel").assertExists()
    }

    private fun setSettingsContent() {
        composeRule.setContent {
            JaySayTheme {
                SettingsScreen(
                    tableData = TableData("测试课表", emptyList(), semesterStart = "2026-09-07"),
                    preferences = AppPreferences(),
                    onUpdatePrefs = {},
                    onExportBackup = {},
                    onImportBackup = {},
                    onBack = {}
                )
            }
        }
    }
}
