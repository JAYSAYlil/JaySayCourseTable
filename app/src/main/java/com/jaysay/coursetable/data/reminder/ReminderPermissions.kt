package com.jaysay.coursetable.data.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * 上课提醒的权限与系统状态诊断。
 *
 * 提醒"到时间不通知"最常见的三个系统侧原因：
 * 1. Android 14+ 对 SCHEDULE_EXACT_ALARM 默认拒绝（USE_EXACT_ALARM 可根治，见 Manifest）；
 * 2. Android 13+ 用户拒绝了 POST_NOTIFICATIONS 运行时权限，通知被静默丢弃；
 * 3. 用户在系统设置中关闭了"上课提醒"通知渠道。
 * 这里统一暴露检查结果，供设置页展示状态与引导修复入口。
 */
object ReminderPermissions {

    /** Android 13+ 的通知运行时权限是否已授予；更低版本始终为 true。 */
    fun notificationsAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    /** 系统是否允许精确闹钟（Android 13+ 由 USE_EXACT_ALARM 自动授予）。 */
    fun exactAlarmsAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** "上课提醒"通知渠道是否被用户在系统设置中关闭。 */
    fun channelBlocked(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = manager.getNotificationChannel(ReminderScheduler.CHANNEL_ID) ?: return false
        return channel.importance == NotificationManager.IMPORTANCE_NONE
    }

    /**
     * 汇总提醒功能当前存在的系统侧阻碍。为空表示可以正常接收提醒。
     * 顺序即建议修复顺序：通知权限 → 精确闹钟 → 通知渠道。
     */
    fun blockers(context: Context): List<ReminderBlocker> = buildList {
        if (!notificationsAllowed(context)) add(ReminderBlocker.NOTIFICATION_PERMISSION)
        if (!exactAlarmsAllowed(context)) add(ReminderBlocker.EXACT_ALARM)
        if (channelBlocked(context)) add(ReminderBlocker.CHANNEL_DISABLED)
    }
}

/** 提醒功能的系统侧阻碍类型，供设置页逐项展示与引导。 */
enum class ReminderBlocker {
    NOTIFICATION_PERMISSION,
    EXACT_ALARM,
    CHANNEL_DISABLED
}
