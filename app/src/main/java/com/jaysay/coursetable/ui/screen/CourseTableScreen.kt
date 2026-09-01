package com.jaysay.coursetable.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.CalendarViewDay
import androidx.compose.material.icons.rounded.CalendarViewMonth
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ViewWeek
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.EditCalendar
import androidx.compose.material.icons.rounded.EventBusy
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.platform.LocalView
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
import com.jaysay.coursetable.ui.theme.pressScale
import com.jaysay.coursetable.ui.theme.buildCourseColorMap
import com.jaysay.coursetable.util.TimeUtils
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import java.time.LocalDate


/**
 * 今日摘要的分钟级订阅只发生在本组件作用域内：
 * 每 30 秒只重组摘要组件，课表网格与课程卡片不随分钟刷新重组。
 */
/** 视图模式对应的切换按钮图标：月视图用整月日历图标，其余沿用原图标。 */
private fun viewModeIcon(mode: ScheduleViewMode) = when (mode) {
    ScheduleViewMode.DAY -> Icons.Rounded.CalendarViewDay
    ScheduleViewMode.MONTH -> Icons.Rounded.CalendarViewMonth
    else -> Icons.Rounded.ViewWeek
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
    val navigationHapticView = LocalView.current

    val today = LocalDate.now()
    val todayWeek = remember(semesterStart, totalWeeks, today) {
        TodayAgendaCalculator.semesterWeek(semesterStart, totalWeeks, today) ?: -1
    }
    val todayDow = today.dayOfWeek.value
    val excludedWeekSet = remember(excludedWeeks) { excludedWeeks.toSet() }
    var searchQuery by remember { mutableStateOf("") }

    // 月视图锚点：epoch 天数，0 表示未初始化（进入月视图时按当前周所在月份重建）。
    var monthAnchorEpoch by rememberSaveable { androidx.compose.runtime.mutableLongStateOf(0L) }
    LaunchedEffect(viewMode) {
        if (viewMode == ScheduleViewMode.MONTH) monthAnchorEpoch = 0L
    }
    // 日视图来源：从月视图点日期进入时，系统返回手势应切回月视图（回到原月份）；
    // 从视图菜单或表头点击进入日视图时，返回行为保持原样。
    var dayOpenedFromMonth by rememberSaveable { mutableStateOf(false) }
    // 日视图重新实现：Pager 页码是日期唯一真源。顶部、星期条、课程内容直接读取
    // 同一个 controller；定位/日期点击只是命令，不再额外保存预览与落定两套日期。
    val dayController = rememberDayViewController(
        semesterStart = semesterStart,
        totalWeeks = totalWeeks,
        currentWeek = currentWeek,
        focusedDay = focusedDay
    )
    dayController?.let { controller ->
        DayViewControllerEffects(
            controller = controller,
            semesterStart = semesterStart,
            totalWeeks = totalWeeks,
            onWeekChange = onWeekChange,
            onFocusedDayChange = onFocusedDayChange
        )
    }
    val fallbackDayDate = remember(semesterStart, totalWeeks, currentWeek, focusedDay) {
        val start = TimeUtils.semesterWeekStartOrNull(semesterStart)
        start?.plusDays(((currentWeek.coerceAtLeast(1) - 1) * 7L) + focusedDay.coerceIn(1, 7) - 1L)
            ?: LocalDate.now()
    }
    val dayPreviewDate = dayController?.displayDate ?: fallbackDayDate
    fun requestDayDate(date: LocalDate) {
        dayController?.navigateTo(date)
    }
    val displayedWeek = if (viewMode == ScheduleViewMode.DAY) {
        TimeUtils.semesterWeekOrNull(semesterStart, totalWeeks, dayPreviewDate) ?: currentWeek
    } else {
        currentWeek
    }
        ?: currentWeek
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
        ScheduleViewMode.WEEK -> 46.dp
        ScheduleViewMode.WORK_WEEK -> 50.dp
        ScheduleViewMode.DAY -> 64.dp
        ScheduleViewMode.MONTH -> 46.dp
    }
    // The seven-day columns are narrow, so they need more vertical room to show
    // course, teacher and classroom text without truncation.
    val cellHeight = when (viewMode) {
        ScheduleViewMode.WEEK -> 106.dp
        ScheduleViewMode.WORK_WEEK -> 116.dp
        ScheduleViewMode.DAY -> 100.dp
        ScheduleViewMode.MONTH -> 106.dp
    }
    // Header counts must use the same date resolver as the rendered grid so that
    // cancellations, suspended weeks and makeup classes stay in sync in every view.
    val calendarWeek = if (viewMode == ScheduleViewMode.DAY) displayedWeek else currentWeek
    val weekCourses = remember(
        displayedCourses, calendarWeek, semesterStart, totalWeeks, excludedWeekSet, dateExceptions
    ) {
        val start = TimeUtils.semesterWeekStartOrNull(semesterStart)
        if (start == null) emptyList() else (0L..6L).flatMap { dayOffset ->
            val date = start.plusDays((calendarWeek - 1L) * 7L + dayOffset)
            ScheduleDateResolver.coursesOn(
                displayedCourses, semesterStart, totalWeeks, excludedWeekSet, dateExceptions, date
            ).map { it.course }
        }
    }
    val weekStatus = remember(calendarWeek, semesterStart, totalWeeks, excludedWeekSet, dateExceptions, weekLabels) {
        AcademicCalendarStatusResolver.week(
            calendarWeek, semesterStart, totalWeeks, excludedWeekSet, dateExceptions, weekLabels
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
    // 月视图跨度：从开学月份到学期最后一个月。
    val semesterStartDate = TimeUtils.semesterWeekStartOrNull(semesterStart)
    val firstMonthStart = semesterStartDate?.withDayOfMonth(1)
    val lastMonthStart = semesterStartDate?.plusDays(totalWeeks * 7L - 1L)?.withDayOfMonth(1)
    fun monthIndexOf(date: LocalDate): Int =
        if (firstMonthStart == null) 0
        else ((date.year - firstMonthStart.year) * 12 + (date.monthValue - firstMonthStart.monthValue)).coerceAtLeast(0)
    val monthAnchorDate = if (monthAnchorEpoch != 0L) {
        LocalDate.ofEpochDay(monthAnchorEpoch).withDayOfMonth(1)
    } else {
        (semesterStartDate?.plusDays((currentWeek - 1L).coerceAtLeast(0) * 7) ?: LocalDate.now()).withDayOfMonth(1)
    }
    val monthAnchorIndex = monthIndexOf(monthAnchorDate)
    val monthCount = if (firstMonthStart == null || lastMonthStart == null) 1 else (monthIndexOf(lastMonthStart) + 1).coerceAtLeast(1)
    fun monthOf(index: Int): LocalDate = firstMonthStart?.plusMonths(index.toLong()) ?: monthAnchorDate
    val monthCourseCount = remember(
        viewMode, displayedCourses, monthAnchorDate, semesterStart, totalWeeks,
        excludedWeekSet, dateExceptions
    ) {
        if (viewMode != ScheduleViewMode.MONTH || firstMonthStart == null) 0
        else {
            // 统计本月实际上课节次（每天的课程条目总和，含同课程多班次）。
            // 月视图重组（例如打开菜单）时复用结果，避免重复解析整月日期异常。
            (0 until monthAnchorDate.lengthOfMonth()).sumOf { dayOffset ->
                ScheduleDateResolver.coursesOn(
                    displayedCourses, semesterStart, totalWeeks, excludedWeekSet, dateExceptions,
                    monthAnchorDate.plusDays(dayOffset.toLong())
                ).size
            }
        }
    }

    val isTodayWeek = currentWeek == todayWeek
    val weekControlTint = MaterialTheme.colorScheme.primary
    val weekDisabledTint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (dark) 0.38f else 0.3f)

    if (viewMode == ScheduleViewMode.DAY && dayOpenedFromMonth) {
        BackHandler {
            // 返回到当前显示日期所在月份的月视图。
            monthAnchorEpoch = dayPreviewDate.withDayOfMonth(1).toEpochDay()
            onViewModeChange(ScheduleViewMode.MONTH)
        }
    }

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
            onLocateToday = {
                // 月视图有独立的月份锚点；定位今天时同步月份，否则只回写周次不会改变月页。
                if (viewMode == ScheduleViewMode.MONTH) {
                    monthAnchorEpoch = today.withDayOfMonth(1).toEpochDay()
                }
                if (viewMode == ScheduleViewMode.DAY) {
                    // Controller 自己负责学期边界钳制；新的定位命令会立即打断旧手势/动画。
                    requestDayDate(today)
                }
                onLocateToday()
            },
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
                            onClick = {
                                if (viewMode == ScheduleViewMode.MONTH) {
                                    monthAnchorEpoch = monthAnchorDate.minusMonths(1).toEpochDay()
                                } else if (viewMode == ScheduleViewMode.DAY) {
                                    requestDayDate(dayPreviewDate.minusWeeks(1))
                                } else {
                                    onWeekChange(currentWeek - 1)
                                }
                            },
                            enabled = when (viewMode) {
                                ScheduleViewMode.MONTH -> monthAnchorIndex > 0
                                ScheduleViewMode.DAY -> displayedWeek > 1
                                else -> currentWeek > 1
                            },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                Icons.Rounded.ChevronLeft,
                                stringResource(if (viewMode == ScheduleViewMode.MONTH) R.string.month_prev_month else R.string.course_prev_week),
                                tint = if (viewMode == ScheduleViewMode.MONTH) {
                                    if (monthAnchorIndex > 0) weekControlTint else weekDisabledTint
                                } else if ((if (viewMode == ScheduleViewMode.DAY) displayedWeek else currentWeek) > 1) {
                                    weekControlTint
                                } else weekDisabledTint,
                                modifier = Modifier.size(27.dp)
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            if (viewMode == ScheduleViewMode.MONTH) {
                                Text(
                                    text = stringResource(
                                        R.string.month_header_title,
                                        monthAnchorDate.year,
                                        monthAnchorDate.monthValue
                                    ),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                                Text(
                                    text = stringResource(R.string.month_course_count, monthCourseCount),
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.course_week_number, displayedWeek),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                            }
                            if (viewMode != ScheduleViewMode.MONTH) Text(
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
                                        if ((if (viewMode == ScheduleViewMode.DAY) displayedWeek else currentWeek) == todayWeek) {
                                            stringResource(R.string.course_week_today_suffix)
                                        } else ""
                                    },
                                fontWeight = FontWeight.Medium,
                                fontSize = 10.sp,
                                color = if ((if (viewMode == ScheduleViewMode.DAY) displayedWeek else currentWeek) == todayWeek) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        IconButton(
                            onClick = {
                                if (viewMode == ScheduleViewMode.MONTH) {
                                    monthAnchorEpoch = monthAnchorDate.plusMonths(1).toEpochDay()
                                } else if (viewMode == ScheduleViewMode.DAY) {
                                    requestDayDate(dayPreviewDate.plusWeeks(1))
                                } else {
                                    onWeekChange(currentWeek + 1)
                                }
                            },
                            enabled = when (viewMode) {
                                ScheduleViewMode.MONTH -> monthAnchorIndex < monthCount - 1
                                ScheduleViewMode.DAY -> displayedWeek < totalWeeks
                                else -> currentWeek < totalWeeks
                            },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                Icons.Rounded.ChevronRight,
                                stringResource(if (viewMode == ScheduleViewMode.MONTH) R.string.month_next_month else R.string.course_next_week),
                                tint = if (viewMode == ScheduleViewMode.MONTH) {
                                    if (monthAnchorIndex < monthCount - 1) weekControlTint else weekDisabledTint
                                } else if ((if (viewMode == ScheduleViewMode.DAY) displayedWeek else currentWeek) < totalWeeks) {
                                    weekControlTint
                                } else weekDisabledTint,
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
                    DropdownMenu(
                        expanded = viewMenuExpanded,
                        onDismissRequest = { viewMenuExpanded = false },
                        shape = AppShapes.medium,
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp,
                        shadowElevation = 12.dp,
                        border = BorderStroke(
                            0.75.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
                        )
                    ) {
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
                                    val modeChanged = mode != viewMode
                                    dayOpenedFromMonth = false
                                    if (mode == ScheduleViewMode.DAY) {
                                        val target = if (isTodayWeek && todayDow in 1..7) {
                                            today
                                        } else {
                                            val start = TimeUtils.semesterWeekStartOrNull(semesterStart)
                                            start?.plusDays((currentWeek - 1L) * 7L + focusedDay.coerceIn(1, 7) - 1L)
                                                ?: dayPreviewDate
                                        }
                                        requestDayDate(target)
                                    }
                                    onViewModeChange(mode)
                                    if (mode == ScheduleViewMode.DAY && isTodayWeek && todayDow in 1..7) {
                                        onFocusedDayChange(todayDow)
                                    }
                                    viewMenuExpanded = false
                                    if (modeChanged) {
                                        navigationHapticView.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            if (viewMode != ScheduleViewMode.MONTH) {
                val animatedWeekProgress by animateFloatAsState(
                    targetValue = displayedWeek.toFloat() / totalWeeks.coerceAtLeast(1),
                    animationSpec = Motion.interactive(),
                    label = "weekProgress"
                )
                LinearProgressIndicator(
                progress = { animatedWeekProgress },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(2.dp).clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = if (dark) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            }

            // 停课/特殊安排提示条：切到相关周次时快速展开淡入，离开时立即收起。
            // 快速左右滑周时提示条会连续进出，必须用短补间（收起 100ms/展开 180ms），
            // 弹簧的低刚度拖尾会让板块在连续翻周时显得反应迟钝。
            AnimatedVisibility(
                visible = weekStatus.hasCalendarContext,
                enter = fadeIn(tween(Motion.DURATION_SHORT, easing = Motion.standard)) +
                    expandVertically(Motion.momentum()),
                exit = fadeOut(tween(90, easing = Motion.exit)) +
                    shrinkVertically(Motion.interactive())
            ) {
                CalendarContextStrip(weekStatus, onCalendarContextClick)
            }

            if (viewMode == ScheduleViewMode.DAY) {
                val chipDate = dayPreviewDate
                val chipWeek = TimeUtils.semesterWeekOrNull(semesterStart, totalWeeks, chipDate)
                DayChipRow(
                    focusedDay = chipDate.dayOfWeek.value,
                    todayDow = todayDow,
                    highlightToday = chipWeek == todayWeek && todayWeek > 0,
                    onFocusedDayChange = { day ->
                        // 星期条是周内导航：跳到显示周的同星期数那一天。
                        val weekStart = chipDate.minusDays((chipDate.dayOfWeek.value - 1).toLong())
                        requestDayDate(weekStart.plusDays((day - 1).toLong()))
                        onFocusedDayChange(day)
                    }
                )
            }

            // 月视图不展示课程表头（星期标题由月历自带），周导航胶囊与进度条仍然保留。
            if (viewMode != ScheduleViewMode.MONTH) {
                val headerDate = if (viewMode == ScheduleViewMode.DAY) dayPreviewDate else fallbackDayDate
                val headerWeek = TimeUtils.semesterWeekOrNull(semesterStart, totalWeeks, headerDate) ?: currentWeek
                val headerDayStatuses = if (viewMode == ScheduleViewMode.DAY) {
                    mapOf(
                        headerDate.dayOfWeek.value to AcademicCalendarStatusResolver.day(
                            headerDate, semesterStart, totalWeeks, excludedWeekSet, dateExceptions, weekLabels
                        )
                    )
                } else {
                    dayStatuses
                }
                DayHeader(
                    visibleDays = if (viewMode == ScheduleViewMode.DAY) listOf(headerDate.dayOfWeek.value) else visibleDays,
                    timeWidth = timeWidth,
                    currentWeek = if (viewMode == ScheduleViewMode.DAY) headerWeek else currentWeek,
                    semesterStart = semesterStart,
                    isTodayWeek = if (viewMode == ScheduleViewMode.DAY) headerWeek == todayWeek && todayWeek > 0 else isTodayWeek,
                    todayDow = todayDow,
                    dayStatuses = headerDayStatuses,
                    onDayClick = { day ->
                        dayOpenedFromMonth = false
                        if (viewMode == ScheduleViewMode.DAY) {
                            val weekStart = headerDate.minusDays((headerDate.dayOfWeek.value - 1).toLong())
                            requestDayDate(weekStart.plusDays((day - 1).toLong()))
                        } else {
                            TimeUtils.semesterWeekStartOrNull(semesterStart)?.let { start ->
                                requestDayDate(start.plusDays((currentWeek - 1L) * 7L + day - 1L))
                            }
                        }
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
            // 月视图：左右滑动按月翻页，跨度为开学月份到学期最后一个月；
            // 头部胶囊显示“某年某月”，月内课程格沿用周视图同口径。
            val monthPagerState = rememberPagerState(
                initialPage = monthAnchorIndex.coerceIn(0, monthCount - 1)
            ) { monthCount }
            // Pager 是月份显示的唯一真源；锚点只用于外部按钮/定位今天的跳转请求。
            // 用 currentPage 做目标判断，避免 settledPage 更新与重组之间重复启动动画。
            LaunchedEffect(monthAnchorEpoch, monthCount) {
                if (monthAnchorEpoch == 0L) return@LaunchedEffect
                // 外部跳转不能抢占用户正在进行的拖拽；若请求发生在拖拽期间，
                // 等待 Pager 空闲后再处理最新目标，避免请求被静默丢弃。
                snapshotFlow { monthPagerState.isScrollInProgress }
                    .first { isScrolling -> !isScrolling }
                val target = monthIndexOf(LocalDate.ofEpochDay(monthAnchorEpoch))
                    .coerceIn(0, monthCount - 1)
                if (monthPagerState.currentPage != target && !monthPagerState.isScrollInProgress) {
                    monthPagerState.animateScrollToPage(target)
                }
            }
            val monthHapticView = LocalView.current
            var skipInitialMonthHaptic by remember { mutableStateOf(true) }
            // collect 协程捕获的 monthAnchorEpoch 是首次组合的旧值；滑动落定后外部
            // 状态已是最新，若与旧值比较会误判并回滚月份锚点。rememberUpdatedState
            // 保证协程内永远读到最新值（与日视图翻页器同一修复模式）。
            val currentMonthAnchor by androidx.compose.runtime.rememberUpdatedState(monthAnchorEpoch)
            LaunchedEffect(monthPagerState) {
                snapshotFlow { monthPagerState.settledPage }
                    .distinctUntilChanged()
                    .collect { page ->
                        val epoch = monthOf(page).toEpochDay()
                        if (epoch != currentMonthAnchor) {
                            monthAnchorEpoch = epoch
                        }
                        if (skipInitialMonthHaptic) {
                            skipInitialMonthHaptic = false
                        } else {
                            monthHapticView.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                        }
                    }
            }
            val monthFling = PagerDefaults.flingBehavior(
                state = monthPagerState,
                // 月视图同样保持“一次手势一页”，避免快速甩动越过月份后
                // 外部锚点回写与 Pager 产生竞态。
                pagerSnapDistance = PagerSnapDistance.atMost(1),
                snapAnimationSpec = spring(dampingRatio = 1f, stiffness = 300f),
                decayAnimationSpec = exponentialDecay(frictionMultiplier = 12f)
            )
            HorizontalPager(
                state = monthPagerState,
                flingBehavior = monthFling,
                modifier = Modifier.fillMaxWidth().weight(1f).testTag("month-swipe-area"),
                // 月份页面内容是纯快照，关闭额外预加载可避免页面重组时复用旧月份状态。
                beyondViewportPageCount = 0,
                key = { page -> monthOf(page).toEpochDay() }
            ) { page ->
                val pageMonthStart = monthOf(page)
                MonthGrid(
                    modifier = Modifier.fillMaxSize().testTag("month-grid"),
                    courses = displayedCourses,
                    monthStart = pageMonthStart,
                    totalWeeks = totalWeeks,
                    semesterStart = semesterStart,
                    excludedWeekSet = excludedWeekSet,
                    dateExceptions = dateExceptions,
                    weekLabels = weekLabels,
                    dark = dark,
                    onDayClick = { date ->
                        // 点击某天：跳到该天所在周并切到单日视图；记住来源月供返回使用。
                        dayOpenedFromMonth = true
                        monthAnchorEpoch = pageMonthStart.toEpochDay()
                        requestDayDate(date)
                        onWeekChange(TimeUtils.semesterWeekOrNull(semesterStart, totalWeeks, date) ?: 1)
                        onFocusedDayChange(date.dayOfWeek.value)
                        onViewModeChange(ScheduleViewMode.DAY)
                    }
                )
            }
        } else if (viewMode == ScheduleViewMode.DAY) {
            // 日视图：左右滑动按天翻页，一周滑完自动切到下一周。
            dayController?.let { controller ->
                DaySchedulePager(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    controller = controller,
                    displayedCourses = displayedCourses,
                    colorMap = colorMap,
                    timeWidth = timeWidth,
                    cellHeight = cellHeight,
                    totalWeeks = totalWeeks,
                    onCourseClick = onCourseClick,
                    onEmptyCellClick = if (readOnlyMessage == null) onAddCourseAt else ({ _, _ -> }),
                    periodTimes = periodTimes,
                    dark = dark,
                    hasCustomBackground = customBackground != null,
                    semesterStart = semesterStart,
                    excludedWeekSet = excludedWeekSet,
                    dateExceptions = dateExceptions
                )
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
    var lastSettledWeekPage by rememberSaveable { androidx.compose.runtime.mutableIntStateOf(pagerState.currentPage) }

    // 用户滑动结束后回写周次；外部（箭头/定位今天）改变周次时滚动到对应页。
    val weekHapticView = LocalView.current
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val pageChanged = page != lastSettledWeekPage
            lastSettledWeekPage = page
            if (page + 1 != currentWeekState) {
                onWeekChangeState(page + 1)
            }
            if (pageChanged) weekHapticView.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
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
                        Icons.Rounded.EventBusy,
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
                }
            }
        }
    }
}

@Composable
private fun DayChipRow(
    focusedDay: Int,
    todayDow: Int,
    highlightToday: Boolean,
    onFocusedDayChange: (Int) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp, vertical = 2.dp)) {
        for (day in 1..7) {
            val selected = day == focusedDay
            val isToday = highlightToday && day == todayDow
            val chipColor by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                animationSpec = Motion.eased(),
                label = "dayChip"
            )
            val dayChipDescription = stringResource(R.string.course_view_day_desc, TimeUtils.getDayName(day))
            val chipInteraction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight().clip(AppShapes.small)
                    .pressScale(chipInteraction, 0.94f)
                    .background(chipColor)
                    .clickable(interactionSource = chipInteraction, indication = null) { onFocusedDayChange(day) }
                    .semantics { contentDescription = dayChipDescription },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        TimeUtils.getDayName(day).replace("周", ""),
                        fontWeight = when {
                            selected -> FontWeight.Bold
                            isToday -> FontWeight.SemiBold
                            else -> FontWeight.Normal
                        },
                        color = when {
                            selected -> MaterialTheme.colorScheme.primary
                            isToday -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    if (isToday) {
                        Spacer(Modifier.height(3.dp))
                        Box(
                            Modifier.size(5.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
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
        status.suspended -> Icons.Rounded.EventBusy
        status.label != null -> Icons.AutoMirrored.Rounded.Label
        else -> Icons.Rounded.EditCalendar
    }
    val interaction = remember { MutableInteractionSource() }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)
            .testTag("calendar-context-strip")
            .pressScale(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
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
                Icons.Rounded.ChevronRight,
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
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.course_period_label),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    stringResource(R.string.course_time_label),
                    fontSize = 8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
                val headerInteraction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight()
                        .background(if (isToday) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) else Color.Transparent)
                        .pressScale(headerInteraction, 0.98f)
                        .clickable(interactionSource = headerInteraction, indication = null) { onDayClick(day) }
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
                    Icon(Icons.Rounded.School, null, modifier = Modifier.size(46.dp), tint = MaterialTheme.colorScheme.primary)
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
        Icon(Icons.Rounded.FileOpen, null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.course_empty_import_button), maxLines = 1)
    }
}

@Composable
private fun EmptyManualButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val contentAlpha = if (enabled) 1f else 0.38f
    val interaction = remember { MutableInteractionSource() }
    Surface(
        modifier = modifier.height(48.dp).clip(AppShapes.small)
            .pressScale(interaction)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
        shape = AppShapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = contentAlpha)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Rounded.AddCircleOutline, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.course_empty_manual_button), maxLines = 1, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha))
        }
    }
}
