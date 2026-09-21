package com.jaysay.coursetable.widget

import android.content.Context
import android.content.Intent
import android.util.TypedValue
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.annotation.LayoutRes
import com.jaysay.coursetable.R
import com.jaysay.coursetable.data.reminder.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.LocalTime

/**
 * 为小组件的今日/明日课程列表提供可滚动条目（Android 11 及以下的集合路径）。
 * 材质变体由 Provider 通过 [CourseWidgetProvider.EXTRA_VARIANT] 明确传入，服务不读全局状态。
 */
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
    private val variant = WidgetVariant.fromTag(intent.getStringExtra(CourseWidgetProvider.EXTRA_VARIANT))
    /** 0 表示 Provider 没传（服务被系统单独拉起）：下面在同一轮 IO 里按偏好补齐，不沿用布局默认色。 */
    private var accentColor = intent.getIntExtra(CourseWidgetProvider.EXTRA_ACCENT, 0)
    private var date: LocalDate = LocalDate.now().plusDays(dayOffset.toLong())
    private var rows: List<WidgetCourseRow> = emptyList()

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        date = LocalDate.now().plusDays(dayOffset.toLong())
        rows = runBlocking(Dispatchers.IO) {
            if (accentColor == 0) accentColor = WidgetAccent.textColorOf(context)
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
        // 0 只可能是强调色尚未换算出来，交给条目沿用布局默认色，避免把文字涂成透明。
        return WidgetCourseItemViews.create(
            context, row, widthMode, variant.itemLayoutRes, accentColor.takeIf { it != 0 }
        )
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long =
        rows.getOrNull(position)?.stableId(date) ?: position.toLong()

    override fun hasStableIds(): Boolean = true
}

/**
 * 新旧小组件集合实现共用同一份条目渲染，避免不同 Android 版本显示分叉；
 * 条目布局由调用方按材质变体给出（实心 / 毛玻璃），渲染内容完全一致。
 */
internal object WidgetCourseItemViews {
    fun create(
        context: Context,
        row: WidgetCourseRow,
        widthMode: WidgetWidthMode,
        @LayoutRes itemLayoutRes: Int,
        accentColor: Int? = null
    ): RemoteViews =
        RemoteViews(context.packageName, itemLayoutRes).apply {
            // 条目时间用主题色；没拿到偏好时保留布局里的默认色。
            accentColor?.let { setTextColor(R.id.widget_item_time, it) }
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
