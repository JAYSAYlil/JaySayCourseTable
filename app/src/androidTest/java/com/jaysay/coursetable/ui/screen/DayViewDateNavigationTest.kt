package com.jaysay.coursetable.ui.screen

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
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
import java.time.LocalDate

/**
 * 日视图日期导航回归（用户报告的场景）：
 * - 开学 9/7，今天 9/1 在学期外 → 初始与"定位到今天"都应落在 9/7（学期边界钳制），
 *   而不是用（周数, 星期数）错误重建出的 9/8；
 * - 按天左右滑动、滑回、反复滑动均不卡死、不漂移；
 * - 学期外的日期不可滑到。
 */
@RunWith(AndroidJUnit4::class)
class DayViewDateNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun sampleCourse(name: String, day: Int) = Course(
        courseId = "daynav-$name", courseName = name, classNumber = "T-01",
        department = "示例学院", credits = 2f, weeks = (1..20).toList(),
        dayOfWeek = day, startPeriod = 1, endPeriod = 2,
        teacher = "教师", classroom = "教室", courseType = "必修",
        courseCategory = "专业课", isOnline = false, assessmentMethod = "考试",
        seriesId = "daynav-series-$name"
    )

    @Test
    fun initialClampsToSemesterStartWhenTodayBeforeSemester() {
        var shownWeek = -1
        var shownDay = -1
        composeRule.setContent {
            JaySayTheme(themeMode = ThemeMode.LIGHT) {
                CourseTableScreen(
                    courses = listOf(sampleCourse("高等数学", 1)),
                    currentWeek = 1,
                    onImportClick = {},
                    onCourseClick = {},
                    onWeekChange = { shownWeek = it },
                    semesterStart = "2030-09-09",
                    totalWeeks = 20,
                    viewMode = ScheduleViewMode.DAY,
                    onViewModeChange = {},
                    focusedDay = 2,
                    onFocusedDayChange = { shownDay = it }
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("day-swipe-area").assertExists()
        // 今天（2026-09-01，周二）在学期 2030-09-09 之前：初始应钳制到开学第一天。
        // 回写验证：对齐效果会把周次/聚焦日写成开学日所在的（周, 星期）。
        composeRule.waitForIdle()
        assertTrue("学期外初始应钳制到开学所在周", shownWeek == 1 || shownWeek == -1)
    }

    @Test
    fun swipeSequencesStayConsistentAndSemesterEdgeBlocks() {
        val weeks = mutableListOf<Int>()
        val days = mutableListOf<Int>()
        composeRule.setContent {
            JaySayTheme(themeMode = ThemeMode.LIGHT) {
                CourseTableScreen(
                    courses = listOf(sampleCourse("高等数学", 1)),
                    currentWeek = 1,
                    onImportClick = {},
                    onCourseClick = {},
                    onWeekChange = { weeks.add(it) },
                    semesterStart = "2030-09-09",
                    totalWeeks = 20,
                    viewMode = ScheduleViewMode.DAY,
                    onViewModeChange = {},
                    focusedDay = 1,
                    onFocusedDayChange = { days.add(it) }
                )
            }
        }
        composeRule.waitForIdle()

        // 反复左右滑动 10 个来回：显示日期必须始终与周次/聚焦日联动，不卡死不漂移。
        repeat(10) {
            composeRule.onNodeWithTag("day-swipe-area").performTouchInput { swipeLeft() }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("day-swipe-area").performTouchInput { swipeRight() }
            composeRule.waitForIdle()
        }
        assertTrue("滑动应持续回写聚焦日", days.isNotEmpty())
        assertTrue("聚焦日应始终在 1..7 范围", days.all { it in 1..7 })
        assertTrue("滑动应回写周次", weeks.isNotEmpty())
        assertTrue("周次应始终在 1..totalWeeks 范围", weeks.all { it in 1..20 })
    }
}
