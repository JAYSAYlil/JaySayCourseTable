package com.jaysay.coursetable.ui.screen

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
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
import com.jaysay.coursetable.ui.theme.DarkBackground
import com.jaysay.coursetable.ui.theme.DarkCourseColors
import com.jaysay.coursetable.ui.theme.DarkPrimaryDark
import com.jaysay.coursetable.ui.theme.Motion
import com.jaysay.coursetable.ui.theme.PrimaryDark
import com.jaysay.coursetable.ui.theme.courseCardBorderColor
import com.jaysay.coursetable.ui.theme.courseCardTextColors
import com.jaysay.coursetable.ui.theme.pressScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import com.jaysay.coursetable.ui.components.HeroRegistry
import com.jaysay.coursetable.util.TimeUtils
import kotlinx.coroutines.delay
import java.util.Calendar

private data class Section(@param:StringRes val labelRes: Int, val periods: List<Int>)

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

/**
 * 课程卡片透明度的单一来源，供视觉对比度回归测试复用。
 */
internal fun courseCardBackgroundAlpha(background: Color, hasCustomBackground: Boolean): Float {
    val isDarkCourseCard = background.luminance() < 0.35f
    return when {
        // 深色材质需要保留颜色本体；渐变高光只作为表层，不稀释卡片层级。
        !hasCustomBackground && isDarkCourseCard -> 0.94f
        !hasCustomBackground -> 0.82f
        isDarkCourseCard -> 0.90f
        else -> 0.74f
    }
}

/**
 * 以 State 形式提供当前分钟数。刷新会对齐到下一分钟边界，避免长期漂移，
 * 让进度线在课间/整点切换时也能稳定重绘。
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
            minuteState.intValue = nowMinute()
            val now = System.currentTimeMillis()
            val untilNextMinute = (60_000L - (now % 60_000L)).coerceIn(1_000L, 60_000L)
            delay(untilNextMinute)
        }
    }
    return minuteState
}

/**
 * 返回当前正在上课的节次索引与节内进度。
 *
 * 课程连续跨节时，课间仍然不属于任何 [PeriodTime]，因此返回 null；这保证红线只在
 * 实际上课时间内出现，而不会把两节课之间的休息时间误判成课程进度。
 */
