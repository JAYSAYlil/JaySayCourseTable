package com.jaysay.coursetable.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.jaysay.coursetable.MainActivity
import com.jaysay.coursetable.R
import com.jaysay.coursetable.data.model.TodayAgenda
import com.jaysay.coursetable.data.model.TodayAgendaCalculator
import com.jaysay.coursetable.data.model.TodayAgendaPhase
import com.jaysay.coursetable.data.preferences.AppPreferences
import com.jaysay.coursetable.data.preferences.PreferencesManager
import com.jaysay.coursetable.data.reminder.ReminderCalculator
import com.jaysay.coursetable.data.reminder.ReminderScheduler
import com.jaysay.coursetable.data.repository.CourseRepository
import com.jaysay.coursetable.util.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Calendar

/**
 * 桌面小组件：显示活动课表名 + 今日/下一节课摘要。
 * 数据直接读应用私有文件（与主应用共用 CourseRepository/PreferencesManager），
 * 点击打开主界面；周期更新 + 应用内数据变化时通过 [ACTION_UPDATE] 定向刷新。
 */
class CourseWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                updateWidgets(context, appWidgetManager, appWidgetIds)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action in REFRESH_ACTIONS) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, CourseWidgetProvider::class.java))
            onUpdate(context, manager, ids)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        onUpdate(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        cancelScheduledRefresh(context)
    }

    private suspend fun updateWidgets(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        if (appWidgetIds.isEmpty()) return
        val preferences = runCatching { PreferencesManager(context).load() }.getOrDefault(AppPreferences())
        val tables = runCatching { CourseRepository(context).loadAllTables() }.getOrNull()
        val preferredIndex = tables?.let { preferences.activeTableIndex.coerceIn(it.indices) } ?: -1
        val activeIndex = tables?.let { list ->
            preferredIndex.takeIf { it in list.indices && !list[it].archived }
                ?: list.indexOfFirst { !it.archived }
        } ?: -1
        val table = tables?.getOrNull(activeIndex)

        var agenda: TodayAgenda? = null
        var tableName = "JaySay 课表"
        var summary = "暂无课表数据"
        var detail = "点击打开应用"
        var targetSeries: String? = null
        if (table == null) {
            // 保留默认占位文案。
        } else {
            val nowMinute = Calendar.getInstance().let {
                it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE)
            }
            val todayAgenda = TodayAgendaCalculator.calculate(
                courses = table.courses,
                periods = table.periods,
                semesterStart = table.semesterStart,
                totalWeeks = table.totalWeeks,
                date = LocalDate.now(),
                minuteOfDay = nowMinute,
                excludedWeeks = table.excludedWeeks.toSet(),
                exceptions = table.dateExceptions
            )
            agenda = todayAgenda
            val future = if (todayAgenda.current == null && todayAgenda.next == null) {
                ReminderCalculator.upcomingInstances(
                    courses = table.courses,
                    semesterStart = table.semesterStart,
                    totalWeeks = table.totalWeeks,
                    periods = table.periods,
                    fromDate = LocalDate.now().plusDays(1),
                    days = 31,
                    excludedWeeks = table.excludedWeeks.toSet(),
                    exceptions = table.dateExceptions
                ).firstOrNull()
            } else null
            tableName = table.name
            summary = future?.let {
                "下次 · ${it.course.courseName} ${it.date.monthValue}月${it.date.dayOfMonth}日 ${it.startMinute.asTime()}"
            } ?: todayAgenda.widgetSummary()
            detail = if (preferences.widgetHideDetails) "详情已隐藏 · 点击打开应用"
            else future?.let { instance ->
                listOfNotNull(
                    instance.course.classroom.takeIf(String::isNotBlank),
                    instance.course.teacher.takeIf(String::isNotBlank)
                ).joinToString(" · ").ifEmpty { "点击查看详情" }
            } ?: todayAgenda.widgetDetail()
            targetSeries = (todayAgenda.current ?: todayAgenda.next)?.course?.seriesKey ?: future?.course?.seriesKey
        }
        appWidgetIds.forEach { widgetId ->
            val views = RemoteViews(context.packageName, R.layout.widget_course)
            views.setTextViewText(R.id.widget_table_name, tableName)
            views.setTextViewText(R.id.widget_summary, summary)
            views.setTextViewText(R.id.widget_detail, detail)
            val minHeight = appWidgetManager.getAppWidgetOptions(widgetId)
                .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 72)
            views.setViewVisibility(
                R.id.widget_detail,
                if (minHeight < 76) View.GONE else View.VISIBLE
            )
            val openIntent = Intent(context, MainActivity::class.java).apply {
                targetSeries?.let {
                    putExtra(ReminderScheduler.EXTRA_TABLE_INDEX, activeIndex)
                    putExtra(ReminderScheduler.EXTRA_SERIES_KEY, it)
                }
            }
            views.setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(
                    context,
                    widgetId,
                    openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            appWidgetManager.updateAppWidget(widgetId, views)
        }
        scheduleNextRefresh(context, agenda)
    }

    companion object {
        const val ACTION_UPDATE = "com.jaysay.coursetable.action.WIDGET_UPDATE"
        private const val REFRESH_REQUEST_CODE = 28_001
        private val REFRESH_ACTIONS = setOf(
            ACTION_UPDATE,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED
        )

        /** 供主应用在数据变化后定向刷新小组件。 */
        fun requestUpdate(context: Context) {
            val intent = Intent(context, CourseWidgetProvider::class.java).setAction(ACTION_UPDATE)
            context.sendBroadcast(intent)
        }

        /** 在下一次上课、下课或跨日边界刷新，避免只依赖系统半小时轮询。 */
        private fun scheduleNextRefresh(context: Context, agenda: TodayAgenda?) {
            val now = LocalDateTime.now()
            val transitionMinute = when (agenda?.phase) {
                TodayAgendaPhase.BEFORE_FIRST, TodayAgendaPhase.BETWEEN_CLASSES -> agenda.next?.startMinute
                TodayAgendaPhase.IN_CLASS -> agenda.current?.endMinute
                else -> null
            }
            val target = transitionMinute?.let {
                now.toLocalDate().atStartOfDay().plusMinutes(it.toLong()).plusSeconds(2)
            }?.takeIf { it.isAfter(now) }
                ?: now.toLocalDate().plusDays(1).atStartOfDay().plusSeconds(2)
            val pending = refreshPendingIntent(context, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
            context.getSystemService(AlarmManager::class.java).apply {
                cancel(pending)
                setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    target.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    pending
                )
            }
        }

        private fun cancelScheduledRefresh(context: Context) {
            val pending = refreshPendingIntent(context, PendingIntent.FLAG_NO_CREATE) ?: return
            context.getSystemService(AlarmManager::class.java).cancel(pending)
            pending.cancel()
        }

        private fun refreshPendingIntent(context: Context, flags: Int): PendingIntent? =
            PendingIntent.getBroadcast(
                context,
                REFRESH_REQUEST_CODE,
                Intent(context, CourseWidgetProvider::class.java).setAction(ACTION_UPDATE),
                flags or PendingIntent.FLAG_IMMUTABLE
            )
    }
}

