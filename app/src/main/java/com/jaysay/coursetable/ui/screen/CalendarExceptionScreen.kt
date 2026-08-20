package com.jaysay.coursetable.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jaysay.coursetable.data.model.*
import com.jaysay.coursetable.data.repository.TableData
import com.jaysay.coursetable.ui.components.AppPanel
import com.jaysay.coursetable.ui.components.AppTopBar
import java.time.LocalDate

@Composable
fun CalendarExceptionScreen(
    tableData: TableData,
    onUpdate: (TableData) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    var table by remember(tableData) { mutableStateOf(tableData) }
    var showAdd by remember { mutableStateOf(false) }
    var showWeekLabel by remember { mutableStateOf(false) }

    fun save(next: TableData) {
        table = next
        onUpdate(next)
    }

    if (showAdd) {
        AddExceptionDialog(
            courses = table.courses,
            onDismiss = { showAdd = false },
            onAdd = { item ->
                save(table.copy(dateExceptions = ScheduleDateResolver.normalize(table.dateExceptions + item)))
                showAdd = false
            }
        )
    }
    if (showWeekLabel) {
        AddWeekLabelDialog(table.totalWeeks, onDismiss = { showWeekLabel = false }) { week, label ->
            save(table.copy(weekLabels = table.weekLabels + (week to label)))
            showWeekLabel = false
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "校历例外",
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { showAdd = true }) { Text("添加日期例外") }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text("可设置整日停课、取消某门课或补课；提醒、日程和小组件会同步生效。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                AppPanel {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("周标签", style = MaterialTheme.typography.titleMedium)
                                Text("为某周标记考试周、实训周等说明", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { showWeekLabel = true }) { Text("添加") }
                        }
                        table.weekLabels.toSortedMap().forEach { (week, label) ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("第 $week 周 · $label", Modifier.weight(1f))
                                IconButton(onClick = { save(table.copy(weekLabels = table.weekLabels - week)) }) {
                                    Icon(Icons.Outlined.Delete, "删除周标签")
                                }
                            }
                        }
                    }
                }
            }
            if (table.dateExceptions.isEmpty()) {
                item { Text("尚未添加具体日期例外", modifier = Modifier.padding(vertical = 24.dp)) }
            }
            items(table.dateExceptions, key = ScheduleDateException::id) { item ->
                AppPanel {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${item.date} · ${item.type.label()}", style = MaterialTheme.typography.titleMedium)
                            val detail = item.title.ifBlank {
                                when (item.type) {
                                    ScheduleExceptionType.DAY_OFF -> "全天课程暂停"
                                    ScheduleExceptionType.COURSE_CANCELLED -> table.courses.firstOrNull { it.seriesKey == item.courseSeriesKey }?.courseName ?: "指定课程"
                                    ScheduleExceptionType.MAKEUP -> item.makeupCourse?.courseName ?: "补课"
                                }
                            }
                            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = {
                            save(table.copy(dateExceptions = table.dateExceptions.filterNot { it.id == item.id }))
                        }) { Icon(Icons.Outlined.Delete, "删除日期例外") }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddExceptionDialog(courses: List<Course>, onDismiss: () -> Unit, onAdd: (ScheduleDateException) -> Unit) {
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var type by remember { mutableStateOf(ScheduleExceptionType.DAY_OFF) }
    var course by remember { mutableStateOf(courses.firstOrNull()) }
    var title by remember { mutableStateOf("") }
    var courseMenu by remember { mutableStateOf(false) }
    val valid = runCatching { LocalDate.parse(date) }.isSuccess &&
        (type == ScheduleExceptionType.DAY_OFF || course != null)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加日期例外") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(date, { date = it.take(10) }, label = { Text("日期（yyyy-MM-dd）") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ScheduleExceptionType.entries.forEach { value ->
                        FilterChip(selected = type == value, onClick = { type = value }, label = { Text(value.label()) })
                    }
                }
                if (type != ScheduleExceptionType.DAY_OFF) {
                    Box {
                        OutlinedButton(onClick = { courseMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(course?.courseName ?: "选择课程")
                        }
                        DropdownMenu(expanded = courseMenu, onDismissRequest = { courseMenu = false }) {
                            courses.distinctBy(Course::seriesKey).forEach { item ->
                                DropdownMenuItem(text = { Text(item.courseName) }, onClick = { course = item; courseMenu = false })
                            }
                        }
                    }
                }
                OutlinedTextField(title, { title = it.take(80) }, label = { Text("备注（可选）") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                val selected = course
                onAdd(
                    ScheduleDateException(
                        date = date,
                        type = type,
                        courseSeriesKey = selected?.seriesKey.takeIf { type != ScheduleExceptionType.DAY_OFF },
                        makeupCourse = selected?.takeIf { type == ScheduleExceptionType.MAKEUP },
                        title = title
                    )
                )
            }) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun AddWeekLabelDialog(totalWeeks: Int, onDismiss: () -> Unit, onAdd: (Int, String) -> Unit) {
    var weekText by remember { mutableStateOf("1") }
    var label by remember { mutableStateOf("") }
    val week = weekText.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加周标签") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(weekText, { weekText = it.filter(Char::isDigit).take(2) }, label = { Text("周次") }, singleLine = true)
                OutlinedTextField(label, { label = it.take(30) }, label = { Text("标签，例如：考试周") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(enabled = week in 1..totalWeeks && label.isNotBlank(), onClick = { onAdd(week!!, label.trim()) }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

private fun ScheduleExceptionType.label(): String = when (this) {
    ScheduleExceptionType.DAY_OFF -> "整日停课"
    ScheduleExceptionType.COURSE_CANCELLED -> "取消课程"
    ScheduleExceptionType.MAKEUP -> "补课"
}
