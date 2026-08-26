package com.jaysay.coursetable.ui.screen

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jaysay.coursetable.data.model.Course
import com.jaysay.coursetable.data.model.ScheduleDateException
import com.jaysay.coursetable.data.model.ScheduleExceptionType
import com.jaysay.coursetable.data.model.ScheduleViewMode
import com.jaysay.coursetable.data.preferences.ThemeMode
import com.jaysay.coursetable.ui.theme.JaySayTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * 视觉契约测试不绑定字体抗锯齿的逐像素结果，而是守住用户真正关心的约束：
 * 三种视图的重要字段不裁切、主控件可见、深浅色页面不会退化为空白或整屏白光。
 */
@RunWith(AndroidJUnit4::class)
class CourseTableVisualContractTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun sevenDayLightBackgroundKeepsAllImportantCourseTextVisible() {
        renderAndAssert(ScheduleViewMode.WEEK, ThemeMode.LIGHT, customBackground = testBackground())
    }

    @Test
    fun fiveDayDarkBackgroundKeepsAllImportantCourseTextVisible() {
        renderAndAssert(ScheduleViewMode.WORK_WEEK, ThemeMode.DARK, customBackground = testBackground())
    }

    @Test
    fun singleDayDarkDefaultBackgroundKeepsAllImportantCourseTextVisible() {
        renderAndAssert(ScheduleViewMode.DAY, ThemeMode.DARK, customBackground = null)
    }

    @Test
    fun suspendedWeekStillSwipesAndCalendarContextIsVisible() {
        var calendarOpened = false
        composeRule.setContent {
            var week by remember { mutableIntStateOf(1) }
            JaySayTheme(themeMode = ThemeMode.LIGHT) {
                CourseTableScreen(
                    courses = listOf(longCourse().copy(weeks = listOf(1, 2))),
                    currentWeek = week,
                    onImportClick = {},
                    onCourseClick = {},
                    onWeekChange = { week = it },
                    onCalendarContextClick = { calendarOpened = true },
                    semesterStart = "2030-02-04",
                    totalWeeks = 20,
                    excludedWeeks = listOf(1),
                    dateExceptions = listOf(
                        ScheduleDateException(
                            date = "2030-02-05",
                            type = ScheduleExceptionType.DAY_OFF,
                            title = "校庆"
                        )
                    ),
                    weekLabels = mapOf(1 to "实践周"),
                    reduceMotion = true,
                    viewMode = ScheduleViewMode.WEEK,
                    onViewModeChange = {},
                    focusedDay = 1,
                    onFocusedDayChange = {}
                )
            }
        }

        composeRule.onNodeWithTag("calendar-context-strip").assertIsDisplayed()
        composeRule.onNodeWithTag("calendar-context-strip").performClick()
        composeRule.runOnIdle { assertTrue(calendarOpened) }
        composeRule.onNodeWithTag("suspended-week-content").assertIsDisplayed()
        composeRule.onNodeWithTag("week-swipe-area").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("第 2 周").assertIsDisplayed()
    }

    private fun renderAndAssert(
        viewMode: ScheduleViewMode,
        themeMode: ThemeMode,
        customBackground: ImageBitmap?
    ) {
        val course = longCourse()
        composeRule.setContent {
            JaySayTheme(themeMode = themeMode) {
                CourseTableScreen(
                    courses = listOf(course),
                    currentWeek = 1,
                    onImportClick = {},
                    onCourseClick = {},
                    onWeekChange = {},
                    semesterStart = "2030-02-04",
                    totalWeeks = 20,
                    reduceMotion = true,
                    customBackground = customBackground,
                    viewMode = viewMode,
                    onViewModeChange = {},
                    focusedDay = 1,
                    onFocusedDayChange = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("course-table-screen").assertIsDisplayed()
        composeRule.onNodeWithTag("week-navigation").assertIsDisplayed()
        composeRule.onNodeWithTag("view-mode-button").assertIsDisplayed()
        composeRule.onNode(hasContentDescription(course.courseName, substring = true))
            .performScrollTo()
            .assertIsDisplayed()

        assertTextHasNoVisualOverflow(course.courseName)
        assertTextHasNoVisualOverflow(course.teacher)
        assertTextHasNoVisualOverflow(course.classroom)
        assertScreenContainsVisualLayers(themeMode == ThemeMode.DARK)
    }

    private fun assertTextHasNoVisualOverflow(text: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText(text, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                action(layouts)
            }
        assertTrue("未取得“$text”的排版结果", layouts.isNotEmpty())
        val overflow = layouts.filter(TextLayoutResult::hasVisualOverflow)
        val details = overflow.joinToString { result ->
            "size=${result.size}, lines=${result.lineCount}, width=${result.didOverflowWidth}, height=${result.didOverflowHeight}"
        }
        assertFalse("“$text”发生裁切或省略：$details", overflow.isNotEmpty())
    }

    private fun assertScreenContainsVisualLayers(dark: Boolean) {
        val image = composeRule.onNodeWithTag("course-table-screen").captureToImage()
        val pixels = image.toPixelMap()
        val stepX = (image.width / 40).coerceAtLeast(1)
        val stepY = (image.height / 60).coerceAtLeast(1)
        val luminances = buildList {
            for (y in 0 until image.height step stepY) {
                for (x in 0 until image.width step stepX) add(pixels[x, y].luminance())
            }
        }
        val spread = luminances.maxOrNull()!! - luminances.minOrNull()!!
        val average = luminances.average()
        assertTrue("页面疑似退化为纯色或空白，亮度跨度=$spread", spread > 0.25f)
        if (dark) assertTrue("深色模式疑似出现整屏白光，平均亮度=$average", average < 0.62)
    }

    private fun testBackground(): ImageBitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply {
        for (y in 0 until height) {
            for (x in 0 until width) {
                setPixel(x, y, if ((x + y) % 2 == 0) 0xFF1B6658.toInt() else 0xFFD8B37A.toInt())
            }
        }
    }.asImageBitmap()

    private fun longCourse() = Course(
        courseId = "visual-contract",
        courseName = "移动应用程序设计与实践",
        classNumber = "TEST-01",
        department = "示例学院",
        credits = 2f,
        weeks = listOf(1),
        dayOfWeek = 1,
        startPeriod = 1,
        endPeriod = 2,
        teacher = "欧阳示例教师",
        classroom = "博学楼A1208智慧教室",
        courseType = "必修",
        courseCategory = "专业课",
        isOnline = false,
        assessmentMethod = "考试",
        seriesId = "visual-contract-series"
    )
}
