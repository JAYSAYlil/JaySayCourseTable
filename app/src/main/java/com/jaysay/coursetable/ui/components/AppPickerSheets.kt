@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.jaysay.coursetable.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jaysay.coursetable.ui.theme.AppShapes
import com.jaysay.coursetable.ui.theme.AppSpacing
import com.jaysay.coursetable.ui.theme.Motion
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import java.time.LocalDate
import java.time.YearMonth

private val WeekdayLabels = listOf("一", "二", "三", "四", "五", "六", "日")

/** 应用自己的月历面板，不依赖系统或 Material 日期弹窗。 */
@Composable
fun AppDatePickerSheet(
    initialDate: LocalDate,
    title: String,
    confirmLabel: String,
    cancelLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit
) {
    var selectedDate by remember(initialDate) { mutableStateOf(initialDate) }
    var visibleMonth by remember(initialDate) { mutableStateOf(YearMonth.from(initialDate)) }
    var transitionDirection by remember { mutableIntStateOf(1) }

    AppPickerSheet(tag = "semester-date-picker-sheet", onDismiss = onDismiss) {
        PickerTitle(title)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = {
                    transitionDirection = -1
                    visibleMonth = visibleMonth.minusMonths(1)
                },
                modifier = Modifier.testTag("date-picker-previous-month")
            ) {
                Icon(Icons.Rounded.ChevronLeft, contentDescription = "上个月")
            }
            Text(
                text = "${visibleMonth.year} 年 ${visibleMonth.monthValue} 月",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    transitionDirection = 1
                    visibleMonth = visibleMonth.plusMonths(1)
                },
                modifier = Modifier.testTag("date-picker-next-month")
            ) {
                Icon(Icons.Rounded.ChevronRight, contentDescription = "下个月")
            }
        }
        Row(Modifier.fillMaxWidth()) {
            WeekdayLabels.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).padding(vertical = 8.dp)
                )
            }
        }
        AnimatedContent(
            targetState = visibleMonth,
            transitionSpec = {
                (slideInHorizontally(Motion.eased(Motion.DURATION_BASE)) {
                    transitionDirection * it / 4
                } + fadeIn(Motion.eased(Motion.DURATION_SHORT))) togetherWith
                    (slideOutHorizontally(Motion.eased(Motion.DURATION_BASE)) {
                        -transitionDirection * it / 4
                    } + fadeOut(Motion.eased(Motion.DURATION_SHORT)))
            },
            label = "calendarMonth"
        ) { month ->
            CalendarMonthGrid(month, selectedDate) { selectedDate = it }
        }
        YearQuickStrip(
            initialYear = initialDate.year,
            visibleYear = visibleMonth.year,
            onSelectYear = { year ->
                if (year != visibleMonth.year) {
                    transitionDirection = if (year > visibleMonth.year) 1 else -1
                    visibleMonth = YearMonth.of(year, visibleMonth.monthValue)
                }
            }
        )
        PickerActions(confirmLabel, cancelLabel, onDismiss) { onConfirm(selectedDate) }
    }
}

/**
 * 年份快速选择条：横向滚动点选年份，免去连续点"上/下个月"跨年。
 * 范围以选择器打开时的年份为中心 ±20 年，并跟随可见月份自动居中。
 */
