package com.jaysay.coursetable.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimeUtilsTest {
    @Test
    fun parsesDayAndPeriodStrictly() {
        assertEquals(1, TimeUtils.parseDayOfWeekOrNull("\u661f\u671f\u4e00"))
        assertEquals(7, TimeUtils.parseDayOfWeekOrNull("\u5468\u65e5"))
        assertNull(TimeUtils.parseDayOfWeekOrNull("unknown"))
        assertEquals(12, TimeUtils.parsePeriodOrNull("\u7b2c12\u8282"))
        assertNull(TimeUtils.parsePeriodOrNull("none"))
    }

    @Test
    fun parsesRangesAndOddEvenWeeks() {
        assertEquals(listOf(1, 3, 5), TimeUtils.parseWeeks("1-6\u5468(\u5355)"))
        assertEquals(listOf(2, 4, 6), TimeUtils.parseWeeks("1~6\u5468(\u53cc)"))
        assertEquals(listOf(2, 4, 5, 7), TimeUtils.parseWeeks("2\u5468,4-5\u5468,7\u5468"))
    }

    @Test
    fun formatsUnsortedWeeksWithoutDuplicates() {
        assertEquals("1-3\u5468\uff0c5\u5468", TimeUtils.formatWeeks(listOf(3, 1, 2, 2, 5)))
    }
}
