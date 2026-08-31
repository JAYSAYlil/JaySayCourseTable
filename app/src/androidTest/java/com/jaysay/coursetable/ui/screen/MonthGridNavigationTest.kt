package com.jaysay.coursetable.ui.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jaysay.coursetable.data.model.Course
import com.jaysay.coursetable.data.model.ScheduleDateException
import com.jaysay.coursetable.data.model.ScheduleExceptionType
import com.jaysay.coursetable.data.model.ScheduleViewMode
import com.jaysay.coursetable.data.preferences.ThemeMode
import com.jaysay.coursetable.ui.theme.JaySayTheme
import com.jaysay.coursetable.util.ChineseCalendarUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** 月视图核心交互：点击学期内的日期格跳到该天（对应周 + 星期 + 单日视图）。 */
@RunWith(AndroidJUnit4::class)
class MonthGridNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun clickingSemesterDayJumpsToThatDayInDayMode() {
        var changedWeek = -1
        var changedDay = -1
        var changedMode: ScheduleViewMode? = null
        composeRule.setContent {
            JaySayTheme(themeMode = ThemeMode.LIGHT) {
                CourseTableScreen(
                    courses = listOf(
                        sampleCourse("高等数学", dayOfWeek = 2, startPeriod = 1, endPeriod = 2),
                        sampleCourse("大学英语", dayOfWeek = 5, startPeriod = 3, endPeriod = 4)
                    ),
                    currentWeek = 1,
                    onImportClick = {},
                    onCourseClick = {},
                    onWeekChange = { changedWeek = it },
                    semesterStart = "2030-02-04",
                    totalWeeks = 20,
                    viewMode = ScheduleViewMode.MONTH,
                    onViewModeChange = { changedMode = it },
                    focusedDay = 1,
                    onFocusedDayChange = { changedDay = it }
                )
            }
        }
        composeRule.waitForIdle()

        // 2030-02-04 是周一：二月网格里 5 日是周二（第 1 周第二天），应可点击。
        // 网格含下月日期，"5" 会出现两次，取第一个（2 月在前）。
        composeRule.onAllNodesWithText("5")[0].performClick()
        composeRule.waitForIdle()
        assertEquals(1, changedWeek)
        assertEquals(2, changedDay)
        assertEquals(ScheduleViewMode.DAY, changedMode)

        // 学期外日期（网格首行的 1 月 30 日，开学前）点击不应触发任何跳转。
        changedWeek = -1
        changedDay = -1
        composeRule.onAllNodesWithText("30")[0].performClick()
        composeRule.waitForIdle()
        assertEquals(-1, changedWeek)
        assertEquals(-1, changedDay)
    }

    @Test
    fun backFromDayOpenedViaMonthReturnsToMonth() {
        val modes = mutableListOf<ScheduleViewMode>()
        composeRule.setContent {
            // 有状态包装：viewMode 真实切换，BackHandler 才会随 DAY 模式启用。
            var mode by androidx.compose.runtime.remember {
                androidx.compose.runtime.mutableStateOf(ScheduleViewMode.MONTH)
            }
            JaySayTheme(themeMode = ThemeMode.LIGHT) {
                CourseTableScreen(
                    courses = listOf(sampleCourse("高等数学", dayOfWeek = 2, startPeriod = 1, endPeriod = 2)),
                    currentWeek = 1,
                    onImportClick = {},
                    onCourseClick = {},
                    onWeekChange = {},
                    semesterStart = "2030-02-04",
                    totalWeeks = 20,
                    viewMode = mode,
                    onViewModeChange = { mode = it; modes.add(it) },
                    focusedDay = 1,
                    onFocusedDayChange = {}
                )
            }
        }
        composeRule.waitForIdle()
        // 月视图点某天进入日视图。
        composeRule.onAllNodesWithText("5")[0].performClick()
        composeRule.waitForIdle()
        assertEquals(ScheduleViewMode.DAY, modes.last())

        // 返回手势：应切回月视图（而不是退出或无操作）。
                // 注入返回键事件（与系统返回手势等价），由启用中的 BackHandler 消费。
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        composeRule.waitForIdle()
        assertEquals(ScheduleViewMode.MONTH, modes.last())
    }

    @Test
    fun monthShowsCalendarContextAndRemovesLegacyLegend() {
        val nationalDay = LocalDate.of(2030, 10, 1)
        val lunarText = ChineseCalendarUtils.label(nationalDay).lunar
        composeRule.setContent {
            JaySayTheme(themeMode = ThemeMode.LIGHT) {
                CourseTableScreen(
                    courses = listOf(sampleCourse("高等数学", dayOfWeek = 2, startPeriod = 1, endPeriod = 2)),
                    currentWeek = 1,
                    onImportClick = {},
                    onCourseClick = {},
                    onWeekChange = {},
                    semesterStart = "2030-09-30",
                    totalWeeks = 20,
                    dateExceptions = listOf(
                        ScheduleDateException(
                            date = "2030-10-02",
                            type = ScheduleExceptionType.DAY_OFF,
                            title = "校庆停课"
                        )
                    ),
                    viewMode = ScheduleViewMode.MONTH,
                    onViewModeChange = {},
                    focusedDay = 1,
                    onFocusedDayChange = {}
                )
            }
        }
        composeRule.waitForIdle()

        assertTrue(composeRule.onAllNodesWithText("国庆节").fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithText(lunarText).fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithText("校庆停课").fetchSemanticsNodes().isNotEmpty())
        composeRule.onNodeWithText("点击日期查看详情").assertDoesNotExist()
        composeRule.onNode(hasContentDescription("2030-10-06", substring = true)).assertIsDisplayed()
    }

    @Test
    fun locateTodayMovesMonthPagerBackToCurrentMonth() {
        val today = LocalDate.now()
        val semesterStart = com.jaysay.coursetable.util.TimeUtils.weekStart(today).minusWeeks(19)
        composeRule.setContent {
            var week by remember { mutableIntStateOf(30) }
            JaySayTheme(themeMode = ThemeMode.LIGHT) {
                CourseTableScreen(
                    courses = listOf(sampleCourse("高等数学", dayOfWeek = 2, startPeriod = 1, endPeriod = 2)),
                    currentWeek = week,
                    onImportClick = {},
                    onCourseClick = {},
                    onWeekChange = { week = it },
                    onLocateToday = { week = 20 },
                    semesterStart = semesterStart.toString(),
                    totalWeeks = 32,
                    viewMode = ScheduleViewMode.MONTH,
                    onViewModeChange = {},
                    focusedDay = 1,
                    onFocusedDayChange = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("more-actions-button").performClick()
        composeRule.onNodeWithText("定位到今天").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("${today.year} 年 ${today.monthValue} 月").assertIsDisplayed()
    }

    private fun sampleCourse(
        name: String,
        dayOfWeek: Int,
        startPeriod: Int,
        endPeriod: Int
    ) = Course(
        courseId = "month-$name",
        courseName = name,
        classNumber = "T-01",
        department = "示例学院",
        credits = 2f,
        weeks = (1..20).toList(),
        dayOfWeek = dayOfWeek,
        startPeriod = startPeriod,
        endPeriod = endPeriod,
        teacher = "教师",
        classroom = "教室",
        courseType = "必修",
        courseCategory = "专业课",
        isOnline = false,
        assessmentMethod = "考试",
        seriesId = "month-series-$name"
    )
}
