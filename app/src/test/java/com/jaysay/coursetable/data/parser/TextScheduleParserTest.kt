package com.jaysay.coursetable.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextScheduleParserTest {

    @Test
    fun parsesTypicalLine() {
        val result = TextScheduleParser.parse("周一 1-2节 高等数学 教1-101 张老师 1-16周")
        assertEquals(1, result.courses.size)
        assertEquals(0, result.errors.size)
        val course = result.courses.single()
        assertEquals("高等数学", course.courseName)
        assertEquals("教1-101", course.classroom)
        assertEquals("张老师", course.teacher)
        assertEquals(1, course.dayOfWeek)
        assertEquals(1, course.startPeriod)
        assertEquals(2, course.endPeriod)
        assertEquals((1..16).toList(), course.weeks)
    }

    @Test
    fun parsesCommaSeparatedAndOddWeeks() {
        val result = TextScheduleParser.parse("星期二,第3-4节,大学英语,外语楼201,李老师,1,3,5,7周")
        val course = result.courses.single()
        assertEquals(2, course.dayOfWeek)
        assertEquals(3, course.startPeriod)
        assertEquals(4, course.endPeriod)
        assertEquals(listOf(1, 3, 5, 7), course.weeks)
    }

    @Test
    fun defaultsWeeksToFullSemesterWhenMissing() {
        val result = TextScheduleParser.parse("周三 5节 体育")
        val course = result.courses.single()
        assertEquals(3, course.dayOfWeek)
        assertEquals(5, course.startPeriod)
        assertEquals(5, course.endPeriod)
        assertEquals((1..20).toList(), course.weeks)
        assertTrue(course.teacher.isBlank())
        assertTrue(course.classroom.isBlank())
    }

    @Test
    fun reportsBadLinesWithoutDroppingGoodOnes() {
        val result = TextScheduleParser.parse(
            """
            # 注释行应被忽略
            周一 1-2节 高数 1-16周
            这不是一行有效课表
            周二 3节 英语 2-8周(单)
            """.trimIndent()
        )
        assertEquals(2, result.courses.size)
        assertEquals(1, result.errors.size)
        assertTrue(result.errors.first().contains("第3行"))
    }

    @Test
    fun rejectsLineWithoutDayOrPeriod() {
        val result = TextScheduleParser.parse("高等数学 教1-101 1-16周")
        assertEquals(0, result.courses.size)
        assertEquals(1, result.errors.size)
    }
}
