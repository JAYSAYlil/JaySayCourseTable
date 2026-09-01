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
import org.junit.Assert.assertEquals
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
    fun semesterDateSheetOpensOnCurrentSemesterStart() {
        setSettingsContent()

        composeRule.onNodeWithText("开学日期").performScrollTo().performClick()
        composeRule.onNodeWithTag("semester-date-picker-sheet").assertExists()
        // 选择器必须直接落在当前课表的开学日期：月份标题与该日可选都来自它。
        composeRule.onNodeWithText("2026 年 9 月").assertExists()
        composeRule.onNodeWithTag("date-picker-day-2026-09-07").assertExists()
    }

    @Test
    fun semesterDateSheetOffersYearQuickSelection() {
        setSettingsContent()

        composeRule.onNodeWithText("开学日期").performScrollTo().performClick()
        composeRule.onNodeWithTag("semester-date-picker-sheet").assertExists()
        composeRule.onNodeWithTag("date-picker-year-2026").assertExists()
        // 点选上一年：月份标题立即切到对应年份，无需连续点"上个月"跨年。
        composeRule.onNodeWithTag("date-picker-year-2025").performScrollTo().performClick()
        composeRule.onNodeWithText("2025 年 9 月").assertExists()
    }

    @Test
    fun semesterDateSavesExactlyPickedDay() {
        var saved: TableData? = null
        composeRule.setContent {
            JaySayTheme {
                SettingsScreen(
                    tableData = TableData("测试课表", emptyList(), semesterStart = "2026-09-07"),
                    preferences = AppPreferences(),
                    onUpdatePrefs = {},
                    onUpdateTable = { saved = it },
                    onExportBackup = {},
                    onImportBackup = {},
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithText("开学日期").performScrollTo().performClick()
        // 9/9 是周三：保存侧不做周一归一化，重开选择器时还原用户选择的那一天。
        composeRule.onNodeWithTag("date-picker-day-2026-09-09").performClick()
        composeRule.onNodeWithText("确定").performClick()
        composeRule.waitForIdle()
        assertEquals("2026-09-09", saved?.semesterStart)
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
                    onUpdateTable = {},
                    onExportBackup = {},
                    onImportBackup = {},
                    onBack = {}
                )
            }
        }
    }
}
