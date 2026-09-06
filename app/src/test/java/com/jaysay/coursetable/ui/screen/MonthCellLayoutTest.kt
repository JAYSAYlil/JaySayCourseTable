package com.jaysay.coursetable.ui.screen

import org.junit.Assert.*
import org.junit.Test

class MonthCellLayoutTest {
    @Test fun largeTextKeepsSpecialStatusAndCourseSummary() {
        val layout = monthCellLayout(98f, 32f, 26f, 2f, true, 4)
        assertEquals(0, layout.courseLines)
        assertTrue(layout.summary)
        assertFalse(layout.lunar)
    }
    @Test fun lunarYieldsToCourseNames() {
        val layout = monthCellLayout(63f, 16f, 13f, 2f, true, 2)
        assertEquals(2, layout.courseLines)
        assertFalse(layout.lunar)
    }
    @Test fun tallCellShowsAllLayersIncludingMoreCount() {
        val layout = monthCellLayout(104f, 16f, 13f, 2f, true, 4)
        assertEquals(2, layout.courseLines)
        assertTrue(layout.lunar)
    }
}
