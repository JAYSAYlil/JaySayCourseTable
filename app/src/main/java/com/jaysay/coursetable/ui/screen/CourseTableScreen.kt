package com.jaysay.coursetable.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.CalendarViewDay
import androidx.compose.material.icons.filled.CalendarViewMonth
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.EventBusy
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jaysay.coursetable.R
import com.jaysay.coursetable.data.model.AcademicCalendarDayStatus
import com.jaysay.coursetable.data.model.AcademicCalendarStatusResolver
import com.jaysay.coursetable.data.model.AcademicCalendarWeekStatus
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
import com.jaysay.coursetable.ui.theme.AppShapes
import com.jaysay.coursetable.ui.theme.Motion
import com.jaysay.coursetable.ui.theme.buildCourseColorMap
import com.jaysay.coursetable.util.TimeUtils
import java.time.LocalDate


/**
 * 今日摘要的分钟级订阅只发生在本组件作用域内：
 * 每 30 秒只重组摘要组件，课表网格与课程卡片不随分钟刷新重组。
 */
/** 视图模式对应的切换按钮图标：月视图用整月日历图标，其余沿用原图标。 */
private fun viewModeIcon(mode: ScheduleViewMode) = when (mode) {
    ScheduleViewMode.DAY -> Icons.Default.CalendarViewDay
    ScheduleViewMode.MONTH -> Icons.Default.CalendarViewMonth
    else -> Icons.Default.ViewWeek
}

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
private fun TodayOverviewSection(
    tableName: String,
    courses: List<Course>,
    periodTimes: List<PeriodTime>,
    semesterStart: String,
    totalWeeks: Int,
    excludedWeekSet: Set<Int>,
    dateExceptions: List<ScheduleDateException>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onTableMenuClick: () -> Unit,
    onAddCourseClick: () -> Unit,
    onImportClick: () -> Unit,
    onLocateToday: () -> Unit,
    onAgendaClick: () -> Unit,
    onSettingsClick: () -> Unit,
    writesEnabled: Boolean
) {
    // 分钟订阅与本组件同作用域：每 30 秒只有本节（今日摘要条）随分钟重组，
    // 屏幕顶层不读取分钟状态，课表网格与课程卡片不受影响。
    val agenda = rememberTodayAgenda(courses, periodTimes, semesterStart, totalWeeks, excludedWeekSet, dateExceptions)
    ScheduleOverviewBar(
        tableName = tableName,
        agenda = agenda,
        searchQuery = searchQuery,
        onSearchQueryChange = onSearchQueryChange,
        onTableMenuClick = onTableMenuClick,
        onAddCourseClick = onAddCourseClick,
        onImportClick = onImportClick,
        onLocateToday = onLocateToday,
        onAgendaClick = onAgendaClick,
        onSettingsClick = onSettingsClick,
        writesEnabled = writesEnabled
    )
}

