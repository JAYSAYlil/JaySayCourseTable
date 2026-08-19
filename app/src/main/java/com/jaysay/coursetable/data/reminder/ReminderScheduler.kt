package com.jaysay.coursetable.data.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.jaysay.coursetable.MainActivity
import com.jaysay.coursetable.data.model.TodayAgendaCalculator
import com.jaysay.coursetable.data.preferences.AppPreferences
import com.jaysay.coursetable.data.repository.TableData
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 上课提醒调度器：按“学期周次 + 课程周次 + 节次时间”计算未来 7 天的课程实例，
 * 用 AlarmManager 精确（或近似）触发广播；提醒被触发时接收器会再次调度，
 * 形成滚动链，应用不打开也能持续提醒。
 */
object ReminderScheduler {
    const val CHANNEL_ID = "course_reminders"
    const val EXTRA_TABLE_INDEX = "extra_table_index"
    const val EXTRA_SERIES_KEY = "extra_series_key"
    const val EXTRA_WEEK = "extra_week"
    const val EXTRA_TITLE = "extra_title"
    const val EXTRA_INFO = "extra_info"
    const val EXTRA_START_MINUTE = "extra_start_minute"

    /** 每次调度未来 7 天的提醒；每次触发后滚动续排。 */
    private const val SCHEDULE_WINDOW_DAYS = 7L

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "上课提醒",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "按节次时间提前提醒上课"
        }
        manager.createNotificationChannel(channel)
    }

    /** 基于当前课表与偏好重新调度活动课表未来 7 天的提醒（覆盖式，可重复调用）。 */
    fun rescheduleAll(context: Context, tables: List<TableData>, preferences: AppPreferences) {
        ensureChannel(context)
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val activeIndex = preferences.activeTableIndex.coerceIn(tables.indices)
        val table = tables.getOrNull(activeIndex) ?: return
        if (!preferences.reminderEnabled) return

        val now = LocalDateTime.now()
        val nowMillis = System.currentTimeMillis()
        val weeks = (0 until SCHEDULE_WINDOW_DAYS).mapNotNull { dayOffset ->
            TodayAgendaCalculator.semesterWeek(
                table.semesterStart,
                table.totalWeeks,
                now.toLocalDate().plusDays(dayOffset)
            )
        }.toSet()

        weeks.forEach { week ->
            ReminderCalculator.courseInstances(
                courses = table.courses,
                semesterStart = table.semesterStart,
                periods = table.periods,
                week = week,
                excludedWeeks = table.excludedWeeks.toSet()
            ).forEach { instance ->
                val triggerMillis = ReminderCalculator.reminderAt(instance, preferences.reminderMinutes)
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                if (triggerMillis > nowMillis) {
                    scheduleOne(context, alarmManager, activeIndex, instance, triggerMillis)
                }
            }
        }
    }

    private fun scheduleOne(
        context: Context,
        alarmManager: AlarmManager,
        tableIndex: Int,
        instance: CourseInstance,
        triggerMillis: Long
    ) {
        val intent = Intent(context, CourseReminderReceiver::class.java).apply {
            putExtra(EXTRA_TABLE_INDEX, tableIndex)
            putExtra(EXTRA_SERIES_KEY, instance.course.seriesKey)
            putExtra(EXTRA_WEEK, instance.week)
            putExtra(EXTRA_TITLE, instance.course.courseName)
            putExtra(
                EXTRA_INFO,
                listOfNotNull(
                    instance.course.teacher.takeIf { it.isNotBlank() },
                    instance.course.classroom.takeIf { it.isNotBlank() }
                ).joinToString(" · ")
            )
            putExtra(EXTRA_START_MINUTE, instance.startMinute)
        }
        // 同表同课程同周同天的提醒用同一请求码，重新调度时直接覆盖旧闹钟。
        val requestCode = "$tableIndex|${instance.course.seriesKey}|${instance.week}|${instance.course.dayOfWeek}".hashCode()
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pending)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pending)
        }
    }

    /** 打开应用的通知：点击回到主界面。 */
    fun buildOpenAppIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
