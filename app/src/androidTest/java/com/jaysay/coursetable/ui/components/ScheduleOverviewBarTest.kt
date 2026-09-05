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
        setCompactContent(darkTheme = false, awayFromToday = false)

        composeRule.onNodeWithTag("course-search-button").assertExists()
        composeRule.onNodeWithTag("add-course-button").assertExists()
        // 导入是低频操作：紧凑宽度收进更多菜单，操作行不再常驻。
        composeRule.onNodeWithTag("import-course-button").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("定位到今天").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("设置").assertDoesNotExist()

        composeRule.onNodeWithContentDescription("更多操作").performClick()
        composeRule.onNodeWithTag("locate-today-menu-item").assertExists()
        composeRule.onNodeWithTag("import-course-menu-item").assertExists()
        composeRule.onNodeWithText("定位到今天").assertExists()
        composeRule.onNodeWithText("导入课表").assertExists()
        composeRule.onNodeWithText("设置").assertExists()
    }

    @Test
    fun compactWidthKeepsActionsVisibleInDarkTheme() {
        setCompactContent(darkTheme = true, awayFromToday = false)

        composeRule.onNodeWithContentDescription("搜索课程").assertExists()
        composeRule.onNodeWithContentDescription("添加课程").assertExists()
        composeRule.onNodeWithContentDescription("更多操作").assertExists()
        composeRule.onNodeWithTag("today-agenda-summary").assertExists()
    }

    @Test
    fun compactWidthShowsDirectLocateTodayWhenAway() {
        // 浏览非今日日期时，“回到今天”直接可达。
        setCompactContent(darkTheme = false, awayFromToday = true)

        composeRule.onNodeWithTag("locate-today-button").assertExists()
        composeRule.onNodeWithContentDescription("更多操作").performClick()
        composeRule.onNodeWithTag("locate-today-menu-item").assertDoesNotExist()
        composeRule.onNodeWithTag("import-course-menu-item").assertExists()
    }

    private fun setCompactContent(darkTheme: Boolean, awayFromToday: Boolean) {
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
                        onSettingsClick = {},
                        awayFromToday = awayFromToday
                    )
                }
            }
        }
    }
}
