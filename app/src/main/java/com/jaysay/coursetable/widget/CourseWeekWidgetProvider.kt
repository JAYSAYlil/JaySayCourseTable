package com.jaysay.coursetable.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import com.jaysay.coursetable.MainActivity
import com.jaysay.coursetable.R
import com.jaysay.coursetable.data.model.AcademicCalendarStatusResolver
import com.jaysay.coursetable.data.model.ResolvedDateCourse
import com.jaysay.coursetable.data.model.ScheduleDateResolver
import com.jaysay.coursetable.data.model.TodayAgenda
import com.jaysay.coursetable.data.model.TodayAgendaCalculator
import com.jaysay.coursetable.data.model.TodayAgendaPhase
import com.jaysay.coursetable.util.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** 整周网格桌面小组件：7 列（周一到周日）× 节次行，今天列描边高亮。 */
class CourseWeekWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 刷新路径不允许未预期异常崩溃进程，与两天列表小组件保持一致。
                runCatching { updateWidgets(context, appWidgetManager, appWidgetIds) }
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action in REFRESH_ACTIONS) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, CourseWeekWidgetProvider::class.java))
            onUpdate(context, manager, ids)
        }
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
        val monday = today.with(DayOfWeek.MONDAY)
        val days = (0..6).map { monday.plusDays(it.toLong()) }

        val weekNumber = active?.let { item ->
            AcademicCalendarStatusResolver.day(
                date = today,
                semesterStart = item.table.semesterStart,
                totalWeeks = item.table.totalWeeks,
                excludedWeeks = item.table.excludedWeeks.toSet(),
                exceptions = item.table.dateExceptions,
                weekLabels = item.table.weekLabels
            ).week
        }
        val suspendedWeek = active?.let { item ->
            AcademicCalendarStatusResolver.day(
                date = today,
                semesterStart = item.table.semesterStart,
                totalWeeks = item.table.totalWeeks,
                excludedWeeks = item.table.excludedWeeks.toSet(),
                exceptions = item.table.dateExceptions,
                weekLabels = item.table.weekLabels
            ).suspendedWeek
        } ?: false
        val dayCourses = days.map { date ->
            date to (active?.let { item ->
                ScheduleDateResolver.coursesOn(
                    courses = item.table.courses,
                    semesterStart = item.table.semesterStart,
                    totalWeeks = item.table.totalWeeks,
                    excludedWeeks = item.table.excludedWeeks.toSet(),
                    exceptions = item.table.dateExceptions,
                    date = date
                )
            }.orEmpty())
        }
        // 节次行按“本周实际有课的最小节次区间到最大节次区间”裁剪，行数上限 12。
        val minPeriod = dayCourses.flatMap { it.second }
            .minOfOrNull { it.course.startPeriod }
        var maxPeriod = dayCourses.flatMap { it.second }
            .maxOfOrNull { it.course.endPeriod }
        if (minPeriod != null && maxPeriod != null) {
            maxPeriod = maxPeriod.coerceAtMost(minPeriod + MAX_ROWS - 1)
        }

        val agenda = active?.let { item ->
            runCatching {
                TodayAgendaCalculator.calculate(
                    courses = item.table.courses,
                    periods = item.table.periods,
                    semesterStart = item.table.semesterStart,
                    totalWeeks = item.table.totalWeeks,
                    date = today,
                    minuteOfDay = LocalDateTime.now().let {
                        it.hour * 60 + it.minute
                    },
                    excludedWeeks = item.table.excludedWeeks.toSet(),
                    exceptions = item.table.dateExceptions
                )
            }.getOrNull()
        }

        val dark = isNightModeEnabled(context.resources.configuration)
        val data = WeekGridData(
            today = today,
            tableName = active?.table?.name,
            weekNumber = weekNumber,
            suspendedWeek = suspendedWeek,
            hasTable = active != null,
            dayCourses = dayCourses,
            minPeriod = minPeriod,
            maxPeriod = maxPeriod,
            colorIndex = buildColorIndex(dayCourses)
        )

        appWidgetIds.forEach { widgetId ->
            val views = buildViews(context, dark, data)
            val openApp = PendingIntent.getActivity(
                context,
                widgetId,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_week_header, openApp)
            views.setOnClickPendingIntent(R.id.widget_week_empty, openApp)
            appWidgetManager.updateAppWidget(widgetId, views)
        }
        scheduleNextRefresh(context, agenda)
    }

    private fun buildViews(context: Context, dark: Boolean, data: WeekGridData): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_week)
        val dateText = "${data.today.monthValue}月${data.today.dayOfMonth}日"
        val tableName = WidgetCalendarPresentation.tableNameLabel(data.tableName)
        views.setTextViewText(
            R.id.widget_week_date,
            if (tableName.isEmpty()) dateText else "$dateText · $tableName"
        )
        if (data.weekNumber != null) {
            views.setTextViewText(R.id.widget_week_badge, "第 ${data.weekNumber} 周")
            views.setViewVisibility(R.id.widget_week_badge, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widget_week_badge, View.GONE)
        }

        views.removeAllViews(R.id.widget_week_grid)
        val hasCourses = data.minPeriod != null && data.maxPeriod != null
        if (!data.hasTable) {
            views.setTextViewText(R.id.widget_week_empty, "暂无课表数据\n点击打开应用")
        } else if (!hasCourses) {
            views.setTextViewText(
                R.id.widget_week_empty,
                if (data.suspendedWeek) "停课周\n本周无课程" else "本周无课\n点击打开应用"
            )
        }
        views.setViewVisibility(R.id.widget_week_grid, if (hasCourses) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_week_empty, if (hasCourses) View.GONE else View.VISIBLE)
        if (!hasCourses) return views

        val todayAccent = context.getColor(R.color.widget_accent)
        val headerSecondary = context.getColor(R.color.widget_text_secondary)
        data.dayCourses.forEach { (date, courses) ->
            val isToday = date == data.today
            val stack = RemoteViews(context.packageName, R.layout.widget_week_day_stack)
            val header = RemoteViews(context.packageName, R.layout.widget_week_day_header)
            val weekdayChar = TimeUtils.getDayName(date.dayOfWeek.value)
                .removePrefix("周")
                .take(1)
            header.setTextViewText(
                R.id.widget_week_day_header_text,
                "$weekdayChar\n${date.dayOfMonth}"
            )
            header.setTextColor(
                R.id.widget_week_day_header_text,
                if (isToday) todayAccent else headerSecondary
            )
            stack.addView(R.id.widget_week_day_stack, header)

            for (period in data.minPeriod..data.maxPeriod) {
                stack.addView(R.id.widget_week_day_stack, buildCell(context, dark, data, courses, period))
            }

            if (isToday) {
                val wrapper = RemoteViews(context.packageName, R.layout.widget_week_day_today)
                wrapper.addView(R.id.widget_week_today_frame, stack)
                views.addView(R.id.widget_week_grid, wrapper)
            } else {
                views.addView(R.id.widget_week_grid, stack)
            }
        }
        return views
    }

    private fun buildCell(
        context: Context,
        dark: Boolean,
        data: WeekGridData,
        courses: List<ResolvedDateCourse>,
        period: Int
    ): RemoteViews {
        val cell = RemoteViews(context.packageName, R.layout.widget_week_cell)
        val textId = R.id.widget_week_cell_text
        val resolved = courses.asSequence()
            .filter { it.course.startPeriod <= period && period <= it.course.endPeriod }
            .maxByOrNull { it.course.startPeriod }
        if (resolved == null) {
            cell.setTextViewText(textId, "")
            cell.setInt(textId, "setBackgroundColor", Color.TRANSPARENT)
        } else {
            val name = resolved.course.courseName.trim().ifEmpty { "未命名课程" }
            val (bg, title) = courseCellColors(
                courseName = name,
                customColor = resolved.course.customColor,
                colorIndex = data.colorIndex,
                dark = dark
            )
            cell.setTextViewText(textId, name)
            cell.setInt(textId, "setBackgroundColor", bg)
            cell.setTextColor(textId, title)
        }
        return cell
    }

    companion object {
        const val ACTION_UPDATE = "com.jaysay.coursetable.action.WIDGET_WEEK_UPDATE"
        private const val REFRESH_REQUEST_CODE = 28_101
        private const val MAX_ROWS = 12
        private val REFRESH_ACTIONS = setOf(
            ACTION_UPDATE,
            // 数据变化时 CourseWidgetProvider.requestUpdate 会显式发框架 action，
            // 两条刷新链必须一致，否则整周网格不跟随课表编辑。
            AppWidgetManager.ACTION_APPWIDGET_UPDATE,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED
        )

        /** 在下一次上课、下课或跨日边界刷新，逻辑与 CourseWidgetProvider 一致。 */
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
                Intent(context, CourseWeekWidgetProvider::class.java).setAction(ACTION_UPDATE),
                flags or PendingIntent.FLAG_IMMUTABLE
            )

        private fun isNightModeEnabled(configuration: Configuration): Boolean =
            (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        /** 按“课程名在本周首次出现顺序”建立调色板索引，与网格课表配色语义一致。 */
        private fun buildColorIndex(
            dayCourses: List<Pair<LocalDate, List<ResolvedDateCourse>>>
        ): Map<String, Int> {
            val names = LinkedHashSet<String>()
            dayCourses.forEach { (_, list) -> list.forEach { names.add(it.course.courseName) } }
            return names.withIndex().associate { (index, name) -> name to index }
        }

        private data class WeekGridData(
            val today: LocalDate,
            val tableName: String?,
            val weekNumber: Int?,
            val suspendedWeek: Boolean,
            val hasTable: Boolean,
            val dayCourses: List<Pair<LocalDate, List<ResolvedDateCourse>>>,
            val minPeriod: Int?,
            val maxPeriod: Int?,
            val colorIndex: Map<String, Int>
        )
    }
}

