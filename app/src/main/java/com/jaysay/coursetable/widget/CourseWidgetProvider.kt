package com.jaysay.coursetable.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.jaysay.coursetable.MainActivity
import com.jaysay.coursetable.R
import com.jaysay.coursetable.data.model.ScheduleDateResolver
import com.jaysay.coursetable.data.model.TodayAgenda
import com.jaysay.coursetable.data.model.TodayAgendaCalculator
import com.jaysay.coursetable.data.model.TodayAgendaPhase
import com.jaysay.coursetable.data.preferences.AppPreferences
import com.jaysay.coursetable.data.preferences.PreferencesManager
import com.jaysay.coursetable.data.repository.CourseRepository
import com.jaysay.coursetable.data.repository.TableData
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
        val todaySchedule = active?.let { WidgetScheduleBuilder.build(it.table, it.tableIndex, today) }
        val tomorrowSchedule = active?.let { WidgetScheduleBuilder.build(it.table, it.tableIndex, tomorrow) }
        val agenda = active?.let { item ->
            val nowMinute = Calendar.getInstance().let {
                it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE)
            }
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
            views.setTextViewText(R.id.widget_weekday, "星期${TimeUtils.getDayName(today.dayOfWeek.value).removePrefix("周")}")
            views.setContentDescription(
                R.id.widget_header,
                "${today.monthValue}月${today.dayOfMonth}日，星期${TimeUtils.getDayName(today.dayOfWeek.value).removePrefix("周")}"
            )
            views.setTextViewText(
                R.id.widget_today_title,
                sectionTitle("今日课程", today, todaySchedule?.courses?.size ?: 0, widthMode)
            )
            views.setTextViewText(
                R.id.widget_tomorrow_title,
                sectionTitle("明日课程", tomorrow, tomorrowSchedule?.courses?.size ?: 0, widthMode)
            )
            views.setTextViewText(
                R.id.widget_today_empty,
                if (active == null) "暂无课表数据\n点击打开应用" else "今日无课"
            )
            views.setTextViewText(R.id.widget_tomorrow_empty, "明日无课")

            val showTomorrow = widthMode != WidgetWidthMode.COMPACT
            views.setViewVisibility(R.id.widget_tomorrow_column, if (showTomorrow) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.widget_column_divider, if (showTomorrow) View.VISIBLE else View.GONE)
            bindCourseList(context, views, widgetId, R.id.widget_today_list, R.id.widget_today_empty, 0, widthMode)
            if (showTomorrow) {
                bindCourseList(
                    context,
                    views,
                    widgetId,
                    R.id.widget_tomorrow_list,
                    R.id.widget_tomorrow_empty,
                    1,
                    widthMode
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
            appWidgetManager.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_today_list)
            if (showTomorrow) {
                appWidgetManager.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_tomorrow_list)
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
        widthMode: WidgetWidthMode
    ) {
        val adapterIntent = Intent(context, CourseWidgetRemoteViewsService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            putExtra(EXTRA_DAY_OFFSET, dayOffset)
            putExtra(EXTRA_WIDTH_MODE, widthMode.name)
            data = Uri.parse("jaysay://widget/$widgetId/day/$dayOffset/${widthMode.name}")
        }
        views.setRemoteAdapter(listId, adapterIntent)
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

    private fun sectionTitle(
        prefix: String,
        date: LocalDate,
        courseCount: Int,
        widthMode: WidgetWidthMode
    ): String = when (widthMode) {
        WidgetWidthMode.COMPACT -> "$prefix · $courseCount 节"
        WidgetWidthMode.MEDIUM -> "${prefix.removeSuffix("课程")} · $courseCount 节"
        WidgetWidthMode.EXPANDED -> "${prefix.removeSuffix("课程")} ${date.monthValue}/${date.dayOfMonth} · $courseCount 节"
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

internal enum class WidgetWidthMode(val referenceWidthDp: Int) {
    COMPACT(180),
    MEDIUM(250),
    EXPANDED(320);

    companion object {
        fun fromMinWidth(widthDp: Int): WidgetWidthMode = when {
            widthDp < 220 -> COMPACT
            widthDp < 270 -> MEDIUM
            else -> EXPANDED
        }
    }
}

internal data class WidgetActiveTable(val tableIndex: Int, val table: TableData)

internal data class WidgetCourseRow(
    val tableIndex: Int,
    val seriesKey: String,
    val courseName: String,
    val classroom: String,
    val teacher: String,
    val timeLabel: String
)

internal data class WidgetDaySchedule(val date: LocalDate, val courses: List<WidgetCourseRow>)

internal object WidgetScheduleLoader {
    suspend fun loadActive(context: Context): WidgetActiveTable? {
        val preferences = runCatching { PreferencesManager(context).load() }.getOrDefault(AppPreferences())
        val tables = runCatching { CourseRepository(context).loadAllTables() }.getOrNull().orEmpty()
        if (tables.isEmpty()) return null
        val preferredIndex = preferences.activeTableIndex.coerceIn(tables.indices)
        val activeIndex = preferredIndex.takeIf { !tables[it].archived }
            ?: tables.indexOfFirst { !it.archived }.takeIf { it >= 0 }
            ?: return null
        return WidgetActiveTable(activeIndex, tables[activeIndex])
    }
}

internal object WidgetScheduleBuilder {
    fun build(table: TableData, tableIndex: Int, date: LocalDate): WidgetDaySchedule {
        val courses = ScheduleDateResolver.coursesOn(
            courses = table.courses,
            semesterStart = table.semesterStart,
            totalWeeks = table.totalWeeks,
            excludedWeeks = table.excludedWeeks.toSet(),
            exceptions = table.dateExceptions,
            date = date
        ).map { resolved ->
            val course = resolved.course
            val start = table.periods.getOrNull(course.startPeriod - 1)?.start
            val end = table.periods.getOrNull(course.endPeriod - 1)?.end
            WidgetCourseRow(
                tableIndex = tableIndex,
                seriesKey = course.seriesKey,
                courseName = course.courseName.trim().ifEmpty { "未命名课程" },
                classroom = course.classroom.trim().ifEmpty { "未填写教室" },
                teacher = course.teacher.trim().ifEmpty { "未填写教师" },
                timeLabel = if (!start.isNullOrBlank() && !end.isNullOrBlank()) {
                    "$start–$end"
                } else {
                    TimeUtils.formatPeriodRange(course.startPeriod, course.endPeriod)
                }
            )
        }
        return WidgetDaySchedule(date, courses)
    }
}