@Composable
fun CourseTableScreen(
    courses: List<Course>,
    currentWeek: Int,
    onImportClick: () -> Unit,
    onCourseClick: (Course) -> Unit,
    onWeekChange: (Int) -> Unit,
    onSettingsClick: () -> Unit = {},
    onCalendarContextClick: () -> Unit = {},
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
    customBackground: ImageBitmap? = null,
    customBackgroundOverlayEnabled: Boolean = true,
    viewMode: ScheduleViewMode,
    onViewModeChange: (ScheduleViewMode) -> Unit,
    focusedDay: Int,
    onFocusedDayChange: (Int) -> Unit,
    onAgendaClick: () -> Unit = {},
    readOnlyMessage: String? = null,
    onRecoveryClick: () -> Unit = {}
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.4f
    val bgColor = MaterialTheme.colorScheme.background

    val today = LocalDate.now()
    val todayWeek = remember(semesterStart, totalWeeks, today) {
        TodayAgendaCalculator.semesterWeek(semesterStart, totalWeeks, today) ?: -1
    }
    val todayDow = today.dayOfWeek.value
    val excludedWeekSet = remember(excludedWeeks) { excludedWeeks.toSet() }
    var searchQuery by remember { mutableStateOf("") }
    val displayedCourses = remember(courses, searchQuery) { CourseSearch.filter(courses, searchQuery) }

    var viewMenuExpanded by remember { mutableStateOf(false) }
    val visibleDays = remember(viewMode, focusedDay) {
        when (viewMode) {
            ScheduleViewMode.WEEK -> (1..7).toList()
            ScheduleViewMode.WORK_WEEK -> (1..5).toList()
            ScheduleViewMode.DAY -> listOf(focusedDay.coerceIn(1, 7))
            ScheduleViewMode.MONTH -> (1..7).toList()
        }
    }
    val timeWidth = when (viewMode) {
        ScheduleViewMode.WEEK -> 38.dp
        ScheduleViewMode.WORK_WEEK -> 44.dp
        ScheduleViewMode.DAY -> 58.dp
        ScheduleViewMode.MONTH -> 38.dp
    }
    // The seven-day columns are narrow, so they need more vertical room to show
    // course, teacher and classroom text without truncation.
    val cellHeight = when (viewMode) {
        ScheduleViewMode.WEEK -> 106.dp
        ScheduleViewMode.WORK_WEEK -> 116.dp
        ScheduleViewMode.DAY -> 100.dp
        ScheduleViewMode.MONTH -> 106.dp
    }
    val weekCourses = remember(displayedCourses, currentWeek, excludedWeekSet) {
        displayedCourses.filter { currentWeek in it.weeks && currentWeek !in excludedWeekSet }
    }
    val weekStatus = remember(currentWeek, semesterStart, totalWeeks, excludedWeekSet, dateExceptions, weekLabels) {
        AcademicCalendarStatusResolver.week(
            currentWeek, semesterStart, totalWeeks, excludedWeekSet, dateExceptions, weekLabels
        )
    }
    val dayStatuses = remember(currentWeek, semesterStart, totalWeeks, excludedWeekSet, dateExceptions, weekLabels) {
        val start = TimeUtils.semesterWeekStartOrNull(semesterStart)
        if (start == null) emptyMap() else (1..7).associateWith { day ->
            val date = start.plusDays((currentWeek - 1L) * 7L + day - 1L)
            AcademicCalendarStatusResolver.day(
                date, semesterStart, totalWeeks, excludedWeekSet, dateExceptions, weekLabels
            )
        }
    }
    val colorMap = remember(courses, dark) { buildCourseColorMap(courses, dark) }
    val isTodayWeek = currentWeek == todayWeek
    val weekControlTint = MaterialTheme.colorScheme.primary
    val weekDisabledTint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (dark) 0.38f else 0.3f)

    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        Box(modifier = Modifier.fillMaxSize().background(bgColor)) {
            if (customBackground != null) {
                CustomBackgroundImage(customBackground)
                if (customBackgroundOverlayEnabled) {
                    Box(
                        Modifier.fillMaxSize().background(
                            if (dark) Color.Black.copy(alpha = 0.48f)
                            else Color.White.copy(alpha = 0.42f)
                        )
                    )
                }
            }
            Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .statusBarsPadding()
                .navigationBarsPadding()
                .testTag("course-table-screen")
        ) {
        TodayOverviewSection(
            tableName = tableName,
            courses = courses,
            periodTimes = periodTimes,
            semesterStart = semesterStart,
            totalWeeks = totalWeeks,
            excludedWeekSet = excludedWeekSet,
            dateExceptions = dateExceptions,
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

        readOnlyMessage?.let { message ->
            ReadOnlyRecoveryBanner(message = message, onRecoveryClick = onRecoveryClick)
        }

        if (courses.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.weight(1f).fillMaxHeight().testTag("week-navigation"),
                    shape = AppShapes.medium,
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
                                text = when {
                                    weekStatus.suspended && weekStatus.label != null -> stringResource(
                                        R.string.course_week_label_suspended, weekStatus.label
                                    )
                                    weekStatus.suspended -> stringResource(R.string.course_calendar_context_suspended)
                                    weekStatus.label != null -> stringResource(
                                        R.string.course_week_label_count, weekStatus.label, weekCourses.size
                                    )
                                    weekStatus.dateAdjustmentCount > 0 -> stringResource(
                                        R.string.course_week_adjustment_count,
                                        weekCourses.size,
                                        weekStatus.dateAdjustmentCount
                                    )
                                    else -> stringResource(R.string.course_week_course_count, weekCourses.size) +
                                        if (isTodayWeek) stringResource(R.string.course_week_today_suffix) else ""
                                },
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
                        shape = AppShapes.medium,
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
                                viewModeIcon(viewMode),
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
                                        viewModeIcon(mode),
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(2.dp).clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = if (dark) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // 停课/特殊安排提示条：切到相关周次时快速展开淡入，离开时立即收起。
            // 快速左右滑周时提示条会连续进出，必须用短补间（收起 100ms/展开 180ms），
            // 弹簧的低刚度拖尾会让板块在连续翻周时显得反应迟钝。
            AnimatedVisibility(
                visible = weekStatus.hasCalendarContext,
                enter = fadeIn(tween(Motion.DURATION_SHORT, easing = Motion.standard)) +
                    expandVertically(tween(Motion.DURATION_SHORT + 30, easing = Motion.standard)),
                exit = fadeOut(tween(90, easing = Motion.exit)) +
                    shrinkVertically(tween(100, easing = Motion.exit))
            ) {
                CalendarContextStrip(weekStatus, onCalendarContextClick)
            }

            if (viewMode == ScheduleViewMode.DAY) {
                DayChipRow(focusedDay = focusedDay, onFocusedDayChange = onFocusedDayChange)
            }

            // 月视图不展示课程表头（星期标题由月历自带），周导航胶囊与进度条仍然保留。
            if (viewMode != ScheduleViewMode.MONTH) {
                DayHeader(
                    visibleDays = visibleDays,
                    timeWidth = timeWidth,
                    currentWeek = currentWeek,
                    semesterStart = semesterStart,
                    isTodayWeek = isTodayWeek,
                    todayDow = todayDow,
                    dayStatuses = dayStatuses,
                    onDayClick = { day ->
                        onFocusedDayChange(day)
                        onViewModeChange(ScheduleViewMode.DAY)
                    }
                )
                HorizontalDivider(thickness = 0.75.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
            }
        }

        if (courses.isEmpty()) {
            EmptySchedule(
                onImportClick = if (readOnlyMessage == null) onImportClick else ({}),
                onAddCourseClick = if (readOnlyMessage == null) onAddCourseClick else ({}),
                readOnly = readOnlyMessage != null
            )
        } else if (viewMode == ScheduleViewMode.MONTH) {
            // 月视图：整月网格放纵向滚动容器里（格子约 74dp，一般整月一屏放得下）。
            // 不做横向翻月——周导航箭头切换周次时，月份跟随锚点周自动变化。
            val monthScrollState = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f)
                    .verticalScroll(monthScrollState)
                    .testTag("month-grid")
            ) {
                MonthGrid(
                    courses = displayedCourses,
                    currentWeek = currentWeek,
                    totalWeeks = totalWeeks,
                    semesterStart = semesterStart,
                    excludedWeekSet = excludedWeekSet,
                    dateExceptions = dateExceptions,
                    dark = dark,
                    onDayClick = { semesterWeek, dayOfWeek ->
                        // 点击某天：跳到该天所在周并切到单日视图。
                        onWeekChange(semesterWeek)
                        onFocusedDayChange(dayOfWeek)
                        onViewModeChange(ScheduleViewMode.DAY)
                    }
                )
                Spacer(Modifier.height(48.dp))
            }
        } else {
            WeekPagerSection(
                modifier = Modifier.fillMaxWidth().weight(1f),
                displayedCourses = displayedCourses,
                colorMap = colorMap,
                visibleDays = visibleDays,
                timeWidth = timeWidth,
                cellHeight = cellHeight,
                currentWeek = currentWeek,
                totalWeeks = totalWeeks,
                onWeekChange = onWeekChange,
                onCourseClick = onCourseClick,
                onEmptyCellClick = if (readOnlyMessage == null) onAddCourseAt else ({ _, _ -> }),
                periodTimes = periodTimes,
                dark = dark,
                todayWeek = todayWeek,
                todayDow = todayDow,
                viewMode = viewMode,
                hasCustomBackground = customBackground != null,
                searchQuery = searchQuery,
                onClearSearch = { searchQuery = "" },
                semesterStart = semesterStart,
                excludedWeekSet = excludedWeekSet,
                dateExceptions = dateExceptions,
                weekLabels = weekLabels
            )
        }
        }
    }
    }
}

