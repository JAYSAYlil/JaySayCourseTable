package com.jaysay.coursetable.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.jaysay.coursetable.MainActivity
import com.jaysay.coursetable.R
import com.jaysay.coursetable.data.model.TodayAgenda
import com.jaysay.coursetable.data.model.TodayAgendaCalculator
import com.jaysay.coursetable.data.model.TodayAgendaPhase
import com.jaysay.coursetable.data.preferences.AppPreferences
import com.jaysay.coursetable.data.preferences.PreferencesManager
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

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        cancelScheduledRefresh(context)
    }

    private suspend fun updateWidgets(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        if (appWidgetIds.isEmpty()) return
        val preferences = runCatching { PreferencesManager(context).load() }.getOrDefault(AppPreferences())
        val tables = runCatching { CourseRepository(context).loadAllTables() }.getOrNull()
        val table = tables?.getOrNull(preferences.activeTableIndex.coerceIn(tables.indices))

        val views = RemoteViews(context.packageName, R.layout.widget_course)
        var agenda: TodayAgenda? = null
        if (table == null) {
            views.setTextViewText(R.id.widget_table_name, "JaySay 课表")
            views.setTextViewText(R.id.widget_summary, "暂无课表数据")
            views.setTextViewText(R.id.widget_detail, "点击打开应用")
        } else {
            val nowMinute = Calendar.getInstance().let {
                it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE)
            }
            agenda = TodayAgendaCalculator.calculate(
                courses = table.courses,
                periods = table.periods,
                semesterStart = table.semesterStart,
                totalWeeks = table.totalWeeks,
                date = LocalDate.now(),
                minuteOfDay = nowMinute,
                excludedWeeks = table.excludedWeeks.toSet()
            )
            views.setTextViewText(R.id.widget_table_name, table.name)
            views.setTextViewText(R.id.widget_summary, agenda.widgetSummary())
            views.setTextViewText(R.id.widget_detail, agenda.widgetDetail())
        }
        views.setOnClickPendingIntent(
            R.id.widget_root,
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        appWidgetManager.updateAppWidget(appWidgetIds, views)
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
