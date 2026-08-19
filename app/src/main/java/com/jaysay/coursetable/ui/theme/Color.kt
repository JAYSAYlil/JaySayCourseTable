
package com.jaysay.coursetable.ui.theme

import androidx.compose.ui.graphics.Color
import com.jaysay.coursetable.data.model.Course

// === 主色调 ===
val Primary = Color(0xFF0D9488)
val PrimaryLight = Color(0xFFCCFBF1)
val PrimaryDark = Color(0xFF0F766E)

// Surface
val Surface = Color(0xFFFAFAFA)
val OnSurface = Color(0xFF1C1B1F)
val OnSurfaceVariant = Color(0xFF6B6B6B)

val Background = Color.White
val Error = Color(0xFFDC2626)

// 课程卡片颜色 — 柔和高亮
val CourseColors = listOf(
    Color(0xFFE0F2FE), Color(0xFFDCFCE7), Color(0xFFFEF3C7),
    Color(0xFFFEE2E2), Color(0xFFF3E8FF), Color(0xFFCCFBF1),
    Color(0xFFFFF7ED), Color(0xFFF1F5F9), Color(0xFFE8E0D9),
    Color(0xFFFCE7F3), Color(0xFFCFFAFE), Color(0xFFFFE4E6),
    Color(0xFFEDE9FE), Color(0xFFECFCCB), Color(0xFFDBEAFE),
)
val CourseTextColor = Color(0xFF1C1B1F)
val CourseSubTextColor = Color(0xFF5A5A5A)

// 深色模式
val DarkCourseColors = listOf(
    Color(0xFF1E3A5F), Color(0xFF1B3D2F), Color(0xFF4A3A0A),
    Color(0xFF4A1A2A), Color(0xFF2D1B4E), Color(0xFF0B3D3A),
    Color(0xFF4A3520), Color(0xFF2A2F3A), Color(0xFF3A2A20),
    Color(0xFF3D1A30), Color(0xFF0A3D4A), Color(0xFF3D1A1A),
    Color(0xFF1A1A4A), Color(0xFF1A3D1A), Color(0xFF0A2A4A),
)
val DarkCourseTextColor = Color(0xFFE8E8E8)
val DarkCourseSubTextColor = Color(0xFFB0B0B0)

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

// Dark mode Material
val DarkPrimary = Color(0xFF2DD4BF)
val DarkPrimaryLight = Color(0xFF134E4A)
val DarkPrimaryDark = Color(0xFF5EEAD4)
val DarkSurface = Color(0xFF1C1C1E)
val DarkBackground = Color(0xFF0D0D0D)
val DarkOnSurface = Color(0xFFE5E5E5)
val DarkOnSurfaceVariant = Color(0xFF9A9A9A)
val DarkSurfaceVariant = Color(0xFF2C2C2E)
val DarkOutlineVariant = Color(0xFF3A3A3C)
