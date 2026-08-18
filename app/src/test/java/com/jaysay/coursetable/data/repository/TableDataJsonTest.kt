package com.jaysay.coursetable.data.repository

import com.jaysay.coursetable.data.model.ScheduleViewMode
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class TableDataJsonTest {
    @Test
    fun viewModeSurvivesStorageRoundTrip() {
        val original = TableData.placeholder().copy(viewMode = ScheduleViewMode.WORK_WEEK)

        val restored = TableDataJson.fromJson(TableDataJson.toJson(listOf(original)), true).single()

        assertEquals(ScheduleViewMode.WORK_WEEK, restored.viewMode)
    }

    @Test
    fun oldTableWithoutViewModeDefaultsToSevenDays() {
        val oldJson = JSONArray().put(
            JSONObject()
                .put("name", "旧课表")
                .put("courses", JSONArray())
        )

        val restored = TableDataJson.fromJson(oldJson).single()

        assertEquals(ScheduleViewMode.WEEK, restored.viewMode)
    }
}