/** 无 Compose 依赖的整周网格颜色工具：调色板/alpha/文字派生均预烘焙为 Int ARGB 常量。 */
private object WeekGridColors {

    // 与 ui/theme/Color.kt 的 CourseColors 一一对应（不透明基色，供文字色派生与索引映射使用）。
    val LIGHT_COURSE_BASE = intArrayOf(
        0xFFE3F0FB.toInt(), 0xFFE2F6E9.toInt(), 0xFFFDF2D2.toInt(), 0xFFFCE7EA.toInt(),
        0xFFE8F2E4.toInt(), 0xFFDCF3F0.toInt(), 0xFFFBEEDF.toInt(), 0xFFECEEF2.toInt(),
        0xFFF0E9E2.toInt(), 0xFFEAF2DC.toInt(), 0xFFDFF1F6.toInt(), 0xFFF6E6EF.toInt(),
        0xFFE4F1EC.toInt(), 0xFFF0EAD9.toInt(), 0xFFE7E9F7.toInt()
    )

    // 与 ui/theme/Color.kt 的 DarkCourseColors 一一对应。
    val DARK_COURSE_BASE = intArrayOf(
        0xFF1E3448.toInt(), 0xFF1D3A2C.toInt(), 0xFF43371A.toInt(), 0xFF46242B.toInt(),
        0xFF263A28.toInt(), 0xFF173B38.toInt(), 0xFF443122.toInt(), 0xFF2C313A.toInt(),
        0xFF3A2F26.toInt(), 0xFF2E3D20.toInt(), 0xFF173A44.toInt(), 0xFF412838.toInt(),
        0xFF1E3830.toInt(), 0xFF3A3620.toInt(), 0xFF2C2F4A.toInt()
    )

