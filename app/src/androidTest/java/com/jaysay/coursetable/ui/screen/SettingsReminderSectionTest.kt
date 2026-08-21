package com.jaysay.coursetable.ui.screen

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jaysay.coursetable.data.preferences.AppPreferences
import com.jaysay.coursetable.data.reminder.ReminderBlocker
import com.jaysay.coursetable.ui.theme.JaySayTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 设置页提醒区与权限状态警告的回归测试。 */
@RunWith(AndroidJUnit4::class)
class SettingsReminderSectionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun reminderSectionRendersWithPermissionWarnings() {
        composeRule.setContent {
            JaySayTheme {
                SettingsScreen(
                    preferences = AppPreferences(reminderEnabled = true, reminderMinutes = 10),
                    onUpdatePrefs = {},
                    onExportBackup = {},
                    onImportBackup = {},
                    reminderBlockers = listOf(
                        ReminderBlocker.NOTIFICATION_PERMISSION,
                        ReminderBlocker.CHANNEL_DISABLED
                    ),
                    onBack = {}
                )
            }
        }
        composeRule.onNodeWithText("提醒上课").assertExists()
        composeRule.onNodeWithText("通知权限未开启，提醒到达时无法显示").assertExists()
        composeRule.onNodeWithText("开启通知权限").assertExists()
        composeRule.onNodeWithText("“上课提醒”通知已在系统中关闭").assertExists()
        composeRule.onNodeWithText("开启通知渠道").assertExists()
    }

    @Test
    fun reminderSectionHiddenStatusWhenDisabled() {
        composeRule.setContent {
            JaySayTheme {
                SettingsScreen(
                    preferences = AppPreferences(reminderEnabled = false),
                    onUpdatePrefs = {},
                    onExportBackup = {},
                    onImportBackup = {},
                    reminderBlockers = emptyList(),
                    onBack = {}
                )
            }
        }
        composeRule.onNodeWithText("提醒上课").assertExists()
        composeRule.onNodeWithText("提醒功能正常，将按节次时间提前通知").assertDoesNotExist()
    }
}