@Composable
private fun YearQuickStrip(
    initialYear: Int,
    visibleYear: Int,
    onSelectYear: (Int) -> Unit
) {
    val years = remember(initialYear) { ((initialYear - 20)..(initialYear + 20)).toList() }
    val yearListState = rememberLazyListState(
        initialFirstVisibleItemIndex = (visibleYear - years.first()).coerceIn(0, years.lastIndex)
    )
    LaunchedEffect(visibleYear) {
        val index = (visibleYear - years.first()).coerceIn(0, years.lastIndex)
        yearListState.animateScrollToItem((index - 2).coerceAtLeast(0))
    }
    LazyRow(
        state = yearListState,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(years, key = { it }) { year ->
            val selected = year == visibleYear
            Surface(
                onClick = { onSelectYear(year) },
                shape = AppShapes.input,
                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                contentColor = if (selected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                border = if (selected) null
                else BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                modifier = Modifier.testTag("date-picker-year-$year")
            ) {
                Text(
                    text = year.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }
        }
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun CalendarMonthGrid(
    month: YearMonth,
    selectedDate: LocalDate,
    onSelect: (LocalDate) -> Unit
) {
    val leadingEmpty = month.atDay(1).dayOfWeek.value - 1
    val cells = remember(month) {
        List(42) { index ->
            val day = index - leadingEmpty + 1
            day.takeIf { it in 1..month.lengthOfMonth() }
        }
    }
    Column(Modifier.fillMaxWidth()) {
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    Box(
                        modifier = Modifier.weight(1f).aspectRatio(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        if (day != null) {
                            val date = month.atDay(day)
                            val selected = date == selectedDate
                            Surface(
                                onClick = { onSelect(date) },
                                shape = CircleShape,
                                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(40.dp).testTag("date-picker-day-$date")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(day.toString(), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 应用自己的 24 小时双滚轮；滚动可打断，松手后吸附到最近数字。 */
@Composable
fun AppTimePickerSheet(
    initialHour: Int,
    initialMinute: Int,
    title: String,
    confirmLabel: String,
    cancelLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit
) {
    var hour by remember(initialHour) { mutableIntStateOf(initialHour.coerceIn(0, 23)) }
    var minute by remember(initialMinute) { mutableIntStateOf(initialMinute.coerceIn(0, 59)) }

    AppPickerSheet(tag = "period-time-picker-sheet", onDismiss = onDismiss) {
        PickerTitle(title)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.lg),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NumberWheel(
                values = (0..23).toList(),
                initialValue = hour,
                suffix = "时",
                onValueChange = { hour = it },
                modifier = Modifier.weight(1f).testTag("time-picker-hour-wheel")
            )
            Text(":", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            NumberWheel(
                values = (0..59).toList(),
                initialValue = minute,
                suffix = "分",
                onValueChange = { minute = it },
                modifier = Modifier.weight(1f).testTag("time-picker-minute-wheel")
            )
        }
        PickerActions(confirmLabel, cancelLabel, onDismiss) { onConfirm(hour, minute) }
    }
}

@Composable
private fun NumberWheel(
    values: List<Int>,
    initialValue: Int,
    suffix: String,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val initialIndex = values.indexOf(initialValue).coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    LaunchedEffect(listState, values) {
        snapshotFlow { listState.isScrollInProgress }
            .filter { scrolling -> !scrolling }
            .distinctUntilChanged()
            .collect {
                values.getOrNull(listState.firstVisibleItemIndex)?.let(onValueChange)
            }
    }

    Box(
        modifier = modifier
            .height(220.dp)
            .clip(AppShapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .border(0.75.dp, MaterialTheme.colorScheme.outlineVariant, AppShapes.medium)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
            shape = AppShapes.input,
            modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 8.dp).height(44.dp)
        ) {}
        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            contentPadding = PaddingValues(vertical = 88.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(values, key = { it }) { value ->
                val distance = kotlin.math.abs(values.indexOf(value) - listState.firstVisibleItemIndex)
                Text(
                    text = "%02d  %s".format(value, suffix),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (distance == 0) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (distance == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .padding(top = 11.dp)
                        .alpha(if (distance <= 1) 1f else 0.38f)
                )
            }
        }
    }
}

@Composable
private fun AppPickerSheet(
    tag: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = AppShapes.sheet,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        scrimColor = Color.Black.copy(alpha = 0.28f),
        modifier = Modifier.testTag(tag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = AppSpacing.screenH)
                .padding(bottom = AppSpacing.xl),
            content = content
        )
    }
}

@Composable
private fun PickerTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth().padding(bottom = AppSpacing.sm)
    )
}

@Composable
private fun PickerActions(
    confirmLabel: String,
    cancelLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Spacer(Modifier.height(AppSpacing.md))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm, Alignment.End),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onDismiss) { Text(cancelLabel) }
        Button(onClick = onConfirm, shape = AppShapes.input) { Text(confirmLabel) }
    }
}
