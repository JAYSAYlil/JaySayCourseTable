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
import com.jaysay.coursetable.data.model.ScheduleDateResolver
import com.jaysay.coursetable.data.repository.CourseRepository
import com.jaysay.coursetable.data.preferences.PreferencesManager
import com.jaysay.coursetable.util.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 提醒广播接收器：触发时先校验提醒仍有效（课程仍存在、该周有课、提醒仍启用），
 * 然后滚动重新调度未来 7 天，最后发通知。
 */
class CourseReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // runCatching 兜底：提醒触发路径上的任何未预期异常（如偏好写入失败）
                // 不允许直接击穿默认异常处理器导致应用崩溃。
                runCatching { deliver(context.applicationContext, intent) }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun deliver(context: Context, intent: Intent) {
        val tableIndex = intent.getIntExtra(ReminderScheduler.EXTRA_TABLE_INDEX, -1)
        val week = intent.getIntExtra(ReminderScheduler.EXTRA_WEEK, -1)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        if (intent.action == ACTION_MUTE_TODAY || intent.action == ACTION_MUTE_WEEK) {
            if (tableIndex < 0) return
            if (intent.action == ACTION_MUTE_TODAY) ReminderSuppression.muteToday(context, tableIndex)
            else if (week > 0) ReminderSuppression.muteWeek(context, tableIndex, week)
            if (notificationId >= 0) NotificationManagerCompat.from(context).cancel(notificationId)
            val preferences = runCatching { PreferencesManager(context).load() }.getOrDefault(
                com.jaysay.coursetable.data.preferences.AppPreferences()
            )
            val tables = runCatching { CourseRepository(context).loadAllTables() }.getOrNull() ?: return
            ReminderScheduler.rescheduleAll(context, tables, preferences)
            return
        }
        val seriesKey = intent.getStringExtra(ReminderScheduler.EXTRA_SERIES_KEY) ?: return
        val instanceDate = intent.getStringExtra(ReminderScheduler.EXTRA_DATE)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return
        val title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE) ?: return
        val info = intent.getStringExtra(ReminderScheduler.EXTRA_INFO).orEmpty()
        val startMinute = intent.getIntExtra(ReminderScheduler.EXTRA_START_MINUTE, -1)
        val endMinute = intent.getIntExtra(ReminderScheduler.EXTRA_END_MINUTE, -1)
        val eventKind = runCatching {
            ReminderEventKind.valueOf(intent.getStringExtra(ReminderScheduler.EXTRA_EVENT_KIND).orEmpty())
        }.getOrDefault(ReminderEventKind.START)

        val preferences = runCatching { PreferencesManager(context).load() }.getOrDefault(
            com.jaysay.coursetable.data.preferences.AppPreferences()
        )
        val tables = runCatching { CourseRepository(context).loadAllTables() }.getOrNull() ?: return
        if (preferences.activeTableIndex != tableIndex) return
        val table = tables.getOrNull(tableIndex) ?: return
        // 校验该课程在本周确实仍有课（用户删除/修改后旧闹钟不得再打扰）。
        val resolvedCourses = ScheduleDateResolver.coursesOn(
            table.courses,
            table.semesterStart,
            table.totalWeeks,
            table.excludedWeeks.toSet(),
            table.dateExceptions,
            instanceDate
        ).map { it.course }
        val validCourse = resolvedCourses.firstOrNull { course ->
            val currentStartMinute = table.periods.getOrNull(course.startPeriod - 1)?.start
                ?.let(TimeUtils::parseMinuteOfDay)
            val currentEndMinute = table.periods.getOrNull(course.endPeriod - 1)?.end
                ?.let(TimeUtils::parseMinuteOfDay)
            course.seriesKey == seriesKey &&
                currentStartMinute == startMinute &&
                currentEndMinute == endMinute &&
                ReminderPolicy.isEnabled(course, preferences) &&
                (eventKind != ReminderEventKind.END || course.endReminderEnabled)
        }
        if (validCourse == null) return
        if (ReminderSuppression.isSuppressed(context, tableIndex, week, instanceDate)) return

        // 滚动续排：保证不打开应用也能持续收到后续提醒。
        ReminderScheduler.extendWindow(context, tables, preferences)

        // Android 13+ 需要 POST_NOTIFICATIONS 运行时权限；未授予时无法显示通知。
        val canNotify = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        if (canNotify) {
            val id = notificationId(tableIndex, seriesKey, week, eventKind)
            val notification = buildNotification(
                context, title, info, startMinute, endMinute, eventKind, tableIndex, seriesKey, week, id
            )
            runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
        }
    }

    private fun buildNotification(
        context: Context,
        title: String,
        info: String,
        startMinute: Int,
        endMinute: Int,
        eventKind: ReminderEventKind,
        tableIndex: Int,
        seriesKey: String,
        week: Int,
        notificationId: Int
    ): Notification {
        val timeText = when (eventKind) {
            ReminderEventKind.START -> if (startMinute >= 0) " ${TimeUtils.formatMinuteOfDay(startMinute)} 开始" else ""
            ReminderEventKind.END -> if (endMinute >= 0) " ${TimeUtils.formatMinuteOfDay(endMinute)} 结束" else ""
        }
        val prefix = if (eventKind == ReminderEventKind.START) "即将上课" else "课程已结束"
        return NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(prefix + timeText + if (info.isNotBlank()) " · $info" else "")
            .setContentIntent(ReminderScheduler.buildOpenAppIntent(context, tableIndex, seriesKey))
            .addAction(0, "今天不再提醒", suppressionIntent(
                context, ACTION_MUTE_TODAY, tableIndex, week, seriesKey, notificationId
            ))
            .addAction(0, "本周暂停", suppressionIntent(
                context, ACTION_MUTE_WEEK, tableIndex, week, seriesKey, notificationId
            ))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    private fun suppressionIntent(
        context: Context,
        action: String,
        tableIndex: Int,
        week: Int,
        seriesKey: String,
        notificationId: Int
    ): PendingIntent = PendingIntent.getBroadcast(
        context,
        "$action|$tableIndex|$week|$seriesKey".hashCode(),
        Intent(context, CourseReminderReceiver::class.java).apply {
            this.action = action
            putExtra(ReminderScheduler.EXTRA_TABLE_INDEX, tableIndex)
            putExtra(ReminderScheduler.EXTRA_WEEK, week)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun notificationId(
        tableIndex: Int,
        seriesKey: String,
        week: Int,
        eventKind: ReminderEventKind
    ): Int = "$tableIndex|$seriesKey|$week|$eventKind".hashCode()

    private companion object {
        const val ACTION_MUTE_TODAY = "com.jaysay.coursetable.action.MUTE_REMINDERS_TODAY"
        const val ACTION_MUTE_WEEK = "com.jaysay.coursetable.action.MUTE_REMINDERS_WEEK"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }
}