internal fun currentCourseProgressPosition(
    currentMinute: Int,
    periodTimes: List<PeriodTime>,
    dayCourses: List<Course>
): Pair<Int, Float>? {
    val activePeriod = periodTimes.mapIndexedNotNull { index, period ->
        val start = TimeUtils.parseMinuteOfDay(period.start)
        val end = TimeUtils.parseMinuteOfDay(period.end)
        if (start != null && end != null && end > start && currentMinute in start until end) {
            Triple(index, start, end)
        } else {
            null
        }
    }.firstOrNull() ?: return null

    val periodNumber = activePeriod.first + 1
    val hasCourseNow = dayCourses.any { course ->
        periodNumber in course.startPeriod.coerceAtLeast(1)..course.endPeriod.coerceAtLeast(course.startPeriod)
    }
    if (!hasCourseNow) return null

    val fraction = ((currentMinute - activePeriod.second).toFloat() /
        (activePeriod.third - activePeriod.second)).coerceIn(0f, 1f)
    return activePeriod.first to fraction
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
    todayWeek: Int,
    todayDow: Int,
    viewMode: ScheduleViewMode,
    hasCustomBackground: Boolean
) {
    val sections = remember(periodTimes) { buildSections(periodTimes) }
    // 只持有 State 引用而不读取 .value，本层不会随分钟刷新重组；
    // 订阅下移到时间线覆盖层与课程卡片内部。
    val currentMinuteState = rememberCurrentMinute()
    // 网格不再叠加半透明底色层：页面底色由屏幕层统一绘制，
    // 自定义背景壁纸直接透出，课程卡片自身的不透明度保证可读性。
    val gridBackground = Color.Transparent
    val sectionBackground = if (dark) Color(0xFF17191B) else Color(0xFFEFF2F1)
    val sectionText = if (dark) DarkPrimaryDark.copy(alpha = 0.72f) else PrimaryDark.copy(alpha = 0.72f)
    val isTodayVisible = currentWeek == todayWeek
    val totalHeight = remember(periodTimes, cellHeight) { cellHeight * periodTimes.size + 20.dp * sections.size }
    val byDay = remember(courses, visibleDays) {
        visibleDays.associateWith { day -> courses.filter { it.dayOfWeek == day }.sortedBy { it.startPeriod } }
    }

    Row(modifier = Modifier.fillMaxWidth().background(gridBackground)) {
        // 在组合上下文取色，供 Canvas 绘制闭包使用（DrawScope 不能访问 @Composable 属性）。
        val gridPrimary = MaterialTheme.colorScheme.primary
        val gridOutline = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f)
        val todayHighlight = gridPrimary.copy(alpha = if (dark) 0.10f else 0.05f)
        val daySectionBg = sectionBackground.copy(alpha = 0.86f)
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
                            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f)),
                        contentAlignment = Alignment.Center
                    ) {
                        // 时间栏保持轻量：用一条品牌色时间轴承接节次和时间，
                        // 避免每个格子都套一张独立卡片，保证课表网格的连续性。
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.52f))
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.Bottom,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        period.toString(),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        lineHeight = 16.sp
                                    )
                                    Text(
                                        stringResource(R.string.course_period_suffix),
                                        modifier = Modifier.padding(start = 1.dp, bottom = 1.dp),
                                        fontSize = 8.sp,
                                        lineHeight = 9.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    periodTime.start,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Box(
                                    modifier = Modifier
                                        .width(18.dp)
                                        .height(1.dp)
                                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
                                )
                                Text(
                                    periodTime.end,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        val cardColor = course.customColor
                            ?.takeIf { it in palette.indices }
                            ?.let(palette::get)
                            ?: colorMap[course.courseName]
                            ?: palette.first()
                        val startMinute = periodTimes.getOrNull(start - 1)?.start?.let(TimeUtils::parseMinuteOfDay)
                        val endMinute = periodTimes.getOrNull(end - 1)?.end?.let(TimeUtils::parseMinuteOfDay)
                        CourseCard(
                            course = course,
                            modifier = Modifier.fillMaxWidth().height(bottom - y).offset(y = y).padding(1.dp),
                            background = cardColor,
                            isCurrentProvider = {
                                // 在卡片自身作用域读取分钟状态：每 30 秒只重组发生状态变化的卡片。
                                isTodayColumn && startMinute != null && endMinute != null &&
                                    currentMinuteState.value >= startMinute &&
                                    currentMinuteState.value < endMinute
                            },
                            viewMode = viewMode,
                            hasCustomBackground = hasCustomBackground,
                            dark = dark,
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
                            dark = dark,
                            dayCourses = dayCourses.filter { it.dayOfWeek == todayDow }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 当前时间线覆盖层：在此组件作用域读取分钟状态，
 * 每分钟只重组并重绘这一条线，不带动网格与卡片。
 */
@Composable
private fun CurrentTimeLineOverlay(
    currentMinuteState: State<Int>,
    periodTimes: List<PeriodTime>,
    sections: List<Section>,
    cellHeight: Dp,
    dark: Boolean,
    dayCourses: List<Course>
) {
    val currentMinute = currentMinuteState.value
    val lineColor = MaterialTheme.colorScheme.error
    Canvas(modifier = Modifier.fillMaxSize().zIndex(3f)) {
        val position = currentCourseProgressPosition(currentMinute, periodTimes, dayCourses)
        position?.let { (periodIndex, fraction) ->
            val lineY = (periodOffset(periodIndex + 1, sections, cellHeight) +
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
            drawCircle(lineColor, radius = 3.5.dp.toPx(), center = Offset(6.dp.toPx(), lineY))
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
    isCurrentProvider: () -> Boolean,
    viewMode: ScheduleViewMode,
    hasCustomBackground: Boolean,
    dark: Boolean,
    onClick: () -> Unit
) {
    // 在卡片作用域内读取分钟状态：只有状态发生变化的卡片才随分钟刷新重组。
    val isCurrent = isCurrentProvider()
    val shape = RoundedCornerShape(if (viewMode == ScheduleViewMode.WEEK) 12.dp else 14.dp)
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
        // MONTH 不渲染课程卡片（月视图走 MonthGrid），分支仅为 when 穷尽性补齐。
        ScheduleViewMode.MONTH -> if (textLoad > 30) 11.5.sp else 13.sp
    }
    val subSize = when (viewMode) {
        ScheduleViewMode.WEEK -> when {
            textLoad > 34 -> 7.sp
            textLoad > 24 -> 7.5.sp
            else -> 8.25.sp
        }
        ScheduleViewMode.WORK_WEEK -> if (textLoad > 30) 9.sp else 10.5.sp
        ScheduleViewMode.DAY -> 12.sp
        ScheduleViewMode.MONTH -> if (textLoad > 30) 9.sp else 10.5.sp
    }
    val titleLines = when (viewMode) {
        ScheduleViewMode.WEEK -> if (compactWeekCard) 3 else (course.periodSpan + 2).coerceAtMost(6)
        ScheduleViewMode.WORK_WEEK -> if (course.periodSpan == 1) 3 else (course.periodSpan + 2).coerceAtMost(6)
        ScheduleViewMode.DAY -> 3
        ScheduleViewMode.MONTH -> 3
    }
    val classroomLines = when (viewMode) {
        ScheduleViewMode.WEEK -> if (compactWeekCard) 5 else (course.periodSpan + 3).coerceAtMost(8)
        ScheduleViewMode.WORK_WEEK -> if (course.periodSpan == 1) 6 else 7
        ScheduleViewMode.DAY -> 5
        ScheduleViewMode.MONTH -> 6
    }
    val teacherLines = when (viewMode) {
        ScheduleViewMode.WEEK -> 2
        ScheduleViewMode.WORK_WEEK -> 2
        ScheduleViewMode.DAY -> 3
        ScheduleViewMode.MONTH -> 2
    }
    val cardOverflow = TextOverflow.Clip
    val description = listOf(course.courseName, course.teacher, course.classroom)
        .filter { it.isNotBlank() }
        .plus(stringResource(R.string.course_card_view_details))
        .joinToString("，")
    val (titleColor, subColor) = courseCardTextColors(background, dark)
    val baseBorder = courseCardBorderColor(background, dark)
    // 当前正在上的课：描边平滑过渡到品牌色并加重阴影，形成呼吸感高亮。
    val borderColor by animateColorAsState(
        targetValue = if (isCurrent) MaterialTheme.colorScheme.primary else baseBorder,
        animationSpec = Motion.eased(),
        label = "courseCardBorder"
    )
    val backgroundAlpha = courseCardBackgroundAlpha(background, hasCustomBackground)
    // 深色卡片使用更深的实色基底与三段式渐变：顶部高光提到 +26%、中段回落基色、
    // 底部压暗到 -22%，光源感比早期的 +14%/-10% 明显得多，仍不稀释色相本身；
    // 浅色则保留原有的平整嵌套卡片，不让两种外观沦为同一种设计。
    val cardFill = if (dark) {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0.00f to lerp(background, Color.White, 0.26f).copy(alpha = 0.98f),
                0.45f to background.copy(alpha = backgroundAlpha),
                1.00f to lerp(background, Color.Black, 0.22f).copy(alpha = backgroundAlpha)
            )
        )
    } else {
        null
    }
    val contentPadding = when (viewMode) {
        ScheduleViewMode.WEEK -> PaddingValues(4.dp, 5.dp, 3.dp, 4.dp)
        else -> PaddingValues(7.dp, 5.dp, 6.dp, 4.dp)
    }
    val cardInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    // Hero 转场卡片端：布局回调把「可见卡片」的 root bounds 与底色快照进注册表；
    // 点击瞬间由注册表发起 overlay 飞行。bounds 只在点击时被消费，
    // 滚动/翻页与进行中的动画完全解耦（替代 sharedBounds 的实时跟踪）。
    val rootView = LocalView.current
    Box(
        modifier = modifier
            .onGloballyPositioned { coords ->
                val rect = coords.boundsInRoot()
                // 只记录视口内的卡片：Pager 相邻周页的同 series 卡片位于屏外，不能覆盖可见位置。
                val visible = rect.left < rootView.width && rect.right > 0f &&
                    rect.top < rootView.height && rect.bottom > 0f
                if (visible) {
                    HeroRegistry.cardBounds[course.seriesKey] = rect
                    HeroRegistry.cardColors[course.seriesKey] = background
                }
            }
            .pressScale(cardInteraction, 0.96f)
            .shadow(if (isCurrent) 6.dp else if (dark) 4.dp else 2.dp, shape, clip = false)
            .clip(shape)
            .then(
                if (cardFill != null) Modifier.background(cardFill, shape)
                else Modifier.background(background.copy(alpha = backgroundAlpha), shape)
            )
            .border(
                if (isCurrent) 1.6.dp else 0.75.dp,
                borderColor,
                shape
            )
            .clickable(interactionSource = cardInteraction, indication = null) {
                HeroRegistry.beginForward(course.seriesKey)
                onClick()
            }
            .semantics { contentDescription = description }
            .padding(contentPadding),
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
                color = titleColor,
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
                    color = subColor,
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
                    color = subColor,
                    lineHeight = subSize * 1.3f
                )
            }
        }
    }
}
