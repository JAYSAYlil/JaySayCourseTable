
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

// Surface（浅色端采用微灰白背景 + 纯白卡片，拉开层次）
val Surface = Color(0xFFFFFFFF)
val OnSurface = Color(0xFF191C1B)
val OnSurfaceVariant = Color(0xFF636967)

val Background = Color(0xFFF6F7F7)
val Error = Color(0xFFDC2626)

// 课程卡片调色板 — 低饱和但色相明确；每个索引在浅/深模式都有一一对应的变体。
val CourseColors = listOf(
    Color(0xFFDCEEFF), Color(0xFFDDF5E7), Color(0xFFFFEFC2), Color(0xFFFFDFE5),
    Color(0xFFE7E0FF), Color(0xFFD8F5F2), Color(0xFFFFE2C7), Color(0xFFE3E8F0),
    Color(0xFFF1E2D5), Color(0xFFE5F0C9), Color(0xFFD9ECFF), Color(0xFFF6DDEB),
    Color(0xFFDCEBDD), Color(0xFFF1E6C9), Color(0xFFE1E3FA), Color(0xFFDDE5E8),
    Color(0xFFFFE0C9), Color(0xFFE0F0D8), Color(0xFFE3DDF7), Color(0xFFD5EEF5),
    Color(0xFFF2E0C1), Color(0xFFF2DCE0), Color(0xFFDCE8D4), Color(0xFFE7E0D8)
)
val CourseTextColor = Color(0xFF1C1B1F)
val CourseSubTextColor = Color(0xFF5A5A5A)

// 深色模式课程卡片 — 保留色相的暗调版本
val DarkCourseColors = listOf(
    Color(0xFF1D3A53), Color(0xFF1D4430), Color(0xFF4A3A16), Color(0xFF4B2630),
    Color(0xFF342A5A), Color(0xFF174441), Color(0xFF4D321B), Color(0xFF303843),
    Color(0xFF493326), Color(0xFF30441D), Color(0xFF19415A), Color(0xFF4C2940),
    Color(0xFF214638), Color(0xFF4A3D1C), Color(0xFF30335B), Color(0xFF294047),
    Color(0xFF4E2F1B), Color(0xFF2B4723), Color(0xFF3A2E5C), Color(0xFF1E4554),
    Color(0xFF493719), Color(0xFF4B2932), Color(0xFF2F4728), Color(0xFF45382B)
)
val DarkCourseTextColor = Color(0xFFE8E8E8)
val DarkCourseSubTextColor = Color(0xFFB0B0B0)

fun coursePalette(dark: Boolean): List<Color> = if (dark) DarkCourseColors else CourseColors

/**
 * 由卡片底色派生同卡内的标题/次级文字颜色：
 * 浅色卡片上取加深墨色，深色卡片上取提亮淡色，
 * 保证自定义颜色与调色板颜色都有一致的对比度表现。
 */
fun courseCardTextColors(cardColor: Color, dark: Boolean): Pair<Color, Color> {
    val anchor = if (dark) Color.White else Color(0xFF171A19)
    val subAnchor = if (dark) Color.White else Color(0xFF3E4442)
    val isDarkCard = cardColor.luminance() < 0.35f
    val titleMix = if (isDarkCard) 0.72f else 0.68f
    val subMix = if (isDarkCard) 0.46f else 0.46f
    return lerp(cardColor, anchor, titleMix) to lerp(cardColor, subAnchor, subMix)
}

/** 课程卡片描边色：底色同相描边，深浅两端都维持极低噪音。 */
fun courseCardBorderColor(cardColor: Color, dark: Boolean): Color =
    courseCardTextColors(cardColor, dark).first.copy(alpha = if (dark) 0.20f else 0.22f)

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
 * 解析单条课程卡片的最终颜色：自定义颜色（索引或 ARGB）优先，
 * 否则按课程名在整表首次出现顺序取调色板颜色。
 */
fun resolveCourseColor(courses: List<Course>, course: Course, dark: Boolean): Color {
    val palette = if (dark) DarkCourseColors else CourseColors
    val customColor = course.customColor
    return when {
        customColor != null && customColor in palette.indices -> palette[customColor]
        customColor != null -> resolveCustomCourseColor(customColor, dark)
        else -> buildCourseColorMap(courses, dark)[course.courseName] ?: palette.first()
    }
}

/** 自定义 ARGB 颜色在另一种外观下做轻量明度校正，保留用户选择的色相。 */
fun resolveCustomCourseColor(argb: Int, dark: Boolean): Color {
    val base = Color(argb)
    return when {
        dark && base.luminance() > 0.62f -> lerp(base, Color.Black, 0.42f)
        !dark && base.luminance() < 0.18f -> lerp(base, Color.White, 0.44f)
        else -> base
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
