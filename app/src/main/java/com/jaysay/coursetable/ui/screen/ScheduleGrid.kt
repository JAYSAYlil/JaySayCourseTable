package com.jaysay.coursetable.ui.screen

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.jaysay.coursetable.R
import com.jaysay.coursetable.data.model.Course
import com.jaysay.coursetable.data.model.ScheduleViewMode
import com.jaysay.coursetable.data.preferences.PeriodTime
import com.jaysay.coursetable.ui.theme.CourseColors
import com.jaysay.coursetable.ui.theme.CourseSubTextColor
import com.jaysay.coursetable.ui.theme.CourseTextColor
import com.jaysay.coursetable.ui.theme.DarkBackground
import com.jaysay.coursetable.ui.theme.DarkCourseColors
import com.jaysay.coursetable.ui.theme.DarkCourseSubTextColor
import com.jaysay.coursetable.ui.theme.DarkCourseTextColor
import com.jaysay.coursetable.ui.theme.DarkPrimaryDark
import com.jaysay.coursetable.ui.theme.PrimaryDark
import com.jaysay.coursetable.util.TimeUtils
import kotlinx.coroutines.delay
import java.util.Calendar

private data class Section(@StringRes val labelRes: Int, val periods: List<Int>)

private fun buildSections(periodTimes: List<PeriodTime>): List<Section> {
    val total = periodTimes.size
    if (total == 0) return emptyList()
    val morningEnd = minOf(4, total)
    val afternoonEnd = minOf(8, total)
    return buildList {
        add(Section(R.string.course_section_morning, (1..morningEnd).toList()))
        if (afternoonEnd > morningEnd) add(Section(R.string.course_section_afternoon, (morningEnd + 1..afternoonEnd).toList()))
        if (total > afternoonEnd) add(Section(R.string.course_section_evening, (afternoonEnd + 1..total).toList()))
    }
}

private fun periodOffset(period: Int, sections: List<Section>, cellHeight: Dp): Dp {
    var offset = 0.dp
    for (section in sections) {
        offset += 20.dp
        if (period in section.periods) {
            return offset + cellHeight * (period - section.periods.first())
        }
        offset += cellHeight * section.periods.size
    }
    return offset
}

/** 课程卡片透明度的单一来源，供视觉对比度回归测试复用。 */
internal fun courseCardBackgroundAlpha(background: Color, hasCustomBackground: Boolean): Float {
    val isDarkCourseCard = background.luminance() < 0.35f
    return when {
        !hasCustomBackground && isDarkCourseCard -> 0.96f
        !hasCustomBackground -> 0.82f
        isDarkCourseCard -> 0.86f
        else -> 0.74f
    }
}

/**
 * 以 State 形式提供当前分钟数（每 30 秒刷新一次）。
 * 返回 State 而不是直接返回值，让读取 .value 的调用点各自建立订阅，
 * 避免在屏幕顶层读取导致整个课表每 30 秒重组一次。
 */
@Composable
internal fun rememberCurrentMinute(): State<Int> {
    fun nowMinute(): Int = Calendar.getInstance().let {
        it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE)
    }
    val minuteState = remember { mutableIntStateOf(nowMinute()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            minuteState.intValue = nowMinute()
        }
    }
    return minuteState
}

