package com.jaysay.coursetable.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jaysay.coursetable.R
import com.jaysay.coursetable.data.model.Course
import com.jaysay.coursetable.data.model.ScheduleDateException
import com.jaysay.coursetable.data.model.ScheduleDateResolver
import com.jaysay.coursetable.data.model.ScheduleExceptionType
import com.jaysay.coursetable.data.repository.TableData
import com.jaysay.coursetable.ui.components.AppPanel
import com.jaysay.coursetable.ui.components.AppTopBar
import com.jaysay.coursetable.util.TimeUtils
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CalendarExceptionScreen(
    tableData: TableData,
    onUpdate: (TableData) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    var table by remember(tableData) { mutableStateOf(tableData) }
    var showDateArrangementDialog by remember { mutableStateOf(false) }
    var editingDateArrangement by remember { mutableStateOf<ScheduleDateException?>(null) }
    var editingWeekLabel by remember { mutableStateOf<Pair<Int, String>?>(null) }

    fun save(next: TableData) {
        val normalized = next.copy(
            excludedWeeks = next.excludedWeeks.filter { it in 1..next.totalWeeks }.distinct().sorted(),
            dateExceptions = ScheduleDateResolver.normalize(next.dateExceptions),
            weekLabels = next.weekLabels.filterKeys { it in 1..next.totalWeeks }
                .mapValues { it.value.trim().take(30) }
                .filterValues(String::isNotEmpty)
        )
        table = normalized
        onUpdate(normalized)
    }

    if (showDateArrangementDialog) {
        DateArrangementDialog(
            courses = table.courses,
            initial = editingDateArrangement,
            onDismiss = {
                showDateArrangementDialog = false
                editingDateArrangement = null
            },
            onSave = { item ->
                val updated = if (editingDateArrangement == null) {
                    table.dateExceptions + item
                } else {
                    table.dateExceptions.map { existing -> if (existing.id == item.id) item else existing }
                }
                save(table.copy(dateExceptions = updated))
                showDateArrangementDialog = false
                editingDateArrangement = null
            }
        )
    }
    editingWeekLabel?.let { initial ->
        WeekLabelDialog(
            totalWeeks = table.totalWeeks,
            initialWeek = initial.first,
            initialLabel = initial.second,
            onDismiss = { editingWeekLabel = null },
            onSave = { week, label ->
                val labels = table.weekLabels - initial.first + (week to label)
                save(table.copy(weekLabels = labels))
                editingWeekLabel = null
            }
        )
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.calendar_title),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.calendar_back))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).testTag("academic-calendar-screen"),
            contentPadding = PaddingValues(16.dp, 14.dp, 16.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                AppPanel(selected = table.excludedWeeks.isNotEmpty() || table.dateExceptions.isNotEmpty()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.calendar_overview_title), fontWeight = FontWeight.Bold)
                            Text(
                                stringResource(
                                    R.string.calendar_overview_summary,
                                    table.excludedWeeks.size,
                                    table.weekLabels.size,
                                    table.dateExceptions.size
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.calendar_intro),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
            }
            item {
                SuspendedWeeksPanel(table) { week ->
                    val selected = table.excludedWeeks.toSet()
                    save(
                        table.copy(
                            excludedWeeks = if (week in selected) (selected - week).sorted()
                            else (selected + week).sorted()
                        )
                    )
                }
            }
            item {
                WeekLabelsPanel(
                    labels = table.weekLabels,
                    onAdd = { editingWeekLabel = 1 to "" },
                    onEdit = { week, label -> editingWeekLabel = week to label },
                    onDelete = { week -> save(table.copy(weekLabels = table.weekLabels - week)) }
                )
            }
            item {
                DateArrangementsPanel(
                    exceptions = table.dateExceptions,
                    courses = table.courses,
                    semesterStart = table.semesterStart,
                    onAdd = {
                        editingDateArrangement = null
                        showDateArrangementDialog = true
                    },
                    onEdit = { item ->
                        editingDateArrangement = item
                        showDateArrangementDialog = true
                    },
                    onDelete = { id ->
                        save(table.copy(dateExceptions = table.dateExceptions.filterNot { it.id == id }))
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SuspendedWeeksPanel(table: TableData, onToggle: (Int) -> Unit) {
    AppPanel {
        Column(Modifier.padding(16.dp)) {
            SectionHeading(
                icon = Icons.Outlined.EventBusy,
                title = stringResource(R.string.calendar_suspended_title),
                subtitle = stringResource(R.string.calendar_suspended_hint)
            )
            Spacer(Modifier.height(12.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth().testTag("excluded-weeks-list"),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
                maxItemsInEachRow = 5
            ) {
                (1..table.totalWeeks).forEach { week ->
                    FilterChip(
                        selected = week in table.excludedWeeks,
                        onClick = { onToggle(week) },
                        label = { Text("$week") },
                        leadingIcon = if (week in table.excludedWeeks) ({
                            Icon(Icons.Outlined.EventBusy, null, modifier = Modifier.size(16.dp))
                        }) else null,
                        modifier = Modifier.testTag("excluded-week-$week")
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                if (table.excludedWeeks.isEmpty()) stringResource(R.string.calendar_suspended_empty)
                else stringResource(R.string.calendar_suspended_selected, TimeUtils.formatWeeks(table.excludedWeeks)),
                color = if (table.excludedWeeks.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                fontWeight = if (table.excludedWeeks.isEmpty()) FontWeight.Normal else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun WeekLabelsPanel(
    labels: Map<Int, String>,
    onAdd: () -> Unit,
    onEdit: (Int, String) -> Unit,
    onDelete: (Int) -> Unit
) {
    AppPanel {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionHeading(
                    icon = Icons.AutoMirrored.Outlined.Label,
                    title = stringResource(R.string.calendar_week_label),
                    subtitle = stringResource(R.string.calendar_week_label_hint),
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onAdd, modifier = Modifier.testTag("add-week-label")) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(stringResource(R.string.calendar_add))
                }
            }
            if (labels.isEmpty()) {
                Text(
                    stringResource(R.string.calendar_labels_empty),
                    modifier = Modifier.padding(top = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            } else {
                Spacer(Modifier.height(8.dp))
                labels.toSortedMap().forEach { (week, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onEdit(week, label) }
                            .padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AssistChip(onClick = { onEdit(week, label) }, label = { Text("第${week}周") })
                        Spacer(Modifier.width(10.dp))
                        Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        IconButton(onClick = { onDelete(week) }) {
                            Icon(Icons.Outlined.Delete, stringResource(R.string.calendar_delete_week_label))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DateArrangementsPanel(
    exceptions: List<ScheduleDateException>,
    courses: List<Course>,
    semesterStart: String,
    onAdd: () -> Unit,
    onEdit: (ScheduleDateException) -> Unit,
    onDelete: (String) -> Unit
) {
    AppPanel {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionHeading(
                    icon = Icons.Outlined.EditCalendar,
                    title = stringResource(R.string.calendar_dates_title),
                    subtitle = stringResource(R.string.calendar_dates_hint),
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onAdd, modifier = Modifier.testTag("add-date-arrangement")) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(stringResource(R.string.calendar_add))
                }
            }
            if (exceptions.isEmpty()) {
                Text(
                    stringResource(R.string.calendar_empty),
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            } else {
                Spacer(Modifier.height(8.dp))
                exceptions.sortedBy(ScheduleDateException::date).forEachIndexed { index, item ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DateArrangementRow(item, courses, semesterStart, onEdit, onDelete)
                }
            }
        }
    }
}

@Composable
private fun DateArrangementRow(
    item: ScheduleDateException,
    courses: List<Course>,
    semesterStart: String,
    onEdit: (ScheduleDateException) -> Unit,
    onDelete: (String) -> Unit
) {
    val date = remember(item.date) { runCatching { LocalDate.parse(item.date) }.getOrNull() }
    val dateText = date?.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)) ?: item.date
    val week = date?.let { parsed ->
        TimeUtils.semesterWeekStartOrNull(semesterStart)?.let { start ->
            val days = java.time.temporal.ChronoUnit.DAYS.between(start, parsed)
            if (days >= 0) (days / 7 + 1).toInt() else null
        }
    }
    val detail = item.title.ifBlank {
        when (item.type) {
            ScheduleExceptionType.DAY_OFF -> stringResource(R.string.calendar_arrangement_day_off)
            ScheduleExceptionType.COURSE_CANCELLED -> courses.firstOrNull { it.seriesKey == item.courseSeriesKey }
                ?.courseName ?: stringResource(R.string.calendar_detail_course_fallback)
            ScheduleExceptionType.MAKEUP -> item.makeupCourse?.courseName ?: stringResource(R.string.calendar_makeup)
        }
    }
    Row(
        Modifier.fillMaxWidth()
            .testTag("edit-date-arrangement-${item.id}")
            .clickable { onEdit(item) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(dateText, fontWeight = FontWeight.Bold)
                week?.let {
                    Spacer(Modifier.width(8.dp))
                    Text("第${it}周", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(4.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    item.type.label(),
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        Icon(
            Icons.Default.ChevronRight,
            stringResource(R.string.calendar_edit_date_exception_action),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        IconButton(onClick = { onDelete(item.id) }) {
            Icon(Icons.Outlined.Delete, stringResource(R.string.calendar_delete_date_exception))
        }
    }
}

@Composable
private fun SectionHeading(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Row(modifier, verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(11.dp))
        Column {
            Text(title, fontWeight = FontWeight.Bold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DateArrangementDialog(
    courses: List<Course>,
    initial: ScheduleDateException?,
    onDismiss: () -> Unit,
    onSave: (ScheduleDateException) -> Unit
) {
    val initialDate = remember(initial?.id, initial?.date) {
        initial?.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()
    }
    val initialCourse = remember(initial?.id, courses) {
        initial?.makeupCourse
            ?: courses.firstOrNull { it.seriesKey == initial?.courseSeriesKey }
            ?: courses.firstOrNull()
    }
    var date by remember(initial?.id) { mutableStateOf(initialDate) }
    var type by remember(initial?.id) { mutableStateOf(initial?.type ?: ScheduleExceptionType.DAY_OFF) }
    var course by remember(initial?.id) { mutableStateOf(initialCourse) }
    var title by remember(initial?.id) { mutableStateOf(initial?.title.orEmpty()) }
    var courseMenu by remember(initial?.id) { mutableStateOf(false) }
    var showDatePicker by remember(initial?.id) { mutableStateOf(false) }
    val valid = type == ScheduleExceptionType.DAY_OFF || course != null

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.settings_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.calendar_cancel))
                }
            }
        ) { DatePicker(state = pickerState) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (initial == null) R.string.calendar_add_date_exception
                    else R.string.calendar_edit_date_exception
                )
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.CalendarMonth, null)
                    Spacer(Modifier.width(8.dp))
                    Text(date.format(DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.CHINA)))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    ScheduleExceptionType.entries.forEach { value ->
                        FilterChip(
                            selected = type == value,
                            onClick = { type = value },
                            label = { Text(value.label()) }
                        )
                    }
                }
                Text(
                    when (type) {
                        ScheduleExceptionType.DAY_OFF -> stringResource(R.string.calendar_arrangement_day_off)
                        ScheduleExceptionType.COURSE_CANCELLED -> stringResource(R.string.calendar_arrangement_cancelled)
                        ScheduleExceptionType.MAKEUP -> stringResource(R.string.calendar_arrangement_makeup)
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
                if (type != ScheduleExceptionType.DAY_OFF) {
                    Box {
                        OutlinedButton(onClick = { courseMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(course?.courseName ?: stringResource(R.string.calendar_select_course), Modifier.weight(1f))
                            Icon(Icons.Default.ChevronRight, null)
                        }
                        DropdownMenu(expanded = courseMenu, onDismissRequest = { courseMenu = false }) {
                            courses.distinctBy(Course::seriesKey).forEach { item ->
                                DropdownMenuItem(
                                    text = { Text(item.courseName) },
                                    onClick = { course = item; courseMenu = false }
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(80) },
                    label = { Text(stringResource(R.string.calendar_remark_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("date-arrangement-title-input")
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                val selected = course
                onSave(
                    ScheduleDateException(
                        id = initial?.id ?: java.util.UUID.randomUUID().toString(),
                        date = date.toString(),
                        type = type,
                        courseSeriesKey = selected?.seriesKey.takeIf { type != ScheduleExceptionType.DAY_OFF },
                        makeupCourse = selected?.takeIf { type == ScheduleExceptionType.MAKEUP },
                        title = title
                    )
                )
            }) { Text(stringResource(R.string.calendar_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.calendar_cancel)) }
        }
    )
}

@Composable
private fun WeekLabelDialog(
    totalWeeks: Int,
    initialWeek: Int,
    initialLabel: String,
    onDismiss: () -> Unit,
    onSave: (Int, String) -> Unit
) {
    var week by remember { mutableStateOf(initialWeek.coerceIn(1, totalWeeks)) }
    var label by remember { mutableStateOf(initialLabel) }
    var weekMenu by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (initialLabel.isBlank()) stringResource(R.string.calendar_add_week_label)
                else stringResource(R.string.calendar_edit_week_label)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box {
                    OutlinedButton(onClick = { weekMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.settings_week_number, week), Modifier.weight(1f))
                        Icon(Icons.Default.ChevronRight, null)
                    }
                    DropdownMenu(
                        expanded = weekMenu,
                        onDismissRequest = { weekMenu = false },
                        modifier = Modifier.heightIn(max = 320.dp)
                    ) {
                        (1..totalWeeks).forEach { item ->
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.settings_week_number, item)) },
                                onClick = { week = item; weekMenu = false }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(30) },
                    label = { Text(stringResource(R.string.calendar_label_example)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(enabled = label.isNotBlank(), onClick = { onSave(week, label.trim()) }) {
                Text(stringResource(R.string.calendar_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.calendar_cancel)) }
        }
    )
}

@Composable
private fun ScheduleExceptionType.label(): String = when (this) {
    ScheduleExceptionType.DAY_OFF -> stringResource(R.string.calendar_type_day_off)
    ScheduleExceptionType.COURSE_CANCELLED -> stringResource(R.string.calendar_type_course_cancelled)
    ScheduleExceptionType.MAKEUP -> stringResource(R.string.calendar_makeup)
}