    // 单元格背景色：alpha 已按 courseCardBackgroundAlpha 语义烘焙进色值
    // （浅色端 0.82 → 0xD1，深色端 0.96 → 0xF5），RemoteViews 直接 setBackgroundColor 使用。
    val LIGHT_COURSE_CELL = intArrayOf(
        0xD1E3F0FB.toInt(), 0xD1E2F6E9.toInt(), 0xD1FDF2D2.toInt(), 0xD1FCE7EA.toInt(),
        0xD1E8F2E4.toInt(), 0xD1DCF3F0.toInt(), 0xD1FBEEDF.toInt(), 0xD1ECEEF2.toInt(),
        0xD1F0E9E2.toInt(), 0xD1EAF2DC.toInt(), 0xD1DFF1F6.toInt(), 0xD1F6E6EF.toInt(),
        0xD1E4F1EC.toInt(), 0xD1F0EAD9.toInt(), 0xD1E7E9F7.toInt()
    )

    val DARK_COURSE_CELL = intArrayOf(
        0xF51E3448.toInt(), 0xF51D3A2C.toInt(), 0xF543371A.toInt(), 0xF546242B.toInt(),
        0xF5263A28.toInt(), 0xF5173B38.toInt(), 0xF5443122.toInt(), 0xF52C313A.toInt(),
        0xF53A2F26.toInt(), 0xF52E3D20.toInt(), 0xF5173A44.toInt(), 0xF5412838.toInt(),
        0xF51E3830.toInt(), 0xF53A3620.toInt(), 0xF52C2F4A.toInt()
    )

