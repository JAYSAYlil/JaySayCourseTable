package com.jaysay.coursetable.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MappedTextScheduleParserTest {
    @Test fun parsesUserSelectedColumnOrder() {
        val text = "张老师|高等数学|周二|A101|3-4节|1-16周"
        val result = MappedTextScheduleParser.parse(
            text,
            TextColumnMapping(courseName = 1, teacher = 0, dayOfWeek = 2, classroom = 3, periods = 4, weeks = 5),
            totalWeeks = 18
        )
        assertTrue(result.errors.isEmpty())
        with(result.courses.single()) {
            assertEquals("高等数学", courseName)
            assertEquals("张老师", teacher)
            assertEquals("A101", classroom)
            assertEquals(2, dayOfWeek)
            assertEquals(3, startPeriod)
            assertEquals(4, endPeriod)
            assertEquals((1..16).toList(), weeks)
        }
    }

    @Test fun reportsShortOrInvalidRowsWithoutCrashing() {
        val result = MappedTextScheduleParser.parse(
            "课程,周一\n课程,周九,1-2,1-16",
            TextColumnMapping(courseName = 0, dayOfWeek = 1, periods = 2, weeks = 3),
            totalWeeks = 20
        )
        assertTrue(result.courses.isEmpty())
        assertEquals(2, result.errors.size)
    }
}
