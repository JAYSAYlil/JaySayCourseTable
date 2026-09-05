
package com.jaysay.coursetable.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.jaysay.coursetable.data.model.Course

// === 主色调（青绿品牌色，浅色端提亮、深色端压暗以贴近系统观感） ===
val Primary = Color(0xFF0F8F82)
val PrimaryLight = Color(0xFFCFF2EC)
val PrimaryDark = Color(0xFF0B6E64)
val Secondary = Color(0xFF2F7D5C)
val SecondaryLight = Color(0xFFDDF0E5)
val SecondaryDark = Color(0xFF155238)
val Tertiary = Color(0xFF5E7C3B)
val TertiaryLight = Color(0xFFE7F0D5)
val TertiaryDark = Color(0xFF314A18)

// Surface（浅色端页面统一纯白，层次交给卡片、描边与分隔线）
val Surface = Color(0xFFFFFFFF)
val OnSurface = Color(0xFF191C1B)
val OnSurfaceVariant = Color(0xFF636967)

val Background = Color(0xFFFFFFFF)
val Error = Color(0xFFDC2626)

// 课程卡片调色板 — 每个色相只保留一个位置，避免相邻课程颜色过近。
// 索引在浅/深模式一一对应，因此手动选择和导入颜色都会随外观可靠切换。
val CourseColors = listOf(
    Color(0xFFBDEFF1), Color(0xFFBCEAE1), Color(0xFFBCEBCC), Color(0xFFCEEABE),
    Color(0xFFE2EFB5), Color(0xFFF1EEB3), Color(0xFFFFF1B8), Color(0xFFFFE0A8),
    Color(0xFFFFCCA7), Color(0xFFFFC4B8), Color(0xFFFFC5CB), Color(0xFFF8C5DB),
    Color(0xFFF2C8ED), Color(0xFFE0CCF7), Color(0xFFD0D3FA), Color(0xFFC7D8FF),
    Color(0xFFBFDEF9), Color(0xFFBCE8F8), Color(0xFFB8E9EF), Color(0xFFBFDCE8),
    Color(0xFFCBD8E7), Color(0xFFE8D5BB), Color(0xFFEACBBE), Color(0xFFE3DCF1)
)
// 深色模式课程卡片 — 同序号保留色相，但采用可叠加半透明高光的深色基底。
val DarkCourseColors = listOf(
    Color(0xFF0B666A), Color(0xFF0B6258), Color(0xFF0A603D), Color(0xFF1E6331),
    Color(0xFF4D6F1A), Color(0xFF666217), Color(0xFF756519), Color(0xFF7B5214),
    Color(0xFF83421B), Color(0xFF843629), Color(0xFF813645), Color(0xFF7A3159),
    Color(0xFF6D3674), Color(0xFF543D82), Color(0xFF35488A), Color(0xFF1D5189),
    Color(0xFF145E80), Color(0xFF0E637A), Color(0xFF0A6267), Color(0xFF1B566B),
    Color(0xFF3D526A), Color(0xFF65503B), Color(0xFF6F4132), Color(0xFF554A68)
)

fun coursePalette(dark: Boolean): List<Color> = if (dark) DarkCourseColors else CourseColors

/**
 * 课程卡片透明度的单一来源，供视觉对比度回归测试复用。
 * 深色卡片叠加自定义背景（遮罩可能关闭、壁纸可能纯白）时提高到 0.96：
 * 这是浅色文字对 4.5:1 对比度在全部预设色上仍然可达的最低不透明度。
 */
fun courseCardBackgroundAlpha(background: Color, hasCustomBackground: Boolean): Float {
    val isDarkCourseCard = background.luminance() < 0.35f
    return when {
        // 深色材质需要保留颜色本体；渐变高光只作为表层，不稀释卡片层级。
        !hasCustomBackground && isDarkCourseCard -> 0.94f
        !hasCustomBackground -> 0.82f
        isDarkCourseCard -> 0.96f
        else -> 0.74f
    }
}

// === 课程卡片文字对比度体系 ===
// 文字颜色不再取固定常量，而是按“实际渲染时最不利的合成背景”动态派生：
// 以最小混色比达到对比度目标，保证可读的同时尽量保留课程色相。
// 标题目标略高于次级信息，用对比度差保留层级（次级还有字号/字重区分）。
private const val COURSE_TITLE_CONTRAST = 5.5f
private const val COURSE_SUB_CONTRAST = 4.5f
private const val COURSE_TITLE_CONTRAST_ENHANCED = 7f
private const val COURSE_SUB_CONTRAST_ENHANCED = 5.5f

/** 浅色卡片文字锚点墨色；深色卡片文字锚点为纯白。 */
private val LightCardTextAnchor = Color(0xFF171A19)

