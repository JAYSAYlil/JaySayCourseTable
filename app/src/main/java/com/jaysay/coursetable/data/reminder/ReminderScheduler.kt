package com.jaysay.coursetable.data.reminder

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.edit
import com.jaysay.coursetable.MainActivity
import com.jaysay.coursetable.data.model.TodayAgendaCalculator
import com.jaysay.coursetable.data.preferences.AppPreferences
import com.jaysay.coursetable.data.repository.TableData
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

enum class ReminderEventKind { START, END }

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
    const val EXTRA_DAY_OF_WEEK = "extra_day_of_week"
    const val EXTRA_DATE = "extra_date"
    const val EXTRA_TITLE = "extra_title"
    const val EXTRA_INFO = "extra_info"
    const val EXTRA_START_MINUTE = "extra_start_minute"
    const val EXTRA_END_MINUTE = "extra_end_minute"
    const val EXTRA_EVENT_KIND = "extra_event_kind"

    /** 覆盖今天至下周同一天，保证每周一次的课程也能接续下一条提醒。 */
    private const val SCHEDULE_WINDOW_DAYS = 8L

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
        scheduleWindow(context, tables, preferences, replaceExisting = true)
        // 应用打开时顺带清理已过期的暂停状态（昨天/上周的暂停不再拦截）。
        val activeIndex = preferences.activeTableIndex.coerceIn(tables.indices)
        tables.getOrNull(activeIndex)?.let { table ->
            val currentWeek = TodayAgendaCalculator.semesterWeek(
                table.semesterStart, table.totalWeeks, LocalDate.now()
            )
            ReminderSuppression.pruneExpired(context, activeIndex, LocalDate.now(), currentWeek ?: -1)
        }
    }

    /**
     * 某条提醒触发后只向后补齐调度窗口，不取消同一时刻尚未送达的其他提醒。
     * 这样冲突课程或系统批量派发广播时不会因互相覆盖而漏通知。
     */
    fun extendWindow(context: Context, tables: List<TableData>, preferences: AppPreferences) {
        scheduleWindow(context, tables, preferences, replaceExisting = false)
    }

    @SuppressLint("ApplySharedPref")
    private fun scheduleWindow(
        context: Context,
        tables: List<TableData>,
        preferences: AppPreferences,
        replaceExisting: Boolean
    ) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        if (replaceExisting) cancelRegistered(context, alarmManager)
        if (tables.isEmpty()) return

        val activeIndex = preferences.activeTableIndex.coerceIn(tables.indices)
        val table = tables.getOrNull(activeIndex) ?: return
        if (table.archived) return
        ensureChannel(context)

        val now = LocalDateTime.now()
        val nowMillis = System.currentTimeMillis()
        val plans = ReminderCalculator.upcomingInstances(
            courses = table.courses,
            semesterStart = table.semesterStart,
            totalWeeks = table.totalWeeks,
            periods = table.periods,
            fromDate = now.toLocalDate(),
            days = SCHEDULE_WINDOW_DAYS,
            excludedWeeks = table.excludedWeeks.toSet(),
            exceptions = table.dateExceptions
        ).filter { instance ->
            ReminderPolicy.isEnabled(instance.course, preferences) &&
                !ReminderSuppression.isSuppressed(context, activeIndex, instance.week, instance.date)
        }.flatMap { instance ->
            buildList {
                val startMillis = ReminderCalculator.reminderAt(
                    instance,
                    ReminderPolicy.advanceMinutes(instance.course, preferences)
                ).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                if (startMillis > nowMillis) {
                    add(
                        ScheduledAlarm(
                            activeIndex, instance, ReminderEventKind.START, startMillis,
                            requestCode(activeIndex, instance, ReminderEventKind.START)
                        )
                    )
                }
                if (instance.course.endReminderEnabled) {
                    val endMillis = instance.date.atTime(instance.endMinute / 60, instance.endMinute % 60)
                        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    if (endMillis > nowMillis) {
                        add(
                            ScheduledAlarm(
                                activeIndex, instance, ReminderEventKind.END, endMillis,
                                requestCode(activeIndex, instance, ReminderEventKind.END)
                            )
                        )
                    }
                }
            }
        }

        // 先登记再调度；即使进程在中途终止，下次仍能取消已经提交给系统的部分闹钟。
        val registeredCodes = if (replaceExisting) {
            emptySet()
        } else {
            registry(context).getStringSet(REGISTRY_CODES, emptySet()).orEmpty()
        }
        registry(context).edit(commit = true) {
            putStringSet(
                REGISTRY_CODES,
                registeredCodes + plans.map { it.requestCode.toString() }
            )
        }
        plans.forEach { plan ->
            scheduleOne(context, alarmManager, plan)
        }
    }

    private fun scheduleOne(
        context: Context,
        alarmManager: AlarmManager,
        plan: ScheduledAlarm
    ) {
        val instance = plan.instance
        val intent = Intent(context, CourseReminderReceiver::class.java).apply {
            putExtra(EXTRA_TABLE_INDEX, plan.tableIndex)
            putExtra(EXTRA_SERIES_KEY, instance.course.seriesKey)
            putExtra(EXTRA_WEEK, instance.week)
            putExtra(EXTRA_DAY_OF_WEEK, instance.course.dayOfWeek)
            putExtra(EXTRA_DATE, instance.date.toString())
            putExtra(EXTRA_TITLE, instance.course.courseName)
            putExtra(
                EXTRA_INFO,
                listOfNotNull(
                    instance.course.teacher.takeIf { it.isNotBlank() },
                    instance.course.classroom.takeIf { it.isNotBlank() }
                ).joinToString(" · ")
            )
            putExtra(EXTRA_START_MINUTE, instance.startMinute)
            putExtra(EXTRA_END_MINUTE, instance.endMinute)
            putExtra(EXTRA_EVENT_KIND, plan.kind.name)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            plan.requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            scheduleWithBestEffort(context, alarmManager, plan, pending)
        } catch (_: SecurityException) {
            // 精确闹钟权限可能在检查后被系统撤销；立即降级，提醒功能仍可用。
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, plan.triggerMillis, pending)
        }
    }

    /**
     * 按可靠性从高到低选择调度方式：
     * 1. setAlarmClock：用户可见闹钟（状态栏闹钟图标）。Doze、应用待机、
     *    省电模式乃至多数国产 ROM 的后台清理都对其保持准点触发，
     *    是课表提醒最可靠的 API，且不需要任何精确闹钟权限。
     * 2. setExactAndAllowWhileIdle：持有精确闹钟权限时的精确调度。
     * 3. setAndAllowWhileIdle：无权限时的近似调度兜底。
     */
    private fun scheduleWithBestEffort(
        context: Context,
        alarmManager: AlarmManager,
        plan: ScheduledAlarm,
        pending: PendingIntent
    ) {
        val showIntent = buildOpenAppIntent(context, plan.tableIndex, plan.instance.course.seriesKey)
        // minSdk 26 已高于 setAlarmClock 所需的 API 21；权限在调用前后变化时，
        // 上层 SecurityException 兜底会自动降级为 setAndAllowWhileIdle。
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(plan.triggerMillis, showIntent),
            pending
        )
    }

    @SuppressLint("ApplySharedPref")
    private fun cancelRegistered(context: Context, alarmManager: AlarmManager) {
        val prefs = registry(context)
        prefs.getStringSet(REGISTRY_CODES, emptySet()).orEmpty().forEach { rawCode ->
            val code = rawCode.toIntOrNull() ?: return@forEach
            val pending = PendingIntent.getBroadcast(
                context,
                code,
                Intent(context, CourseReminderReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pending != null) {
                alarmManager.cancel(pending)
                pending.cancel()
            }
        }
        prefs.edit(commit = true) { remove(REGISTRY_CODES) }
    }

    private fun requestCode(tableIndex: Int, instance: CourseInstance, kind: ReminderEventKind): Int =
        "$tableIndex|${instance.course.seriesKey}|${instance.date}|$kind".hashCode()

    private fun registry(context: Context) =
        context.getSharedPreferences(REGISTRY_NAME, Context.MODE_PRIVATE)

    private data class ScheduledAlarm(
        val tableIndex: Int,
        val instance: CourseInstance,
        val kind: ReminderEventKind,
        val triggerMillis: Long,
        val requestCode: Int
    )

    /** 打开应用的通知：点击回到主界面。 */
    fun buildOpenAppIntent(context: Context, tableIndex: Int, seriesKey: String): PendingIntent =
        PendingIntent.getActivity(
            context,
            "$tableIndex|$seriesKey".hashCode(),
            Intent(context, MainActivity::class.java).apply {
                putExtra(EXTRA_TABLE_INDEX, tableIndex)
                putExtra(EXTRA_SERIES_KEY, seriesKey)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private const val REGISTRY_NAME = "course_reminder_alarms"
    private const val REGISTRY_CODES = "request_codes"
}
