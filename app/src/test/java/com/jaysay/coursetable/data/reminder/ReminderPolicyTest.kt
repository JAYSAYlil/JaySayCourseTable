package com.jaysay.coursetable.data.reminder

import com.jaysay.coursetable.data.model.Course
import com.jaysay.coursetable.data.model.CourseReminderMode
import com.jaysay.coursetable.data.preferences.AppPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderPolicyTest {
    private fun course(mode: CourseReminderMode, minutes: Int? = null) = Course(
        courseId = "id", courseName = "课程", classNumber = "", department = "", credits = 0f,
        weeks = listOf(1), dayOfWeek = 1, startPeriod = 1, endPeriod = 2, teacher = "",
        classroom = "", courseType = "", courseCategory = "", isOnline = false,
        assessmentMethod = "", seriesId = "series", reminderMode = mode,
        reminderMinutesOverride = minutes
    )

    @Test fun courseModeCanOverrideGlobalSetting() {
        assertTrue(ReminderPolicy.isEnabled(course(CourseReminderMode.ENABLED), AppPreferences(reminderEnabled = false)))
        assertFalse(ReminderPolicy.isEnabled(course(CourseReminderMode.DISABLED), AppPreferences(reminderEnabled = true)))
        assertTrue(ReminderPolicy.isEnabled(course(CourseReminderMode.INHERIT), AppPreferences(reminderEnabled = true)))
    }

    @Test fun courseAdvanceOverridesGlobalAdvance() {
        assertEquals(30, ReminderPolicy.advanceMinutes(course(CourseReminderMode.ENABLED, 30), AppPreferences(reminderMinutes = 10)))
        assertEquals(10, ReminderPolicy.advanceMinutes(course(CourseReminderMode.INHERIT), AppPreferences(reminderMinutes = 10)))
    }
}