/** 深色渐变高光的亮度上限：纯白文字在该亮度上仍能保持约 4.8:1。 */
private const val COURSE_HIGHLIGHT_MAX_LUMINANCE = 0.15f

/**
 * 卡片填充的停止点（含各自的最终透明度），是课程卡片实际绘制的唯一来源；
 * 对比度测试复用同一函数合成真实渲染背景，避免测试与渲染各算一套。
 * 深色卡片保留三段式渐变：顶部高光、中段基色、底部压暗；
 * 高光强度按基色自适应，使其合成亮度不超过 [COURSE_HIGHLIGHT_MAX_LUMINANCE]，
 * 让文字在任何高光位置都满足对比度要求。
 */
fun courseCardFillStops(background: Color, dark: Boolean, hasCustomBackground: Boolean): List<Pair<Float, Color>> {
    val alpha = courseCardBackgroundAlpha(background, hasCustomBackground)
    return if (!dark) {
        listOf(0f to background.copy(alpha = alpha))
    } else {
        val bgLuminance = background.luminance()
        val highlightLerp = if (bgLuminance >= COURSE_HIGHLIGHT_MAX_LUMINANCE) 0f
        else ((COURSE_HIGHLIGHT_MAX_LUMINANCE - bgLuminance) / (1f - bgLuminance)).coerceAtMost(0.26f)
        listOf(
            0f to lerp(background, Color.White, highlightLerp).copy(alpha = 0.98f),
            0.45f to background.copy(alpha = alpha),
            1f to lerp(background, Color.Black, 0.22f).copy(alpha = alpha)
        )
    }
}

/**
 * 课程卡片背后可能出现的最不利底色集合（遮罩开与关都要覆盖）：
 * - 无自定义背景：页面纯色底。
 * - 自定义背景遮罩开启：浅色模式至少叠加 42% 白、深色模式最多保留 52% 原图亮度。
 * - 自定义背景遮罩关闭：原图不做任何提亮/压暗，纯白与纯黑都是合法极端。
 */
fun courseCardUnderlays(dark: Boolean, hasCustomBackground: Boolean): List<Color> = when {
    !hasCustomBackground -> listOf(if (dark) DarkBackground else Background)
    dark -> listOf(
        Color.White,
        Color.Black.copy(alpha = 0.48f).compositeOver(Color.White)
    )
    else -> listOf(
        Color.Black,
        Color.White.copy(alpha = 0.42f).compositeOver(Color.Black),
        Color.White
    )
}

/** 深色卡片文字为浅色，最不利背景是最亮的合成结果；浅色卡片文字为深色，取最暗者。 */
fun courseCardWorstTextBackplate(cardColor: Color, dark: Boolean, hasCustomBackground: Boolean): Color {
    val lightText = cardColor.luminance() < 0.35f
    var worst: Color? = null
    var worstLuminance = if (lightText) -1f else 2f
    courseCardFillStops(cardColor, dark, hasCustomBackground).forEach { (_, stop) ->
        courseCardUnderlays(dark, hasCustomBackground).forEach { underlay ->
            val composite = if (stop.alpha >= 0.999f) stop else stop.compositeOver(underlay)
            val compositeLuminance = composite.luminance()
            val lessReadable = if (lightText) compositeLuminance > worstLuminance else compositeLuminance < worstLuminance
            if (lessReadable) {
                worst = composite
                worstLuminance = compositeLuminance
            }
        }
    }
    return worst ?: cardColor
}

private fun contrastRatio(foreground: Color, background: Color): Float {
    val lighter = maxOf(foreground.luminance(), background.luminance())
    val darker = minOf(foreground.luminance(), background.luminance())
    return (lighter + 0.05f) / (darker + 0.05f)
}

/** 在卡片色与锚点之间二分最小混色比，使文字对不利背景达到对比度目标；不可达时取锚点。 */
private fun mixForContrast(cardColor: Color, anchor: Color, backplate: Color, target: Float): Float {
    if (contrastRatio(cardColor, backplate) >= target) return 0f
    if (contrastRatio(anchor, backplate) < target) return 1f
    var low = 0f
    var high = 1f
    repeat(16) {
        val middle = (low + high) / 2f
        if (contrastRatio(lerp(cardColor, anchor, middle), backplate) >= target) high = middle else low = middle
    }
    return high
}

/**
 * 由卡片底色派生同卡内的标题/次级文字颜色。
 * [hasCustomBackground] 决定是否把自定义背景（含遮罩关闭）的极端底色计入不利位置；
 * [enhancedContrast] 对应系统“增强对比度”设置，提高对比度目标并保留层级差。
 */
