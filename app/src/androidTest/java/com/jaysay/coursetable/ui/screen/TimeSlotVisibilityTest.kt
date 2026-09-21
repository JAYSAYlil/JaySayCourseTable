package com.jaysay.coursetable.ui.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.jaysay.coursetable.data.model.Course
import com.jaysay.coursetable.data.model.ScheduleViewMode
import com.jaysay.coursetable.data.preferences.defaultPeriodTimes
import com.jaysay.coursetable.ui.theme.JaySayTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** “不显示上课时间段”模式：左侧栏只留节数，时间文本整体消失。 */
class TimeSlotVisibilityTest {
    @get:Rule val rule = createComposeRule()

    private val course = Course("slot-1", "示例课程", "", "", 0f, (1..20).toList(), 1, 1, 2,
        "示例教师", "示例教室", "", "", false, "", seriesId = "slot-1")

    private fun render(hideTimeSlots: Boolean) {
        rule.setContent {
            JaySayTheme {
                CourseTableScreen(
                    courses = listOf(course),
                    currentWeek = 1,
                    onImportClick = {},
                    onCourseClick = {},
                    onWeekChange = {},
                    semesterStart = "2030-02-04",
                    totalWeeks = 20,
                    periodTimes = defaultPeriodTimes(),
                    viewMode = ScheduleViewMode.WEEK,
                    onViewModeChange = {},
                    focusedDay = 1,
                    onFocusedDayChange = {},
                    hideTimeSlots = hideTimeSlots
                )
            }
        }
    }

    @Test fun timesAreShownByDefault() {
        render(hideTimeSlots = false)
        val periods = defaultPeriodTimes()
        // 周视图会同时组合相邻页，同一文案可能出现多个节点，这里按“存在”断言。
        assertTrue(
            "默认必须显示第 1 节的开始时间",
            rule.onAllNodesWithText(periods.first().start).fetchSemanticsNodes().isNotEmpty()
        )
        assertTrue(
            "默认必须显示第 1 节的结束时间",
            rule.onAllNodesWithText(periods.first().end).fetchSemanticsNodes().isNotEmpty()
        )
    }

    @Test fun timesDisappearWhenTheModeIsOn() {
        render(hideTimeSlots = true)
        val periods = defaultPeriodTimes()
        rule.onNodeWithText(periods.first().start).assertDoesNotExist()
        rule.onNodeWithText(periods.first().end).assertDoesNotExist()
        rule.onNodeWithText(periods[1].start).assertDoesNotExist()
        // 节数本身必须还在，否则用户无法判断第几节。
        assertTrue(
            "节数不能一起消失",
            rule.onAllNodesWithText("1").fetchSemanticsNodes().isNotEmpty()
        )
        assertTrue(
            "课程卡片必须仍然显示",
            rule.onAllNodesWithText("示例课程").fetchSemanticsNodes().isNotEmpty()
        )
    }

    @Test fun courseCardsKeepFullWidthWhenTimesAreHidden() {
        render(hideTimeSlots = true)
        rule.onNodeWithTag("schedule-scroll").assertExists()
        rule.onAllNodesWithText("示例课程")[0].assertIsDisplayed()
    }
}
