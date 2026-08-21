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
                deliver(context.applicationContext, intent)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun deliver(context: Context, intent: Intent) {
        val tableIndex = intent.getIntExtra(ReminderScheduler.EXTRA_TABLE_INDEX, -1)
        val week = intent.getIntExtra(ReminderScheduler.EXTRA_WEEK, -1)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        if (intent.action == ACTION_RESUME) {
            if (tableIndex < 0) return
            ReminderSuppression.clear(context)
            NotificationManagerCompat.from(context).cancel(mutedNoticeId(tableIndex, week))
            val preferences = runCatching { PreferencesManager(context).load() }.getOrDefault(
                com.jaysay.coursetable.data.preferences.AppPreferences()
            )
            val tables = runCatching { CourseRepository(context).loadAllTables() }.getOrNull() ?: return
            ReminderScheduler.rescheduleAll(context, tables, preferences)
            ReminderDiagnostics.record(context, "已恢复提醒（用户点击恢复）")
            return
        }
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
            // 发一条带“恢复提醒”按钮的通知，解决“暂停后找不到恢复入口”的问题。
            postMutedNotice(context, tableIndex, week, intent.action == ACTION_MUTE_TODAY)
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
        val tables = runCatching { CourseRepository(context).loadAllTables() }.getOrNull() ?: run {
            ReminderDiagnostics.record(context, "触发但课表数据读取失败")
            return
        }
        if (preferences.activeTableIndex != tableIndex) {
            ReminderDiagnostics.record(context, "触发但活动课表已切换，跳过")
            return
        }
        val table = tables.getOrNull(tableIndex) ?: run {
            ReminderDiagnostics.record(context, "触发但课表不存在，跳过")
            return
        }
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
        if (validCourse == null) {
            ReminderDiagnostics.record(context, "触发但课程已变更，跳过（$title）")
            return
        }
        if (ReminderSuppression.isSuppressed(context, tableIndex, week, instanceDate)) {
            ReminderDiagnostics.record(context, "触发但今天/本周已暂停，跳过（$title）")
            return
        }

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
                .onSuccess {
                    ReminderDiagnostics.record(context, "通知已发送（$title）")
                    // 保活服务继续运行，保证后续闹钟在未打开应用时也能即时执行。
                    ReminderKeepAliveService.ensureRunning(context)
                }
                .onFailure { error ->
                    ReminderDiagnostics.record(context, "通知发送异常：" + (error.message ?: "未知"))
                }
        } else {
            ReminderDiagnostics.record(context, "触发但通知权限未授予，通知被系统拦截（$title）")
            ReminderKeepAliveService.ensureRunning(context)
        }
    }

    /** 暂停后发送“已暂停，点此恢复”通知，恢复入口始终可见。 */
    private fun postMutedNotice(context: Context, tableIndex: Int, week: Int, today: Boolean) {
        val canNotify = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        if (!canNotify) return
        val mutedId = mutedNoticeId(tableIndex, week)
        val text = if (today) {
            context.getString(R.string.reminder_muted_notification_today)
        } else {
            context.getString(R.string.reminder_muted_notification_week)
        }
        val notification = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.reminder_muted_notification_title))
            .setContentText(text)
            .setContentIntent(
                PendingIntent.getBroadcast(
                    context,
                    mutedId,
                    Intent(context, CourseReminderReceiver::class.java).apply {
                        action = ACTION_RESUME
                        putExtra(ReminderScheduler.EXTRA_TABLE_INDEX, tableIndex)
                        putExtra(ReminderScheduler.EXTRA_WEEK, week)
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .addAction(
                0,
                context.getString(R.string.reminder_muted_notification_resume),
                PendingIntent.getBroadcast(
                    context,
                    mutedId + 1,
                    Intent(context, CourseReminderReceiver::class.java).apply {
                        action = ACTION_RESUME
                        putExtra(ReminderScheduler.EXTRA_TABLE_INDEX, tableIndex)
                        putExtra(ReminderScheduler.EXTRA_WEEK, week)
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(mutedId, notification) }
        ReminderDiagnostics.record(context, if (today) "已暂停今天，已发恢复入口通知" else "已暂停本周，已发恢复入口通知")
    }

    private fun mutedNoticeId(tableIndex: Int, week: Int): Int =
        ("muted|$tableIndex|$week").hashCode()

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
        const val ACTION_RESUME = "com.jaysay.coursetable.action.RESUME_REMINDERS"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }
}
