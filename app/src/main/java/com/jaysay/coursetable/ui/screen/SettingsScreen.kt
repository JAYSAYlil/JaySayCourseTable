package com.jaysay.coursetable.ui.screen

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jaysay.coursetable.R
import com.jaysay.coursetable.data.model.CourseImportAnalyzer
import com.jaysay.coursetable.data.preferences.*
import com.jaysay.coursetable.data.reminder.ReminderBlocker
import com.jaysay.coursetable.data.reminder.ReminderCalculator
import com.jaysay.coursetable.data.reminder.ReminderPolicy
import com.jaysay.coursetable.data.repository.TableData
import com.jaysay.coursetable.ui.components.AppPanel
import com.jaysay.coursetable.ui.components.AppTopBar
import com.jaysay.coursetable.ui.components.CustomBackgroundImage
import com.jaysay.coursetable.ui.theme.*
import com.jaysay.coursetable.util.TimeUtils
import java.util.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    tableData: TableData = TableData("课表1", emptyList(), semesterStart = TimeUtils.currentWeekStartDate()),
    preferences: AppPreferences,
    onUpdatePrefs: (AppPreferences) -> Unit,
    customBackground: ImageBitmap? = null,
    onChooseCustomBackground: () -> Unit = {},
    onClearCustomBackground: () -> Unit = {},
    onUpdateTable: ((TableData) -> Unit)? = null,
    onExportBackup: (sanitized: Boolean) -> Unit,
    onExportEncryptedBackup: () -> Unit = {},
    onImportBackup: () -> Unit,
    onPasteImport: () -> Unit = {},
    onExportCalendar: () -> Unit = {},
    onExportDiagnostics: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onOpenCalendarExceptions: () -> Unit = {},
    reminderPauseStatus: String? = null,
    onClearReminderPause: () -> Unit = {},
    reminderBlockers: List<ReminderBlocker> = emptyList(),
    onRequestNotificationPermission: () -> Unit = {},
    onOpenExactAlarmSettings: () -> Unit = {},
    onOpenChannelSettings: () -> Unit = {},
    onOpenBatteryOptimizationSettings: () -> Unit = {},
    onOpenAutostartSettings: () -> Unit = {},
    tablesCount: Int = 1,
    readOnlyMessage: String? = null,
    onBack: () -> Unit
) {
    // 拦截系统返回手势，回到主界面而非退出
    BackHandler(onBack = onBack)
    val context = LocalContext.current

    var table by remember(tableData) { mutableStateOf(tableData) }
    // 外观和背景不修改课表文件，数据保护模式下仍可正常使用。
    fun save(new: AppPreferences) { onUpdatePrefs(new) }
    fun saveTable(new: TableData) {
        if (readOnlyMessage == null) {
            table = new
            onUpdateTable?.invoke(new)
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.settings_title),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.settings_back))
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
                .padding(bottom = 24.dp)
        ) {
            readOnlyMessage?.let { message ->
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(stringResource(R.string.settings_readonly_title), fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.settings_readonly_message, message), fontSize = 12.sp)
                    }
                }
            }

            // ===== 外观模式 =====
            SettingsSection(title = stringResource(R.string.settings_section_appearance)) {
                val options = listOf(
                    Triple(ThemeMode.LIGHT, stringResource(R.string.settings_theme_light), Icons.Outlined.LightMode),
                    Triple(ThemeMode.DARK, stringResource(R.string.settings_theme_dark), Icons.Outlined.DarkMode),
                    Triple(ThemeMode.SYSTEM, stringResource(R.string.settings_theme_system), Icons.Outlined.SettingsBrightness),
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

            SettingsSection(title = stringResource(R.string.settings_section_background)) {
                val backgroundActive = preferences.customBackgroundRevision > 0L && customBackground != null
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(156.dp)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                        .testTag("custom-background-preview")
                ) {
                    if (customBackground != null) {
                        CustomBackgroundImage(customBackground)
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)))
                    } else {
                        Icon(
                            Icons.Outlined.Wallpaper,
                            contentDescription = null,
                            modifier = Modifier.size(42.dp).align(Alignment.Center),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                    Column(
                        modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)
                    ) {
                        Text(
                            if (backgroundActive) stringResource(R.string.settings_background_custom_enabled) else stringResource(R.string.settings_background_default),
                            color = if (customBackground != null) Color.White else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            stringResource(R.string.settings_background_hint),
                            color = if (customBackground != null) Color.White.copy(alpha = 0.86f)
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onChooseCustomBackground,
                        modifier = Modifier.weight(1f).testTag("choose-custom-background")
                    ) {
                        Icon(Icons.Outlined.Image, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (backgroundActive) stringResource(R.string.settings_change_image) else stringResource(R.string.settings_choose_image))
                    }
                    if (preferences.customBackgroundRevision > 0L) {
                        OutlinedButton(
                            onClick = onClearCustomBackground,
                            modifier = Modifier.testTag("clear-custom-background")
                        ) {
                            Text(stringResource(R.string.settings_restore_default))
                        }
                    }
                }
                Text(
                    stringResource(R.string.settings_background_privacy),
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            SettingsSection(title = stringResource(R.string.settings_section_display)) {
                PreferenceSwitchRow(
                    title = stringResource(R.string.settings_high_contrast),
                    subtitle = stringResource(R.string.settings_high_contrast_subtitle),
                    checked = preferences.highContrast,
                    onCheckedChange = { save(preferences.copy(highContrast = it)) }
                )
                PreferenceSwitchRow(
                    title = stringResource(R.string.settings_reduce_motion),
                    subtitle = stringResource(R.string.settings_reduce_motion_subtitle),
                    checked = preferences.reduceMotion,
                    onCheckedChange = { save(preferences.copy(reduceMotion = it)) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ===== 节次时间设置 =====
            val periodCount = table.periods.size
            SettingsSection(title = stringResource(R.string.settings_section_periods)) {
                Text(
                    stringResource(R.string.settings_period_count, periodCount),
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
                            stringResource(R.string.settings_period_index, idx + 1),
                            fontSize = 13.sp, modifier = Modifier.width(44.dp), fontWeight = FontWeight.Medium
                        )

                        // 开始时间
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                                .clickable(enabled = readOnlyMessage == null) { showStartPicker = true }
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
                                .clickable(enabled = readOnlyMessage == null) { showEndPicker = true }
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Text(period.end, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }

                        if (periodCount > 1) {
                            IconButton(enabled = readOnlyMessage == null, onClick = {
                                val np = table.periods.toMutableList()
                                np.removeAt(idx)
                                saveTable(table.copy(periods = np))
                            }) {
                                Icon(Icons.Default.RemoveCircleOutline, stringResource(R.string.settings_delete), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
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
                    OutlinedButton(enabled = readOnlyMessage == null, onClick = {
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
                            Toast.makeText(context, context.getString(R.string.settings_period_time_exceeds), Toast.LENGTH_SHORT).show()
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
                        Text(stringResource(R.string.settings_add_period), fontSize = 13.sp)
                    }

                    OutlinedButton(
                        enabled = readOnlyMessage == null,
                        onClick = { saveTable(table.copy(periods = TableData.defaultPeriods())) },
                        modifier = Modifier.height(48.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Icon(Icons.Default.Restore, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.settings_restore_default), fontSize = 13.sp)
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
            SettingsSection(title = stringResource(R.string.settings_section_semester)) {
                var showDatePicker by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(enabled = readOnlyMessage == null) { showDatePicker = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.CalendarMonth, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_semester_start_date), fontSize = 15.sp)
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
                            }) { Text(stringResource(R.string.settings_confirm)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.settings_cancel)) }
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
                    Text(stringResource(R.string.settings_total_weeks), fontSize = 15.sp, modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = { if (totalWeeks > 1) saveTable(table.copy(totalWeeks = table.totalWeeks - 1)) },
                        enabled = readOnlyMessage == null && totalWeeks > 1
                    ) {
                        Icon(Icons.Default.Remove, stringResource(R.string.settings_decrease_weeks), modifier = Modifier.size(18.dp))
                    }
                    Text("" + totalWeeks, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
                    IconButton(
                        onClick = { if (totalWeeks < 30) saveTable(table.copy(totalWeeks = table.totalWeeks + 1)) },
                        enabled = readOnlyMessage == null && totalWeeks < 30
                    ) {
                        Icon(Icons.Default.Add, stringResource(R.string.settings_increase_weeks), modifier = Modifier.size(18.dp))
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)

                // ===== 停课周（校历）=====
                var showExcludedWeeksDialog by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .testTag("excluded-weeks-setting")
                        .clickable(enabled = readOnlyMessage == null) { showExcludedWeeksDialog = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.EventBusy, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_excluded_weeks), fontSize = 15.sp)
                        Text(
                            if (table.excludedWeeks.isEmpty()) stringResource(R.string.settings_excluded_weeks_unset)
                            else stringResource(R.string.settings_excluded_weeks_value, TimeUtils.formatWeeks(table.excludedWeeks)),
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
                        title = { Text(stringResource(R.string.settings_excluded_weeks_dialog_title)) },
                        text = {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    stringResource(R.string.settings_excluded_weeks_hint),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(8.dp))
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 360.dp)
                                        .testTag("excluded-weeks-list")
                                ) {
                                    items((1..totalWeeks).toList(), key = { it }) { week ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().clickable {
                                                selected = if (week in selected) selected - week else selected + week
                                            }.padding(vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(checked = week in selected, onCheckedChange = {
                                                selected = if (week in selected) selected - week else selected + week
                                            })
                                            Text(stringResource(R.string.settings_week_number, week), fontSize = 14.sp)
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                saveTable(table.copy(excludedWeeks = selected.sorted()))
                                showExcludedWeeksDialog = false
                            }) { Text(stringResource(R.string.settings_confirm)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showExcludedWeeksDialog = false }) { Text(stringResource(R.string.settings_cancel)) }
                        }
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SettingsActionRow(
                    icon = Icons.Outlined.EditCalendar,
                    title = stringResource(R.string.settings_calendar_exceptions),
                    subtitle = stringResource(R.string.settings_calendar_exceptions_subtitle),
                    enabled = readOnlyMessage == null,
                    onClick = onOpenCalendarExceptions
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ===== 上课提醒 =====
            SettingsSection(title = stringResource(R.string.settings_section_reminder)) {
                var visiblePauseStatus by remember(reminderPauseStatus) { mutableStateOf(reminderPauseStatus) }
                val nextReminder = remember(table, preferences) {
                    if (!preferences.reminderEnabled) null else {
                        val now = LocalDateTime.now()
                        ReminderCalculator.upcomingInstances(
                            courses = table.courses,
                            semesterStart = table.semesterStart,
                            totalWeeks = table.totalWeeks,
                            periods = table.periods,
                            fromDate = LocalDate.now(),
                            days = 31,
                            excludedWeeks = table.excludedWeeks.toSet(),
                            exceptions = table.dateExceptions
                        ).asSequence().filter { ReminderPolicy.isEnabled(it.course, preferences) }
                            .map { it to ReminderCalculator.reminderAt(it, ReminderPolicy.advanceMinutes(it.course, preferences)) }
                            .firstOrNull { (_, time) -> time.isAfter(now) }
                    }
                }
                var showReminderConfirm by remember { mutableStateOf(false) }
                var showAutostartGuide by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_reminder_enable), fontSize = 15.sp)
                        Text(
                            stringResource(R.string.settings_reminder_enable_subtitle),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = preferences.reminderEnabled,
                        enabled = readOnlyMessage == null,
                        onCheckedChange = { enabled ->
                            if (enabled) showReminderConfirm = true
                            else save(preferences.copy(reminderEnabled = false))
                        }
                    )
                }
                if (showReminderConfirm) {
                    AlertDialog(
                        onDismissRequest = { showReminderConfirm = false },
                        title = { Text(stringResource(R.string.settings_reminder_enable_confirm_title)) },
                        text = { Text(stringResource(R.string.settings_reminder_enable_confirm_text)) },
                        confirmButton = {
                            TextButton(onClick = {
                                showReminderConfirm = false
                                save(preferences.copy(reminderEnabled = true))
                                showAutostartGuide = true
                            }) {
                                Text(stringResource(R.string.settings_reminder_enable_confirm_ok))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showReminderConfirm = false }) {
                                Text(stringResource(R.string.settings_reminder_enable_confirm_cancel))
                            }
                        }
                    )
                }
                if (showAutostartGuide) {
                    AlertDialog(
                        onDismissRequest = { showAutostartGuide = false },
                        title = { Text(stringResource(R.string.settings_reminder_autostart_guide_title)) },
                        text = { Text(stringResource(R.string.settings_reminder_autostart_guide_text)) },
                        confirmButton = {
                            TextButton(onClick = {
                                showAutostartGuide = false
                                onOpenAutostartSettings()
                            }) {
                                Text(stringResource(R.string.settings_reminder_autostart_guide_go))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showAutostartGuide = false }) {
                                Text(stringResource(R.string.settings_reminder_autostart_guide_later))
                            }
                        }
                    )
                }
                if (preferences.reminderEnabled) {
                    if (reminderBlockers.isEmpty()) {
                        Text(
                            stringResource(R.string.settings_reminder_status_ok),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        reminderBlockers.forEach { blocker ->
                            val (label, fixLabel, onFix) = when (blocker) {
                                ReminderBlocker.NOTIFICATION_PERMISSION -> Triple(
                                    stringResource(R.string.settings_reminder_notif_permission),
                                    stringResource(R.string.settings_reminder_notif_fix),
                                    onRequestNotificationPermission
                                )
                                ReminderBlocker.EXACT_ALARM -> Triple(
                                    stringResource(R.string.settings_reminder_exact_alarm),
                                    stringResource(R.string.settings_reminder_exact_fix),
                                    onOpenExactAlarmSettings
                                )
                                ReminderBlocker.CHANNEL_DISABLED -> Triple(
                                    stringResource(R.string.settings_reminder_channel_off),
                                    stringResource(R.string.settings_reminder_channel_fix),
                                    onOpenChannelSettings
                                )
                                ReminderBlocker.BATTERY_OPTIMIZATION -> Triple(
                                    stringResource(R.string.settings_reminder_battery),
                                    stringResource(R.string.settings_reminder_battery_fix),
                                    onOpenBatteryOptimizationSettings
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.WarningAmber,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    label,
                                    modifier = Modifier.weight(1f),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                                TextButton(onClick = onFix, contentPadding = PaddingValues(horizontal = 8.dp)) {
                                    Text(fixLabel, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.settings_reminder_advance), fontSize = 15.sp, modifier = Modifier.weight(1f))
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
                                stringResource(R.string.settings_reminder_minutes, minutes),
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(stringResource(R.string.settings_next_reminder), fontSize = 15.sp)
                    Text(
                        nextReminder?.let { (instance, time) ->
                            "${instance.course.courseName} · ${time.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))}"
                        } ?: if (preferences.reminderEnabled) stringResource(R.string.settings_no_upcoming_reminder) else stringResource(R.string.settings_reminder_disabled),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (visiblePauseStatus != null) {
                        Text(
                            visiblePauseStatus.orEmpty(),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                        TextButton(
                            onClick = {
                                onClearReminderPause()
                                visiblePauseStatus = null
                            },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(stringResource(R.string.settings_resume_reminder))
                        }
                    }
                }
                if (preferences.reminderEnabled) {
                    // 已开启提醒时显示一行“自启动”提示入口，便于随时重新打开系统自启动设置。
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.settings_reminder_autostart_hint),
                            modifier = Modifier.weight(1f),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = onOpenAutostartSettings, contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Text(stringResource(R.string.settings_reminder_autostart_fix), fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            SettingsSection(title = stringResource(R.string.settings_section_backup)) {
                SettingsActionRow(
                    icon = Icons.Outlined.Backup,
                    title = stringResource(R.string.settings_export_full_backup),
                    subtitle = stringResource(R.string.settings_export_full_backup_subtitle),
                    enabled = readOnlyMessage == null,
                    onClick = { onExportBackup(false) }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SettingsActionRow(
                    icon = Icons.Outlined.Lock,
                    title = stringResource(R.string.settings_export_encrypted_backup),
                    subtitle = stringResource(R.string.settings_export_encrypted_backup_subtitle),
                    enabled = readOnlyMessage == null,
                    onClick = onExportEncryptedBackup
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SettingsActionRow(
                    icon = Icons.Outlined.Share,
                    title = stringResource(R.string.settings_export_sanitized),
                    subtitle = stringResource(R.string.settings_export_sanitized_subtitle),
                    enabled = readOnlyMessage == null,
                    onClick = { onExportBackup(true) }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SettingsActionRow(
                    icon = Icons.Outlined.Restore,
                    title = stringResource(R.string.settings_import_backup),
                    subtitle = stringResource(R.string.settings_import_backup_subtitle),
                    onClick = onImportBackup
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SettingsActionRow(
                    icon = Icons.Outlined.History,
                    title = stringResource(R.string.settings_history),
                    subtitle = stringResource(R.string.settings_history_subtitle),
                    onClick = onOpenHistory
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SettingsActionRow(
                    icon = Icons.Outlined.ContentPaste,
                    title = stringResource(R.string.settings_paste_import),
                    subtitle = stringResource(R.string.settings_paste_import_subtitle),
                    enabled = readOnlyMessage == null,
                    onClick = onPasteImport
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SettingsActionRow(
                    icon = Icons.Outlined.CalendarMonth,
                    title = stringResource(R.string.settings_export_calendar),
                    subtitle = stringResource(R.string.settings_export_calendar_subtitle),
                    enabled = readOnlyMessage == null,
                    onClick = onExportCalendar
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ===== 数据诊断（只显示统计，不展示课程原文）=====
            SettingsSection(title = stringResource(R.string.settings_section_diagnostics)) {
                val courseCount = table.courses.size
                val conflictCount = remember(table.courses) {
                    CourseImportAnalyzer.findConflictsAmong(table.courses).size
                }
                val stats = listOf(
                    stringResource(R.string.settings_diag_table_count) to "$tablesCount",
                    stringResource(R.string.settings_diag_course_count) to "$courseCount",
                    stringResource(R.string.settings_diag_period_count) to "${table.periods.size}",
                    stringResource(R.string.settings_diag_total_weeks) to "${table.totalWeeks}",
                    stringResource(R.string.settings_excluded_weeks) to "${table.excludedWeeks.size}",
                    stringResource(R.string.settings_diag_conflicts) to if (conflictCount > 0) stringResource(R.string.settings_diag_conflicts_value, conflictCount) else stringResource(R.string.settings_none)
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
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SettingsActionRow(
                    icon = Icons.Outlined.BugReport,
                    title = stringResource(R.string.settings_export_diagnostics),
                    subtitle = stringResource(R.string.settings_export_diagnostics_subtitle),
                    onClick = onExportDiagnostics
                )
            }
        }
    }
}

@Composable
private fun PreferenceSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
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
        title = { Text(stringResource(R.string.settings_time_picker_title)) },
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.settings_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) } }
    )
}
