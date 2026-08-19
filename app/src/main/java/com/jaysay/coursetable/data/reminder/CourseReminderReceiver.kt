package com.jaysay.coursetable.data.reminder

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.jaysay.coursetable.R
import com.jaysay.coursetable.data.repository.CourseRepository
import com.jaysay.coursetable.data.preferences.PreferencesManager
import com.jaysay.coursetable.util.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 提醒广播接收器：触发时先校验提醒仍有效（课程仍存在、该周有课、提醒仍启用），
 * 然后滚动重新调度未来 7 天，最后发通知。
 */
class CourseReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                deliver(context.applicationContext, intent)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun deliver(context: Context, intent: Intent) {
        val tableIndex = intent.getIntExtra(ReminderScheduler.EXTRA_TABLE_INDEX, -1)
        val seriesKey = intent.getStringExtra(ReminderScheduler.EXTRA_SERIES_KEY) ?: return
        val week = intent.getIntExtra(ReminderScheduler.EXTRA_WEEK, -1)
        val title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE) ?: return
        val info = intent.getStringExtra(ReminderScheduler.EXTRA_INFO).orEmpty()
        val startMinute = intent.getIntExtra(ReminderScheduler.EXTRA_START_MINUTE, -1)

        val preferences = runCatching { PreferencesManager(context).load() }.getOrDefault(
            com.jaysay.coursetable.data.preferences.AppPreferences()
        )
        val tables = runCatching { CourseRepository(context).loadAllTables() }.getOrNull() ?: return
        if (!preferences.reminderEnabled) return
        val table = tables.getOrNull(tableIndex) ?: return
        // 校验该课程在本周确实仍有课（用户删除/修改后旧闹钟不得再打扰）。
        val stillValid = table.courses.any {
            it.seriesKey == seriesKey && week in it.weeks && week !in table.excludedWeeks
        }
        if (!stillValid) return

        // 滚动续排：保证不打开应用也能持续收到后续提醒。
        ReminderScheduler.rescheduleAll(context, tables, preferences)

        // Android 13+ 需要 POST_NOTIFICATIONS 运行时权限；未授予时静默跳过。
        val canNotify = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        if (canNotify) {
            val notification = buildNotification(context, title, info, startMinute)
            runCatching { NotificationManagerCompat.from(context).notify(intent.hashCode(), notification) }
        }
    }

    private fun buildNotification(context: Context, title: String, info: String, startMinute: Int): Notification {
        val timeText = if (startMinute >= 0) " ${TimeUtils.formatMinuteOfDay(startMinute)} 开始" else ""
        return NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText("即将上课$timeText" + if (info.isNotBlank()) " · $info" else "")
            .setContentIntent(ReminderScheduler.buildOpenAppIntent(context))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }
}
