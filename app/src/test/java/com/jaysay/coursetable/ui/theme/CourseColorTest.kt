package com.jaysay.coursetable.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.jaysay.coursetable.data.model.Course
import com.jaysay.coursetable.ui.screen.courseCardBackgroundAlpha
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 回归测试：课表网格与课程详情页必须共用同一配色解析，
 * 防止详情页回退到 hashCode 取色导致同一课程两处颜色不一致。
 */
class CourseColorTest {

    private fun course(name: String, customColor: Int? = null) = Course(
        courseId = "id-$name",
        courseName = name,
        classNumber = "",
        department = "",
        credits = 0f,
        weeks = listOf(1),
        dayOfWeek = 1,
        startPeriod = 1,
        endPeriod = 1,
        teacher = "",
        classroom = "",
        courseType = "",
        courseCategory = "",
        isOnline = false,
        assessmentMethod = "",
        customColor = customColor,
        notes = "",
        seriesId = ""
    )

    @Test
    fun detailColorMatchesGridColorMap() {
        val names = listOf("高数", "英语", "线性代数", "毛概", "体育")
        val courses = names.flatMap { name -> listOf(course(name)) }
        val dark = false
        val colorMap = buildCourseColorMap(courses, dark)
        courses.forEach { c ->
            val gridColor = colorMap[c.courseName] ?: CourseColors.first()
            val detailColor = resolveCourseColor(courses, c, dark)
            assertEquals("课程 ${c.courseName} 网格与详情颜色不一致", gridColor, detailColor)
        }
    }

    @Test
    fun colorAssignedByFirstAppearanceOrderNotHash() {
        val courses = listOf(course("甲"), course("乙"), course("丙"))
        val colorMap = buildCourseColorMap(courses, dark = false)
        assertEquals(CourseColors[0], colorMap["甲"])
        assertEquals(CourseColors[1], colorMap["乙"])
        assertEquals(CourseColors[2], colorMap["丙"])
    }

    @Test
    fun presetColorIndexTakesPriority() {
        val courses = listOf(course("甲", customColor = 7))
        val color = resolveCourseColor(courses, courses.first(), dark = false)
        assertEquals(CourseColors[7], color)
    }

    @Test
    fun legacyArgbFallsBackToAutomaticPalette() {
        val argb = 0xFF336699.toInt()
        val courses = listOf(course("甲", customColor = argb))
        val color = resolveCourseColor(courses, courses.first(), dark = false)
        assertEquals(CourseColors[0], color)
    }

    @Test
    fun darkModeUsesDarkPalette() {
        val courses = listOf(course("甲"))
        val color = resolveCourseColor(courses, courses.first(), dark = true)
        assertEquals(DarkCourseColors[0], color)
    }

    @Test
    fun lightAndDarkPalettesStayPairedAndUnique() {
        assertEquals(CourseColors.size, DarkCourseColors.size)
        assertEquals(CourseColors.size, CourseColors.distinct().size)
        assertEquals(DarkCourseColors.size, DarkCourseColors.distinct().size)
        assertEquals("预设色数量应稳定为 24", 24, CourseColors.size)
    }

    @Test
    fun courseTextKeepsReadableContrastWithCustomBackgrounds() {
        // 自定义背景会先经过页面遮罩：浅色模式至少叠加 42% 白色，
        // 深色模式最多只保留 52% 原图亮度。分别测试最不利的明暗端点。
        val lightUnderlays = listOf(Color.White.copy(alpha = 0.42f).compositeOver(Color.Black), Color.White)
        val darkUnderlays = listOf(Color.Black, Color.Black.copy(alpha = 0.48f).compositeOver(Color.White))

        CourseColors.forEach { card ->
            lightUnderlays.forEach { underlay ->
                val rendered = card.copy(alpha = courseCardBackgroundAlpha(card, true)).compositeOver(underlay)
                assertContrastAtLeast(CourseTextColor, rendered, 4.5f, "浅色课程标题")
                assertContrastAtLeast(CourseSubTextColor, rendered, 3.0f, "浅色课程附加信息")
            }
        }
        DarkCourseColors.forEach { card ->
            darkUnderlays.forEach { underlay ->
                val rendered = card.copy(alpha = courseCardBackgroundAlpha(card, true)).compositeOver(underlay)
                assertContrastAtLeast(DarkCourseTextColor, rendered, 4.5f, "深色课程标题")
                assertContrastAtLeast(DarkCourseSubTextColor, rendered, 3.0f, "深色课程附加信息")
            }
        }
    }

    private fun assertContrastAtLeast(foreground: Color, background: Color, minimum: Float, label: String) {
        val lighter = maxOf(foreground.luminance(), background.luminance())
        val darker = minOf(foreground.luminance(), background.luminance())
        val ratio = (lighter + 0.05f) / (darker + 0.05f)
        assertTrue("$label 对比度 $ratio 低于 $minimum", ratio >= minimum)
    }
}