/**
 * 周次翻页：HorizontalPager 提供跟手滑动、边缘回弹与翻页联动；
 * 纵向滚动状态独立于周次，翻页时保持阅读位置。
 */
@Composable
private fun WeekPagerSection(
    modifier: Modifier,
    displayedCourses: List<Course>,
    colorMap: Map<String, Color>,
    visibleDays: List<Int>,
    timeWidth: Dp,
    cellHeight: Dp,
    currentWeek: Int,
    totalWeeks: Int,
    onWeekChange: (Int) -> Unit,
    onCourseClick: (Course) -> Unit,
    onEmptyCellClick: (Int, Int) -> Unit,
    periodTimes: List<PeriodTime>,
    dark: Boolean,
    todayWeek: Int,
    todayDow: Int,
    viewMode: ScheduleViewMode,
    hasCustomBackground: Boolean,
    searchQuery: String,
    onClearSearch: () -> Unit,
    semesterStart: String,
    excludedWeekSet: Set<Int>,
    dateExceptions: List<ScheduleDateException>,
    weekLabels: Map<Int, String>
) {
    val onWeekChangeState by rememberUpdatedState(onWeekChange)
    val currentWeekState by rememberUpdatedState(currentWeek)
    val pagerState = rememberPagerState(
        initialPage = (currentWeek - 1).coerceIn(0, (totalWeeks - 1).coerceAtLeast(0))
    ) { totalWeeks.coerceAtLeast(1) }

    // 用户滑动结束后回写周次；外部（箭头/定位今天）改变周次时滚动到对应页。
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            if (page + 1 != currentWeekState) onWeekChangeState(page + 1)
        }
    }
    LaunchedEffect(currentWeek, totalWeeks) {
        val target = (currentWeek - 1).coerceIn(0, totalWeeks - 1)
        if (pagerState.settledPage != target && !pagerState.isScrollInProgress) {
            pagerState.animateScrollToPage(target)
        }
    }

    // 纵向滚动状态独立于周次：翻页时保持当前位置不重置，
    // 离开详情页或 Activity 重建时也能恢复到原位置。
    val scrollState = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }
    HorizontalPager(
        state = pagerState,
        modifier = modifier.testTag("week-swipe-area"),
        key = { it + 1 },
        beyondViewportPageCount = 0
    ) { pageIndex ->
        val displayedWeek = pageIndex + 1
        val displayedStatus = remember(
            displayedWeek, semesterStart, totalWeeks, excludedWeekSet, dateExceptions, weekLabels
        ) {
            AcademicCalendarStatusResolver.week(
                displayedWeek, semesterStart, totalWeeks, excludedWeekSet, dateExceptions, weekLabels
            )
        }
        val resolvedCourses = remember(
            displayedCourses, displayedWeek, semesterStart, totalWeeks,
            excludedWeekSet, dateExceptions, searchQuery
        ) {
            val start = TimeUtils.semesterWeekStartOrNull(semesterStart)
            if (start == null) emptyList() else (0L..6L).flatMap { dayOffset ->
                val date = start.plusDays((displayedWeek - 1L) * 7L + dayOffset)
                ScheduleDateResolver.coursesOn(
                    displayedCourses, semesterStart, totalWeeks, excludedWeekSet, dateExceptions, date
                ).filter { CourseSearch.filter(listOf(it.course), searchQuery).isNotEmpty() }
                    .map { resolved -> resolved.course.copy(dayOfWeek = dayOffset.toInt() + 1) }
            }
        }
        when {
            displayedStatus.suspended && displayedStatus.makeupCount == 0 -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp).testTag("suspended-week-content"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Outlined.EventBusy,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(38.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        displayedStatus.label ?: stringResource(
                            R.string.course_week_suspended, displayedWeek
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.course_week_suspended_hint),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            displayedCourses.isEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        stringResource(R.string.course_search_no_match, searchQuery),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = onClearSearch) {
                        Text(stringResource(R.string.course_search_clear))
                    }
                }
            }
            else -> {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(scrollState)
                        .testTag("schedule-scroll")
                ) {
                    TableGrid(
                        courses = resolvedCourses,
                        colorMap = colorMap,
                        visibleDays = visibleDays,
                        timeWidth = timeWidth,
                        cellHeight = cellHeight,
                        currentWeek = displayedWeek,
                        onCourseClick = onCourseClick,
                        onEmptyCellClick = onEmptyCellClick,
                        periodTimes = periodTimes,
                        dark = dark,
                        todayWeek = todayWeek,
                        todayDow = todayDow,
                        viewMode = viewMode,
                        hasCustomBackground = hasCustomBackground
                    )
                    // 底部 48dp 收尾：让最后一节的时间标签能滚出圆角屏 R 角与手势区，
                    // 空隙本身透明，不遮挡自定义背景。
                    Spacer(Modifier.height(48.dp))
                }
            }
        }
    }
}

