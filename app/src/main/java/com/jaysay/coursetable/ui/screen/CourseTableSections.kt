package com.jaysay.coursetable.ui.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.EditCalendar
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.jaysay.coursetable.data.model.AcademicCalendarWeekStatus
import com.jaysay.coursetable.ui.theme.AppShapes
import com.jaysay.coursetable.ui.theme.AppSizes
import com.jaysay.coursetable.ui.theme.Motion
import com.jaysay.coursetable.ui.theme.pressScale
import com.jaysay.coursetable.util.TimeUtils
import kotlin.math.max

/**
 * 主课表屏幕的静态展示区块（星期条、日期表头、进度标尺、校历提示条、空课表状态）。
 * 从 CourseTableScreen.kt 按职责提取，均为无状态 Composable：日期与滚动等状态
 * 仍由 CourseTableScreen / DayViewController 单一来源持有，这里不做任何同步。
 */

/** 日视图星期选择条：周内快速切换，今日带圆点强调。 */
@Composable
internal fun DayChipRow(
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

/**
 * 周进度标尺：4dp 圆角轨道 + 品牌双色（primary → tertiary）渐变填充，
 * 当前周位置带一枚柔光圆点，替代原先 2dp 单色细条；深浅色各自取材。
 */
@Composable
internal fun WeekProgressRuler(progress: Float, modifier: Modifier = Modifier) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.4f
    val fillStart = MaterialTheme.colorScheme.primary
    val fillEnd = MaterialTheme.colorScheme.tertiary
    val trackColor = if (dark) Color.White.copy(alpha = 0.14f)
    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val clamped = progress.coerceIn(0f, 1f)
    Canvas(modifier.height(16.dp)) {
        val barHeight = 4.dp.toPx()
        val barTop = size.height / 2f - barHeight / 2f
        drawRoundRect(
            color = trackColor,
            topLeft = Offset(0f, barTop),
            size = Size(size.width, barHeight),
            cornerRadius = CornerRadius(barHeight / 2f)
        )
        if (clamped > 0f) {
            val fillWidth = max(size.width * clamped, barHeight)
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(fillStart, fillEnd)),
                topLeft = Offset(0f, barTop),
                size = Size(fillWidth, barHeight),
                cornerRadius = CornerRadius(barHeight / 2f)
            )
            val headX = (size.width * clamped).coerceIn(barHeight / 2f, size.width - barHeight / 2f)
            val centerY = size.height / 2f
            drawCircle(color = fillStart.copy(alpha = 0.20f), radius = 7.dp.toPx(), center = Offset(headX, centerY))
            drawCircle(color = fillStart, radius = 3.dp.toPx(), center = Offset(headX, centerY))
        }
    }
}

/** 停课/特殊安排提示条：展示周状态摘要，点击进入学期安排。 */
@Composable
internal fun CalendarContextStrip(
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

/** 星期表头：可点击进入对应日期，今日列品牌色强调，含日期调整角标。 */
@Composable
internal fun DayHeader(
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
internal fun ColumnScope.EmptySchedule(
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
    Button(onClick = onClick, enabled = enabled, shape = AppShapes.small, modifier = modifier.height(AppSizes.control)) {
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
        modifier = modifier.height(AppSizes.control).clip(AppShapes.small)
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
