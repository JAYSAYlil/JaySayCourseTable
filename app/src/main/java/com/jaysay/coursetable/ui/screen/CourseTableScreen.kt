package com.jaysay.coursetable.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.CalendarViewDay
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
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
import com.jaysay.coursetable.data.model.CourseSearch
import com.jaysay.coursetable.data.model.ScheduleDateException
import com.jaysay.coursetable.data.model.ScheduleDateResolver
import com.jaysay.coursetable.data.model.ScheduleViewMode
import com.jaysay.coursetable.data.model.TodayAgendaCalculator
import com.jaysay.coursetable.data.preferences.AppPreferences
import com.jaysay.coursetable.data.preferences.PeriodTime
import com.jaysay.coursetable.ui.components.ReadOnlyRecoveryBanner
import com.jaysay.coursetable.ui.components.ScheduleOverviewBar
import com.jaysay.coursetable.ui.components.CustomBackgroundImage
import com.jaysay.coursetable.ui.theme.CourseColors
import com.jaysay.coursetable.ui.theme.CourseSubTextColor
import com.jaysay.coursetable.ui.theme.CourseTextColor
import com.jaysay.coursetable.ui.theme.DarkBackground
import com.jaysay.coursetable.ui.theme.DarkCourseColors
import com.jaysay.coursetable.ui.theme.DarkCourseSubTextColor
import com.jaysay.coursetable.ui.theme.DarkCourseTextColor
import com.jaysay.coursetable.ui.theme.DarkPrimaryDark
import com.jaysay.coursetable.ui.theme.PrimaryDark
import com.jaysay.coursetable.ui.theme.buildCourseColorMap
import com.jaysay.coursetable.util.TimeUtils
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.util.Calendar
import kotlin.math.abs

private data class Section(val name: String, val periods: List<Int>)

