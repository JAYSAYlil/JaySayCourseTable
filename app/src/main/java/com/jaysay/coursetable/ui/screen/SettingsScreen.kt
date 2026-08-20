package com.jaysay.coursetable.ui.screen

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jaysay.coursetable.data.model.CourseImportAnalyzer
import com.jaysay.coursetable.data.preferences.*
import com.jaysay.coursetable.data.repository.TableData
import com.jaysay.coursetable.ui.components.AppPanel
import com.jaysay.coursetable.ui.components.AppTopBar
import com.jaysay.coursetable.ui.theme.*
import com.jaysay.coursetable.util.TimeUtils
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    tableData: TableData = TableData("课表1", emptyList(), semesterStart = com.jaysay.coursetable.util.TimeUtils.todayDate()),
    preferences: AppPreferences,
    onUpdatePrefs: (AppPreferences) -> Unit,
    onUpdateTable: ((TableData) -> Unit)? = null,
    onExportBackup: (sanitized: Boolean) -> Unit,
    onImportBackup: () -> Unit,
    onPasteImport: () -> Unit = {},
    onExportCalendar: () -> Unit = {},
    tablesCount: Int = 1,
    readOnlyMessage: String? = null,
    onBack: () -> Unit
) {
    // 拦截系统返回手势，回到主界面而非退出
    BackHandler(onBack = onBack)
    val context = LocalContext.current

    var table by remember(tableData) { mutableStateOf(tableData) }
    fun save(new: AppPreferences) { if (readOnlyMessage == null) onUpdatePrefs(new) }
    fun saveTable(new: TableData) {
        if (readOnlyMessage == null) {
            table = new
            onUpdateTable?.invoke(new)
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "设置",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .background(MaterialTheme.colorScheme.background)
                .padding(bottom = 80.dp) // 底部留空间避免遮挡
        ) {
            readOnlyMessage?.let { message ->
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("课表数据处于只读保护", fontWeight = FontWeight.Bold)
                        Text("$message\n普通设置已停用，请使用下方“从备份恢复”。", fontSize = 12.sp)
                    }
                }
            }

            // ===== 外观模式 =====
            SettingsSection(title = "外观模式") {
                val options = listOf(
                    Triple(ThemeMode.LIGHT, "浅色模式", Icons.Outlined.LightMode),
                    Triple(ThemeMode.DARK, "深色模式", Icons.Outlined.DarkMode),
                    Triple(ThemeMode.SYSTEM, "跟随系统", Icons.Outlined.SettingsBrightness),
                )
                options.forEach { (mode, label, icon) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { save(preferences.copy(themeMode = mode)) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(label, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        RadioButton(
                            selected = preferences.themeMode == mode,
                            onClick = { save(preferences.copy(themeMode = mode)) },
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ===== 节次时间设置 =====
            val periodCount = table.periods.size
            SettingsSection(title = "节次时间设置") {
                Text(
                    "共 " + periodCount + " 节课，点击时间修改",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )

                table.periods.forEachIndexed { idx, period ->
                    key(idx) {
                    var showStartPicker by remember { mutableStateOf(false) }
                    var showEndPicker by remember { mutableStateOf(false) }

                    // 解析当前时间
                    val startParts = period.start.split(":")
                    val startH = startParts.getOrNull(0)?.toIntOrNull() ?: 8
                    val startM = startParts.getOrNull(1)?.toIntOrNull() ?: 0
                    val endParts = period.end.split(":")
                    val endH = endParts.getOrNull(0)?.toIntOrNull() ?: 8
                    val endM = endParts.getOrNull(1)?.toIntOrNull() ?: 45

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "第" + (idx + 1) + "节",
                            fontSize = 13.sp, modifier = Modifier.width(44.dp), fontWeight = FontWeight.Medium
                        )

                        // 开始时间
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                                .clickable { showStartPicker = true }
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Text(period.start, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }

                        Text(" ～ ", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        // 结束时间
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                                .clickable { showEndPicker = true }
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Text(period.end, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }

                        if (periodCount > 1) {
                            IconButton(onClick = {
                                val np = table.periods.toMutableList()
                                np.removeAt(idx)
                                saveTable(table.copy(periods = np))
                            }) {
                                Icon(Icons.Default.RemoveCircleOutline, "删除", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    // 弹窗用LaunchedEffect防重复弹出
                    if (showStartPicker) {
                        var pickH by remember { mutableIntStateOf(startH) }
                        var pickM by remember { mutableIntStateOf(startM) }
                        TimePickerDialog(onDismiss = { showStartPicker = false }, onConfirm = {
                            val newTime = "%02d:%02d".format(pickH, pickM)
                            val np = table.periods.toMutableList()
                            np[idx] = PeriodTime(newTime, period.end)
                            saveTable(table.copy(periods = np))
                            showStartPicker = false
                        }, initialHour = startH, initialMinute = startM,
                            onTimeChange = { h, m -> pickH = h; pickM = m })
                    }
                    if (showEndPicker) {
                        var pickH by remember { mutableIntStateOf(endH) }
                        var pickM by remember { mutableIntStateOf(endM) }
                        TimePickerDialog(onDismiss = { showEndPicker = false }, onConfirm = {
                            val newTime = "%02d:%02d".format(pickH, pickM)
                            val np = table.periods.toMutableList()
                            np[idx] = PeriodTime(period.start, newTime)
                            saveTable(table.copy(periods = np))
                            showEndPicker = false
                        }, initialHour = endH, initialMinute = endM,
                            onTimeChange = { h, m -> pickH = h; pickM = m })
                    }
                    } // end key(idx)
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(onClick = {
                        val last = table.periods.lastOrNull()
                        val startMinute = if (last != null) {
                            val p = last.end.split(":")
                            val h = p.getOrNull(0)?.toIntOrNull() ?: 22
                            val m = p.getOrNull(1)?.toIntOrNull() ?: 0
                            h * 60 + m + 10
                        } else 8 * 60
                        val endMinute = startMinute + 45
                        if (endMinute > 23 * 60 + 59) {
                            // 避免跨天回绕出 00:xx 的错误时间
                            Toast.makeText(context, "节次时间不能超过 23:59，请先修改最后一节的结束时间", Toast.LENGTH_SHORT).show()
                        } else {
                            val ns = "%02d:%02d".format(startMinute / 60, startMinute % 60)
                            val ne = "%02d:%02d".format(endMinute / 60, endMinute % 60)
                            val np = table.periods.toMutableList()
                            np.add(PeriodTime(ns, ne))
                            saveTable(table.copy(periods = np))
                        }
                    }, modifier = Modifier.height(48.dp)) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("添加节次", fontSize = 13.sp)
                    }

                    OutlinedButton(onClick = { saveTable(table.copy(periods = TableData.defaultPeriods())) },
                        modifier = Modifier.height(48.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Icon(Icons.Default.Restore, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("恢复默认", fontSize = 13.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ===== 学期设置 =====
            val startMillis = remember(table.semesterStart) {
                val p = table.semesterStart.split("-")
                val cal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
                cal.set(p[0].toInt(), p[1].toInt() - 1, p[2].toInt(), 12, 0, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            SettingsSection(title = "学期设置") {
                var showDatePicker by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.CalendarMonth, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("开学日期", fontSize = 15.sp)
                        Text(table.semesterStart, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
                }
                // 状态放在 if 外面避免重建导致卡顿
                val dateState = rememberDatePickerState()
                LaunchedEffect(startMillis) { dateState.selectedDateMillis = startMillis }
                if (showDatePicker) {
                    DatePickerDialog(
                        onDismissRequest = { showDatePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                val millis = dateState.selectedDateMillis
                                if (millis != null) {
                                    val picked = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
                                    picked.timeInMillis = millis
                                    val dow = picked.get(Calendar.DAY_OF_WEEK)
                                    val daysBack = if (dow == Calendar.SUNDAY) 6 else dow - Calendar.MONDAY
                                    picked.add(Calendar.DAY_OF_YEAR, -daysBack)
                                    val monday = "%04d-%02d-%02d".format(
                                        picked.get(Calendar.YEAR), picked.get(Calendar.MONTH)+1, picked.get(Calendar.DAY_OF_MONTH))
                                    saveTable(table.copy(semesterStart = monday))
                                }
                                showDatePicker = false
                            }) { Text("确定") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDatePicker = false }) { Text("取消") }
                        }
                    ) {
                        DatePicker(state = dateState)
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)

                val totalWeeks = table.totalWeeks
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.ViewWeek, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("总周数", fontSize = 15.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = { if (totalWeeks > 1) saveTable(table.copy(totalWeeks = table.totalWeeks - 1)) }) {
                        Icon(Icons.Default.Remove, "减少总周数", modifier = Modifier.size(18.dp))
                    }
                    Text("" + totalWeeks, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
                    IconButton(onClick = { if (totalWeeks < 30) saveTable(table.copy(totalWeeks = table.totalWeeks + 1)) }) {
                        Icon(Icons.Default.Add, "增加总周数", modifier = Modifier.size(18.dp))
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)

                // ===== 停课周（校历）=====
                var showExcludedWeeksDialog by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(enabled = readOnlyMessage == null) { showExcludedWeeksDialog = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.EventBusy, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("停课周", fontSize = 15.sp)
                        Text(
                            if (table.excludedWeeks.isEmpty()) "未设置，用于节假日/考试周"
                            else "第 ${TimeUtils.formatWeeks(table.excludedWeeks)}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
                }
                if (showExcludedWeeksDialog) {
                    var selected by remember(table.excludedWeeks) {
                        mutableStateOf(table.excludedWeeks.toSet())
                    }
                    AlertDialog(
                        onDismissRequest = { showExcludedWeeksDialog = false },
                        title = { Text("选择停课周") },
                        text = {
                            Column(modifier = Modifier.verticalScroll(rememberScrollState()).heightIn(max = 360.dp)) {
                                Text(
                                    "这些周不显示课程、不触发上课提醒（如节假日、考试周）",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(8.dp))
                                (1..totalWeeks).forEach { week ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            selected = if (week in selected) selected - week else selected + week
                                        }.padding(vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(checked = week in selected, onCheckedChange = {
                                            selected = if (week in selected) selected - week else selected + week
                                        })
                                        Text("第 $week 周", fontSize = 14.sp)
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                saveTable(table.copy(excludedWeeks = selected.sorted()))
                                showExcludedWeeksDialog = false
                            }) { Text("确定") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showExcludedWeeksDialog = false }) { Text("取消") }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ===== 上课提醒 =====
            SettingsSection(title = "上课提醒") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("提醒上课", fontSize = 15.sp)
                        Text(
                            "按节次时间提前通知；精确闹钟不可用时自动采用近似提醒",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = preferences.reminderEnabled,
                        enabled = readOnlyMessage == null,
                        onCheckedChange = { enabled ->
                            save(preferences.copy(reminderEnabled = enabled))
                        }
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("提前提醒", fontSize = 15.sp, modifier = Modifier.weight(1f))
                    listOf(5, 10, 15, 30).forEach { minutes ->
                        val selected = preferences.reminderMinutes == minutes
                        Box(
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable(enabled = readOnlyMessage == null) {
                                    save(preferences.copy(reminderMinutes = minutes))
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "$minutes 分钟",
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            SettingsSection(title = "数据备份与恢复") {
                SettingsActionRow(
                    icon = Icons.Outlined.Backup,
                    title = "导出完整备份",
                    subtitle = "包含全部课表和备注，可用于恢复",
                    enabled = readOnlyMessage == null,
                    onClick = { onExportBackup(false) }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SettingsActionRow(
                    icon = Icons.Outlined.Share,
                    title = "导出脱敏副本",
                    subtitle = "移除教师、教室、班号、院系和备注，仅用于分享",
                    enabled = readOnlyMessage == null,
                    onClick = { onExportBackup(true) }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SettingsActionRow(
                    icon = Icons.Outlined.Restore,
                    title = "从备份恢复",
                    subtitle = "先校验文件，再确认是否替换当前课表",
                    onClick = onImportBackup
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SettingsActionRow(
                    icon = Icons.Outlined.ContentPaste,
                    title = "粘贴导入课程",
                    subtitle = "复制网页课表文本，每行：星期 节次 课程名 [教室] [教师] [周次]",
                    enabled = readOnlyMessage == null,
                    onClick = onPasteImport
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SettingsActionRow(
                    icon = Icons.Outlined.CalendarMonth,
                    title = "导出日历（.ics）",
                    subtitle = "生成 iCal 文件，可导入系统日历或分享",
                    onClick = onExportCalendar
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ===== 数据诊断（只显示统计，不展示课程原文）=====
            SettingsSection(title = "数据诊断") {
                val courseCount = table.courses.size
                val conflictCount = remember(table.courses) {
                    CourseImportAnalyzer.findConflictsAmong(table.courses).size
                }
                val stats = listOf(
                    "课表总数" to "$tablesCount",
                    "当前课表课程数" to "$courseCount",
                    "节次数量" to "${table.periods.size}",
                    "学期总周数" to "${table.totalWeeks}",
                    "停课周" to "${table.excludedWeeks.size}",
                    "课程时间冲突" to if (conflictCount > 0) "$conflictCount 处（建议在课程编辑时确认）" else "无"
                )
                stats.forEach { (label, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val contentAlpha = if (enabled) 1f else 0.38f
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary.copy(alpha = contentAlpha), modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha))
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha))
        }
        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f))
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 7.dp))
        AppPanel(modifier = Modifier.padding(horizontal = 16.dp)) {
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    onDismiss: () -> Unit, onConfirm: () -> Unit,
    initialHour: Int, initialMinute: Int,
    onTimeChange: (Int, Int) -> Unit
) {
    val state = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute, is24Hour = true)
    LaunchedEffect(state.hour, state.minute) { onTimeChange(state.hour, state.minute) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择时间") },
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
