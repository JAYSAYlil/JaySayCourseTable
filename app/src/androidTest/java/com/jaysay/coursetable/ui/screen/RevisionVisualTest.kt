package com.jaysay.coursetable.ui.screen

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.jaysay.coursetable.data.model.Course
import com.jaysay.coursetable.data.model.ScheduleViewMode
import com.jaysay.coursetable.data.preferences.AppPreferences
import com.jaysay.coursetable.data.preferences.ThemeAccent
import com.jaysay.coursetable.data.preferences.ThemeMode
import com.jaysay.coursetable.ui.theme.JaySayTheme
import com.jaysay.coursetable.util.TimeUtils
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import java.io.File

/** Fictional release QA snapshots; independent of the installed application's course data. */
class RevisionVisualTest {
    @get:Rule val rule = createComposeRule()
    private val courses = (0..3).map { i ->
        Course("visual-$i", listOf("高等数学", "大学英语", "操作系统", "移动应用开发")[i], "", "", 0f,
            (1..20).toList(), i % 3 + 1, if (i == 1) 3 else 1, if (i == 1) 4 else 2,
            "示例教师", "教学楼A20${i + 1}", "", "", false, "", seriesId = "visual-$i")
    }
    @Test fun weekLight() = render(ScheduleViewMode.WEEK, false)
    @Test fun dayLight() = render(ScheduleViewMode.DAY, false)
    @Test fun dayDark() = render(ScheduleViewMode.DAY, true)
    @Test fun monthLight() = render(ScheduleViewMode.MONTH, false)
    @Test fun monthLargeFont() = render(ScheduleViewMode.MONTH, false, 2f)
    @Test fun editorLight() = renderEditor(false)
    @Test fun editorDark() = renderEditor(true)
    private fun renderEditor(dark: Boolean) {
        rule.setContent { JaySayTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
            CourseEditDialog(courses.first(), totalWeeks = 20, onSave = { _, _ -> }, onDelete = {}, onDismiss = {})
        } }
        capture("editor-${if (dark) "dark" else "light"}", "course-edit-dialog")
        rule.onNodeWithTag("course-apply-from-week").performScrollTo().performClick().assertIsOn()
        capture("editor-scope-${if (dark) "dark" else "light"}", "course-edit-dialog")
    }
    @Test fun detailLight() {
        var closes = 0
        rule.setContent { JaySayTheme(themeMode = ThemeMode.LIGHT) {
            CourseDetailScreen(courses.first(), allCourses = courses, onClose = { closes++ }, onEdit = {}, onDelete = {})
        } }
        capture("detail")
        val start = rule.onNodeWithText(courses.first().courseName).fetchSemanticsNode().boundsInRoot.center
        rule.onRoot().performTouchInput {
            swipe(start, start + androidx.compose.ui.geometry.Offset(0f, 35f), durationMillis = 1000)
        }
        rule.runOnIdle { org.junit.Assert.assertEquals("Short drags return instead of closing", 0, closes) }
        val afterReturn = rule.onNodeWithText(courses.first().courseName).fetchSemanticsNode().boundsInRoot.center
        org.junit.Assert.assertEquals(start.y, afterReturn.y, 1f)
        val bottom = rule.onRoot().fetchSemanticsNode().boundsInRoot.bottom
        rule.onRoot().performTouchInput {
            swipe(afterReturn, androidx.compose.ui.geometry.Offset(afterReturn.x, bottom - 40f), durationMillis = 1000)
        }
        rule.runOnIdle { org.junit.Assert.assertEquals("Long drags dismiss exactly once", 1, closes) }
    }
    @Test fun detailDragKeepsReturningWhenParentRecomposesMidDrag() {
        var closes = 0
        val tick = androidx.compose.runtime.mutableIntStateOf(0)
        rule.setContent { JaySayTheme(themeMode = ThemeMode.LIGHT) {
            // 在组合里读取 tick，并让 onClose 捕获这个会变化的 Int：每次重组都生成新的 lambda 实例，
            // 等价于 MainActivity 里每次重组重建的 closeCourseDetail。
            val revision = tick.intValue
            CourseDetailScreen(courses.first(), allCourses = courses,
                onClose = { if (revision >= 0) closes++ }, onEdit = {}, onDelete = {})
        } }
        val start = rule.onNodeWithText(courses.first().courseName).fetchSemanticsNode().boundsInRoot.center
        rule.onRoot().performTouchInput { down(start); moveBy(androidx.compose.ui.geometry.Offset(0f, 60f)) }
        // 手指还按着的时候父级重组：旧实现会重建 pointerInput 并丢掉这次拖动。
        rule.runOnUiThread { tick.intValue++ }
        rule.onRoot().performTouchInput { up() }
        rule.waitForIdle()
        val after = rule.onNodeWithText(courses.first().courseName).fetchSemanticsNode().boundsInRoot.center
        org.junit.Assert.assertEquals("未达阈值必须回到原位，不能卡在拖到一半的位置", start.y, after.y, 2f)
        rule.runOnIdle { org.junit.Assert.assertEquals("短拖不能触发关闭", 0, closes) }
    }

    @Test fun weekWithoutTimeSlots() {
        rule.setContent { JaySayTheme(themeMode = ThemeMode.LIGHT) {
            CourseTableScreen(courses, 1, {}, {}, {}, tableName = "示例学期课表", semesterStart = TimeUtils.currentWeekStartDate(),
                totalWeeks = 20, viewMode = ScheduleViewMode.WEEK, onViewModeChange = {}, focusedDay = 1, onFocusedDayChange = {},
                hideTimeSlots = true)
        } }
        rule.onNodeWithTag("course-table-screen").assertIsDisplayed()
        capture("week-no-times")
    }

    @Test fun weekWithNonDefaultAccentKeepsEveryBrandDetailColored() {
        rule.setContent { JaySayTheme(themeMode = ThemeMode.LIGHT, themeAccent = ThemeAccent.VIOLET) {
            CourseTableScreen(courses, 1, {}, {}, {}, tableName = "示例学期课表", semesterStart = TimeUtils.currentWeekStartDate(),
                totalWeeks = 20, viewMode = ScheduleViewMode.WEEK, onViewModeChange = {}, focusedDay = 1, onFocusedDayChange = {})
        } }
        rule.onNodeWithTag("course-table-screen").assertIsDisplayed()
        capture("week-accent-violet")
    }

    @Test fun calendarLight() {
        rule.setContent { JaySayTheme(themeMode = ThemeMode.LIGHT) {
            CalendarExceptionScreen(com.jaysay.coursetable.data.repository.TableData("示例学期", courses), {}, {})
        } }
        capture("calendar")
    }
    @Test fun settingsStatusBoardRemoved() {
        rule.setContent { JaySayTheme(themeMode = ThemeMode.LIGHT) {
            SettingsScreen(preferences = AppPreferences(), onUpdatePrefs = {}, onExportBackup = {}, onImportBackup = {}, onBack = {})
        } }
        rule.onNodeWithTag("service-status-card").assertDoesNotExist()
        capture("settings")
    }
    private fun render(mode: ScheduleViewMode, dark: Boolean, font: Float = 1f) {
        rule.setContent {
            val current = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(current.density, font)) {
                JaySayTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                    CourseTableScreen(courses, 1, {}, {}, {}, tableName = "示例学期课表", semesterStart = TimeUtils.currentWeekStartDate(),
                        totalWeeks = 20, viewMode = mode, onViewModeChange = {}, focusedDay = 1, onFocusedDayChange = {})
                }
            }
        }
        rule.onNodeWithTag("course-table-screen").assertIsDisplayed()
        capture("${mode.name}-${if (dark) "dark" else "light"}-$font")
        if (mode == ScheduleViewMode.MONTH) {
            val layouts = mutableListOf<TextLayoutResult>()
            rule.onNodeWithTag("month-course-count").assertIsDisplayed()
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue("Month course count must expose its layout", layouts.isNotEmpty())
            val layout = layouts.single()
            assertFalse("Month count overflow: size=${layout.size}, constraints=${layout.layoutInput.constraints}, paragraph=${layout.multiParagraph.width}x${layout.multiParagraph.height}, width=${layout.didOverflowWidth}, height=${layout.didOverflowHeight}", layout.hasVisualOverflow)
        }
    }
    private fun capture(name: String, tag: String? = null) {
        rule.waitForIdle()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.getExternalFilesDir(null), "visual-3.4.29").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            (if (tag == null) rule.onRoot() else rule.onNodeWithTag(tag)).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