fun courseCardTextColors(
    cardColor: Color,
    dark: Boolean,
    hasCustomBackground: Boolean = false,
    enhancedContrast: Boolean = false
): Pair<Color, Color> {
    val anchor = if (cardColor.luminance() < 0.35f) Color.White else LightCardTextAnchor
    val backplate = courseCardWorstTextBackplate(cardColor, dark, hasCustomBackground)
    val titleMix = mixForContrast(cardColor, anchor, backplate, if (enhancedContrast) COURSE_TITLE_CONTRAST_ENHANCED else COURSE_TITLE_CONTRAST)
    val subMix = mixForContrast(cardColor, anchor, backplate, if (enhancedContrast) COURSE_SUB_CONTRAST_ENHANCED else COURSE_SUB_CONTRAST)
    return lerp(cardColor, anchor, titleMix) to lerp(cardColor, anchor, subMix)
}

/** 课程卡片描边色：由标题色同相派生，深浅两端都维持极低噪音。 */
fun courseCardBorderColor(cardColor: Color, dark: Boolean, enhancedContrast: Boolean = false): Color =
    courseCardTextColors(cardColor, dark).first.copy(
        alpha = when {
            enhancedContrast -> if (dark) 0.48f else 0.5f
            else -> if (dark) 0.20f else 0.22f
        }
    )

/**
 * 按“课程名首次出现顺序”构建课表配色映射，避免哈希碰撞导致相邻课程颜色过近。
 * 网格课表和课程详情页必须共用同一映射，保证同一课程两处颜色一致。
 */
fun buildCourseColorMap(courses: List<Course>, dark: Boolean): Map<String, Color> {
    val palette = if (dark) DarkCourseColors else CourseColors
    return buildMap {
        courses.distinctBy { it.courseName }.forEachIndexed { index, course ->
            put(course.courseName, palette[index % palette.size])
        }
    }
}

/**
 * 解析单条课程卡片的最终颜色：用户选择的预设色优先，
 * 否则按课程名在整表首次出现顺序取调色板颜色。
 * 旧版保存过的 ARGB 自定义值不再使用，安全回退为自动配色。
 */
fun resolveCourseColor(courses: List<Course>, course: Course, dark: Boolean): Color {
    val palette = if (dark) DarkCourseColors else CourseColors
    val customColor = course.customColor
    return when {
        customColor != null && customColor in palette.indices -> palette[customColor]
        else -> buildCourseColorMap(courses, dark)[course.courseName] ?: palette.first()
    }
}

/**
 * 一次性构建“课程名 → 最终显示色”映射：先按首次出现顺序构建基础映射，
 * 再应用每门课的自定义预设色。周/日/月/详情共用同一结果，
 * 避免逐课程调用 [resolveCourseColor] 时重复构建整表映射（O(n²) → O(n)）。
 * 语义与 [resolveCourseColor] 完全一致。
 */
fun buildResolvedCourseColorMap(courses: List<Course>, dark: Boolean): Map<String, Color> {
    val palette = if (dark) DarkCourseColors else CourseColors
    val baseMap = buildCourseColorMap(courses, dark)
    return buildMap {
        courses.distinctBy { it.courseName }.forEach { course ->
            val customColor = course.customColor
            put(
                course.courseName,
                when {
                    customColor != null && customColor in palette.indices -> palette[customColor]
                    else -> baseMap[course.courseName] ?: palette.first()
                }
            )
        }
    }
}

/** 在半透明遮罩之上合成一层保证可读性的底色（供模糊吸顶等场景使用）。 */
fun scrimColor(base: Color, alpha: Float): Color = base.copy(alpha = alpha).compositeOver(base)

// Dark mode Material（中性深灰体系，接近系统暗色观感）
val DarkPrimary = Color(0xFF3ADBC4)
val DarkPrimaryLight = Color(0xFF12433D)
val DarkPrimaryDark = Color(0xFF6FEADD)
val DarkSecondary = Color(0xFF8BD5B2)
val DarkSecondaryLight = Color(0xFF173E2D)
val DarkSecondaryDark = Color(0xFFB6F2D3)
val DarkTertiary = Color(0xFFB7D58A)
val DarkTertiaryLight = Color(0xFF31451E)
val DarkTertiaryDark = Color(0xFFD8F4AE)
val DarkSurface = Color(0xFF16181A)
val DarkBackground = Color(0xFF0B0C0D)
val DarkOnSurface = Color(0xFFE4E6E5)
val DarkOnSurfaceVariant = Color(0xFF9CA1A0)
val DarkSurfaceVariant = Color(0xFF232628)
val DarkOutlineVariant = Color(0xFF3A3D40)
