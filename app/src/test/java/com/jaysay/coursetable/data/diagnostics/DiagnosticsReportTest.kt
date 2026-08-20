package com.jaysay.coursetable.data.diagnostics

import com.jaysay.coursetable.data.model.Course
import com.jaysay.coursetable.data.preferences.AppPreferences
import com.jaysay.coursetable.data.repository.TableData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsReportTest {
    @Test
    fun aggregatesSafeCountsAndAnomalies() {
        val course = course("高等数学", "张老师", "南楼-A101").copy(weeks = listOf(1, 21), dayOfWeek = 8)
        val tables = listOf(
            TableData("真实课表名称", listOf(course), totalWeeks = 20, excludedWeeks = listOf(0, 2)),
            TableData("另一个真实名称", emptyList())
        )

        val report = DiagnosticsReportGenerator.generate(
            DiagnosticsEnvironment("2.8.0/unsafe", 81, 34, false),
            tables,
            AppPreferences(reminderEnabled = true, reminderMinutes = 99)
        )

        assertEquals(2, report.schedule.tableCount)
        assertEquals(1, report.schedule.courseCount)
        assertEquals(1, report.schedule.emptyTableCount)
        assertEquals(1, report.anomalies.invalidDayOfWeekCount)
        assertEquals(1, report.anomalies.weeksOutsideTableRangeCount)
        assertEquals(1, report.anomalies.invalidExcludedWeekCount)
        assertEquals(60, report.settings.reminderMinutes)
        assertEquals("2.8.0_unsafe", report.environment.appVersionName)
    }

    @Test
    fun textAndJsonNeverContainSensitiveFieldsOrValues() {
        val sensitive = listOf("机密课程名", "机密教师", "机密教室", "机密备注", "C:\\Users\\secret\\backup.json")
        val report = DiagnosticsReportGenerator.generate(
            DiagnosticsEnvironment("2.8.0", 81, 34, false),
            listOf(TableData(sensitive[0], listOf(course(sensitive[0], sensitive[1], sensitive[2]).copy(notes = sensitive[3])))),
            AppPreferences()
        )

        val text = report.toText()
        val json = report.toJson()
        sensitive.forEach {
            assertFalse("敏感值泄露：$it", text.contains(it))
            assertFalse("敏感值泄露：$it", json.contains(it))
        }
        listOf("courseName", "teacher", "classroom", "notes", "courseId", "seriesId", "path", "backup")
            .forEach { field ->
                assertFalse("敏感字段泄露：$field", text.contains(field, ignoreCase = true))
                assertFalse("敏感字段泄露：$field", json.contains(field, ignoreCase = true))
            }
        assertTrue(json.contains("courseCount"))
    }

    @Test
    fun emptyInputRemainsSafeAndUiFriendly() {
        val report = DiagnosticsReportGenerator.generate(
            DiagnosticsEnvironment("", -1, -1, true), emptyList(), AppPreferences(activeTableIndex = 2),
        )

        assertEquals("unknown", report.environment.appVersionName)
        assertEquals(0, report.environment.appVersionCode)
        assertEquals(0, report.environment.androidApiLevel)
        assertFalse(report.settings.activeTableIndexValid)
        assertTrue(report.toText().isNotBlank())
    }

    private fun course(name: String, teacher: String, classroom: String) = Course(
        courseId = "internal-id", courseName = name, classNumber = "class-1", department = "dept",
        credits = 2f, weeks = listOf(1), dayOfWeek = 1, startPeriod = 1, endPeriod = 1,
        teacher = teacher, classroom = classroom, courseType = "type", courseCategory = "category",
        isOnline = false, assessmentMethod = "assessment", notes = "", seriesId = "stable-id"
    )
}
