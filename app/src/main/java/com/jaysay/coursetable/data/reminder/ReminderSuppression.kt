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

    /**
     * 清理已过期的暂停状态：昨天的“今天暂停”或本周之前的“本周暂停”
     * 不再拦截，避免用户忘记恢复而长期收不到提醒。
     */
    fun pruneExpired(context: Context, tableIndex: Int, today: LocalDate, currentWeek: Int) {
        val values = prefs(context)
        if (values.getInt(MUTED_TABLE, -1) != tableIndex) return
        val editor = values.edit()
        val mutedDate = values.getString(MUTED_DATE, null)
        if (mutedDate != null &&
            runCatching { LocalDate.parse(mutedDate) }.getOrNull()?.isBefore(today) == true
        ) {
            editor.remove(MUTED_DATE)
        }
        val mutedWeek = values.getInt(MUTED_WEEK, -1)
        if (currentWeek > 0 && mutedWeek in 1 until currentWeek) {
            editor.remove(MUTED_WEEK)
        }
        editor.apply()
    }

    fun isSuppressed(context: Context, tableIndex: Int, week: Int, date: LocalDate): Boolean {
        val values = prefs(context)
        if (values.getInt(MUTED_TABLE, -1) != tableIndex) return false
        return values.getString(MUTED_DATE, null) == date.toString() || values.getInt(MUTED_WEEK, -1) == week
    }

    fun activeLabel(context: Context, tableIndex: Int, week: Int, date: LocalDate): String? {
        val values = prefs(context)
        if (values.getInt(MUTED_TABLE, -1) != tableIndex) return null
        return when {
            values.getString(MUTED_DATE, null) == date.toString() -> "今天的课程提醒已暂停"
            values.getInt(MUTED_WEEK, -1) == week -> "本周的课程提醒已暂停"
            else -> null
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
}
