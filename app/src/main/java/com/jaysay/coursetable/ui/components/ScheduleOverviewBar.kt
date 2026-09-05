@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.jaysay.coursetable.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jaysay.coursetable.R
import com.jaysay.coursetable.data.model.AgendaCourseSlot
import com.jaysay.coursetable.data.model.TodayAgenda
import com.jaysay.coursetable.data.model.TodayAgendaPhase
import com.jaysay.coursetable.ui.theme.AppShapes
import com.jaysay.coursetable.util.TimeUtils

@Composable
fun ScheduleOverviewBar(
    tableName: String,
    agenda: TodayAgenda,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onTableMenuClick: () -> Unit,
    onAddCourseClick: () -> Unit,
    onImportClick: () -> Unit,
    onLocateToday: () -> Unit,
    onAgendaClick: () -> Unit = {},
    onSettingsClick: () -> Unit,
    writesEnabled: Boolean = true,
    /** 浏览非今日日期时为 true：紧凑宽度下也在操作行直接展示“回到今天”。 */
    awayFromToday: Boolean = false
) {
    var searchVisible by remember { mutableStateOf(false) }
    var agendaVisible by remember { mutableStateOf(false) }
    var moreActionsVisible by remember { mutableStateOf(false) }
    val compactAgenda = agenda.compactText()
    val agendaSummaryDescription = stringResource(R.string.overview_agenda_summary_desc, agenda.accessibilityText())

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val compactActions = maxWidth < 430.dp
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
        if (searchVisible) {
            // 展开即输入：搜索框出现的第一帧就持有焦点，无需二次点击。
            val searchFocus = remember { FocusRequester() }
            LaunchedEffect(Unit) { searchFocus.requestFocus() }
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.weight(1f).focusRequester(searchFocus).testTag("course-search-input"),
                placeholder = { Text(stringResource(R.string.overview_search_placeholder), maxLines = 1) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Rounded.Clear, stringResource(R.string.overview_clear_search))
                        }
                    }
                },
                singleLine = true
            )
            IconButton(onClick = { onSearchQueryChange(""); searchVisible = false }) {
                Icon(Icons.Rounded.Close, stringResource(R.string.overview_close_search))
            }
        } else {
            Column(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = writesEnabled, onClick = onTableMenuClick).padding(vertical = 3.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        tableName,
                        modifier = Modifier.weight(1f, fill = false),
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.4).sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(Icons.Rounded.ArrowDropDown, stringResource(R.string.overview_switch_table), tint = MaterialTheme.colorScheme.primary)
                }
                Text(
                    compactAgenda,
                    modifier = Modifier.fillMaxWidth().clickable { agendaVisible = true }
                        .semantics { contentDescription = agendaSummaryDescription }
                        .testTag("today-agenda-summary"),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = { searchVisible = true }, modifier = Modifier.size(48.dp).testTag("course-search-button")) {
                Icon(Icons.Rounded.Search, stringResource(R.string.overview_search_course), tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onAddCourseClick, enabled = writesEnabled, modifier = Modifier.size(48.dp).testTag("add-course-button")) {
                Icon(Icons.Rounded.AddCircleOutline, stringResource(R.string.overview_add_course), tint = MaterialTheme.colorScheme.primary)
            }
            // 浏览其他日期时，紧凑宽度的“回到今天”也直接可达，不再依赖更多菜单；
            // 宽屏操作行本就常驻定位按钮，不重复添加。
            if (compactActions && awayFromToday) {
                IconButton(onClick = onLocateToday, modifier = Modifier.size(48.dp).testTag("locate-today-button")) {
                    Icon(Icons.Rounded.MyLocation, stringResource(R.string.overview_locate_today), tint = MaterialTheme.colorScheme.primary)
                }
            }
            if (compactActions) {
                Box {
                    IconButton(
                        onClick = { moreActionsVisible = true },
                        modifier = Modifier.size(48.dp).testTag("more-actions-button")
                    ) {
                        Icon(Icons.Rounded.MoreVert, stringResource(R.string.overview_more_actions), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(
                        expanded = moreActionsVisible,
                        onDismissRequest = { moreActionsVisible = false },
                        shape = AppShapes.medium,
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp,
                        shadowElevation = 12.dp,
                        border = BorderStroke(
                            0.75.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
                        )
                    ) {
                        if (!awayFromToday) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.overview_locate_today)) },
                                leadingIcon = { Icon(Icons.Rounded.MyLocation, null) },
                                onClick = { moreActionsVisible = false; onLocateToday() },
                                modifier = Modifier.testTag("locate-today-menu-item")
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.overview_agenda_list)) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.FormatListBulleted, null) },
                            onClick = { moreActionsVisible = false; onAgendaClick() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.overview_import_table)) },
                            leadingIcon = { Icon(Icons.Rounded.FileOpen, null) },
                            onClick = { moreActionsVisible = false; onImportClick() },
                            enabled = writesEnabled,
                            modifier = Modifier.testTag("import-course-menu-item")
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.overview_settings)) },
                            leadingIcon = { Icon(Icons.Rounded.Settings, null) },
                            onClick = { moreActionsVisible = false; onSettingsClick() }
                        )
                    }
                }
            } else {
                IconButton(onClick = onAgendaClick, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.FormatListBulleted, stringResource(R.string.overview_agenda_list), tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(
                    onClick = onLocateToday,
                    modifier = Modifier.size(48.dp).testTag("locate-today-button")
                ) {
                    Icon(Icons.Rounded.MyLocation, stringResource(R.string.overview_locate_today), tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onImportClick, enabled = writesEnabled, modifier = Modifier.size(48.dp).testTag("import-course-button")) {
                    Icon(Icons.Rounded.FileOpen, stringResource(R.string.overview_import_table), tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onSettingsClick, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Settings, stringResource(R.string.overview_settings), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    }

    if (agendaVisible) {
        ModalBottomSheet(
            onDismissRequest = { agendaVisible = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = AppShapes.sheet,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
                Text(
                    stringResource(R.string.overview_dialog_today_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(14.dp))
                Text(agenda.accessibilityText(), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(22.dp))
                TextButton(onClick = { agendaVisible = false }, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.overview_dialog_got_it))
                }
            }
        }
    }
}

@Composable
fun ReadOnlyRecoveryBanner(message: String, onRecoveryClick: () -> Unit) {
    val bannerDescription = stringResource(R.string.overview_readonly_banner_desc)
    Surface(
        onClick = onRecoveryClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("read-only-recovery-banner")
            .semantics { contentDescription = bannerDescription },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f))
    ) {
        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.WarningAmber, null)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.overview_readonly_banner_title), fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.overview_readonly_banner_message), fontSize = 12.sp)
                Text(message, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun TodayAgenda.compactText(): String = when (phase) {
    TodayAgendaPhase.OUTSIDE_SEMESTER -> stringResource(R.string.overview_compact_outside_semester)
    TodayAgendaPhase.NO_COURSES -> stringResource(R.string.overview_compact_no_courses)
    TodayAgendaPhase.BEFORE_FIRST -> stringResource(R.string.overview_compact_next_class, next?.course?.courseName.orEmpty(), next?.startMinute.asTime())
    TodayAgendaPhase.IN_CLASS -> stringResource(R.string.overview_compact_in_class, current?.course?.courseName.orEmpty(), current?.endMinute.asTime())
    TodayAgendaPhase.BETWEEN_CLASSES -> stringResource(R.string.overview_compact_break, next?.course?.courseName.orEmpty(), next?.startMinute.asTime())
    TodayAgendaPhase.FINISHED -> stringResource(R.string.overview_compact_finished)
    TodayAgendaPhase.INVALID_TIME -> stringResource(R.string.overview_compact_invalid_time)
}

@Composable
private fun TodayAgenda.accessibilityText(): String {
    val slot = current ?: next
    val detail = if (slot != null) slot.fullText() else ""
    val remainder = if (remainingCount > 0) stringResource(R.string.overview_a11y_remaining, remainingCount) else ""
    val invalid = if (invalidCourseCount > 0) stringResource(R.string.overview_a11y_invalid_extra, invalidCourseCount) else ""
    return when (phase) {
        TodayAgendaPhase.OUTSIDE_SEMESTER -> stringResource(R.string.overview_a11y_outside_semester)
        TodayAgendaPhase.NO_COURSES -> stringResource(R.string.overview_a11y_no_courses, week ?: 0)
        TodayAgendaPhase.BEFORE_FIRST -> stringResource(R.string.overview_a11y_next_class, detail, remainder, invalid)
        TodayAgendaPhase.IN_CLASS -> stringResource(R.string.overview_a11y_in_class, detail, remainder, invalid)
        TodayAgendaPhase.BETWEEN_CLASSES -> stringResource(R.string.overview_a11y_break, detail, remainder, invalid)
        TodayAgendaPhase.FINISHED -> stringResource(R.string.overview_a11y_finished, invalid)
        TodayAgendaPhase.INVALID_TIME -> stringResource(R.string.overview_a11y_invalid_time)
    }
}

@Composable
private fun AgendaCourseSlot.fullText(): String {
    val teacherText = stringResource(R.string.overview_a11y_teacher, course.teacher)
    val classroomText = stringResource(R.string.overview_a11y_classroom, course.classroom)
    val timeText = stringResource(R.string.overview_a11y_time_range, startMinute.asTime(), endMinute.asTime())
    return buildString {
        append(course.courseName)
        if (course.teacher.isNotBlank()) append(teacherText)
        if (course.classroom.isNotBlank()) append(classroomText)
        append(timeText)
    }
}

private fun Int?.asTime(): String = this?.let { TimeUtils.formatMinuteOfDay(it) }.orEmpty()
