package com.jaysay.coursetable.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.jaysay.coursetable.ui.theme.JaySayTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NativeControlsTest {
    @get:Rule val rule = createComposeRule()

    @Test fun switchRetainsToggleAndDisabledSemantics() {
        var changes = 0
        rule.setContent {
            JaySayTheme {
                var checked by remember { mutableStateOf(false) }
                Column {
                    AppSwitch(checked, { checked = it; changes++ }, Modifier.testTag("switch"))
                    AppSwitch(false, { changes++ }, Modifier.testTag("disabled"), enabled = false)
                }
            }
        }
        rule.onNodeWithTag("switch").assertIsOff().performClick().assertIsOn()
            .performClick().assertIsOff()
        rule.onNodeWithTag("disabled").assertIsNotEnabled().performClick()
        rule.runOnIdle { assertEquals(2, changes) }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test fun sheetWaitsForExitAndCommitsRepeatedTapsOnlyOnce() {
        var commits = 0
        rule.setContent {
            JaySayTheme {
                var visible by remember { mutableStateOf(true) }
                if (visible) {
                    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                    val dismiss = rememberSheetDismiss(state)
                    AppModalBottomSheet(onDismissRequest = { visible = false }, sheetState = state) {
                        AppButton(onClick = { dismiss { commits++; visible = false } },
                            modifier = Modifier.testTag("commit")) { Text("Confirm") }
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("commit").performClick().performClick()
        rule.runOnIdle { assertEquals(0, commits) }
        rule.mainClock.advanceTimeBy(3000)
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(1, commits) }
        rule.onNodeWithTag("commit").assertDoesNotExist()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test fun sheetCommitSurvivesHostDisposalDuringExit() {
        var commits = 0
        val visible = mutableStateOf(true)
        rule.setContent {
            JaySayTheme {
                if (visible.value) {
                    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                    val dismiss = rememberSheetDismiss(state)
                    AppModalBottomSheet(onDismissRequest = { visible.value = false }, sheetState = state) {
                        AppButton(onClick = { dismiss { commits++ } }, modifier = Modifier.testTag("commit")) { Text("Confirm") }
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("commit").performClick()
        // 收起动画进行中宿主把面板移出组合：等价于这 250ms 内点了遮罩／下滑／按系统返回。
        rule.runOnIdle { visible.value = false }
        rule.mainClock.advanceTimeBy(3000)
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        rule.runOnIdle { assertEquals("面板退出期间宿主消失也不能吞掉确认动作", 1, commits) }
    }

    @Test fun alertActionsRemainReachableAtLargeFontAndNarrowWidth() {
        var confirmed = false
        rule.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 1.6f)) {
                JaySayTheme {
                    AppAlertDialog(onDismissRequest = {}, modifier = Modifier.width(260.dp),
                        title = { Text("Restore timetable") }, text = { Text("Review changes before restoring.") },
                        confirmButton = { AppTextButton(onClick = { confirmed = true }) { Text("Restore backup") } },
                        dismissButton = { AppTextButton(onClick = {}) { Text("Cancel") } })
                }
            }
        }
        rule.onNodeWithText("Cancel").assertIsDisplayed()
        rule.onNodeWithText("Restore backup").assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals(true, confirmed) }
    }
}
