package com.jaysay.coursetable.ui.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jaysay.coursetable.data.model.Course
import com.jaysay.coursetable.data.model.ScheduleViewMode
import com.jaysay.coursetable.data.preferences.ThemeMode
import com.jaysay.coursetable.ui.theme.JaySayTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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
