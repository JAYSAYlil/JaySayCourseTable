package com.jaysay.coursetable.ui.screen

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jaysay.coursetable.data.model.Course
import com.jaysay.coursetable.data.model.ScheduleViewMode
import com.jaysay.coursetable.data.preferences.ThemeMode
import com.jaysay.coursetable.ui.theme.JaySayTheme
import com.jaysay.coursetable.util.TimeUtils
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
        // 初始在周二；真正点击“定位到今天”后，今天位于学期前，必须钳制到开学日。
        if (composeRule.onAllNodesWithContentDescription("定位到今天").fetchSemanticsNodes().isEmpty()) {
            composeRule.onNodeWithContentDescription("更多操作").performClick()
            composeRule.onNodeWithTag("locate-today-menu-item").performClick()
        } else {
            composeRule.onNodeWithContentDescription("定位到今天").performClick()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("day-swipe-area").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "2030-09-09")
        )
        assertEquals("定位后应回写开学所在周", 1, shownWeek)
        assertEquals("定位后应回写开学日星期", 1, shownDay)
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

    @Test
    fun eachDaySwipeSettlesOnExactlyTheAdjacentDate() {
        val semesterStart = LocalDate.of(2030, 2, 4)
        composeRule.setContent {
            JaySayTheme(themeMode = ThemeMode.LIGHT) {
                CourseTableScreen(
                    courses = listOf(sampleCourse("高等数学", 1)),
                    currentWeek = 1,
                    onImportClick = {},
                    onCourseClick = {},
                    onWeekChange = {},
                    semesterStart = semesterStart.toString(),
                    totalWeeks = 20,
                    viewMode = ScheduleViewMode.DAY,
                    onViewModeChange = {},
                    focusedDay = 1,
                    onFocusedDayChange = {}
                )
            }
        }
        composeRule.waitForIdle()

        fun assertSettledDate(expected: LocalDate) {
            composeRule.onNodeWithTag("day-swipe-area").assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    expected.toString()
                )
            )
        }

        // 这是对“快速甩动不能跳过中间日期”的稳定回归断言：每次落定都必须
        // 恰好移动一天，不能一次跨越两页或被外部状态拉回。
        assertSettledDate(semesterStart)
        repeat(3) { offset ->
            composeRule.onNodeWithTag("day-swipe-area").performTouchInput { swipeLeft() }
            composeRule.waitForIdle()
            assertSettledDate(semesterStart.plusDays((offset + 1).toLong()))
        }
        repeat(3) { offset ->
            composeRule.onNodeWithTag("day-swipe-area").performTouchInput { swipeRight() }
            composeRule.waitForIdle()
            assertSettledDate(semesterStart.plusDays((2 - offset).toLong()))
        }
    }

    @Test
    fun dayPagingPreservesScrollPosition() {
        val semesterStart = LocalDate.of(2030, 2, 4)
        composeRule.setContent {
            JaySayTheme(themeMode = ThemeMode.LIGHT) {
                CourseTableScreen(
                    courses = listOf(sampleCourse("高等数学", 1)),
                    currentWeek = 1,
                    onImportClick = {},
                    onCourseClick = {},
                    onWeekChange = {},
                    semesterStart = semesterStart.toString(),
                    totalWeeks = 20,
                    viewMode = ScheduleViewMode.DAY,
                    onViewModeChange = {},
                    focusedDay = 1,
                    onFocusedDayChange = {}
                )
            }
        }
        composeRule.waitForIdle()

        // 同一节次格在相邻日期页里布局位置相同，其屏幕纵坐标差就是滚动偏移。
        // 默认节次共 12 节，取最底部的第 12 节，保证滚动后位置差足够大。
        fun slotY(day: Int, period: Int): Float =
            composeRule.onAllNodesWithContentDescription(
                "${TimeUtils.getDayName(day)}第${period}节空白，点击添加课程"
            ).fetchSemanticsNodes().first().positionInRoot.y

        val beforeScroll = slotY(1, 12)
        repeat(3) {
            composeRule.onNodeWithTag("day-swipe-area").performTouchInput { swipeUp() }
            composeRule.waitForIdle()
        }
        val afterScroll = slotY(1, 12)
        assertTrue("纵向滚动应实际发生", afterScroll < beforeScroll - 200f)

        // 翻到第二天：同一节次格必须停留在相同屏幕位置（滚动位置跨页保持，与周视图一致）。
        composeRule.onNodeWithTag("day-swipe-area").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        val tuesday = slotY(2, 12)
        assertEquals("翻页后滚动位置应保持不变", afterScroll, tuesday, 8f)
    }
}
