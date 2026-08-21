package com.jaysay.coursetable.data.reminder

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.jaysay.coursetable.MainActivity
import com.jaysay.coursetable.R
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 提醒功能的运行诊断：
 * 1. 记录每次闹钟触发的处理结果（时间 + 结果 + 未通知原因），
 *    供设置页展示，便于“到点没通知”时快速定位卡在哪一步；
 * 2. 提供一键测试通知，直接验证“应用 → 系统通知”链路。
 */
object ReminderDiagnostics {
    private const val NAME = "course_reminder_diagnostics"
    private const val KEY_EVENTS = "recent_events"
    private const val MAX_EVENTS = 5

    private val timeFormat = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")

    /** 记录一次触发结果。 */
    fun record(context: Context, outcome: String) {
        val timestamp = LocalDateTime.now().format(timeFormat)
        val events = prefs(context).getString(KEY_EVENTS, null)
            ?.split('\n')
            ?.filter { it.isNotBlank() }
            ?.toMutableList() ?: mutableListOf()
        events.add(0, timestamp + " " + outcome)
        while (events.size > MAX_EVENTS) events.removeAt(events.size - 1)
        prefs(context).edit().putString(KEY_EVENTS, events.joinToString("\n")).apply()
    }

    /** 最近的触发记录，最新在前；无记录时返回空列表。 */
    fun recent(context: Context): List<String> =
        prefs(context).getString(KEY_EVENTS, null)
            ?.split('\n')
            ?.filter { it.isNotBlank() }
            .orEmpty()

    /**
     * 立即发送一条测试通知，验证通知链路（权限 + 渠道 + 系统投递）。
     * 返回 null 表示发送成功；否则返回失败原因。
     */
    fun sendTestNotification(context: Context): String? {
        // 显式权限检查（lint 可验证），拒绝时给出明确失败原因。
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            record(context, "测试通知失败：通知权限未开启")
            return "通知权限未开启"
        }
        if (ReminderPermissions.channelBlocked(context)) {
            record(context, "测试通知失败：上课提醒通知渠道已关闭")
            return "上课提醒通知渠道已关闭"
        }
        ReminderScheduler.ensureChannel(context)
        val id = System.currentTimeMillis().toInt()
        val notification = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("提醒测试")
            .setContentText("如果你能看到这条通知，说明提醒链路正常")
            .setContentIntent(PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
            .onFailure { error ->
                record(context, "测试通知异常：" + (error.message ?: "未知"))
                return "系统拒绝发送：" + (error.message ?: "未知错误")
            }
        record(context, "测试通知已发送")
        return null
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
}
