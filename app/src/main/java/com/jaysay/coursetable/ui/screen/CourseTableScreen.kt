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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarViewDay
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jaysay.coursetable.data.model.Course
import com.jaysay.coursetable.data.model.ScheduleViewMode
import com.jaysay.coursetable.data.preferences.AppPreferences
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

private val dateFormat by lazy { SimpleDateFormat("M/d", Locale.getDefault()) }

private data class Section(val name: String, val periods: List<Int>)

private fun refDate(week: Int, dayOfWeek: Int, semesterStart: String): String {
    val cal = Calendar.getInstance()
    try {
        val parts = semesterStart.split("-")
        cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt(), 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
    } catch (_: Exception) {
        cal.set(2026, Calendar.FEBRUARY, 23, 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
    }
    cal.add(Calendar.DAY_OF_YEAR, (week - 1) * 7 + dayOfWeek - 1)
    return dateFormat.format(cal.time)
}

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

private fun parseMinutes(value: String): Int? {
    val parts = value.split(":")
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return hour * 60 + minute
}

@Composable
private fun rememberCurrentMinute(): Int {
    fun nowMinute(): Int = Calendar.getInstance().let {
        it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE)
    }
    var minute by remember { mutableIntStateOf(nowMinute()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            minute = nowMinute()
        }
    }
    return minute
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
    semesterStart: String = TimeUtils.todayDate(),
    totalWeeks: Int = 20,
    viewMode: ScheduleViewMode,
    onViewModeChange: (ScheduleViewMode) -> Unit,
    focusedDay: Int,
    onFocusedDayChange: (Int) -> Unit
) {
    val dark = MaterialTheme.colorScheme.background == DarkBackground
    val bgColor = MaterialTheme.colorScheme.background
    val cTextColor = if (dark) DarkCourseTextColor else CourseTextColor
    val cSubColor = if (dark) DarkCourseSubTextColor else CourseSubTextColor
    val density = LocalDensity.current
    val swipeThresholdPx = remember(density) { with(density) { 64.dp.toPx() } }

    val (todayWeek, todayDow) = remember(semesterStart) {
        val semCal = Calendar.getInstance().apply {
            try {
                val p = semesterStart.split("-")
                set(p[0].toInt(), p[1].toInt() - 1, p[2].toInt())
            } catch (_: Exception) {
                set(2026, Calendar.FEBRUARY, 23)
            }
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val daysDiff = ((today.timeInMillis - semCal.timeInMillis) / 86_400_000L).toInt()
        if (daysDiff >= 0) daysDiff / 7 + 1 to daysDiff % 7 + 1 else -1 to -1
    }

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
    val weekCourses = remember(courses, currentWeek) { courses.filter { currentWeek in it.weeks } }
    val colorMap = remember(courses, dark) {
        val palette = if (dark) DarkCourseColors else CourseColors
        buildMap {
            courses.distinctBy { it.courseName }.forEachIndexed { index, course ->
                put(course.courseName, palette[index % palette.size])
            }
        }
    }
    val isTodayWeek = currentWeek == todayWeek
    val weekControlTint = MaterialTheme.colorScheme.primary
    val weekDisabledTint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (dark) 0.38f else 0.3f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("course-table-screen")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable(onClick = onTableMenuClick)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    tableName,
                    modifier = Modifier.weight(1f, fill = false),
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(Icons.Default.ArrowDropDown, "切换课表", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onAddCourseClick, modifier = Modifier.size(48.dp).testTag("add-course-button")) {
                Icon(Icons.Default.AddCircleOutline, "添加课程", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onImportClick, modifier = Modifier.size(48.dp).testTag("import-course-button")) {
                Icon(Icons.Default.FileOpen, "导入课表", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onLocateToday, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.MyLocation, "定位到今天", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onSettingsClick, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Settings, "设置", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

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
                                "上一周",
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
                                text = "第 $currentWeek 周",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                            Text(
                                text = "${weekCourses.size} 门课程" + if (isTodayWeek) " · 本周" else "",
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
                                "下一周",
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
                                "当前${viewMode.label}视图，点击切换",
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
                                        "${mode.label}视图",
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
                        Box(
                            modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(10.dp))
                                .background(chipColor).clickable { onFocusedDayChange(day) }
                                .semantics { contentDescription = "查看${TimeUtils.getDayName(day)}" },
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
            EmptySchedule(onImportClick = onImportClick, onAddCourseClick = onAddCourseClick)
        } else {
            AnimatedContent(
                targetState = currentWeek,
                modifier = Modifier.fillMaxWidth().weight(1f),
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally(tween(210)) { it / 3 } + fadeIn(tween(180))) togetherWith
                            (slideOutHorizontally(tween(180)) { -it / 4 } + fadeOut(tween(140)))
                    } else {
                        (slideInHorizontally(tween(210)) { -it / 3 } + fadeIn(tween(180))) togetherWith
                            (slideOutHorizontally(tween(180)) { it / 4 } + fadeOut(tween(140)))
                    }
                },
                label = "weekContent"
            ) { displayedWeek ->
                val scrollState = remember(displayedWeek) { androidx.compose.foundation.ScrollState(0) }
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
                ) {
                    TableGrid(
                        courses = courses.filter { displayedWeek in it.weeks },
                        colorMap = colorMap,
                        visibleDays = visibleDays,
                        timeWidth = timeWidth,
                        cellHeight = cellHeight,
                        currentWeek = displayedWeek,
                        onCourseClick = onCourseClick,
                        onEmptyCellClick = onAddCourseAt,
                        periodTimes = periodTimes,
                        dark = dark,
                        cTextColor = cTextColor,
                        cSubColor = cSubColor,
                        todayWeek = todayWeek,
                        todayDow = todayDow,
                        viewMode = viewMode
                    )
                    // Lets the final period scroll clear of rounded display corners and
                    // gesture navigation areas on compact phones.
                    Spacer(Modifier.height(48.dp))
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
            Text("节次", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        visibleDays.forEach { day ->
            key(day) {
                val isToday = isTodayWeek && day == todayDow
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight()
                        .background(if (isToday) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) else Color.Transparent)
                        .clickable { onDayClick(day) }
                        .semantics { contentDescription = "${TimeUtils.getDayName(day)} ${refDate(currentWeek, day, semesterStart)}，点击查看单日" },
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
                            refDate(currentWeek, day, semesterStart),
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
private fun ColumnScope.EmptySchedule(onImportClick: () -> Unit, onAddCourseClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(92.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.School, null, modifier = Modifier.size(46.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("还没有课程", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("可以导入 Excel，也可以手动添加第一门课程", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            BoxWithConstraints {
                val narrow = maxWidth < 340.dp
                val containerModifier = if (narrow) Modifier.width(200.dp) else Modifier
                val arrangement = if (narrow) Arrangement.spacedBy(10.dp) else Arrangement.spacedBy(12.dp)
                if (narrow) {
                    Column(modifier = containerModifier, verticalArrangement = arrangement) {
                        EmptyImportButton(onClick = onImportClick, modifier = Modifier.fillMaxWidth())
                        EmptyManualButton(onClick = onAddCourseClick, modifier = Modifier.fillMaxWidth())
                    }
                } else {
                    Row(horizontalArrangement = arrangement) {
                        EmptyImportButton(onClick = onImportClick)
                        EmptyManualButton(onClick = onAddCourseClick)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyImportButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onClick, shape = RoundedCornerShape(12.dp), modifier = modifier.height(48.dp)) {
        Icon(Icons.Default.FileOpen, null)
        Spacer(Modifier.width(8.dp))
        Text("导入课表", maxLines = 1)
    }
}

@Composable
private fun EmptyManualButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.height(48.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.AddCircleOutline, null)
            Spacer(Modifier.width(8.dp))
            Text("手动添加", maxLines = 1, fontWeight = FontWeight.Medium)
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
    viewMode: ScheduleViewMode
) {
    val sections = remember(periodTimes) { buildSections(periodTimes) }
    val currentMinute = rememberCurrentMinute()
    val gridBackground = if (dark) DarkBackground else MaterialTheme.colorScheme.background
    val sectionBackground = if (dark) Color(0xFF17201D) else Color(0xFFF3F7F6)
    val sectionText = if (dark) DarkPrimaryDark.copy(alpha = 0.72f) else PrimaryDark.copy(alpha = 0.72f)
    val isTodayVisible = currentWeek == todayWeek
    val totalHeight = remember(periodTimes, cellHeight) { cellHeight * periodTimes.size + 20.dp * sections.size }
    val byDay = remember(courses) {
        (1..7).associateWith { day -> courses.filter { it.dayOfWeek == day }.sortedBy { it.startPeriod } }
    }

    Row(modifier = Modifier.fillMaxWidth().background(gridBackground)) {
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
                Box(
                    modifier = Modifier.weight(1f).height(totalHeight)
                        .background(
                            if (isTodayColumn) MaterialTheme.colorScheme.primary.copy(alpha = if (dark) 0.08f else 0.045f)
                            else Color.Transparent
                        )
                ) {
                    sections.forEach { section ->
                        val y = section.periods.firstOrNull()?.let { periodOffset(it, sections, cellHeight) - 20.dp } ?: 0.dp
                        Box(modifier = Modifier.fillMaxWidth().height(20.dp).offset(y = y).background(sectionBackground.copy(alpha = 0.82f)))
                    }

                    periodTimes.indices.forEach { index ->
                        val period = index + 1
                        val occupied = dayCourses.any { period in it.startPeriod..it.endPeriod }
                        Box(
                            modifier = Modifier.fillMaxWidth().height(cellHeight).offset(y = periodOffset(period, sections, cellHeight))
                                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                .then(
                                    if (occupied) Modifier
                                    else Modifier.clickable { onEmptyCellClick(day, period) }
                                        .semantics { contentDescription = "${TimeUtils.getDayName(day)}第${period}节空白，点击添加课程" }
                                )
                        )
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
                        val startMinute = periodTimes.getOrNull(start - 1)?.start?.let(::parseMinutes)
                        val endMinute = periodTimes.getOrNull(end - 1)?.end?.let(::parseMinutes)
                        val isCurrent = isTodayColumn && startMinute != null && endMinute != null && currentMinute in startMinute..endMinute
                        CourseCard(
                            course = course,
                            modifier = Modifier.fillMaxWidth().height(bottom - y).offset(y = y).padding(1.dp),
                            background = cardColor,
                            accent = accent,
                            textColor = cTextColor,
                            subTextColor = cSubColor,
                            isCurrent = isCurrent,
                            viewMode = viewMode,
                            onClick = { onCourseClick(course) }
                        )
                    }

                    if (isTodayColumn) {
                        val activeIndex = periodTimes.indexOfFirst { period ->
                            val start = parseMinutes(period.start)
                            val end = parseMinutes(period.end)
                            start != null && end != null && currentMinute in start..end
                        }
                        if (activeIndex >= 0) {
                            val start = parseMinutes(periodTimes[activeIndex].start) ?: currentMinute
                            val end = parseMinutes(periodTimes[activeIndex].end) ?: currentMinute + 1
                            val fraction = ((currentMinute - start).toFloat() / (end - start).coerceAtLeast(1)).coerceIn(0f, 1f)
                            val lineY = periodOffset(activeIndex + 1, sections, cellHeight) + cellHeight * fraction
                            Row(modifier = Modifier.fillMaxWidth().height(8.dp).offset(y = lineY - 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(7.dp).background(MaterialTheme.colorScheme.error, CircleShape))
                                Box(modifier = Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.error))
                            }
                        }
                    }
                }
            }
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
    isCurrent: Boolean,
    viewMode: ScheduleViewMode,
    onClick: () -> Unit
) {
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
        .plus("点击查看详情")
        .joinToString("，")
    val highlight = if (background.luminance() < 0.35f) Color.White.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.3f)
    Box(
        modifier = modifier
            .shadow(if (isCurrent) 4.dp else 2.dp, shape, clip = false)
            .clip(shape)
            .background(background.copy(alpha = if (background.luminance() < 0.35f) 0.96f else 0.82f))
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
