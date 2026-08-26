package com.jaysay.coursetable.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import com.jaysay.coursetable.MainActivity
import com.jaysay.coursetable.R
import com.jaysay.coursetable.data.model.TodayAgenda
import com.jaysay.coursetable.data.model.TodayAgendaCalculator
import com.jaysay.coursetable.data.model.TodayAgendaPhase
import com.jaysay.coursetable.util.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Calendar

/** 桌面小组件：两行高，按 3/4/5 列宽自适应显示今日或今日+明日课程。 */
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

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        cancelScheduledRefresh(context)
    }

    private suspend fun updateWidgets(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        if (appWidgetIds.isEmpty()) return
        val active = WidgetScheduleLoader.loadActive(context)
        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)
        val nowMinute = Calendar.getInstance().let {
            it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE)
        }
        val todaySchedule = active?.let {
            WidgetScheduleBuilder.build(it.table, it.tableIndex, today, afterMinute = nowMinute)
        }
        val tomorrowSchedule = active?.let { WidgetScheduleBuilder.build(it.table, it.tableIndex, tomorrow) }
        val agenda = active?.let { item ->
            TodayAgendaCalculator.calculate(
                courses = item.table.courses,
                periods = item.table.periods,
                semesterStart = item.table.semesterStart,
                totalWeeks = item.table.totalWeeks,
                date = today,
                minuteOfDay = nowMinute,
                excludedWeeks = item.table.excludedWeeks.toSet(),
                exceptions = item.table.dateExceptions
            )
        }

        appWidgetIds.forEach { widgetId ->
            val options = appWidgetManager.getAppWidgetOptions(widgetId)
            val widthMode = WidgetWidthMode.fromMinWidth(
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, WidgetWidthMode.COMPACT.referenceWidthDp)
            )
            val views = RemoteViews(context.packageName, R.layout.widget_course)
            views.setTextViewText(R.id.widget_date, "${today.monthValue}月${today.dayOfMonth}日")
            val weekday = "星期${TimeUtils.getDayName(today.dayOfWeek.value).removePrefix("周")}"
            val headerBadge = WidgetCalendarPresentation.headerBadge(weekday, todaySchedule)
            views.setTextViewText(
                R.id.widget_weekday,
                headerBadge
            )
            views.setContentDescription(
                R.id.widget_header,
                "${today.monthValue}月${today.dayOfMonth}日，$headerBadge"
            )
            views.setTextViewText(
                R.id.widget_today_title,
                WidgetCalendarPresentation.sectionTitle(
                    "今日课程", today, todaySchedule?.courses?.size ?: 0, widthMode, todaySchedule
                )
            )
            views.setTextViewText(
                R.id.widget_tomorrow_title,
                WidgetCalendarPresentation.sectionTitle(
                    "明日课程", tomorrow, tomorrowSchedule?.courses?.size ?: 0, widthMode, tomorrowSchedule
                )
            )
            views.setTextViewText(
                R.id.widget_today_empty,
                if (active == null) "暂无课表数据\n点击打开应用"
                else WidgetCalendarPresentation.emptyText("今日无课", todaySchedule)
            )
            views.setTextViewText(
                R.id.widget_tomorrow_empty,
                WidgetCalendarPresentation.emptyText("明日无课", tomorrowSchedule)
            )

            val showTomorrow = widthMode != WidgetWidthMode.COMPACT
            views.setViewVisibility(R.id.widget_tomorrow_column, if (showTomorrow) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.widget_column_divider, if (showTomorrow) View.VISIBLE else View.GONE)
            bindCourseList(
                context, views, widgetId, R.id.widget_today_list, R.id.widget_today_empty,
                dayOffset = 0, widthMode = widthMode, schedule = todaySchedule
            )
            if (showTomorrow) {
                bindCourseList(
                    context,
                    views,
                    widgetId,
                    R.id.widget_tomorrow_list,
                    R.id.widget_tomorrow_empty,
                    1,
                    widthMode,
                    tomorrowSchedule
                )
            }

            val openApp = PendingIntent.getActivity(
                context,
                widgetId,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_header, openApp)
            views.setOnClickPendingIntent(R.id.widget_today_empty, openApp)
            views.setOnClickPendingIntent(R.id.widget_tomorrow_empty, openApp)
            appWidgetManager.updateAppWidget(widgetId, views)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                notifyLegacyCollectionChanged(appWidgetManager, widgetId, R.id.widget_today_list)
                if (showTomorrow) {
                    notifyLegacyCollectionChanged(appWidgetManager, widgetId, R.id.widget_tomorrow_list)
                }
            }
        }
        scheduleNextRefresh(context, agenda)
    }

    private fun bindCourseList(
        context: Context,
        views: RemoteViews,
        widgetId: Int,
        listId: Int,
        emptyId: Int,
        dayOffset: Int,
        widthMode: WidgetWidthMode,
        schedule: WidgetDaySchedule?
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            bindModernCollection(context, views, listId, schedule, widthMode)
        } else {
            bindLegacyCollection(context, views, widgetId, listId, dayOffset, widthMode)
        }
        views.setEmptyView(listId, emptyId)
        val mutableFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val template = PendingIntent.getActivity(
            context,
            widgetId * 10 + dayOffset + 1,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag
        )
        views.setPendingIntentTemplate(listId, template)
    }

    @Suppress("DEPRECATION")
    private fun bindLegacyCollection(
        context: Context,
        views: RemoteViews,
        widgetId: Int,
        listId: Int,
        dayOffset: Int,
        widthMode: WidgetWidthMode
    ) {
        val adapterIntent = Intent(context, CourseWidgetRemoteViewsService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            putExtra(EXTRA_DAY_OFFSET, dayOffset)
            putExtra(EXTRA_WIDTH_MODE, widthMode.name)
            data = "jaysay://widget/$widgetId/day/$dayOffset/${widthMode.name}".toUri()
        }
        views.setRemoteAdapter(listId, adapterIntent)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun bindModernCollection(
        context: Context,
        views: RemoteViews,
        listId: Int,
        schedule: WidgetDaySchedule?,
        widthMode: WidgetWidthMode
    ) {
        val date = schedule?.date ?: LocalDate.now()
        val collection = RemoteViews.RemoteCollectionItems.Builder()
            .setHasStableIds(true)
            .setViewTypeCount(1)
            .apply {
                schedule?.courses.orEmpty().forEach { row ->
                    addItem(row.stableId(date), WidgetCourseItemViews.create(context, row, widthMode))
                }
            }
            .build()
        views.setRemoteAdapter(listId, collection)
    }

    @Suppress("DEPRECATION")
    private fun notifyLegacyCollectionChanged(
        manager: AppWidgetManager,
        widgetId: Int,
        listId: Int
    ) {
        manager.notifyAppWidgetViewDataChanged(widgetId, listId)
    }

    companion object {
        const val ACTION_UPDATE = "com.jaysay.coursetable.action.WIDGET_UPDATE"
        const val EXTRA_DAY_OFFSET = "widget_day_offset"
        const val EXTRA_WIDTH_MODE = "widget_width_mode"
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

        /** 在下一次上课、下课或跨日边界刷新。 */
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
            val alarmManager = context.getSystemService(AlarmManager::class.java)
            alarmManager.cancel(pending)
            val triggerAt = target.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            // 有精确闹钟能力时用精确调度，跨日/上课下课边界刷新更准时；
            // 否则退回非精确调度（系统仍会在 Doze 维护窗口内尽量触发）。
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
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
