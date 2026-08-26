package com.jaysay.coursetable.widget

import android.content.Context
import android.content.Intent
import android.util.TypedValue
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.jaysay.coursetable.R
import com.jaysay.coursetable.data.reminder.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.LocalTime

/** 为小组件的今日/明日课程列表提供可滚动条目。 */
class CourseWidgetRemoteViewsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        CourseWidgetRemoteViewsFactory(applicationContext, intent)
}

private class CourseWidgetRemoteViewsFactory(
    private val context: Context,
    intent: Intent
) : RemoteViewsService.RemoteViewsFactory {
    private val dayOffset = intent.getIntExtra(CourseWidgetProvider.EXTRA_DAY_OFFSET, 0).coerceIn(0, 1)
    private val widthMode = runCatching {
        WidgetWidthMode.valueOf(intent.getStringExtra(CourseWidgetProvider.EXTRA_WIDTH_MODE).orEmpty())
    }.getOrDefault(WidgetWidthMode.COMPACT)
    private var date: LocalDate = LocalDate.now().plusDays(dayOffset.toLong())
    private var rows: List<WidgetCourseRow> = emptyList()

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        date = LocalDate.now().plusDays(dayOffset.toLong())
        rows = runBlocking(Dispatchers.IO) {
            WidgetScheduleLoader.loadActive(context)?.let { active ->
                val afterMinute = if (dayOffset == 0) {
                    LocalTime.now().let { it.hour * 60 + it.minute }
                } else null
                WidgetScheduleBuilder.build(
                    active.table,
                    active.tableIndex,
                    date,
                    afterMinute = afterMinute
                ).courses
            }.orEmpty()
        }
    }

    override fun onDestroy() {
        rows = emptyList()
    }

    override fun getCount(): Int = rows.size

    override fun getViewAt(position: Int): RemoteViews? {
        val row = rows.getOrNull(position) ?: return null
        return WidgetCourseItemViews.create(context, row, widthMode)
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long =
        rows.getOrNull(position)?.stableId(date) ?: position.toLong()

    override fun hasStableIds(): Boolean = true
}

/** 新旧小组件集合实现共用同一条目布局，避免不同 Android 版本显示分叉。 */
internal object WidgetCourseItemViews {
    fun create(context: Context, row: WidgetCourseRow, widthMode: WidgetWidthMode): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_course_item).apply {
            setTextViewText(R.id.widget_item_time, row.timeLabel)
            setTextViewText(R.id.widget_item_course_name, row.courseName)
            setTextViewText(R.id.widget_item_classroom, "教室 · ${row.classroom}")
            setTextViewText(R.id.widget_item_teacher, "教师 · ${row.teacher}")
            setContentDescription(
                R.id.widget_item_root,
                "${row.timeLabel}，${row.courseName}，教室 ${row.classroom}，教师 ${row.teacher}"
            )
            val courseTextSize = when (widthMode) {
                WidgetWidthMode.COMPACT -> 14f
                WidgetWidthMode.MEDIUM -> 12f
                WidgetWidthMode.EXPANDED -> 13f
            }
            val detailTextSize = if (widthMode == WidgetWidthMode.MEDIUM) 11f else 12f
            setTextViewTextSize(R.id.widget_item_course_name, TypedValue.COMPLEX_UNIT_SP, courseTextSize)
            setTextViewTextSize(R.id.widget_item_classroom, TypedValue.COMPLEX_UNIT_SP, detailTextSize)
            setTextViewTextSize(R.id.widget_item_teacher, TypedValue.COMPLEX_UNIT_SP, detailTextSize)
            setOnClickFillInIntent(
                R.id.widget_item_root,
                Intent().apply {
                    putExtra(ReminderScheduler.EXTRA_TABLE_INDEX, row.tableIndex)
                    putExtra(ReminderScheduler.EXTRA_SERIES_KEY, row.seriesKey)
                }
            )
        }
}
