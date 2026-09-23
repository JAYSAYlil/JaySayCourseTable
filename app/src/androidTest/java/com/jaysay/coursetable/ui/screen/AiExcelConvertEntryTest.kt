package com.jaysay.coursetable.ui.screen

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jaysay.coursetable.data.preferences.AppPreferences
import com.jaysay.coursetable.data.model.Course
import com.jaysay.coursetable.data.parser.AiScheduleResult
import com.jaysay.coursetable.ui.theme.JaySayTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue

class AiExcelConvertEntryTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun settingsEntryOpensCallbackAndConversionRequiresFileAndConsent() {
        rule.setContent {
            var showAiConvert by remember { mutableStateOf(false) }
            JaySayTheme {
                if (showAiConvert) {
                    AiExcelConvertScreen(
                        totalWeeks = 18,
                        canImport = true,
                        initialResult = null,
                        onResultChanged = {},
                        onImport = {},
                        onBack = { showAiConvert = false }
                    )
                } else {
                    SettingsScreen(
                        preferences = AppPreferences(),
                        onUpdatePrefs = {},
                        onExportBackup = {},
                        onImportBackup = {},
                        onOpenAiExcelConvert = { showAiConvert = true },
                        onBack = {}
                    )
                }
            }
        }
        rule.onNodeWithText("AI 转换学校课表").performScrollTo().assertIsDisplayed().performClick()
        rule.onNodeWithTag("ai-excel-convert").performScrollTo().assertIsDisplayed().assertIsNotEnabled()
        rule.onNodeWithTag("ai-excel-provider").performScrollTo().performClick()
        val providerBottom = rule.onNodeWithTag("ai-excel-provider").fetchSemanticsNode().boundsInRoot.bottom
        val customProvider = rule.onNodeWithText("自定义兼容接口")
        val customProviderTop = customProvider.fetchSemanticsNode().boundsInRoot.top
        assertTrue("provider choices must stay below their button", customProviderTop >= providerBottom)
        customProvider.performScrollTo().assertIsDisplayed().performClick()
        rule.onNodeWithTag("ai-excel-endpoint").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("ai-provider-test").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun narrowResultPreviewKeepsCourseDetailsAndActionsAccessible() {
        val result = AiScheduleResult(
            courses = listOf(
                Course(
                    courseId = "C-1",
                    courseName = "示例高等数学",
                    classNumber = "1",
                    department = "理学院",
                    credits = 4f,
                    weeks = listOf(1, 2, 3),
                    dayOfWeek = 2,
                    startPeriod = 3,
                    endPeriod = 4,
                    teacher = "李老师",
                    classroom = "A101",
                    courseType = "必修",
                    courseCategory = "数学",
                    isOnline = false,
                    assessmentMethod = "考试"
                )
            ),
            workbookBytes = byteArrayOf(),
            generatedCourseIds = 0,
            maximumWeek = 3
        )
        rule.setContent {
            Box(Modifier.size(320.dp, 640.dp)) {
                JaySayTheme {
                    AiExcelConvertScreen(
                        totalWeeks = 18,
                        canImport = true,
                        initialResult = result,
                        onResultChanged = {},
                        onImport = {},
                        onBack = {}
                    )
                }
            }
        }

        rule.onNodeWithText("示例高等数学").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("周2 · 第3–4节 · 周次 1,2,3").performScrollTo().assertIsDisplayed().assertTextEquals("周2 · 第3–4节 · 周次 1,2,3")
        rule.onNodeWithTag("ai-excel-save").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("ai-excel-import").performScrollTo().assertIsDisplayed()
    }
}
