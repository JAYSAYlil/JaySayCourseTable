package com.jaysay.coursetable.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jaysay.coursetable.R
import com.jaysay.coursetable.data.model.AcademicCalendarStatusResolver
import com.jaysay.coursetable.data.model.Course
import com.jaysay.coursetable.data.model.ScheduleDateException
import com.jaysay.coursetable.data.model.ScheduleDateResolver
import com.jaysay.coursetable.ui.theme.AppShapes
import com.jaysay.coursetable.ui.theme.courseCardTextColors
import com.jaysay.coursetable.ui.theme.pressScale
import com.jaysay.coursetable.ui.theme.resolveCourseColor
import com.jaysay.coursetable.util.ChineseCalendarUtils
import com.jaysay.coursetable.util.TimeUtils
import java.time.LocalDate

/**
 * 月视图单个日期格的静态快照：
 * 月份、周次、停课状态与课程列表在组合外一次性算好并 remember 缓存，
 * 重组期间不再重复解析日期异常与课程归属。
 */
private data class MonthDayData(
    val date: LocalDate,
    val inMonth: Boolean,
    val semesterWeek: Int?,
    val dayOfWeek: Int,
    val isToday: Boolean,
    val suspended: Boolean,
    val weekLabel: String?,
    val dayOffTitle: String?,
    val lunarText: String,
    val holidayName: String?,
    val cancelledCount: Int,
    val makeupCount: Int,
    val courses: List<Course>
)

/**
 * 月视图网格：展示 [monthStart] 所在自然月的整月日历（翻页由外层 HorizontalPager 驱动）。
 *
 * - 课程解析复用 [ScheduleDateResolver.coursesOn]，与周视图完全同口径
 *   （日期异常/停课周/单双周规则一致），颜色复用 buildCourseColorMap + resolveCourseColor。
 * - 今天：不受学期范围、停课周或放假影响，始终加 primary 描边与淡底高亮。
 * - 停课日（整周停课或当天放假）：日期数字置灰加删除线。
 * - 学期外日期正常显示但无课程、不可点击。
 */
@Composable
fun MonthGrid(
    modifier: Modifier = Modifier,
    courses: List<Course>,
    monthStart: LocalDate,
    totalWeeks: Int,
    semesterStart: String,
    excludedWeekSet: Set<Int>,
    dateExceptions: List<ScheduleDateException>,
    weekLabels: Map<Int, String>,
    dark: Boolean,
    onDayClick: (date: java.time.LocalDate) -> Unit
) {
    val today = remember { LocalDate.now() }
    // 与周视图同源的课程配色映射（含自定义颜色），按课程名去重后一次解析。
    val courseColors = remember(courses, dark) {
        courses.distinctBy { it.courseName }.associate { course ->
            course.courseName to resolveCourseColor(courses, course, dark)
        }
    }
    val cells = remember(
        courses, monthStart, totalWeeks, semesterStart, excludedWeekSet, dateExceptions, weekLabels, today
    ) {
        buildMonthCells(
            courses = courses,
            monthStart = monthStart,
            totalWeeks = totalWeeks,
            semesterStart = semesterStart,
            excludedWeekSet = excludedWeekSet,
            dateExceptions = dateExceptions,
            weekLabels = weekLabels,
            today = today
        )
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // 小屏也要完整看到 5～6 行月份；大屏则把空余高度还给日期格，避免固定高度裁切。
        val headerHeight = 23.dp
        val rowHeight = ((maxHeight - headerHeight) / cells.size.coerceAtLeast(1)).coerceAtMost(106.dp)
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth().height(20.dp).padding(horizontal = 6.dp)) {
                (1..7).forEach { day ->
                    Text(
                        text = TimeUtils.getDayName(day).replace("周", ""),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(3.dp))
            cells.forEach { weekCells ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowHeight)
                        .padding(horizontal = 5.dp, vertical = 1.5.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    weekCells.forEach { cell ->
                        MonthDayCell(
                            cell = cell,
                            modifier = Modifier.weight(1f),
                            courseColors = courseColors,
                            dark = dark,
                            onDayClick = onDayClick
                        )
                    }
                }
            }
        }
    }
}