@Composable
private fun DayChipRow(focusedDay: Int, onFocusedDayChange: (Int) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp, vertical = 2.dp)) {
        for (day in 1..7) {
            val selected = day == focusedDay
            val chipColor by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                animationSpec = Motion.eased(),
                label = "dayChip"
            )
            val dayChipDescription = stringResource(R.string.course_view_day_desc, TimeUtils.getDayName(day))
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight().clip(AppShapes.small)
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

@Composable
private fun CalendarContextStrip(
    status: AcademicCalendarWeekStatus,
    onClick: () -> Unit
) {
    val details = buildList {
        if (status.suspended) add(stringResource(R.string.course_calendar_detail_suspended))
        if (status.dayOffCount > 0) add(stringResource(R.string.course_calendar_detail_day_off, status.dayOffCount))
        if (status.cancelledCount > 0) add(stringResource(R.string.course_calendar_detail_cancelled, status.cancelledCount))
        if (status.makeupCount > 0) add(stringResource(R.string.course_calendar_detail_makeup, status.makeupCount))
    }
    val title = status.label ?: if (status.suspended) {
        stringResource(R.string.course_calendar_context_suspended)
    } else {
        stringResource(R.string.course_calendar_context_default)
    }
    val icon = when {
        status.suspended -> Icons.Outlined.EventBusy
        status.label != null -> Icons.AutoMirrored.Outlined.Label
        else -> Icons.Outlined.EditCalendar
    }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)
            .testTag("calendar-context-strip")
            .clickable(onClick = onClick),
        shape = AppShapes.small,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
        border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                if (details.isNotEmpty()) {
                    Text(
                        details.joinToString(" · "),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 10.sp,
                        maxLines = 1
                    )
                }
            }
            Icon(
                Icons.Outlined.ChevronRight,
                stringResource(R.string.course_calendar_context_open),
                tint = MaterialTheme.colorScheme.primary
            )
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
    dayStatuses: Map<Int, AcademicCalendarDayStatus>,
    onDayClick: (Int) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth().height(48.dp)) {
        Box(modifier = Modifier.width(timeWidth).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.course_period_label), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        visibleDays.forEach { day ->
            key(day) {
                val isToday = isTodayWeek && day == todayDow
                val dayStatus = dayStatuses[day]
                val baseDescription = stringResource(
                    R.string.course_day_header_desc,
                    TimeUtils.getDayName(day),
                    TimeUtils.refDate(currentWeek, day, semesterStart)
                )
                val dayHeaderDescription = if (dayStatus?.hasDateAdjustment == true) {
                    stringResource(R.string.course_day_header_adjustment_desc, baseDescription)
                } else baseDescription
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                TimeUtils.refDate(currentWeek, day, semesterStart),
                                fontSize = 10.sp,
                                color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 12.sp
                            )
                            if (dayStatus?.hasDateAdjustment == true) {
                                Spacer(Modifier.width(3.dp))
                                Box(
                                    Modifier.size(5.dp).clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.tertiary)
                                )
                            }
                        }
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
    Button(onClick = onClick, enabled = enabled, shape = AppShapes.small, modifier = modifier.height(48.dp)) {
        Icon(Icons.Default.FileOpen, null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.course_empty_import_button), maxLines = 1)
    }
}

@Composable
private fun EmptyManualButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val contentAlpha = if (enabled) 1f else 0.38f
    Surface(
        modifier = modifier.height(48.dp).clip(AppShapes.small).clickable(enabled = enabled, onClick = onClick),
        shape = AppShapes.small,
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
