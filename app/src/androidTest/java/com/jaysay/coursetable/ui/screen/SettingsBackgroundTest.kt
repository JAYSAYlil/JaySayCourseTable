package com.jaysay.coursetable.ui.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jaysay.coursetable.data.preferences.AppPreferences
import com.jaysay.coursetable.ui.theme.JaySayTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsBackgroundTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun readabilityOverlayCanBeDisabledAndPreferenceIsSaved() {
        composeRule.setContent {
            var preferences by remember { mutableStateOf(AppPreferences()) }
            JaySayTheme {
                SettingsScreen(
                    preferences = preferences,
                    onUpdatePrefs = { preferences = it },
                    onExportBackup = {},
                    onImportBackup = {},
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithTag("background-readability-overlay-switch")
            .performScrollTo()
            .assertIsOn()
            .performClick()
            .assertIsOff()
    }
}
