package com.jaysay.coursetable.ui.theme

import androidx.compose.ui.graphics.Color
import com.jaysay.coursetable.data.model.Course
import org.junit.Assert.assertEquals
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
    fun customColorIndexTakesPriority() {
        val courses = listOf(course("甲", customColor = 7))
        val color = resolveCourseColor(courses, courses.first(), dark = false)
        assertEquals(CourseColors[7], color)
    }

    @Test
    fun customArgbTakesPriority() {
        val argb = 0xFF336699.toInt()
        val courses = listOf(course("甲", customColor = argb))
        val color = resolveCourseColor(courses, courses.first(), dark = false)
        assertEquals(Color(argb), color)
    }

    @Test
    fun darkModeUsesDarkPalette() {
        val courses = listOf(course("甲"))
        val color = resolveCourseColor(courses, courses.first(), dark = true)
        assertEquals(DarkCourseColors[0], color)
    }
}
