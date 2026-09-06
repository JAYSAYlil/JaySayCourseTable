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
    @Test fun settingsStatus() {
        rule.setContent { JaySayTheme(themeMode = ThemeMode.LIGHT) {
            SettingsScreen(preferences = AppPreferences(), onUpdatePrefs = {}, onExportBackup = {}, onImportBackup = {}, onBack = {})
        } }
        rule.onNodeWithTag("service-status-card").assertIsDisplayed()
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
    private fun capture(name: String) {
        rule.waitForIdle()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.getExternalFilesDir(null), "visual-3.4.13").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
