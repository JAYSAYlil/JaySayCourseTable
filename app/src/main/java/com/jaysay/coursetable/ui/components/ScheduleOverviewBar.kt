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
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WarningAmber
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    writesEnabled: Boolean = true
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
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.weight(1f).testTag("course-search-input"),
                placeholder = { Text(stringResource(R.string.overview_search_placeholder), maxLines = 1) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Clear, stringResource(R.string.overview_clear_search))
                        }
                    }
                },
                singleLine = true
            )
            IconButton(onClick = { onSearchQueryChange(""); searchVisible = false }) {
                Icon(Icons.Default.Close, stringResource(R.string.overview_close_search))
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
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(Icons.Default.ArrowDropDown, stringResource(R.string.overview_switch_table), tint = MaterialTheme.colorScheme.primary)
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
                Icon(Icons.Default.Search, stringResource(R.string.overview_search_course), tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onAddCourseClick, enabled = writesEnabled, modifier = Modifier.size(48.dp).testTag("add-course-button")) {
                Icon(Icons.Default.AddCircleOutline, stringResource(R.string.overview_add_course), tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onImportClick, enabled = writesEnabled, modifier = Modifier.size(48.dp).testTag("import-course-button")) {
                Icon(Icons.Default.FileOpen, stringResource(R.string.overview_import_table), tint = MaterialTheme.colorScheme.primary)
            }
            if (compactActions) {
                Box {
                    IconButton(
                        onClick = { moreActionsVisible = true },
                        modifier = Modifier.size(48.dp).testTag("more-actions-button")
                    ) {
                        Icon(Icons.Default.MoreVert, stringResource(R.string.overview_more_actions), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(
                        expanded = moreActionsVisible,
                        onDismissRequest = { moreActionsVisible = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.overview_locate_today)) },
                            leadingIcon = { Icon(Icons.Default.MyLocation, null) },
                            onClick = { moreActionsVisible = false; onLocateToday() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.overview_agenda_list)) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.FormatListBulleted, null) },
                            onClick = { moreActionsVisible = false; onAgendaClick() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.overview_settings)) },
                            leadingIcon = { Icon(Icons.Default.Settings, null) },
                            onClick = { moreActionsVisible = false; onSettingsClick() }
                        )
                    }
                }
            } else {
                IconButton(onClick = onAgendaClick, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.FormatListBulleted, stringResource(R.string.overview_agenda_list), tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onLocateToday, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.MyLocation, stringResource(R.string.overview_locate_today), tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onSettingsClick, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Settings, stringResource(R.string.overview_settings), tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Icon(Icons.Default.WarningAmber, null)
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
