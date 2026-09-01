
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
val CourseTextColor = Color(0xFF1C1B1F)
val CourseSubTextColor = Color(0xFF5A5A5A)

// 深色模式课程卡片 — 同序号保留色相，但采用可叠加半透明高光的深色基底。
val DarkCourseColors = listOf(
    Color(0xFF0B666A), Color(0xFF0B6258), Color(0xFF0A603D), Color(0xFF1E6331),
    Color(0xFF4D6F1A), Color(0xFF666217), Color(0xFF756519), Color(0xFF7B5214),
    Color(0xFF83421B), Color(0xFF843629), Color(0xFF813645), Color(0xFF7A3159),
    Color(0xFF6D3674), Color(0xFF543D82), Color(0xFF35488A), Color(0xFF1D5189),
    Color(0xFF145E80), Color(0xFF0E637A), Color(0xFF0A6267), Color(0xFF1B566B),
    Color(0xFF3D526A), Color(0xFF65503B), Color(0xFF6F4132), Color(0xFF554A68)
)
val DarkCourseTextColor = Color(0xFFE8E8E8)
val DarkCourseSubTextColor = Color(0xFFC8CBCA)

fun coursePalette(dark: Boolean): List<Color> = if (dark) DarkCourseColors else CourseColors

/**
 * 由卡片底色派生同卡内的标题/次级文字颜色：
 * 浅色卡片上取加深墨色，深色卡片上取提亮淡色。
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