@Composable
internal fun TableGrid(
    courses: List<Course>,
    colorMap: Map<String, Color>,
    visibleDays: List<Int>,
    timeWidth: Dp,
    cellHeight: Dp,
    currentWeek: Int,
    onCourseClick: (Course) -> Unit,
    onEmptyCellClick: (Int, Int) -> Unit,
    periodTimes: List<PeriodTime>,
    dark: Boolean,
    cTextColor: Color,
    cSubColor: Color,
    todayWeek: Int,
    todayDow: Int,
    viewMode: ScheduleViewMode,
    hasCustomBackground: Boolean
) {
    val sections = remember(periodTimes) { buildSections(periodTimes) }
    // 只持有 State 引用而不读取 .value，本层不会随分钟刷新重组；
    // 订阅下移到时间线覆盖层与课程卡片内部。
    val currentMinuteState = rememberCurrentMinute()
    val gridBackground = if (hasCustomBackground) {
        MaterialTheme.colorScheme.background.copy(alpha = if (dark) 0.38f else 0.30f)
    } else if (dark) DarkBackground else MaterialTheme.colorScheme.background
    val sectionBackground = if (dark) Color(0xFF17201D) else Color(0xFFF3F7F6)
    val sectionText = if (dark) DarkPrimaryDark.copy(alpha = 0.72f) else PrimaryDark.copy(alpha = 0.72f)
    val isTodayVisible = currentWeek == todayWeek
    val totalHeight = remember(periodTimes, cellHeight) { cellHeight * periodTimes.size + 20.dp * sections.size }
    val byDay = remember(courses) {
        (1..7).associateWith { day -> courses.filter { it.dayOfWeek == day }.sortedBy { it.startPeriod } }
    }

    Row(modifier = Modifier.fillMaxWidth().background(gridBackground)) {
        // 在组合上下文取色，供 Canvas 绘制闭包使用（DrawScope 不能访问 @Composable 属性）。
        val gridPrimary = MaterialTheme.colorScheme.primary
        val gridOutline = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        val todayHighlight = gridPrimary.copy(alpha = if (dark) 0.08f else 0.045f)
        val daySectionBg = sectionBackground.copy(alpha = 0.82f)
        Column(modifier = Modifier.width(timeWidth)) {
            sections.forEach { section ->
                Box(
                    modifier = Modifier.fillMaxWidth().height(20.dp).background(sectionBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(section.labelRes), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = sectionText)
                }
                section.periods.forEach periodLoop@{ period ->
                    val periodTime = periodTimes.getOrNull(period - 1) ?: return@periodLoop
                    Box(
                        modifier = Modifier.fillMaxWidth().height(cellHeight)
                            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier.fillMaxHeight().padding(vertical = 7.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Surface(
                                modifier = Modifier.size(24.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (dark) 0.82f else 0.66f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        period.toString(),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    periodTime.start,
                                    fontSize = 8.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 10.sp
                                )
                                Box(
                                    Modifier.width(12.dp).height(1.dp)
                                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f))
                                )
                                Text(
                                    periodTime.end,
                                    fontSize = 8.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        visibleDays.forEach { day ->
            key(day) {
                val dayCourses = byDay[day].orEmpty()
                val isTodayColumn = isTodayVisible && day == todayDow
                val occupiedPeriods = remember(dayCourses) {
                    dayCourses.flatMap { it.startPeriod..it.endPeriod }.toSet()
                }
                Box(modifier = Modifier.weight(1f).height(totalHeight)) {
                    // 背景高亮、分区标题条、网格线与当前时间线一次绘制，
                    // 避免为每个节次格创建组合节点；时间线只重绘不重组。
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        if (isTodayColumn) {
                            drawRect(todayHighlight)
                        }
                        sections.forEach { section ->
                            val y = section.periods.firstOrNull()?.let {
                                periodOffset(it, sections, cellHeight) - 20.dp
                            } ?: 0.dp
                            drawRect(
                                daySectionBg,
                                topLeft = Offset(0f, y.toPx()),
                                size = Size(size.width, 20.dp.toPx())
                            )
                        }
                        val stroke = 0.5.dp.toPx()
                        periodTimes.indices.forEach { index ->
                            val y = periodOffset(index + 1, sections, cellHeight).toPx()
                            drawLine(gridOutline, Offset(0f, y), Offset(size.width, y), strokeWidth = stroke)
                        }
                        drawLine(
                            gridOutline,
                            Offset(0f, size.height - stroke / 2f),
                            Offset(size.width, size.height - stroke / 2f),
                            strokeWidth = stroke
                        )
                    }

                    // 网格视觉元素由 Canvas 批量绘制；空白节次仍保留独立可点击语义节点，
                    // 确保 TalkBack 用户能定位到具体星期和节次，而不是只能读到整列。
                    for (index in periodTimes.indices) {
                        val period = index + 1
                        if (period !in occupiedPeriods) {
                            val emptyCellDescription = stringResource(
                                R.string.course_empty_cell_desc,
                                TimeUtils.getDayName(day),
                                period
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(cellHeight)
                                    .offset(y = periodOffset(period, sections, cellHeight))
                                    .clickable { onEmptyCellClick(day, period) }
                                    .semantics { contentDescription = emptyCellDescription }
                            )
                        }
                    }

                    dayCourses.forEach { course ->
                        val start = course.startPeriod.coerceIn(1, periodTimes.size.coerceAtLeast(1))
                        val end = course.endPeriod.coerceIn(start, periodTimes.size.coerceAtLeast(start))
                        val y = periodOffset(start, sections, cellHeight)
                        val bottom = periodOffset(end, sections, cellHeight) + cellHeight
                        val palette = if (dark) DarkCourseColors else CourseColors
                        val cardColor = when {
                            course.customColor != null && course.customColor in palette.indices -> palette[course.customColor]
                            course.customColor != null -> Color(course.customColor)
                            else -> colorMap[course.courseName] ?: palette.first()
                        }
                        val accent = lerp(cardColor, if (dark) Color.White else Color.Black, if (dark) 0.28f else 0.22f)
                        val startMinute = periodTimes.getOrNull(start - 1)?.start?.let(TimeUtils::parseMinuteOfDay)
                        val endMinute = periodTimes.getOrNull(end - 1)?.end?.let(TimeUtils::parseMinuteOfDay)
                        CourseCard(
                            course = course,
                            modifier = Modifier.fillMaxWidth().height(bottom - y).offset(y = y).padding(1.dp),
                            background = cardColor,
                            accent = accent,
                            textColor = cTextColor,
                            subTextColor = cSubColor,
                            isCurrentProvider = {
                                // 在卡片自身作用域读取分钟状态：每 30 秒只重组发生状态变化的卡片。
                                isTodayColumn && startMinute != null && endMinute != null &&
                                    currentMinuteState.value >= startMinute &&
                                    currentMinuteState.value < endMinute
                            },
                            viewMode = viewMode,
                            hasCustomBackground = hasCustomBackground,
                            onClick = { onCourseClick(course) }
                        )
                    }

                    // 当前时间线必须在课程卡片之后绘制，否则会被卡片完全遮挡。
                    if (isTodayColumn) {
                        CurrentTimeLineOverlay(
                            currentMinuteState = currentMinuteState,
                            periodTimes = periodTimes,
                            sections = sections,
                            cellHeight = cellHeight,
                            dark = dark
                        )
                    }
                }
            }
        }
    }
}

/**
 * 当前时间线覆盖层：在此组件作用域读取分钟状态，
 * 每 30 秒只重组并重绘这一条线，不带动网格与卡片。
 */
@Composable
private fun CurrentTimeLineOverlay(
    currentMinuteState: State<Int>,
    periodTimes: List<PeriodTime>,
    sections: List<Section>,
    cellHeight: Dp,
    dark: Boolean
) {
    val currentMinute = currentMinuteState.value
    val lineColor = MaterialTheme.colorScheme.error
    Canvas(modifier = Modifier.fillMaxSize().zIndex(3f)) {
        val activeIndex = periodTimes.indexOfFirst { period ->
            val start = TimeUtils.parseMinuteOfDay(period.start)
            val end = TimeUtils.parseMinuteOfDay(period.end)
            start != null && end != null && currentMinute >= start && currentMinute < end
        }
        if (activeIndex >= 0) {
            val start = TimeUtils.parseMinuteOfDay(periodTimes[activeIndex].start) ?: currentMinute
            val end = TimeUtils.parseMinuteOfDay(periodTimes[activeIndex].end) ?: currentMinute + 1
            val fraction = ((currentMinute - start).toFloat() /
                (end - start).coerceAtLeast(1)).coerceIn(0f, 1f)
            val lineY = (periodOffset(activeIndex + 1, sections, cellHeight) +
                cellHeight * fraction).toPx()
            val halo = if (dark) Color.Black.copy(alpha = 0.78f)
                else Color.White.copy(alpha = 0.9f)
            drawCircle(halo, radius = 6.dp.toPx(), center = Offset(6.dp.toPx(), lineY))
            drawLine(
                halo,
                Offset(10.dp.toPx(), lineY),
                Offset(size.width, lineY),
                strokeWidth = 4.dp.toPx()
            )
            drawCircle(lineColor, radius = 4.dp.toPx(), center = Offset(6.dp.toPx(), lineY))
            drawLine(
                lineColor,
                Offset(10.dp.toPx(), lineY),
                Offset(size.width, lineY),
                strokeWidth = 2.dp.toPx()
            )
        }
    }
}

@Composable
private fun CourseCard(
    course: Course,
    modifier: Modifier,
    background: Color,
    accent: Color,
    textColor: Color,
    subTextColor: Color,
    isCurrentProvider: () -> Boolean,
    viewMode: ScheduleViewMode,
    hasCustomBackground: Boolean,
    onClick: () -> Unit
) {
    // 在卡片作用域内读取分钟状态：只有状态发生变化的卡片才随分钟刷新重组。
    val isCurrent = isCurrentProvider()
    val shape = RoundedCornerShape(if (viewMode == ScheduleViewMode.WEEK) 14.dp else 16.dp)
    val compactWeekCard = viewMode == ScheduleViewMode.WEEK && course.periodSpan == 1
    val textLoad = course.courseName.length + course.teacher.length + course.classroom.length
    val titleSize = when (viewMode) {
        ScheduleViewMode.WEEK -> when {
            textLoad > 34 -> 8.sp
            compactWeekCard -> 9.sp
            else -> 10.5.sp
        }
        ScheduleViewMode.WORK_WEEK -> if (textLoad > 30) 11.5.sp else 13.sp
        ScheduleViewMode.DAY -> 16.sp
    }
    val subSize = when (viewMode) {
        ScheduleViewMode.WEEK -> when {
            textLoad > 34 -> 7.sp
            textLoad > 24 -> 7.5.sp
            else -> 8.25.sp
        }
        ScheduleViewMode.WORK_WEEK -> if (textLoad > 30) 9.sp else 10.5.sp
        ScheduleViewMode.DAY -> 12.sp
    }
    val titleLines = when (viewMode) {
        ScheduleViewMode.WEEK -> if (compactWeekCard) 3 else (course.periodSpan + 2).coerceAtMost(6)
        ScheduleViewMode.WORK_WEEK -> if (course.periodSpan == 1) 3 else (course.periodSpan + 2).coerceAtMost(6)
        ScheduleViewMode.DAY -> 3
    }
    val classroomLines = when (viewMode) {
        ScheduleViewMode.WEEK -> if (compactWeekCard) 5 else (course.periodSpan + 3).coerceAtMost(8)
        ScheduleViewMode.WORK_WEEK -> if (course.periodSpan == 1) 6 else 7
        ScheduleViewMode.DAY -> 5
    }
    val teacherLines = when (viewMode) {
        ScheduleViewMode.WEEK -> 2
        ScheduleViewMode.WORK_WEEK -> 2
        ScheduleViewMode.DAY -> 3
    }
    val cardOverflow = TextOverflow.Clip
    val description = listOf(course.courseName, course.teacher, course.classroom)
        .filter { it.isNotBlank() }
        .plus(stringResource(R.string.course_card_view_details))
        .joinToString("，")
    val isDarkCourseCard = background.luminance() < 0.35f
    val highlight = if (isDarkCourseCard) Color.White.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.3f)
    val backgroundAlpha = courseCardBackgroundAlpha(background, hasCustomBackground)
    Box(
        modifier = modifier
            .shadow(if (isCurrent) 4.dp else 2.dp, shape, clip = false)
            .clip(shape)
            .background(background.copy(alpha = backgroundAlpha))
            .background(
                Brush.linearGradient(
                    colors = listOf(highlight, Color.Transparent),
                    start = Offset.Zero,
                    end = Offset(0f, 220f)
                ),
                shape
            )
            .border(
                if (isCurrent) 1.8.dp else 0.65.dp,
                if (isCurrent) MaterialTheme.colorScheme.primary else accent.copy(alpha = 0.42f),
                shape
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = description }
            .padding(
                start = if (viewMode == ScheduleViewMode.WEEK) 4.dp else 7.dp,
                end = if (viewMode == ScheduleViewMode.WEEK) 3.dp else 6.dp,
                top = 5.dp,
                bottom = 4.dp
            ),
        contentAlignment = Alignment.TopStart
    ) {
        Column {
            Text(
                course.courseName,
                modifier = Modifier.fillMaxWidth(),
                fontWeight = FontWeight.Bold,
                fontSize = titleSize,
                maxLines = titleLines,
                softWrap = true,
                overflow = cardOverflow,
                color = textColor,
                lineHeight = titleSize * 1.24f
            )
            if (course.teacher.isNotBlank()) {
                Text(
                    course.teacher,
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = subSize,
                    maxLines = teacherLines,
                    softWrap = true,
                    overflow = cardOverflow,
                    color = subTextColor,
                    lineHeight = subSize * 1.3f
                )
            }
            if (course.classroom.isNotBlank()) {
                Text(
                    course.classroom,
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = subSize,
                    maxLines = classroomLines,
                    softWrap = true,
                    overflow = cardOverflow,
                    color = subTextColor,
                    lineHeight = subSize * 1.3f
                )
            }
        }
    }
}
