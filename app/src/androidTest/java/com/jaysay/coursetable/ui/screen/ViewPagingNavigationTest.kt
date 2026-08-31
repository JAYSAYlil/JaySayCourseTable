package com.jaysay.coursetable.ui.screen

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jaysay.coursetable.data.model.Course
import com.jaysay.coursetable.data.model.ScheduleViewMode
import com.jaysay.coursetable.data.preferences.ThemeMode
import com.jaysay.coursetable.ui.theme.JaySayTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 视图分页导航回归：
 * - 日视图存在按天翻页区域，滑动后向视图回写周次与聚焦日；
 * - 月视图存在按月翻页区域（月内课程与点击跳转由 MonthGridNavigationTest 覆盖）。
 */
@RunWith(AndroidJUnit4::class)
class ViewPagingNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun sampleCourse(name: String, day: Int) = Course(
        courseId = "paging-$name", courseName = name, classNumber = "T-01",
        department = "示例学院", credits = 2f, weeks = (1..20).toList(),
        dayOfWeek = day, startPeriod = 1, endPeriod = 2,
        teacher = "教师", classroom = "教室", courseType = "必修",
        courseCategory = "专业课", isOnline = false, assessmentMethod = "考试",
        seriesId = "paging-series-$name"
    )

    @Test
    fun dayViewSwipeRewritesWeekAndDay() {
        val weeks = mutableListOf<Int>()
        val days = mutableListOf<Int>()
        composeRule.setContent {
            JaySayTheme(themeMode = ThemeMode.LIGHT) {
                CourseTableScreen(
                    courses = listOf(sampleCourse("高等数学", 1), sampleCourse("体育", 3)),
                    currentWeek = 1,
                    onImportClick = {},
                    onCourseClick = {},
                    onWeekChange = { weeks.add(it) },
                    semesterStart = "2030-02-04",
                    totalWeeks = 20,
                    viewMode = ScheduleViewMode.DAY,
                    onViewModeChange = {},
                    focusedDay = 1,
                    onFocusedDayChange = { days.add(it) }
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("day-swipe-area").assertExists()

        // 向左滑一天：聚焦日推进；连续滑到周日后再滑应跨入下一周。
        repeat(7) {
            composeRule.onNodeWithTag("day-swipe-area").performTouchInput { swipeLeft() }
            composeRule.waitForIdle()
        }
        assertTrue("滑动应回写聚焦日", days.isNotEmpty())
        assertTrue("聚焦日应在 1..7 范围内", days.last() in 1..7)
        assertTrue("滑动应回写周次", weeks.isNotEmpty())
    }

    @Test
    fun monthViewPagingAreaExists() {
        composeRule.setContent {
            JaySayTheme(themeMode = ThemeMode.LIGHT) {
                CourseTableScreen(
                    courses = listOf(sampleCourse("高等数学", 2)),
                    currentWeek = 1,
                    onImportClick = {},
                    onCourseClick = {},
                    onWeekChange = {},
                    semesterStart = "2030-02-04",
                    totalWeeks = 20,
                    viewMode = ScheduleViewMode.MONTH,
                    onViewModeChange = {},
                    focusedDay = 1,
                    onFocusedDayChange = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("month-swipe-area").assertExists()
    }
}