private fun buildMonthCells(
    courses: List<Course>,
    monthStart: LocalDate,
    totalWeeks: Int,
    semesterStart: String,
    excludedWeekSet: Set<Int>,
    dateExceptions: List<ScheduleDateException>,
    weekLabels: Map<Int, String>,
    today: LocalDate
): List<List<MonthDayData>> {
    // 锚点即调用方给定的自然月；网格从该月 1 日所在周的周一起，按需铺满 5～6 行。
    val anchor = monthStart
    val gridStart = TimeUtils.weekStart(anchor.withDayOfMonth(1))
    val lastDay = anchor.withDayOfMonth(anchor.lengthOfMonth())
    val dayOffset = java.time.temporal.ChronoUnit.DAYS.between(gridStart, lastDay).toInt()
    val rowCount = ((dayOffset + 1 + 6) / 7).coerceIn(5, 6)
    return (0 until rowCount).map { row ->
        (0 until 7).map { col ->
            val date = gridStart.plusDays((row * 7 + col).toLong())
            val calendarLabel = ChineseCalendarUtils.label(date)
            val week = TimeUtils.semesterWeekOrNull(semesterStart, totalWeeks, date)
            val status = AcademicCalendarStatusResolver.day(
                date, semesterStart, totalWeeks, excludedWeekSet, dateExceptions, weekLabels
            )
            MonthDayData(
                date = date,
                inMonth = date.year == anchor.year && date.monthValue == anchor.monthValue,
                semesterWeek = week,
                dayOfWeek = date.dayOfWeek.value,
                isToday = date == today,
                suspended = status.suspendedWeek || status.dayOff,
                weekLabel = status.weekLabel,
                dayOffTitle = status.dayOffTitle,
                lunarText = calendarLabel.lunar,
                holidayName = calendarLabel.holiday,
                cancelledCount = status.cancelledCount,
                makeupCount = status.makeupCount,
                courses = ScheduleDateResolver.coursesOn(
                    courses, semesterStart, totalWeeks, excludedWeekSet, dateExceptions, date
                ).map { it.course }
            )
        }
    }
}

@Composable
private fun MonthDayCell(
    cell: MonthDayData,
    modifier: Modifier = Modifier,
    courseColors: Map<String, Color>,
    dark: Boolean,
    onDayClick: (date: java.time.LocalDate) -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outlineVariant
    val isToday = cell.isToday
    val statusText = when {
        cell.dayOffTitle?.isNotBlank() == true -> cell.dayOffTitle
        cell.holidayName?.isNotBlank() == true -> cell.holidayName
        cell.suspended -> "停课"
        cell.makeupCount > 0 -> "补课 ${cell.makeupCount}"
        cell.cancelledCount > 0 -> "停 ${cell.cancelledCount}"
        cell.weekLabel?.isNotBlank() == true -> cell.weekLabel
        else -> null
    }
    val cellDescription = buildString {
        append(cell.date)
        append("，${cell.lunarText}")
        cell.weekLabel?.let { append("，$it") }
        statusText?.let { append("，$it") }
        if (cell.courses.isNotEmpty()) append("，课程 ${cell.courses.joinToString("、") { it.courseName }}")
        else append("，无课程")
    }
    val clickableModifier = cell.semesterWeek?.let { week ->
        val cellInteraction = androidx.compose.runtime.remember {
            androidx.compose.foundation.interaction.MutableInteractionSource()
        }
        Modifier
            .pressScale(cellInteraction, 0.94f)
            .clickable(interactionSource = cellInteraction, indication = null) {
                onDayClick(cell.date)
            }
    } ?: Modifier
    val cellFill = when {
        isToday -> primary.copy(alpha = if (dark) 0.20f else 0.10f)
        cell.courses.isNotEmpty() -> MaterialTheme.colorScheme.surface.copy(alpha = if (dark) 0.92f else 0.84f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (dark) 0.46f else 0.34f)
    }
    Column(
        modifier = modifier
            .padding(1.dp)
            .fillMaxHeight()
            .clip(AppShapes.small)
            .background(cellFill)
            .border(
                width = if (isToday) 1.25.dp else 0.5.dp,
                color = if (isToday) primary else outline.copy(alpha = 0.28f),
                shape = AppShapes.small
            )
            .then(clickableModifier)
            .semantics { contentDescription = cellDescription }
            .padding(horizontal = 4.dp, vertical = 3.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = cell.date.dayOfMonth.toString(),
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
            color = when {
                isToday -> primary
                cell.suspended -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                !cell.inMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f)
                else -> MaterialTheme.colorScheme.onSurface
            },
            textDecoration = if (cell.suspended) TextDecoration.LineThrough else null,
            maxLines = 1
        )
        Text(
            text = cell.lunarText,
            fontSize = 8.sp,
            lineHeight = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (cell.holidayName != null) primary.copy(alpha = 0.88f)
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (cell.inMonth) 0.76f else 0.38f)
        )
        statusText?.let { text ->
            Text(
                text = text,
                fontSize = 8.sp,
                lineHeight = 9.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = when {
                    cell.suspended || cell.cancelledCount > 0 -> MaterialTheme.colorScheme.error
                    cell.makeupCount > 0 -> primary
                    cell.holidayName != null -> primary
                    else -> MaterialTheme.colorScheme.secondary
                }
            )
        }
        cell.courses.take(2).forEach { course ->
            val color = courseColors[course.courseName] ?: primary
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .padding(end = 2.dp)
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Text(
                    text = course.courseName,
                    fontSize = 8.sp,
                    lineHeight = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = courseCardTextColors(color, dark).first
                )
            }
        }
        if (cell.courses.size > 2) {
            Text(
                text = stringResource(R.string.month_more_courses, cell.courses.size - 2),
                fontSize = 8.sp,
                lineHeight = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
