package com.jaysay.coursetable.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.jaysay.coursetable.data.model.Course
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 课程卡片颜色回归测试。
 *
 * 渲染真相是 [courseCardFillStops]（卡片填充，含深色三段渐变）与
 * [courseCardUnderlays]（自定义背景遮罩开/关的极端底色），二者同时被真实绘制使用。
 * 测试从这两个函数出发独立合成“实际文字区域内的不利位置”，
 * 再断言 [courseCardTextColors] 派生出的文字颜色达标——不复制任何派生算法。
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

    /**
     * 真实渲染矩阵：浅/深色 × 自定义背景开/关 × 全部预设色。
     * 课程标题与次级信息（教室、教师）同为小字号，统一要求 4.5:1；
     * 标题对比度不得低于次级，保留层级。
     */
    @Test
    fun cardTextMeetsContrastOnRealRenderedFill() {
        for (dark in booleanArrayOf(false, true)) {
            for (hasCustomBackground in booleanArrayOf(false, true)) {
                coursePalette(dark).forEachIndexed { index, card ->
                    val (title, sub) = courseCardTextColors(card, dark, hasCustomBackground)
                    val worst = worstRenderedComposite(card, dark, hasCustomBackground)
                    val label = "卡片 #$index(dark=$dark,customBg=$hasCustomBackground)"
                    assertContrastAtLeast(title, worst, 4.5f, "$label 标题")
                    assertContrastAtLeast(sub, worst, 4.5f, "$label 次级信息")
                    assertTrue(
                        "$label 标题对比度低于次级，层级反转",
                        contrast(title, worst) >= contrast(sub, worst) - 0.01f
                    )
                }
            }
        }
    }

    /** 标题按 5.5:1 派生；仅在锚点物理上限不足时允许钳制到可达的最大对比度。 */
    @Test
    fun cardTitleDerivesToFivePointFiveWhenReachable() {
        for (dark in booleanArrayOf(false, true)) {
            for (hasCustomBackground in booleanArrayOf(false, true)) {
                coursePalette(dark).forEachIndexed { index, card ->
                    val (title, _) = courseCardTextColors(card, dark, hasCustomBackground)
                    val worst = worstRenderedComposite(card, dark, hasCustomBackground)
                    val anchor = if (card.luminance() < 0.35f) Color.White else Color(0xFF171A19)
                    val expected = minOf(5.5f, contrast(anchor, worst))
                    assertTrue(
                        "卡片 #$index(dark=$dark,customBg=$hasCustomBackground) 标题对比度低于目标 $expected",
                        contrast(title, worst) >= expected - 0.05f
                    )
                }
            }
        }
    }

    /** 深色三段渐变保留，且顶部高光亮度受文字可读性约束：纯白文字在其上仍高于 4.5:1。 */
    @Test
    fun darkGradientHighlightStaysUnderReadableCeiling() {
        coursePalette(true).forEachIndexed { index, card ->
            val stops = courseCardFillStops(card, dark = true, hasCustomBackground = true)
            assertEquals("深色卡片应保留三段式渐变", 3, stops.size)
            val highlight = stops[0].second
            assertTrue(
                "卡片 #$index 高光亮度过高（${highlight.luminance()}），白字无法保证 4.5:1",
                contrast(Color.White, highlight) >= 4.5f
            )
            assertTrue("高光应亮于中段基色", highlight.luminance() >= stops[1].second.luminance() - 0.001f)
            assertTrue("底部压暗不应亮于中段", stops[2].second.luminance() <= stops[1].second.luminance() + 0.001f)
        }
    }

    /** 浅色卡片为单段实色填充，透明度与渲染保持一致。 */
    @Test
    fun lightCardUsesSingleFillStopWithDocumentedAlpha() {
        coursePalette(false).forEach { card ->
            val stops = courseCardFillStops(card, dark = false, hasCustomBackground = false)
            assertEquals(1, stops.size)
            assertEquals(card.copy(alpha = courseCardBackgroundAlpha(card, false)), stops[0].second)
        }
    }

    /** 增强对比度必须实际改善课程文字与边界，而不是只改 Material 色。 */
    @Test
    fun enhancedContrastImprovesCardTextAndBorder() {
        for (dark in booleanArrayOf(false, true)) {
            for (hasCustomBackground in booleanArrayOf(false, true)) {
                coursePalette(dark).forEachIndexed { index, card ->
                    val worst = worstRenderedComposite(card, dark, hasCustomBackground)
                    val (title, sub) = courseCardTextColors(card, dark, hasCustomBackground)
                    val (titleHc, subHc) = courseCardTextColors(card, dark, hasCustomBackground, enhancedContrast = true)
                    val label = "卡片 #$index(dark=$dark,customBg=$hasCustomBackground)"
                    assertTrue(
                        "$label 增强对比度后标题未改善",
                        contrast(titleHc, worst) >= contrast(title, worst) - 0.01f
                    )
                    assertTrue(
                        "$label 增强对比度后次级未改善",
                        contrast(subHc, worst) >= contrast(sub, worst) - 0.01f
                    )
                    assertTrue(
                        "$label 增强对比度后描边未加重",
                        courseCardBorderColor(card, dark, true).alpha > courseCardBorderColor(card, dark).alpha
                    )
                }
            }
        }
    }

    /** 浅色卡片标题以最小混色达标，不应整体坍缩为纯墨色（保留课程色相辨识）。 */
    @Test
    fun lightCardTitleKeepsHueInsteadOfCollapsingToAnchor() {
        coursePalette(false).forEachIndexed { index, card ->
            val (title, _) = courseCardTextColors(card, dark = false, hasCustomBackground = false)
            assertNotEquals("卡片 #$index 标题退化为纯墨色", Color(0xFF171A19), title)
        }
    }

    /** 批量映射与逐课程解析语义一致（含自定义预设色覆盖），保证周/日/月/详情同色。 */
    @Test
    fun resolvedColorMapMatchesPerCourseResolution() {
        val courses = listOf(
            course("高数"),
            course("英语", customColor = 5),
            course("高数", customColor = null),
            course("体育", customColor = 99),
            course("化学")
        )
        for (dark in booleanArrayOf(false, true)) {
            val resolved = buildResolvedCourseColorMap(courses, dark)
            val palette = coursePalette(dark)
            courses.forEach { c ->
                assertEquals(
                    "课程 ${c.courseName}(custom=${c.customColor}, dark=$dark) 映射与解析不一致",
                    resolveCourseColor(courses, c, dark),
                    resolved[c.courseName]
                )
            }
            assertEquals(CourseColors.size, coursePalette(false).size)
            // 合法自定义预设色直接生效；非法下标（99）回退到首次出现顺序配色。
            assertEquals(palette[5], resolved["英语"])
            assertEquals(palette[2], resolved["体育"])
        }
    }

    /** 独立合成最不利背景：深色卡片文字为浅色取最亮合成，浅色卡片取最暗合成。 */
    private fun worstRenderedComposite(card: Color, dark: Boolean, hasCustomBackground: Boolean): Color {
        val darkText = card.luminance() < 0.35f
        val composites = courseCardFillStops(card, dark, hasCustomBackground).flatMap { (_, stop) ->
            courseCardUnderlays(dark, hasCustomBackground).map { underlay ->
                if (stop.alpha >= 0.999f) stop else stop.compositeOver(underlay)
            }
        }
        return if (darkText) composites.maxBy { it.luminance() } else composites.minBy { it.luminance() }
    }

    private fun contrast(foreground: Color, background: Color): Float {
        val lighter = maxOf(foreground.luminance(), background.luminance())
        val darker = minOf(foreground.luminance(), background.luminance())
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    private fun assertContrastAtLeast(foreground: Color, background: Color, minimum: Float, label: String) {
        val ratio = contrast(foreground, background)
        assertTrue("$label 对比度 $ratio 低于 $minimum", ratio >= minimum)
    }
}
