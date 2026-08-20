package com.jaysay.coursetable.data.reminder

import android.content.Context
import java.time.LocalDate

/** 通知快捷操作产生的短期静音状态；只保存日期/教学周，不包含课程隐私。 */
object ReminderSuppression {
    private const val NAME = "course_reminder_suppression"
    private const val MUTED_DATE = "muted_date"
    private const val MUTED_WEEK = "muted_week"
    private const val MUTED_TABLE = "muted_table"

    fun muteToday(context: Context, tableIndex: Int, date: LocalDate = LocalDate.now()) {
        prefs(context).edit().putString(MUTED_DATE, date.toString()).putInt(MUTED_TABLE, tableIndex).apply()
    }

    fun muteWeek(context: Context, tableIndex: Int, week: Int) {
        prefs(context).edit().putInt(MUTED_WEEK, week).putInt(MUTED_TABLE, tableIndex).apply()
    }

    fun clear(context: Context) = prefs(context).edit().clear().apply()

    fun isSuppressed(context: Context, tableIndex: Int, week: Int, date: LocalDate): Boolean {
        val values = prefs(context)
        if (values.getInt(MUTED_TABLE, -1) != tableIndex) return false
        return values.getString(MUTED_DATE, null) == date.toString() || values.getInt(MUTED_WEEK, -1) == week
    }

    private fun prefs(context: Context) = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
}
