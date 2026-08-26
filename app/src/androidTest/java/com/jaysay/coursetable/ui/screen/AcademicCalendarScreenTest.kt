package com.jaysay.coursetable.ui.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jaysay.coursetable.data.model.ScheduleDateException
import com.jaysay.coursetable.data.model.ScheduleExceptionType
import com.jaysay.coursetable.data.repository.TableData
import com.jaysay.coursetable.ui.theme.JaySayTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AcademicCalendarScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun allWeeksAreReachableAndChangesAreSavedImmediately() {
        var saved = TableData(
            name = "测试课表",
            courses = emptyList(),
            semesterStart = "2030-02-04",
            totalWeeks = 20
        )
        var updateCount = 0
        composeRule.setContent {
            JaySayTheme {
                CalendarExceptionScreen(
                    tableData = saved,
                    onUpdate = {
                        saved = it
                        updateCount++
                    },
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithTag("excluded-week-20")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(listOf(20), saved.excludedWeeks)
            assertEquals(1, updateCount)
        }

        composeRule.onNodeWithTag("academic-calendar-screen")
            .performScrollToNode(hasTestTag("add-week-label"))
        composeRule.onNodeWithTag("add-week-label").performClick()
        composeRule.onNodeWithText("添加周标签").assertIsDisplayed()
        composeRule.runOnIdle { assertTrue(20 in saved.excludedWeeks) }
    }

    @Test
    fun existingDateArrangementCanBeEditedWithoutCreatingDuplicate() {
        val arrangementId = "arrangement-1"
        var saved = TableData(
            name = "测试课表",
            courses = emptyList(),
            semesterStart = "2030-02-04",
            totalWeeks = 20,
            dateExceptions = listOf(
                ScheduleDateException(
                    id = arrangementId,
                    date = "2030-02-05",
                    type = ScheduleExceptionType.DAY_OFF,
                    title = "校庆"
                )
            )
        )
        composeRule.setContent {
            JaySayTheme {
                CalendarExceptionScreen(
                    tableData = saved,
                    onUpdate = { saved = it },
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithTag("academic-calendar-screen")
            .performScrollToNode(hasTestTag("edit-date-arrangement-$arrangementId"))
        composeRule.onNodeWithTag("edit-date-arrangement-$arrangementId").performClick()
        composeRule.onNodeWithText("编辑具体日期安排").assertIsDisplayed()
        composeRule.onNodeWithTag("date-arrangement-title-input").performTextReplacement("调整后的校庆安排")
        composeRule.onNodeWithText("保存").performClick()

        composeRule.runOnIdle {
            assertEquals(1, saved.dateExceptions.size)
            assertEquals(arrangementId, saved.dateExceptions.single().id)
            assertEquals("调整后的校庆安排", saved.dateExceptions.single().title)
        }
    }
}
