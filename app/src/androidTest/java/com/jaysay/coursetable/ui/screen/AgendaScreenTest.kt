package com.jaysay.coursetable.ui.screen

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jaysay.coursetable.data.model.Course
import com.jaysay.coursetable.data.preferences.PeriodTime
import com.jaysay.coursetable.data.preferences.ThemeMode
import com.jaysay.coursetable.ui.theme.JaySayTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class AgendaScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsDateSectionsCourseDetailsAndInvokesCourseCallback() {
        var selected: String? = null
        composeRule.setContent {
            JaySayTheme {
                AgendaScreen(
                    courses = courses(),
                    onCourseClick = { selected = it.courseId },
                    periodTimes = periods,
                    semesterStart = "2026-02-23",
                    totalWeeks = 2,
                    today = LocalDate.parse("2026-02-23")
                )
            }
        }

        composeRule.onNodeWithText("今天").assertExists()
        composeRule.onNodeWithText("明天").assertExists()
        composeRule.onNodeWithText("本周").assertExists()
        composeRule.onNodeWithText("教师：王老师").assertExists()
        composeRule.onNodeWithText("教室：A101").assertExists()
        composeRule.onNodeWithContentDescription("高等数学，2月23日，周一，08:00 - 09:40，第1-2节，教师王老师，教室A101，双击查看课程详情")
            .performClick()
        assertEquals("today", selected)
        composeRule.onNodeWithTag("agenda-list").performScrollToNode(hasText("后续"))
        composeRule.onNodeWithText("后续").assertExists()
    }

    @Test
    fun localSearchFiltersCardsAndLargeDarkTextLayoutKeepsContentAccessible() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 1.5f)) {
                JaySayTheme(ThemeMode.DARK) {
                    AgendaScreen(
                        courses = courses(),
                        onCourseClick = {},
                        periodTimes = periods,
                        semesterStart = "2026-02-23",
                        totalWeeks = 2,
                        today = LocalDate.parse("2026-02-23")
                    )
                }
            }
        }

        composeRule.onNodeWithTag("agenda-list").assertExists()
        composeRule.onNodeWithTag("agenda-search").performTextInput("B202")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("大学英语").assertExists()
        composeRule.onNodeWithText("高等数学").assertDoesNotExist()
        composeRule.onNodeWithText("教室：B202").assertExists()
    }

    @Test
    fun excludedWeekDoesNotRenderCoursesAndEmptyStateExplainsIt() {
        composeRule.setContent {
            JaySayTheme {
                AgendaScreen(
                    courses = listOf(courses().first()),
                    onCourseClick = {},
                    periodTimes = periods,
                    semesterStart = "2026-02-23",
                    totalWeeks = 1,
                    excludedWeeks = listOf(1),
                    today = LocalDate.parse("2026-02-23")
                )
            }
        }

        composeRule.onNodeWithTag("agenda-empty").assertExists()
        composeRule.onNodeWithText("从今天起没有安排的课程").assertExists()
    }

    private fun courses() = listOf(
        course("today", "高等数学", 1, listOf(1), "王老师", "A101"),
        course("tomorrow", "大学英语", 2, listOf(1), "李老师", "B202"),
        course("this-week", "程序设计", 5, listOf(1), "陈老师", "C303"),
        course("upcoming", "线性代数", 1, listOf(2), "周老师", "D404")
    )

    private fun course(id: String, name: String, day: Int, weeks: List<Int>, teacher: String, classroom: String) = Course(
        courseId = id,
        courseName = name,
        classNumber = "测试班",
        department = "测试学院",
        credits = 2f,
        weeks = weeks,
        dayOfWeek = day,
        startPeriod = 1,
        endPeriod = 2,
        teacher = teacher,
        classroom = classroom,
        courseType = "必修",
        courseCategory = "测试",
        isOnline = false,
        assessmentMethod = "考试"
    )

    private companion object {
        val periods = listOf(
            PeriodTime("08:00", "08:45"),
            PeriodTime("08:55", "09:40")
        )
    }
}