private fun TodayAgenda.widgetSummary(): String = when (phase) {
    TodayAgendaPhase.OUTSIDE_SEMESTER -> "当前不在学期周次"
    TodayAgendaPhase.NO_COURSES -> "今天没有课程"
    TodayAgendaPhase.BEFORE_FIRST -> "下一节 · ${next?.course?.courseName.orEmpty()} ${next?.startMinute?.asTime()}"
    TodayAgendaPhase.IN_CLASS -> "正在上 · ${current?.course?.courseName.orEmpty()} 至 ${current?.endMinute?.asTime()}"
    TodayAgendaPhase.BETWEEN_CLASSES -> "课间 · 下一节 ${next?.course?.courseName.orEmpty()} ${next?.startMinute?.asTime()}"
    TodayAgendaPhase.FINISHED -> "今日课程已结束"
    TodayAgendaPhase.INVALID_TIME -> "节次时间异常，请在设置中检查"
}

private fun TodayAgenda.widgetDetail(): String {
    val slot = current ?: next
    if (slot == null) return "点击打开应用"
    return listOfNotNull(
        slot.course.classroom.takeIf { it.isNotBlank() },
        slot.course.teacher.takeIf { it.isNotBlank() }
    ).joinToString(" · ").ifEmpty { "点击查看详情" }
}

private fun Int?.asTime(): String = this?.let(TimeUtils::formatMinuteOfDay).orEmpty()
