package com.jaysay.coursetable.data.backup

import com.jaysay.coursetable.data.model.Course
import com.jaysay.coursetable.data.preferences.AppPreferences
import com.jaysay.coursetable.data.preferences.ThemeMode
import com.jaysay.coursetable.data.repository.TableData
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {
    @Test
    fun fullBackupRoundTripPreservesAllUserData() {
        val original = BackupData(
            tables = listOf(TableData("我的课表", listOf(course()))),
            preferences = AppPreferences(ThemeMode.DARK, 0)
        )

        val restored = BackupCodec.decode(BackupCodec.encode(original))

        assertEquals(original, restored)
    }

    @Test
    fun sanitizedCopyRemovesPrivateFieldsAndCannotBeRestored() {
        val json = BackupCodec.encode(
            BackupData(listOf(TableData("分享", listOf(course()))), AppPreferences()),
            sanitized = true
        )

        val root = JSONObject(json)
        assertTrue(root.getBoolean("sanitized"))
        val sanitizedCourse = root.getJSONArray("tables").getJSONObject(0)
            .getJSONArray("courses").getJSONObject(0)
        listOf("courseId", "classNumber", "department", "teacher", "classroom", "notes").forEach {
            assertEquals("Sanitized field $it was not cleared", "", sanitizedCourse.optString(it))
        }
        listOf("教师甲", "教室A", "私人备注", "学院", "C001").forEach {
            assertFalse("Sanitized JSON leaked $it", json.contains(it))
        }
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(json) }
    }

    @Test
    fun damagedBackupIsRejectedInsteadOfPartiallyReplacingData() {
        val valid = BackupCodec.encode(
            BackupData(listOf(TableData("课表", listOf(course()))), AppPreferences())
        )
        val root = JSONObject(valid)
        root.getJSONArray("tables").getJSONObject(0)
            .getJSONArray("courses").getJSONObject(0).put("dayOfWeek", 99)

        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(root.toString()) }
    }

    private fun course() = Course(
        courseId = "C001",
        courseName = "课程",
        classNumber = "01",
        department = "学院",
        credits = 2f,
        weeks = listOf(1, 2, 3),
        dayOfWeek = 1,
        startPeriod = 1,
        endPeriod = 2,
        teacher = "教师甲",
        classroom = "教室A",
        courseType = "必修",
        courseCategory = "专业",
        isOnline = false,
        assessmentMethod = "考试",
        customColor = 0x123456,
        notes = "私人备注"
    )
}
