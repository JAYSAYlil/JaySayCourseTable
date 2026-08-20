package com.jaysay.coursetable.data.repository

import com.jaysay.coursetable.data.model.ScheduleViewMode
import com.jaysay.coursetable.data.model.ScheduleDateException
import com.jaysay.coursetable.data.model.ScheduleExceptionType
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

    @Test
    fun legacyMidweekSemesterStartMigratesToMondayWithoutUserReset() {
        val oldJson = JSONArray().put(
            JSONObject()
                .put("name", "旧课表")
                .put("semesterStart", "2026-08-20")
                .put("courses", JSONArray())
        )

        val restored = TableDataJson.fromJson(oldJson, requireEveryRowValid = true).single()

        assertEquals("2026-08-17", restored.semesterStart)
    }

    @Test
    fun calendarMetadataAndArchiveSurviveRoundTrip() {
        val original = TableData.placeholder().copy(
            dateExceptions = listOf(
                ScheduleDateException(id = "exception-1", date = "2026-03-02", type = ScheduleExceptionType.DAY_OFF)
            ),
            weekLabels = mapOf(3 to "考试周"),
            archived = true,
            archivedAt = "2026-08-20T08:00:00Z"
        )

        // 唯一课表不能全部归档，规范化会自动恢复为活动状态；加一张活动课表验证归档字段。
        val restored = TableDataJson.fromJson(
            TableDataJson.toJson(listOf(TableData.placeholder(), original)), true
        )[1]

        assertEquals("2026-03-02", restored.dateExceptions.single().date)
        assertEquals(ScheduleExceptionType.DAY_OFF, restored.dateExceptions.single().type)
        assertEquals("考试周", restored.weekLabels[3])
        assertEquals(true, restored.archived)
        assertEquals("2026-08-20T08:00:00Z", restored.archivedAt)
    }
}
