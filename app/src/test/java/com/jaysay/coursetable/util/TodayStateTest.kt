package com.jaysay.coursetable.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 跨午夜日期状态测试：通过注入时钟验证“今天”在日期/时区变化后校准，
 * 全程不修改设备系统时间。
 */
class TodayStateTest {

    @Test
    fun refreshPicksUpNewDateAfterMidnight() {
        var today: LocalDate = LocalDate.of(2026, 9, 5)
        val state = TodayState { today }
        assertEquals(LocalDate.of(2026, 9, 5), state.value)

        today = LocalDate.of(2026, 9, 6)
        state.refresh()
        assertEquals("跨午夜后应更新到新的一天", LocalDate.of(2026, 9, 6), state.value)
    }

    @Test
    fun refreshKeepsValueWhenDateUnchanged() {
        var today: LocalDate = LocalDate.of(2026, 9, 5)
        val state = TodayState { today }
        state.refresh()
        state.refresh()
        assertEquals("日期未变时保持原值", LocalDate.of(2026, 9, 5), state.value)
    }

    @Test
    fun clockResolutionFollowsInjectedZone() {
        // 同一时刻：东八区已是 9 月 6 日，洛杉矶仍是 9 月 5 日。
        val instant = Instant.parse("2026-09-05T17:00:00Z")
        val east = TodayState { LocalDate.ofInstant(instant, ZoneId.of("Asia/Shanghai")) }
        val west = TodayState { LocalDate.ofInstant(instant, ZoneId.of("America/Los_Angeles")) }
        assertEquals(LocalDate.of(2026, 9, 6), east.value)
        assertEquals(LocalDate.of(2026, 9, 5), west.value)
    }
}