    // 自定义颜色（不在调色板内）按 courseCardBackgroundAlpha 的自定义分支取 alpha：
    // 深色卡片 0.86 → 0xDB，浅色卡片 0.74 → 0xBD。
    private const val CUSTOM_DARK_CARD_ALPHA_BITS = 0xDB shl 24
    private const val CUSTOM_LIGHT_CARD_ALPHA_BITS = 0xBD shl 24

    /** 复刻 ui/theme/Color.kt 的 courseCardTextColors 标题色派生，纯 Int ARGB 无 Compose 依赖。 */
    fun titleColor(cardColor: Int, dark: Boolean): Int {
        val anchor = if (dark) 0xFFFFFFFF.toInt() else 0xFF171A19.toInt()
        val isDarkCard = luminance(cardColor) < 0.35f
        val titleMix = if (isDarkCard) 0.72f else 0.68f
        return lerp(cardColor, anchor, titleMix)
    }

    /** 课程单元格颜色：返回 背景色(已烘焙 alpha) to 标题文字色。 */
    fun cellColors(
        courseName: String,
        customColor: Int?,
        colorIndex: Map<String, Int>,
        dark: Boolean
    ): Pair<Int, Int> {
        val basePalette = if (dark) DARK_COURSE_BASE else LIGHT_COURSE_BASE
        val cellPalette = if (dark) DARK_COURSE_CELL else LIGHT_COURSE_CELL
        val index = customColor?.takeIf { it in basePalette.indices }
            ?: (colorIndex[courseName] ?: 0) % basePalette.size
        val base = if (customColor != null && customColor !in basePalette.indices) {
            // 自定义颜色为原始 ARGB；补齐不透明 alpha 供文字色派生。
            customColor or 0xFF000000.toInt()
        } else {
            basePalette[index]
        }
        val background = if (customColor != null && customColor !in basePalette.indices) {
            val alphaBits = if (luminance(customColor) < 0.35f) {
                CUSTOM_DARK_CARD_ALPHA_BITS
            } else {
                CUSTOM_LIGHT_CARD_ALPHA_BITS
            }
            (customColor and 0x00FFFFFF) or alphaBits
        } else {
            cellPalette[index]
        }
        return background to titleColor(base, dark)
    }

    // 与 Compose Color.luminance 相同的 sRGB 加权求和实现。
    private fun luminance(argb: Int): Float {
        val r = (argb shr 16 and 0xFF) / 255f
        val g = (argb shr 8 and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    // 与 Compose lerp(Color) 相同的逐通道线性插值。
    private fun lerp(start: Int, stop: Int, fraction: Float): Int {
        fun channel(value: Int, shift: Int): Float = (value shr shift and 0xFF) / 255f
        fun mix(a: Float, b: Float): Int = ((a + (b - a) * fraction) * 255f + 0.5f)
            .toInt().coerceIn(0, 255)
        val a = mix(channel(start, 24), channel(stop, 24))
        val r = mix(channel(start, 16), channel(stop, 16))
        val g = mix(channel(start, 8), channel(stop, 8))
        val b = mix(channel(start, 0), channel(stop, 0))
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}

/** 供 [CourseWeekWidgetProvider] 使用的颜色入口（保持 provider 类主体简洁）。 */
private fun courseCellColors(
    courseName: String,
    customColor: Int?,
    colorIndex: Map<String, Int>,
    dark: Boolean
): Pair<Int, Int> = WeekGridColors.cellColors(courseName, customColor, colorIndex, dark)
