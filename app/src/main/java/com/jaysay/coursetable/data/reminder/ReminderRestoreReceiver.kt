package com.jaysay.coursetable.data.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jaysay.coursetable.data.preferences.AppPreferences
import com.jaysay.coursetable.data.preferences.PreferencesManager
import com.jaysay.coursetable.data.repository.CourseRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private val REMINDER_RESTORE_ACTIONS = setOf(
    Intent.ACTION_BOOT_COMPLETED,
    Intent.ACTION_MY_PACKAGE_REPLACED,
    Intent.ACTION_TIME_CHANGED,
    Intent.ACTION_TIMEZONE_CHANGED
)

internal fun shouldRestoreReminders(action: String?): Boolean = action in REMINDER_RESTORE_ACTIONS

/** 手机重启、时区/时间变化或应用升级后恢复未来课程提醒。 */
class ReminderRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Manifest 中虽然已设置 exported=false，仍只接受明确登记的系统事件，
        // 避免未来调整清单配置后未知广播误触发磁盘读取和全量重调度。
        if (!shouldRestoreReminders(intent.action)) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val appContext = context.applicationContext
                val preferences = runCatching { PreferencesManager(appContext).load() }
                    .getOrDefault(AppPreferences())
                val tables = runCatching { CourseRepository(appContext).loadAllTables() }
                    .getOrNull() ?: return@launch
                ReminderScheduler.rescheduleAll(appContext, tables, preferences)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
