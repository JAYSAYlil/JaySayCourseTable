package com.jaysay.coursetable.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jaysay.coursetable.R
import com.jaysay.coursetable.data.model.AcademicCalendarStatusResolver
import com.jaysay.coursetable.data.model.Course
import com.jaysay.coursetable.data.model.ScheduleDateException
import com.jaysay.coursetable.data.model.ScheduleDateResolver
import com.jaysay.coursetable.ui.theme.AppShapes
import com.jaysay.coursetable.ui.theme.courseCardTextColors
import com.jaysay.coursetable.ui.theme.resolveCourseColor
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
    val courses: List<Course>
)

/**
 * 月视图网格：以 [currentWeek] 所在周的周一为锚点，展示该自然月的整月日历。
 *
 * - 课程解析复用 [ScheduleDateResolver.coursesOn]，与周视图完全同口径
 *   （日期异常/停课周/单双周规则一致），颜色复用 buildCourseColorMap + resolveCourseColor。
 * - 今天：在学期内且对应周未被停课排除时，加 primary 描边与淡底高亮。
 * - 停课日（整周停课或当天放假）：日期数字置灰加删除线。
 * - 学期外日期正常显示但无课程、不可点击。
 */
@Composable
fun MonthGrid(
    modifier: Modifier = Modifier,
    courses: List<Course>,
    currentWeek: Int,
    totalWeeks: Int,
    semesterStart: String,
    excludedWeekSet: Set<Int>,
    dateExceptions: List<ScheduleDateException>,
    dark: Boolean,
    onDayClick: (semesterWeek: Int, dayOfWeek: Int) -> Unit
) {
    val today = remember { LocalDate.now() }
    // 与周视图同源的课程配色映射（含自定义颜色），按课程名去重后一次解析。
    val courseColors = remember(courses, dark) {
        courses.distinctBy { it.courseName }.associate { course ->
            course.courseName to resolveCourseColor(courses, course, dark)
        }
    }
    val cells = remember(
        courses, currentWeek, totalWeeks, semesterStart, excludedWeekSet, dateExceptions, today
    ) {
        buildMonthCells(
            courses = courses,
            currentWeek = currentWeek,
            totalWeeks = totalWeeks,
            semesterStart = semesterStart,
            excludedWeekSet = excludedWeekSet,
            dateExceptions = dateExceptions,
            today = today
        )
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 3.dp)) {
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
                    .heightIn(min = 74.dp)
                    .padding(horizontal = 3.dp)
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

private fun buildMonthCells(
    courses: List<Course>,
    currentWeek: Int,
    totalWeeks: Int,
    semesterStart: String,
    excludedWeekSet: Set<Int>,
    dateExceptions: List<ScheduleDateException>,
    today: LocalDate
): List<List<MonthDayData>> {
    // 锚点 = 学期第 currentWeek 周的周一；开学日期非法时回退到今天所在周。
    val anchor = TimeUtils.semesterWeekStartOrNull(semesterStart)
        ?.plusDays((currentWeek - 1L).coerceAtLeast(0) * 7L)
        ?: TimeUtils.weekStart(today)
    val gridStart = TimeUtils.weekStart(anchor.withDayOfMonth(1))
    return (0 until 6).map { row ->
        (0 until 7).map { col ->
            val date = gridStart.plusDays((row * 7 + col).toLong())
            val week = TimeUtils.semesterWeekOrNull(semesterStart, totalWeeks, date)
            // weekLabels 只影响周标签字段，月视图只关心停课/放假状态，传空表即可。
            val status = AcademicCalendarStatusResolver.day(
                date, semesterStart, totalWeeks, excludedWeekSet, dateExceptions, emptyMap()
            )
            MonthDayData(
                date = date,
                inMonth = date.monthValue == anchor.monthValue,
                semesterWeek = week,
                dayOfWeek = date.dayOfWeek.value,
                isToday = date == today && week != null && week !in excludedWeekSet,
                suspended = status.suspendedWeek || status.dayOff,
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
    onDayClick: (semesterWeek: Int, dayOfWeek: Int) -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outlineVariant
    val isToday = cell.isToday
    val clickableModifier = cell.semesterWeek?.let { week ->
        Modifier.clickable { onDayClick(week, cell.dayOfWeek) }
    } ?: Modifier
    Column(
        modifier = modifier
            .padding(1.dp)
            .heightIn(min = 72.dp)
            .clip(AppShapes.small)
            .background(if (isToday) primary.copy(alpha = 0.08f) else Color.Transparent)
            .border(
                width = if (isToday) 1.25.dp else 0.5.dp,
                color = if (isToday) primary else outline.copy(alpha = 0.28f),
                shape = AppShapes.small
            )
            .then(clickableModifier)
            .padding(horizontal = 4.dp, vertical = 3.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp)
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
        cell.courses.take(3).forEach { course ->
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
        if (cell.courses.size > 3) {
            Text(
                text = stringResource(R.string.month_more_courses, cell.courses.size - 3),
                fontSize = 8.sp,
                lineHeight = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
