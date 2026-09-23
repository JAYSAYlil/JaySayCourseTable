package com.jaysay.coursetable.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
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
import com.jaysay.coursetable.ui.theme.pressScale
import com.jaysay.coursetable.util.ChineseCalendarUtils
import com.jaysay.coursetable.util.TimeUtils
import com.jaysay.coursetable.util.rememberToday
import java.time.LocalDate
import androidx.compose.ui.unit.Dp

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
    courseColors: Map<String, Color>,
    monthStart: LocalDate,
    totalWeeks: Int,
    semesterStart: String,
    excludedWeekSet: Set<Int>,
    dateExceptions: List<ScheduleDateException>,
    weekLabels: Map<Int, String>,
    dark: Boolean,
    onDayClick: (date: java.time.LocalDate) -> Unit
) {
    // 生命周期感知的“今天”：跨午夜、回到前台或时区变化后自动校准今日高亮，
    // 不驱动月份锚点或翻页位置。
    val today = rememberToday().value
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
        val density = LocalDensity.current
        val measurer = rememberTextMeasurer()
        val dateHeight = measurer.measure("日期Ag", TextStyle(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold)).size.height.toFloat()
        val detailHeight = measurer.measure("课程Ag", TextStyle(fontSize = 10.sp, lineHeight = 13.sp, fontWeight = FontWeight.Medium)).size.height.toFloat()
        val contentWidth = with(density) { (((maxWidth - 42.dp) / 7) - 8.dp).toPx() }
        val lunarWidth = measurer.measure("廿三", TextStyle(fontSize = 10.sp, lineHeight = 13.sp)).size.width
        val headerHeight = with(density) { detailHeight.toDp() } + 8.dp
        // 大字体时允许竖向滚动，保住日期、特殊安排和课程计数，而不是裁掉最后几行。
        val minimumRow = with(density) { (dateHeight + detailHeight * 2).toDp() } + 16.dp
        val rowHeight = ((maxHeight - headerHeight) / cells.size.coerceAtLeast(1))
            .coerceAtMost(106.dp).coerceAtLeast(minimumRow)
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("month-grid-scroll")) {
            Row(modifier = Modifier.fillMaxWidth().height(headerHeight - 3.dp).padding(horizontal = 6.dp)) {
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
                            rowHeight = rowHeight,
                            dateHeight = dateHeight,
                            detailHeight = detailHeight,
                            lunarFitsWidth = lunarWidth <= contentWidth,
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
    val prepared = ScheduleDateResolver.prepare(
        courses, semesterStart, totalWeeks, excludedWeekSet, dateExceptions
    )
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
                courses = prepared.coursesOn(date).map { it.course }
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
    rowHeight: Dp,
    dateHeight: Float,
    detailHeight: Float,
    lunarFitsWidth: Boolean,
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
    // 月视图空间分配：日期格按高度分档展示。农历最先让位；
    // 极矮行（六行月份 + 大字体 + 小屏）改用“N 门课”计数，
    // 与头部“共 N 节课”的节数口径明确区分；停课/节假日状态任何档位都保留。
    val density = LocalDensity.current
    val measuredLayout = monthCellLayout(
        with(density) { (rowHeight - 11.dp).toPx() }, dateHeight, detailHeight,
        with(density) { 2.dp.toPx() }, statusText != null, cell.courses.size
    )
    // 课程名始终优先显示，列宽不足时由 TextOverflow.Ellipsis 截断；
    // 不再因为窄列直接退化成只有“共 N 节”。
    val layout = measuredLayout.copy(lunar = measuredLayout.lunar && lunarFitsWidth)
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
            fontSize = 13.sp,
            lineHeight = 16.sp,
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
        if (layout.lunar) {
            Text(
                text = cell.lunarText,
                fontSize = 10.sp,
                lineHeight = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (cell.holidayName != null) primary.copy(alpha = 0.88f)
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (cell.inMonth) 0.76f else 0.38f)
            )
        }
        statusText?.let { text ->
            Text(
                text = text,
                fontSize = 10.sp,
                lineHeight = 13.sp,
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
        if (layout.courseLines > 0) {
            cell.courses.take(layout.courseLines).forEach { course ->
                val color = courseColors[course.uniqueKey] ?: courseColors[course.courseName] ?: primary
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
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        // 文字位于中性单元格背景上，用主题前景色而不是课程圆点底色派生；
                        // 课程色相由圆点承载。
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (cell.inMonth) 0.88f else 0.5f)
                    )
                }
            }
            if (cell.courses.size > 2) {
                Text(
                    text = stringResource(R.string.month_day_course_count, cell.courses.size),
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else if (layout.summary) {
            Text(
                text = stringResource(R.string.month_day_course_count, cell.courses.size),
                fontSize = 10.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (cell.inMonth) 0.72f else 0.45f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
