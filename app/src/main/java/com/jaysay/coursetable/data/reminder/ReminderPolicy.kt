package com.jaysay.coursetable.data.reminder

import com.jaysay.coursetable.data.model.Course
import com.jaysay.coursetable.data.model.CourseReminderMode
import com.jaysay.coursetable.data.preferences.AppPreferences

object ReminderPolicy {
    fun isEnabled(course: Course, preferences: AppPreferences): Boolean = when (course.reminderMode) {
        CourseReminderMode.INHERIT -> preferences.reminderEnabled
        CourseReminderMode.ENABLED -> true
        CourseReminderMode.DISABLED -> false
    }

    fun advanceMinutes(course: Course, preferences: AppPreferences): Int =
        course.reminderMinutesOverride?.coerceIn(1, 60) ?: preferences.reminderMinutes.coerceIn(1, 60)
}
