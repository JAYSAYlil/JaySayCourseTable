package com.jaysay.coursetable.ui.screen

import androidx.compose.ui.unit.dp
import com.jaysay.coursetable.data.preferences.PeriodTime
import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleGridOffsetsTest {
    @Test
    fun precomputedOffsetsPreserveEmptyAndSectionBoundaryPositions() {
        val periods = List(9) { PeriodTime("08:00", "08:45") }

        assertEquals(listOf(0.dp), buildPeriodOffsets(emptyList(), 106.dp))
        assertEquals(
            listOf(20.dp, 126.dp, 232.dp, 338.dp, 444.dp),
            buildPeriodOffsets(periods.take(4), 106.dp)
        )
        assertEquals(
            listOf(20.dp, 126.dp, 232.dp, 338.dp, 464.dp, 570.dp),
            buildPeriodOffsets(periods.take(5), 106.dp)
        )
        assertEquals(
            listOf(20.dp, 126.dp, 232.dp, 338.dp, 464.dp, 570.dp, 676.dp, 782.dp, 908.dp, 1014.dp),
            buildPeriodOffsets(periods, 106.dp)
        )
    }
}
