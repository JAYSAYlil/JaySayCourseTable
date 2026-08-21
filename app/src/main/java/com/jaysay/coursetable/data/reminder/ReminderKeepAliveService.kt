package com.jaysay.coursetable.data.reminder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.jaysay.coursetable.MainActivity
import com.jaysay.coursetable.R
import com.jaysay.coursetable.data.model.TodayAgendaCalculator
import com.jaysay.coursetable.data.preferences.PreferencesManager
import com.jaysay.coursetable.data.repository.CourseRepository
import com.jaysay.coursetable.widget.CourseWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 轻量前台保活服务：让应用进程在“未打开应用”时保持存活，
 * 从而保证提醒闹钟与小组件边界刷新闹钟触发时能立即执行。
 *
 * 资源占用设计（最小内存、最小续航）：
 * - 不另起进程，与主应用同进程，内存零额外开销；
 * - 不持有 WakeLock、不联网、不轮询磁盘高频数据；
 * - 仅每 60 秒做一次内存级轻量心跳：过期暂停清理 + 小组件状态指纹比较（变化才刷新）；
 * - 前台通知使用静默渠道（IMPORTANCE_MIN），不响铃不振动；
 *   Android 13+ 用户拒绝通知权限时服务照常运行，只是不显示常驻通知。
 *
 * 运行条件（heartbeat 自动退出）：提醒已开启，或桌面上存在小组件。
 */
class ReminderKeepAliveService : Service() {

    private var scope: CoroutineScope? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel(this)
        startForegroundWithNotification()
        startTicker()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundWithNotification()
        return START_STICKY
    }

    private fun startForegroundWithNotification() {
        val notification = NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.keepalive_notification_title))
            .setContentText(getString(R.string.keepalive_notification_text))
            .setContentIntent(PendingIntent.getActivity(
                this, 0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ))
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startTicker() {
        val newScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        scope = newScope
        newScope.launch {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                runCatching { heartbeat() }
                    .onFailure { ReminderDiagnostics.record(this@ReminderKeepAliveService, "保活心跳异常：" + (it.message ?: "未知")) }
            }
        }
    }

    /** 60 秒一次的内存级轻量任务。 */
    private suspend fun heartbeat() {
        val preferences = runCatching { PreferencesManager(this).load() }.getOrNull() ?: return
        val widgetPresent = isWidgetPresent(this)

        // 提醒已关闭且没有小组件时退出，不留常驻进程。
        if (!preferences.reminderEnabled && !widgetPresent) {
            stopSelf()
            return
        }

        // 清理已过期（昨天/上周）的暂停状态，避免用户忘记后一直静默。
        val tablesNow = runCatching { CourseRepository(this).loadAllTables() }.getOrNull()
        val currentWeek = tablesNow
            ?.getOrNull(preferences.activeTableIndex)
            ?.let { TodayAgendaCalculator.semesterWeek(it.semesterStart, it.totalWeeks, LocalDate.now()) }
            ?: -1
        ReminderSuppression.pruneExpired(this, preferences.activeTableIndex, LocalDate.now(), currentWeek)

        // 小组件状态指纹：只有“当前/下一节课”变化时才真正刷新 RemoteViews。
        if (widgetPresent) maybeRefreshWidget(preferences.activeTableIndex)
    }

    private suspend fun maybeRefreshWidget(activeTableIndex: Int) {
        val tables = runCatching { CourseRepository(this).loadAllTables() }.getOrNull() ?: return
        val table = tables.getOrNull(activeTableIndex) ?: return
        val now = LocalDateTime.now()
        val agenda = TodayAgendaCalculator.calculate(
            courses = table.courses,
            periods = table.periods,
            semesterStart = table.semesterStart,
            totalWeeks = table.totalWeeks,
            date = LocalDate.now(),
            minuteOfDay = now.hour * 60 + now.minute,
            excludedWeeks = table.excludedWeeks.toSet(),
            exceptions = table.dateExceptions
        )
        val fingerprint = agenda.phase.name +
            "|" + (agenda.current?.course?.courseName.orEmpty()) +
            "|" + (agenda.next?.course?.courseName.orEmpty())
        if (fingerprint != lastWidgetFingerprint) {
            lastWidgetFingerprint = fingerprint
            CourseWidgetProvider.requestUpdate(this)
        }
    }

    override fun onDestroy() {
        scope?.cancel()
        scope = null
        super.onDestroy()
    }

    companion object {
        const val SERVICE_CHANNEL_ID = "reminder_keepalive"
        private const val NOTIFICATION_ID = 24_001
        private const val HEARTBEAT_INTERVAL_MS = 60_000L
        private const val ACTION_STOP = "com.jaysay.coursetable.action.KEEPALIVE_STOP"

        private var lastWidgetFingerprint: String? = null

        /** 桌面是否已有小组件。 */
        fun isWidgetPresent(context: Context): Boolean {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, CourseWidgetProvider::class.java))
            return ids.isNotEmpty()
        }

        /**
         * 启动保活服务（前台调用或闹钟触发的接收器内调用）。
         * Android 12+ 后台启动限制下可能抛异常：忽略即可，通知照常发送。
         */
        fun ensureRunning(context: Context) {
            runCatching {
                context.startForegroundService(
                    Intent(context, ReminderKeepAliveService::class.java)
                )
            }.onFailure {
                ReminderDiagnostics.record(context, "保活服务启动受限：" + (it.message ?: "未知"))
            }
        }

        /** 停止保活服务（提醒关闭时由调度器显式停止）。 */
        fun stop(context: Context) {
            context.startService(
                Intent(context, ReminderKeepAliveService::class.java).setAction(ACTION_STOP)
            )
        }

        fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(SERVICE_CHANNEL_ID) != null) return
            manager.createNotificationChannel(
                NotificationChannel(
                    SERVICE_CHANNEL_ID,
                    context.getString(R.string.keepalive_channel_name),
                    NotificationManager.IMPORTANCE_MIN
                ).apply {
                    description = context.getString(R.string.keepalive_channel_desc)
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(false)
                }
            )
        }
    }
}
