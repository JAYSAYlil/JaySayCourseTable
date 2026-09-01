package com.jaysay.coursetable.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.jaysay.coursetable.data.model.TodayAgenda
import com.jaysay.coursetable.data.model.TodayAgendaPhase
import org.junit.Rule
import org.junit.Test

class ScheduleOverviewBarTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun compactWidthGroupsLowFrequencyActionsAndKeepsTalkBackLabels() {
        setCompactContent(darkTheme = false)

        composeRule.onNodeWithTag("course-search-button").assertExists()
        composeRule.onNodeWithTag("add-course-button").assertExists()
        composeRule.onNodeWithTag("import-course-button").assertExists()
        composeRule.onNodeWithContentDescription("定位到今天").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("设置").assertDoesNotExist()

        composeRule.onNodeWithContentDescription("更多操作").performClick()
        composeRule.onNodeWithTag("locate-today-menu-item").assertExists()
        composeRule.onNodeWithText("定位到今天").assertExists()
        composeRule.onNodeWithText("设置").assertExists()
    }

    @Test
    fun compactWidthKeepsActionsVisibleInDarkTheme() {
        setCompactContent(darkTheme = true)

        composeRule.onNodeWithContentDescription("搜索课程").assertExists()
        composeRule.onNodeWithContentDescription("添加课程").assertExists()
        composeRule.onNodeWithContentDescription("导入课表").assertExists()
        composeRule.onNodeWithContentDescription("更多操作").assertExists()
        composeRule.onNodeWithTag("today-agenda-summary").assertExists()
    }

    private fun setCompactContent(darkTheme: Boolean) {
        composeRule.setContent {
            MaterialTheme(colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()) {
                Box(Modifier.width(360.dp)) {
                    ScheduleOverviewBar(
                        tableName = "2026 年秋季学期课程表",
                        agenda = TodayAgenda(phase = TodayAgendaPhase.NO_COURSES, week = 1),
                        searchQuery = "",
                        onSearchQueryChange = {},
                        onTableMenuClick = {},
                        onAddCourseClick = {},
                        onImportClick = {},
                        onLocateToday = {},
                        onSettingsClick = {}
                    )
                }
            }
        }
    }
}
