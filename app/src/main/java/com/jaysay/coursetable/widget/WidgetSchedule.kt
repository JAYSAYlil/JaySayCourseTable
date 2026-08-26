package com.jaysay.coursetable.widget

import android.content.Context
import com.jaysay.coursetable.data.model.AcademicCalendarDayStatus
import com.jaysay.coursetable.data.model.AcademicCalendarStatusResolver
import com.jaysay.coursetable.data.model.ScheduleDateResolver
import com.jaysay.coursetable.data.preferences.AppPreferences
import com.jaysay.coursetable.data.preferences.PreferencesManager
import com.jaysay.coursetable.data.repository.CourseRepository
import com.jaysay.coursetable.data.repository.TableData
import com.jaysay.coursetable.util.TimeUtils
import java.time.LocalDate

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
) {
    fun stableId(date: LocalDate): Long =
        "$date|$seriesKey|$timeLabel".hashCode().toLong()
}

internal data class WidgetDaySchedule(
    val date: LocalDate,
    val courses: List<WidgetCourseRow>,
    val calendarStatus: AcademicCalendarDayStatus
)

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
    fun build(
        table: TableData,
        tableIndex: Int,
        date: LocalDate,
        afterMinute: Int? = null
    ): WidgetDaySchedule {
        val calendarStatus = AcademicCalendarStatusResolver.day(
            date = date,
            semesterStart = table.semesterStart,
            totalWeeks = table.totalWeeks,
            excludedWeeks = table.excludedWeeks.toSet(),
            exceptions = table.dateExceptions,
            weekLabels = table.weekLabels
        )
        val courses = ScheduleDateResolver.coursesOn(
            courses = table.courses,
            semesterStart = table.semesterStart,
            totalWeeks = table.totalWeeks,
            excludedWeeks = table.excludedWeeks.toSet(),
            exceptions = table.dateExceptions,
            date = date
        ).filter { resolved ->
            if (afterMinute == null) true else {
                val endMinute = table.periods.getOrNull(resolved.course.endPeriod - 1)
                    ?.end
                    ?.let(TimeUtils::parseMinuteOfDay)
                endMinute == null || endMinute > afterMinute
            }
        }.map { resolved ->
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
        return WidgetDaySchedule(date, courses, calendarStatus)
    }
}

/** 小组件的校历文案集中生成，保证三档宽度表达同一状态。 */
internal object WidgetCalendarPresentation {
    fun headerBadge(defaultWeekday: String, schedule: WidgetDaySchedule?): String {
        val status = schedule?.calendarStatus ?: return defaultWeekday
        val suffix = status.weekLabel ?: status.week?.let { "第${it}周" }
        return suffix?.let { "$defaultWeekday · ${it.take(8)}" } ?: defaultWeekday
    }

    fun sectionTitle(
        prefix: String,
        date: LocalDate,
        courseCount: Int,
        widthMode: WidgetWidthMode,
        schedule: WidgetDaySchedule?
    ): String {
        val status = schedule?.calendarStatus
        val shortPrefix = prefix.removeSuffix("课程")
        val special = when {
            status?.suspendedWeek == true -> "停课周"
            status?.dayOff == true -> status.dayOffTitle ?: "整日停课"
            status != null && status.dateAdjustmentCount > 0 -> "${status.dateAdjustmentCount}项调整"
            else -> null
        }
        if (special != null) return "$shortPrefix · ${special.take(10)}"
        return when (widthMode) {
            WidgetWidthMode.COMPACT -> "$prefix · $courseCount 节"
            WidgetWidthMode.MEDIUM -> "$shortPrefix · $courseCount 节"
            WidgetWidthMode.EXPANDED -> "$shortPrefix ${date.monthValue}/${date.dayOfMonth} · $courseCount 节"
        }
    }

    fun emptyText(defaultText: String, schedule: WidgetDaySchedule?): String {
        val status = schedule?.calendarStatus ?: return defaultText
        return when {
            status.suspendedWeek -> "停课周\n本日无课程"
            status.dayOff -> "${status.dayOffTitle ?: "整日停课"}\n本日无课程"
            status.cancelledCount > 0 -> "课程已调整\n本日无安排"
            else -> defaultText
        }
    }
}