private fun buildSections(periodTimes: List<PeriodTime>): List<Section> {
    val total = periodTimes.size
    if (total == 0) return emptyList()
    val morningEnd = minOf(4, total)
    val afternoonEnd = minOf(8, total)
    return buildList {
        add(Section("上午", (1..morningEnd).toList()))
        if (afternoonEnd > morningEnd) add(Section("下午", (morningEnd + 1..afternoonEnd).toList()))
        if (total > afternoonEnd) add(Section("晚上", (afternoonEnd + 1..total).toList()))
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
 * 以 State 形式提供当前分钟数（每 30 秒刷新一次）。
 * 返回 State 而不是直接返回值，让读取 .value 的调用点各自建立订阅，
 * 避免在屏幕顶层读取导致整个课表每 30 秒重组一次。
 */
@Composable
private fun rememberCurrentMinute(): State<Int> {
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

/**
 * 今日摘要的分钟级订阅只发生在本组件作用域内：
 * 每 30 秒只重组摘要组件，课表网格与课程卡片不随分钟刷新重组。
 */
@Composable
private fun rememberTodayAgenda(
    courses: List<Course>,
    periodTimes: List<PeriodTime>,
    semesterStart: String,
    totalWeeks: Int,
    excludedWeekSet: Set<Int>,
    dateExceptions: List<ScheduleDateException>
) = run {
    val currentMinute = rememberCurrentMinute().value
    val today = LocalDate.now()
    remember(courses, periodTimes, semesterStart, totalWeeks, today, currentMinute, excludedWeekSet, dateExceptions) {
        TodayAgendaCalculator.calculate(
            courses = courses,
            periods = periodTimes,
            semesterStart = semesterStart,
            totalWeeks = totalWeeks,
            date = today,
            minuteOfDay = currentMinute,
            excludedWeeks = excludedWeekSet,
            exceptions = dateExceptions
        )
    }
}

@Composable
fun CourseTableScreen(
    courses: List<Course>,
    currentWeek: Int,
    onImportClick: () -> Unit,
    onCourseClick: (Course) -> Unit,
    onWeekChange: (Int) -> Unit,
    onSettingsClick: () -> Unit = {},
    onAddCourseClick: () -> Unit = {},
    onAddCourseAt: (dayOfWeek: Int, period: Int) -> Unit = { _, _ -> },
    onLocateToday: () -> Unit = {},
    onTableMenuClick: () -> Unit = {},
    tableName: String = "课表1",
    periodTimes: List<PeriodTime> = AppPreferences.defaultPeriods(),
    semesterStart: String = TimeUtils.currentWeekStartDate(),
    totalWeeks: Int = 20,
    excludedWeeks: List<Int> = emptyList(),
    dateExceptions: List<ScheduleDateException> = emptyList(),
    weekLabels: Map<Int, String> = emptyMap(),
    reduceMotion: Boolean = false,
    customBackground: ImageBitmap? = null,
    viewMode: ScheduleViewMode,
    onViewModeChange: (ScheduleViewMode) -> Unit,
    focusedDay: Int,
    onFocusedDayChange: (Int) -> Unit,
    onAgendaClick: () -> Unit = {},
    readOnlyMessage: String? = null,
    onRecoveryClick: () -> Unit = {}
) {
    val dark = MaterialTheme.colorScheme.background == DarkBackground
    val bgColor = MaterialTheme.colorScheme.background
    val cTextColor = if (dark) DarkCourseTextColor else CourseTextColor
    val cSubColor = if (dark) DarkCourseSubTextColor else CourseSubTextColor
    val density = LocalDensity.current
    val swipeThresholdPx = remember(density) { with(density) { 64.dp.toPx() } }

    val today = LocalDate.now()
    val todayWeek = remember(semesterStart, totalWeeks, today) {
        TodayAgendaCalculator.semesterWeek(semesterStart, totalWeeks, today) ?: -1
    }
    val todayDow = today.dayOfWeek.value
    val excludedWeekSet = remember(excludedWeeks) { excludedWeeks.toSet() }
    val agenda = rememberTodayAgenda(courses, periodTimes, semesterStart, totalWeeks, excludedWeekSet, dateExceptions)
    var searchQuery by remember { mutableStateOf("") }
    val displayedCourses = remember(courses, searchQuery) { CourseSearch.filter(courses, searchQuery) }

    var viewMenuExpanded by remember { mutableStateOf(false) }
    val visibleDays = remember(viewMode, focusedDay) {
        when (viewMode) {
            ScheduleViewMode.WEEK -> (1..7).toList()
            ScheduleViewMode.WORK_WEEK -> (1..5).toList()
            ScheduleViewMode.DAY -> listOf(focusedDay.coerceIn(1, 7))
        }
    }
    val timeWidth = when (viewMode) {
        ScheduleViewMode.WEEK -> 38.dp
        ScheduleViewMode.WORK_WEEK -> 44.dp
        ScheduleViewMode.DAY -> 58.dp
    }
    // The seven-day columns are narrow, so they need more vertical room to show
    // course, teacher and classroom text without truncation.
    val cellHeight = when (viewMode) {
        ScheduleViewMode.WEEK -> 106.dp
        ScheduleViewMode.WORK_WEEK -> 116.dp
        ScheduleViewMode.DAY -> 100.dp
    }
    val isExcludedWeek = currentWeek in excludedWeekSet
    val weekCourses = remember(displayedCourses, currentWeek, excludedWeekSet) {
        displayedCourses.filter { currentWeek in it.weeks && currentWeek !in excludedWeekSet }
    }
    val colorMap = remember(courses, dark) { buildCourseColorMap(courses, dark) }
    val isTodayWeek = currentWeek == todayWeek
    val weekControlTint = MaterialTheme.colorScheme.primary
    val weekDisabledTint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (dark) 0.38f else 0.3f)

    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        Box(modifier = Modifier.fillMaxSize().background(bgColor)) {
            if (customBackground != null) {
                CustomBackgroundImage(customBackground)
                Box(
                    Modifier.fillMaxSize().background(
                        if (dark) Color.Black.copy(alpha = 0.48f)
                        else Color.White.copy(alpha = 0.42f)
                    )
                )
            }
            Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .statusBarsPadding()
                .navigationBarsPadding()
                .testTag("course-table-screen")
        ) {
        ScheduleOverviewBar(
            tableName = tableName,
            agenda = agenda,
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
            onTableMenuClick = onTableMenuClick,
            onAddCourseClick = onAddCourseClick,
            onImportClick = onImportClick,
            onLocateToday = onLocateToday,
            onAgendaClick = onAgendaClick,
            onSettingsClick = onSettingsClick,
            writesEnabled = readOnlyMessage == null
        )

        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

        readOnlyMessage?.let { message ->
            ReadOnlyRecoveryBanner(message = message, onRecoveryClick = onRecoveryClick)
        }

        if (courses.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.weight(1f).fillMaxHeight().testTag("week-navigation"),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (dark) 0.76f else 0.62f),
                    border = BorderStroke(
                        0.75.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
                    )
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { onWeekChange(currentWeek - 1) },
                            enabled = currentWeek > 1,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                Icons.Outlined.ChevronLeft,
                                stringResource(R.string.course_prev_week),
                                tint = if (currentWeek > 1) weekControlTint else weekDisabledTint,
                                modifier = Modifier.size(27.dp)
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = stringResource(R.string.course_week_number, currentWeek),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                            Text(
                                text = weekLabels[currentWeek]?.let {
                                    stringResource(R.string.course_week_label_count, it, weekCourses.size)
                                } ?: (stringResource(R.string.course_week_course_count, weekCourses.size) +
                                    if (isTodayWeek) stringResource(R.string.course_week_today_suffix) else ""),
                                fontWeight = FontWeight.Medium,
                                fontSize = 10.sp,
                                color = if (isTodayWeek) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        IconButton(
                            onClick = { onWeekChange(currentWeek + 1) },
                            enabled = currentWeek < totalWeeks,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                Icons.Outlined.ChevronRight,
                                stringResource(R.string.course_next_week),
                                tint = if (currentWeek < totalWeeks) weekControlTint else weekDisabledTint,
                                modifier = Modifier.size(27.dp)
                            )
                        }
                    }
                }
                Box {
                    Surface(
                        onClick = { viewMenuExpanded = true },
                        modifier = Modifier.width(70.dp).fillMaxHeight().testTag("view-mode-button"),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (dark) 0.92f else 0.78f),
                        border = BorderStroke(
                            0.9.dp,
                            weekControlTint.copy(alpha = if (dark) 0.68f else 0.38f)
                        )
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                if (viewMode == ScheduleViewMode.DAY) Icons.Default.CalendarViewDay else Icons.Default.ViewWeek,
                                stringResource(R.string.course_view_mode_current_desc, viewMode.label),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(21.dp)
                            )
                            Text(
                                viewMode.label,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    DropdownMenu(expanded = viewMenuExpanded, onDismissRequest = { viewMenuExpanded = false }) {
                        ScheduleViewMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                modifier = Modifier.testTag("view-mode-${mode.name.lowercase()}"),
                                text = {
                                    Text(
                                        stringResource(R.string.course_view_mode_label, mode.label),
                                        color = if (mode == viewMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = if (mode == viewMode) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        if (mode == ScheduleViewMode.DAY) Icons.Default.CalendarViewDay else Icons.Default.ViewWeek,
                                        null,
                                        tint = if (mode == viewMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                onClick = {
                                    onViewModeChange(mode)
                                    if (mode == ScheduleViewMode.DAY && isTodayWeek && todayDow in 1..7) {
                                        onFocusedDayChange(todayDow)
                                    }
                                    viewMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            LinearProgressIndicator(
                progress = { currentWeek.toFloat() / totalWeeks.coerceAtLeast(1) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(2.dp).clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = if (dark) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            if (viewMode == ScheduleViewMode.DAY) {
                Row(modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp, vertical = 2.dp)) {
                    for (day in 1..7) {
                        val selected = day == focusedDay
                        val chipColor by animateColorAsState(
                            if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            label = "dayChip"
                        )
                        val dayChipDescription = stringResource(R.string.course_view_day_desc, TimeUtils.getDayName(day))
                        Box(
                            modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(10.dp))
                                .background(chipColor).clickable { onFocusedDayChange(day) }
                                .semantics { contentDescription = dayChipDescription },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                TimeUtils.getDayName(day).replace("周", ""),
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            DayHeader(
                visibleDays = visibleDays,
                timeWidth = timeWidth,
                currentWeek = currentWeek,
                semesterStart = semesterStart,
                isTodayWeek = isTodayWeek,
                todayDow = todayDow,
                onDayClick = { day ->
                    onFocusedDayChange(day)
                    onViewModeChange(ScheduleViewMode.DAY)
                }
            )
            HorizontalDivider(thickness = 0.75.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
        }

        if (courses.isEmpty()) {
            EmptySchedule(
                onImportClick = if (readOnlyMessage == null) onImportClick else ({}),
                onAddCourseClick = if (readOnlyMessage == null) onAddCourseClick else ({}),
                readOnly = readOnlyMessage != null
            )
        } else if (isExcludedWeek) {
            // 校历停课周：本周无课，展示提示而非空网格
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(stringResource(R.string.course_week_suspended, currentWeek), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.course_week_suspended_hint),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (displayedCourses.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(stringResource(R.string.course_search_no_match, searchQuery), color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { searchQuery = "" }) { Text(stringResource(R.string.course_search_clear)) }
            }
        } else {
            // 纵向滚动状态独立于周次：切换周次时保持当前位置不重置，
            // 离开详情页或 Activity 重建时也能恢复到原位置。
            val scrollState = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }
            AnimatedContent(
                targetState = currentWeek,
                modifier = Modifier.fillMaxWidth().weight(1f),
                transitionSpec = {
                    val enterMs = if (reduceMotion) 0 else 210
                    val fadeMs = if (reduceMotion) 0 else 180
                    if (targetState > initialState) {
                        (slideInHorizontally(tween(enterMs)) { it / 3 } + fadeIn(tween(fadeMs))) togetherWith
                            (slideOutHorizontally(tween(fadeMs)) { -it / 4 } + fadeOut(tween(if (reduceMotion) 0 else 140)))
                    } else {
                        (slideInHorizontally(tween(enterMs)) { -it / 3 } + fadeIn(tween(fadeMs))) togetherWith
                            (slideOutHorizontally(tween(fadeMs)) { it / 4 } + fadeOut(tween(if (reduceMotion) 0 else 140)))
                    }
                },
                label = "weekContent"
            ) { displayedWeek ->
                var dragDistance by remember(displayedWeek) { mutableFloatStateOf(0f) }
                Column(
                    modifier = Modifier.fillMaxSize()
                        .pointerInput(displayedWeek, totalWeeks, swipeThresholdPx) {
                            detectHorizontalDragGestures(
                                onDragStart = { dragDistance = 0f },
                                onDragCancel = { dragDistance = 0f },
                                onDragEnd = {
                                    when {
                                        dragDistance <= -swipeThresholdPx && displayedWeek < totalWeeks -> onWeekChange(displayedWeek + 1)
                                        dragDistance >= swipeThresholdPx && displayedWeek > 1 -> onWeekChange(displayedWeek - 1)
                                    }
                                    dragDistance = 0f
                                }
                            ) { change, amount ->
                                dragDistance += amount
                                change.consume()
                            }
                        }
                        .verticalScroll(scrollState)
                        .testTag("schedule-scroll")
                ) {
                    TableGrid(
                        courses = remember(displayedCourses, displayedWeek, semesterStart, totalWeeks, excludedWeekSet, dateExceptions) {
                            val start = TimeUtils.semesterWeekStartOrNull(semesterStart)
                            if (start == null) emptyList() else (0L..6L).flatMap { dayOffset ->
                                val date = start.plusDays((displayedWeek - 1L) * 7L + dayOffset)
                                ScheduleDateResolver.coursesOn(
                                    displayedCourses, semesterStart, totalWeeks, excludedWeekSet, dateExceptions, date
                                ).filter { CourseSearch.filter(listOf(it.course), searchQuery).isNotEmpty() }
                                    .map { resolved -> resolved.course.copy(dayOfWeek = dayOffset.toInt() + 1) }
                            }
                        },
                        colorMap = colorMap,
                        visibleDays = visibleDays,
                        timeWidth = timeWidth,
                        cellHeight = cellHeight,
                        currentWeek = displayedWeek,
                        onCourseClick = onCourseClick,
                        onEmptyCellClick = if (readOnlyMessage == null) onAddCourseAt else ({ _, _ -> }),
                        periodTimes = periodTimes,
                        dark = dark,
                        cTextColor = cTextColor,
                        cSubColor = cSubColor,
                        todayWeek = todayWeek,
                        todayDow = todayDow,
                        viewMode = viewMode,
                        hasCustomBackground = customBackground != null
                    )
                    // Lets the final period scroll clear of rounded display corners and
                    // gesture navigation areas on compact phones.
                    Spacer(Modifier.height(48.dp))
                }
            }
        }
        }
    }
    }
}

@Composable
private fun DayHeader(
    visibleDays: List<Int>,
    timeWidth: Dp,
    currentWeek: Int,
    semesterStart: String,
    isTodayWeek: Boolean,
    todayDow: Int,
    onDayClick: (Int) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth().height(48.dp)) {
        Box(modifier = Modifier.width(timeWidth).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.course_period_label), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        visibleDays.forEach { day ->
            key(day) {
                val isToday = isTodayWeek && day == todayDow
                val dayHeaderDescription = stringResource(
                    R.string.course_day_header_desc,
                    TimeUtils.getDayName(day),
                    TimeUtils.refDate(currentWeek, day, semesterStart)
                )
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight()
                        .background(if (isToday) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) else Color.Transparent)
                        .clickable { onDayClick(day) }
                        .semantics { contentDescription = dayHeaderDescription },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            TimeUtils.getDayName(day),
                            fontSize = 12.sp,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            lineHeight = 14.sp
                        )
                        Text(
                            TimeUtils.refDate(currentWeek, day, semesterStart),
                            fontSize = 10.sp,
                            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.EmptySchedule(
    onImportClick: () -> Unit,
    onAddCourseClick: () -> Unit,
    readOnly: Boolean = false
) {
    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(92.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.School, null, modifier = Modifier.size(46.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.course_empty_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                if (readOnly) stringResource(R.string.course_empty_read_only_hint) else stringResource(R.string.course_empty_hint),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
            BoxWithConstraints {
                val narrow = maxWidth < 340.dp
                val containerModifier = if (narrow) Modifier.width(200.dp) else Modifier
                val arrangement = if (narrow) Arrangement.spacedBy(10.dp) else Arrangement.spacedBy(12.dp)
                if (narrow) {
                    Column(modifier = containerModifier, verticalArrangement = arrangement) {
                        EmptyImportButton(onClick = onImportClick, modifier = Modifier.fillMaxWidth(), enabled = !readOnly)
                        EmptyManualButton(onClick = onAddCourseClick, modifier = Modifier.fillMaxWidth(), enabled = !readOnly)
                    }
                } else {
                    Row(horizontalArrangement = arrangement) {
                        EmptyImportButton(onClick = onImportClick, enabled = !readOnly)
                        EmptyManualButton(onClick = onAddCourseClick, enabled = !readOnly)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyImportButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(12.dp), modifier = modifier.height(48.dp)) {
        Icon(Icons.Default.FileOpen, null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.course_empty_import_button), maxLines = 1)
    }
}

@Composable
private fun EmptyManualButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val contentAlpha = if (enabled) 1f else 0.38f
    Surface(
        modifier = modifier.height(48.dp).clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = contentAlpha)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.AddCircleOutline, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.course_empty_manual_button), maxLines = 1, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha))
        }
    }
}

@Composable
private fun TableGrid(
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
                    Text(section.name, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = sectionText)
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
    val backgroundAlpha = when {
        !hasCustomBackground && isDarkCourseCard -> 0.96f
        !hasCustomBackground -> 0.82f
        isDarkCourseCard -> 0.86f
        else -> 0.74f
    }
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
