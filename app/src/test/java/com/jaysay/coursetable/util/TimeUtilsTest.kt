package com.jaysay.coursetable.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class TimeUtilsTest {
    @Test
    fun parsesDayAndPeriodStrictly() {
        assertEquals(1, TimeUtils.parseDayOfWeekOrNull("\u661f\u671f\u4e00"))
        assertEquals(7, TimeUtils.parseDayOfWeekOrNull("\u5468\u65e5"))
        assertNull(TimeUtils.parseDayOfWeekOrNull("unknown"))
        assertNull(TimeUtils.parseDayOfWeekOrNull("大学英语二"))
        assertNull(TimeUtils.parseDayOfWeekOrNull("周一课程"))
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

    @Test
    fun parsesMinuteOfDayStrictly() {
        assertEquals(8 * 60, TimeUtils.parseMinuteOfDay("08:00"))
        assertEquals(23 * 60 + 59, TimeUtils.parseMinuteOfDay("23:59"))
        // 宽松小时/分钟补零写法也接受
        assertEquals(8 * 60 + 5, TimeUtils.parseMinuteOfDay("8:05"))
        assertNull(TimeUtils.parseMinuteOfDay("24:00"))
        assertNull(TimeUtils.parseMinuteOfDay("08:60"))
        assertNull(TimeUtils.parseMinuteOfDay("08"))
        assertNull(TimeUtils.parseMinuteOfDay("08:00:00"))
        assertNull(TimeUtils.parseMinuteOfDay(""))
    }

    @Test
    fun formatsMinuteOfDayClampedToDay() {
        assertEquals("00:00", TimeUtils.formatMinuteOfDay(0))
        assertEquals("08:05", TimeUtils.formatMinuteOfDay(8 * 60 + 5))
        assertEquals("23:59", TimeUtils.formatMinuteOfDay(24 * 60))
        // 负值夹紧到下界 00:00
        assertEquals("00:00", TimeUtils.formatMinuteOfDay(-1))
    }

    @Test
    fun refDateUsesJavaTimeAndStableFallback() {
        assertEquals("2/23", TimeUtils.refDate(1, 1, "2026-02-23", Locale.US))
        assertEquals("3/1", TimeUtils.refDate(1, 7, "2026-02-23", Locale.US))
        assertEquals("3/2", TimeUtils.refDate(2, 1, "2026-02-23", Locale.US))
        // 非法开学日期回退到 2026-02-23，不抛异常
        assertEquals("2/23", TimeUtils.refDate(1, 1, "not-a-date", Locale.US))
    }

    @Test
    fun legacyMidweekSemesterStartIsAutomaticallyAlignedToMonday() {
        // 2026-08-20 是周四；旧版本若把它直接保存为开学日期，所有列都会整体偏移。
        assertEquals(LocalDate.parse("2026-08-17"), TimeUtils.semesterWeekStartOrNull("2026-08-20"))
        assertEquals(LocalDate.parse("2026-08-20"), TimeUtils.semesterDateOrNull("2026-08-20", 1, 4))
        assertEquals("8/20", TimeUtils.refDate(1, 4, "2026-08-20", Locale.US))
        assertEquals("8/21", TimeUtils.refDate(1, 5, "2026-08-20", Locale.US))
    }
}
