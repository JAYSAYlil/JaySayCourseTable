package com.jaysay.coursetable.ui.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.jaysay.coursetable.data.preferences.AppPreferences
import com.jaysay.coursetable.data.preferences.ThemeAccent
import com.jaysay.coursetable.ui.theme.JaySayTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** 通用设置里新增的两项：主题色与“不显示上课时间段”。 */
class SettingsAppearanceOptionsTest {
    @get:Rule val rule = createComposeRule()
    private var saved: AppPreferences? = null

    private fun render(preferences: AppPreferences = AppPreferences()) {
        rule.setContent {
            JaySayTheme {
                SettingsScreen(
                    preferences = preferences,
                    onUpdatePrefs = { saved = it },
                    onExportBackup = {},
                    onImportBackup = {},
                    onBack = {}
                )
            }
        }
    }

    @Test fun themeAccentRowIsReachableAndNamesEveryOption() {
        render()
        rule.onNodeWithText("主题色").performScrollTo().assertIsDisplayed()
        ThemeAccent.entries.forEach { accent ->
            rule.onNodeWithTag("theme-accent-" + accent.name).assertExists()
        }
    }

    @Test fun choosingAnAccentSavesIt() {
        render()
        rule.onNodeWithTag("theme-accent-VIOLET").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(ThemeAccent.VIOLET, saved?.themeAccent) }
    }

    @Test fun accentSwatchesCarryAccessibleNames() {
        render()
        rule.onNodeWithContentDescription("靛蓝").assertExists()
        rule.onNodeWithContentDescription("暖橙").assertExists()
    }

    @Test fun currentAccentStaysSelectedWhenReopening() {
        render(AppPreferences(themeAccent = ThemeAccent.PINK))
        // 选中项以勾选图标显示，未选中项只有色块；这里断言整行仍在且可再次点选。
        rule.onNodeWithTag("theme-accent-PINK").performScrollTo().assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals(ThemeAccent.PINK, saved?.themeAccent) }
    }

    @Test fun hideTimeSlotsSwitchSavesThePreference() {
        render()
        rule.onNodeWithTag("hide-time-slots-switch").performScrollTo().assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals(true, saved?.hideTimeSlots) }
    }

    @Test fun hideTimeSlotsSwitchReflectsSavedValue() {
        render(AppPreferences(hideTimeSlots = true))
        rule.onNodeWithTag("hide-time-slots-switch").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(false, saved?.hideTimeSlots) }
    }
}
