
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

// 课程卡片调色板 — 低饱和莫兰迪系，同一课程在浅/深两套下保持同色相
val CourseColors = listOf(
    Color(0xFFE3F0FB), Color(0xFFE2F6E9), Color(0xFFFDF2D2),
    Color(0xFFFCE7EA), Color(0xFFE8F2E4), Color(0xFFDCF3F0),
    Color(0xFFFBEEDF), Color(0xFFECEEF2), Color(0xFFF0E9E2),
    Color(0xFFEAF2DC), Color(0xFFDFF1F6), Color(0xFFF6E6EF),
    Color(0xFFE4F1EC), Color(0xFFF0EAD9), Color(0xFFE7E9F7),
)
val CourseTextColor = Color(0xFF1C1B1F)
val CourseSubTextColor = Color(0xFF5A5A5A)

// 深色模式课程卡片 — 保留色相的暗调版本
val DarkCourseColors = listOf(
    Color(0xFF1E3448), Color(0xFF1D3A2C), Color(0xFF43371A),
    Color(0xFF46242B), Color(0xFF263A28), Color(0xFF173B38),
    Color(0xFF443122), Color(0xFF2C313A), Color(0xFF3A2F26),
    Color(0xFF2E3D20), Color(0xFF173A44), Color(0xFF412838),
    Color(0xFF1E3830), Color(0xFF3A3620), Color(0xFF2C2F4A),
)
val DarkCourseTextColor = Color(0xFFE8E8E8)
val DarkCourseSubTextColor = Color(0xFFB0B0B0)

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
        customColor != null -> Color(customColor)
        else -> buildCourseColorMap(courses, dark)[course.courseName] ?: palette.first()
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
