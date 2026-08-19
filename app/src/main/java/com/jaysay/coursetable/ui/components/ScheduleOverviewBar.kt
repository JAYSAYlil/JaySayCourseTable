package com.jaysay.coursetable.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jaysay.coursetable.data.model.AgendaCourseSlot
import com.jaysay.coursetable.data.model.TodayAgenda
import com.jaysay.coursetable.data.model.TodayAgendaPhase
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
    onSettingsClick: () -> Unit,
    writesEnabled: Boolean = true
) {
    var searchVisible by remember { mutableStateOf(false) }
    var agendaVisible by remember { mutableStateOf(false) }
    var moreActionsVisible by remember { mutableStateOf(false) }
    val compactAgenda = agenda.compactText()

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
                placeholder = { Text("课程、教师或教室", maxLines = 1) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Clear, "清空搜索")
                        }
                    }
                },
                singleLine = true
            )
            IconButton(onClick = { onSearchQueryChange(""); searchVisible = false }) {
                Icon(Icons.Default.Close, "关闭搜索")
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
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(Icons.Default.ArrowDropDown, "切换课表", tint = MaterialTheme.colorScheme.primary)
                }
                Text(
                    compactAgenda,
                    modifier = Modifier.fillMaxWidth().clickable { agendaVisible = true }
                        .semantics { contentDescription = "今日课程摘要，${agenda.accessibilityText()}，点击查看详情" }
                        .testTag("today-agenda-summary"),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = { searchVisible = true }, modifier = Modifier.size(48.dp).testTag("course-search-button")) {
                Icon(Icons.Default.Search, "搜索课程", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onAddCourseClick, enabled = writesEnabled, modifier = Modifier.size(48.dp).testTag("add-course-button")) {
                Icon(Icons.Default.AddCircleOutline, "添加课程", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onImportClick, enabled = writesEnabled, modifier = Modifier.size(48.dp).testTag("import-course-button")) {
                Icon(Icons.Default.FileOpen, "导入课表", tint = MaterialTheme.colorScheme.primary)
            }
            if (compactActions) {
                Box {
                    IconButton(
                        onClick = { moreActionsVisible = true },
                        modifier = Modifier.size(48.dp).testTag("more-actions-button")
                    ) {
                        Icon(Icons.Default.MoreVert, "更多操作", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(
                        expanded = moreActionsVisible,
                        onDismissRequest = { moreActionsVisible = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("定位到今天") },
                            leadingIcon = { Icon(Icons.Default.MyLocation, null) },
                            onClick = { moreActionsVisible = false; onLocateToday() }
                        )
                        DropdownMenuItem(
                            text = { Text("设置") },
                            leadingIcon = { Icon(Icons.Default.Settings, null) },
                            onClick = { moreActionsVisible = false; onSettingsClick() }
                        )
                    }
                }
            } else {
                IconButton(onClick = onLocateToday, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.MyLocation, "定位到今天", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onSettingsClick, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Settings, "设置", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    }

    if (agendaVisible) {
        AlertDialog(
            onDismissRequest = { agendaVisible = false },
            title = { Text("今日课程") },
            text = { Text(agenda.accessibilityText()) },
            confirmButton = { TextButton(onClick = { agendaVisible = false }) { Text("知道了") } }
        )
    }
}

@Composable
fun ReadOnlyRecoveryBanner(message: String, onRecoveryClick: () -> Unit) {
    Surface(
        onClick = onRecoveryClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("read-only-recovery-banner")
            .semantics { contentDescription = "数据损坏，只读保护已启用。点击进入设置恢复完整备份" },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f))
    ) {
        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.WarningAmber, null)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("课表数据保护模式", fontWeight = FontWeight.Bold)
                Text("数据未被覆盖。当前只读，点击进入设置并恢复完整备份。", fontSize = 12.sp)
                Text(message, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun TodayAgenda.compactText(): String = when (phase) {
    TodayAgendaPhase.OUTSIDE_SEMESTER -> "今日 · 当前不在学期周次"
    TodayAgendaPhase.NO_COURSES -> "今日 · 无课"
    TodayAgendaPhase.BEFORE_FIRST -> "下一节 · ${next?.course?.courseName.orEmpty()} ${next?.startMinute.asTime()}"
    TodayAgendaPhase.IN_CLASS -> "正在上 · ${current?.course?.courseName.orEmpty()} 至 ${current?.endMinute.asTime()}"
    TodayAgendaPhase.BETWEEN_CLASSES -> "课间 · 下一节 ${next?.course?.courseName.orEmpty()} ${next?.startMinute.asTime()}"
    TodayAgendaPhase.FINISHED -> "今日课程已结束"
    TodayAgendaPhase.INVALID_TIME -> "今日课程时间异常 · 请在设置中检查节次"
}

private fun TodayAgenda.accessibilityText(): String {
    val slot = current ?: next
    val detail = slot?.fullText().orEmpty()
    val remainder = if (remainingCount > 0) "，今日还有 $remainingCount 节安排" else ""
    val invalid = if (invalidCourseCount > 0) "，另有 $invalidCourseCount 条课程时间异常" else ""
    return when (phase) {
        TodayAgendaPhase.OUTSIDE_SEMESTER -> "当前日期不在已设置的学期周次内"
        TodayAgendaPhase.NO_COURSES -> "第 ${week ?: 0} 周，今天没有课程"
        TodayAgendaPhase.BEFORE_FIRST -> "下一节课：$detail$remainder$invalid"
        TodayAgendaPhase.IN_CLASS -> "正在上课：$detail$remainder$invalid"
        TodayAgendaPhase.BETWEEN_CLASSES -> "现在是课间，下一节课：$detail$remainder$invalid"
        TodayAgendaPhase.FINISHED -> "今天的课程已经全部结束$invalid"
        TodayAgendaPhase.INVALID_TIME -> "今天有课程，但节次时间无效，请在设置中检查"
    }
}

private fun AgendaCourseSlot.fullText(): String = buildString {
    append(course.courseName)
    if (course.teacher.isNotBlank()) append("，教师 ${course.teacher}")
    if (course.classroom.isNotBlank()) append("，教室 ${course.classroom}")
    append("，${startMinute.asTime()} 到 ${endMinute.asTime()}")
}

private fun Int?.asTime(): String = this?.let { TimeUtils.formatMinuteOfDay(it) }.orEmpty()
