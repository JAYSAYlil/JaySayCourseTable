package com.jaysay.coursetable.ui.screen

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
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
import com.jaysay.coursetable.ui.components.AppTopBar
import com.jaysay.coursetable.ui.components.AppDatePickerSheet
import com.jaysay.coursetable.ui.components.AppTimePickerSheet
import com.jaysay.coursetable.ui.components.CustomBackgroundImage
import com.jaysay.coursetable.ui.theme.*
import com.jaysay.coursetable.util.TimeUtils
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** 卡片内的一个可搜索单元：keywords 参与不区分大小写匹配，content 为条目内容。 */
internal class SettingsItem(
    val keywords: List<String>,
    val content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
)

/** 一张分组卡片：标题显示在卡片上方，items 按搜索过滤后渲染。 */
private class SettingsSectionData(
    val title: String,
    val items: List<SettingsItem>
)

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
    onExportExcelTemplate: () -> Unit = {},
    onExportDiagnostics: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onOpenCalendarExceptions: () -> Unit = {},
    reminderPauseStatus: String? = null,
    onClearReminderPause: () -> Unit = {},
    reminderBlockers: List<ReminderBlocker> = emptyList(),
    onRequestNotificationPermission: () -> Unit = {},
    onOpenExactAlarmSettings: () -> Unit = {},
    onOpenChannelSettings: () -> Unit = {},
    onOpenAutostartSettings: () -> Unit = {},
    widgetPresent: Boolean = false,
    onChooseAutoBackupLocation: () -> Unit = {},
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

    var searchText by remember { mutableStateOf("") }
    val query = searchText.trim()
    val isSearching = query.isNotEmpty()

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.settings_title),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.settings_back))
                    }
                }
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(top = AppSpacing.screenH, bottom = AppSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            readOnlyMessage?.let { message ->
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
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

            // ===== 搜索框 =====
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                singleLine = true,
                shape = AppShapes.input,
                placeholder = {
                    Text(
                        stringResource(R.string.settings_search_placeholder),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (searchText.isNotEmpty()) {
                        IconButton(onClick = { searchText = "" }, modifier = Modifier.testTag("settings-search-clear")) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.settings_search_clear))
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .testTag("settings-search-field")
            )

            // ===== 分区数据（标题/副标题参与搜索匹配）=====

            // —— 通用：外观模式 + 课表背景 + 显示与无障碍 ——
            val generalItems = listOf(
                SettingsItem(
                    keywords = listOf(
                        stringResource(R.string.settings_section_appearance),
                        stringResource(R.string.settings_theme_light),
                        stringResource(R.string.settings_theme_dark),
                        stringResource(R.string.settings_theme_system)
                    )
                ) {
                    SettingsGroupHeader(stringResource(R.string.settings_section_appearance))
                    val options = listOf(
                        Triple(ThemeMode.LIGHT, stringResource(R.string.settings_theme_light), Icons.Rounded.LightMode),
                        Triple(ThemeMode.DARK, stringResource(R.string.settings_theme_dark), Icons.Rounded.DarkMode),
                        Triple(ThemeMode.SYSTEM, stringResource(R.string.settings_theme_system), Icons.Rounded.SettingsBrightness),
                    )
                    options.forEach { (mode, label, icon) ->
                        val interaction = remember { MutableInteractionSource() }
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .pressScale(interaction)
                                .clickable(interactionSource = interaction, indication = null) {
                                    save(preferences.copy(themeMode = mode))
                                }
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
                },
                SettingsItem(
                    keywords = listOf(
                        stringResource(R.string.settings_section_background),
                        stringResource(R.string.settings_choose_image),
                        stringResource(R.string.settings_change_image),
                        stringResource(R.string.settings_restore_default),
                        stringResource(R.string.settings_background_default),
                        stringResource(R.string.settings_background_custom_enabled),
                        stringResource(R.string.settings_background_hint),
                        stringResource(R.string.settings_background_privacy)
                    )
                ) {
                    SettingsGroupHeader(stringResource(R.string.settings_section_background))
                    val backgroundActive = preferences.customBackgroundRevision > 0L && customBackground != null
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(156.dp)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                            .testTag("custom-background-preview")
                    ) {
                        if (customBackground != null) {
                            CustomBackgroundImage(customBackground)
                            if (preferences.customBackgroundOverlayEnabled) {
                                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)))
                            }
                        } else {
                            Icon(
                                Icons.Rounded.Wallpaper,
                                contentDescription = null,
                                modifier = Modifier.size(42.dp).align(Alignment.Center),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                        Column(
                            modifier = Modifier.align(Alignment.BottomStart)
                                .clip(RoundedCornerShape(9.dp))
                                .background(
                                    if (customBackground != null) Color.Black.copy(alpha = 0.52f)
                                    else Color.Transparent
                                )
                                .padding(10.dp)
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
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onChooseCustomBackground,
                            modifier = Modifier.weight(1f).testTag("choose-custom-background")
                        ) {
                            Icon(Icons.Rounded.Image, null, modifier = Modifier.size(18.dp))
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
                },
                SettingsItem(
                    keywords = listOf(
                        stringResource(R.string.settings_background_overlay),
                        stringResource(R.string.settings_background_overlay_subtitle)
                    )
                ) {
                    PreferenceSwitchRow(
                        title = stringResource(R.string.settings_background_overlay),
                        subtitle = stringResource(R.string.settings_background_overlay_subtitle),
                        checked = preferences.customBackgroundOverlayEnabled,
                        onCheckedChange = { save(preferences.copy(customBackgroundOverlayEnabled = it)) },
                        switchTestTag = "background-readability-overlay-switch"
                    )
                },
                SettingsItem(
                    keywords = listOf(
                        stringResource(R.string.settings_section_display),
                        stringResource(R.string.settings_high_contrast),
                        stringResource(R.string.settings_high_contrast_subtitle)
                    )
                ) {
                    SettingsGroupHeader(stringResource(R.string.settings_section_display))
                    PreferenceSwitchRow(
                        title = stringResource(R.string.settings_high_contrast),
                        subtitle = stringResource(R.string.settings_high_contrast_subtitle),
                        checked = preferences.highContrast,
                        onCheckedChange = { save(preferences.copy(highContrast = it)) }
                    )
                },
            )

            // —— 上课提醒 ——
            val reminderItems = listOf(
                SettingsItem(
                    keywords = listOf(
                        stringResource(R.string.settings_reminder_enable),
                        stringResource(R.string.settings_reminder_enable_subtitle),
                        stringResource(R.string.settings_reminder_notif_permission)
                    )
                ) {
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
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Rounded.WarningAmber,
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
                },
                SettingsItem(
                    keywords = listOf(stringResource(R.string.settings_reminder_advance))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.settings_reminder_advance), fontSize = 15.sp, modifier = Modifier.weight(1f))
                        listOf(5, 10, 15, 30).forEach { minutes ->
                            val selected = preferences.reminderMinutes == minutes
                            val chipInteraction = remember { MutableInteractionSource() }
                            val chipColor by animateColorAsState(
                                targetValue = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                                animationSpec = Motion.eased(),
                                label = "reminderChipColor"
                            )
                            Box(
                                modifier = Modifier
                                    .padding(start = 6.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(chipColor)
                                    .pressScale(chipInteraction)
                                    .clickable(
                                        interactionSource = chipInteraction,
                                        indication = null,
                                        enabled = readOnlyMessage == null
                                    ) {
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
                },
                SettingsItem(
                    keywords = listOf(stringResource(R.string.settings_next_reminder))
                ) {
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
                },
                SettingsItem(
                    keywords = listOf(
                        stringResource(R.string.settings_reminder_autostart_hint),
                        stringResource(R.string.settings_reminder_autostart_fix)
                    )
                ) {
                    if (preferences.reminderEnabled || widgetPresent) {
                        // 提醒开启或桌面有小组件时显示“自启动”入口，作为用户点“稍后”后重新打开系统自启动设置的通道。
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.Info,
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
                },
            )

            // —— 学期设置（含节次时间）——
            val semesterItems = listOf(
                SettingsItem(
                    keywords = listOf(stringResource(R.string.settings_semester_start_date))
                ) {
                    var showDatePicker by remember { mutableStateOf(false) }
                    val dateRowInteraction = remember { MutableInteractionSource() }
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .pressScale(dateRowInteraction)
                            .clickable(
                                interactionSource = dateRowInteraction,
                                indication = null,
                                enabled = readOnlyMessage == null
                            ) { showDatePicker = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.CalendarMonth, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_semester_start_date), fontSize = 15.sp)
                            Text(table.semesterStart, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                        }
                        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
                    }
                    if (showDatePicker) {
                        AppDatePickerSheet(
                            initialDate = LocalDate.parse(table.semesterStart),
                            title = stringResource(R.string.settings_semester_start_date),
                            confirmLabel = stringResource(R.string.settings_confirm),
                            cancelLabel = stringResource(R.string.settings_cancel),
                            onDismiss = { showDatePicker = false },
                            onConfirm = { picked ->
                                // 保存用户选择的原日期：周次计算在读取侧统一按周一归一
                                // （TimeUtils.semesterWeekStartOrNull），保存侧不再改写，
                                // 重新打开选择器时忠实还原用户当时选择的那一天。
                                saveTable(table.copy(semesterStart = picked.toString()))
                                showDatePicker = false
                            }
                        )
                    }
                },
                SettingsItem(
                    keywords = listOf(stringResource(R.string.settings_total_weeks))
                ) {
                    val totalWeeks = table.totalWeeks
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.ViewWeek, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.settings_total_weeks), fontSize = 15.sp, modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = { if (totalWeeks > 1) saveTable(table.copy(totalWeeks = table.totalWeeks - 1)) },
                            enabled = readOnlyMessage == null && totalWeeks > 1
                        ) {
                            Icon(Icons.Rounded.Remove, stringResource(R.string.settings_decrease_weeks), modifier = Modifier.size(18.dp))
                        }
                        Text("" + totalWeeks, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
                        IconButton(
                            onClick = { if (totalWeeks < 30) saveTable(table.copy(totalWeeks = table.totalWeeks + 1)) },
                            enabled = readOnlyMessage == null && totalWeeks < 30
                        ) {
                            Icon(Icons.Rounded.Add, stringResource(R.string.settings_increase_weeks), modifier = Modifier.size(18.dp))
                        }
                    }
                },
                SettingsItem(
                    keywords = listOf(
                        stringResource(R.string.settings_calendar_exceptions),
                        stringResource(R.string.settings_calendar_exceptions_subtitle),
                        stringResource(R.string.settings_excluded_weeks)
                    )
                ) {
                    SettingsActionRow(
                        modifier = Modifier.testTag("excluded-weeks-setting"),
                        icon = Icons.Rounded.CalendarMonth,
                        title = stringResource(R.string.settings_calendar_exceptions),
                        subtitle = stringResource(
                            R.string.settings_calendar_exceptions_subtitle,
                            table.excludedWeeks.size,
                            table.weekLabels.size,
                            table.dateExceptions.size
                        ),
                        enabled = readOnlyMessage == null,
                        onClick = onOpenCalendarExceptions
                    )
                },
                SettingsItem(
                    keywords = listOf(
                        stringResource(R.string.settings_section_periods),
                        stringResource(R.string.settings_period_count),
                        stringResource(R.string.settings_add_period),
                        stringResource(R.string.settings_restore_default),
                        stringResource(R.string.settings_period_time_exceeds)
                    )
                ) {
                    SettingsGroupHeader(stringResource(R.string.settings_section_periods))
                    val periodCount = table.periods.size
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
                        val startInteraction = remember { MutableInteractionSource() }
                        val endInteraction = remember { MutableInteractionSource() }

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
                                    .pressScale(startInteraction, 0.97f)
                                    .clickable(
                                        interactionSource = startInteraction,
                                        indication = null,
                                        enabled = readOnlyMessage == null
                                    ) { showStartPicker = true }
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
                                    .pressScale(endInteraction, 0.97f)
                                    .clickable(
                                        interactionSource = endInteraction,
                                        indication = null,
                                        enabled = readOnlyMessage == null
                                    ) { showEndPicker = true }
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
                                    Icon(Icons.Rounded.RemoveCircleOutline, stringResource(R.string.settings_delete), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        // 弹窗用LaunchedEffect防重复弹出
                        if (showStartPicker) {
                            AppTimePickerSheet(
                                onDismiss = { showStartPicker = false },
                                onConfirm = { hour, minute ->
                                    val newTime = "%02d:%02d".format(hour, minute)
                                    val np = table.periods.toMutableList()
                                    np[idx] = PeriodTime(newTime, period.end)
                                    saveTable(table.copy(periods = np))
                                    showStartPicker = false
                                },
                                initialHour = startH,
                                initialMinute = startM,
                                title = stringResource(R.string.settings_time_picker_title),
                                confirmLabel = stringResource(R.string.settings_confirm),
                                cancelLabel = stringResource(R.string.settings_cancel)
                            )
                        }
                        if (showEndPicker) {
                            AppTimePickerSheet(
                                onDismiss = { showEndPicker = false },
                                onConfirm = { hour, minute ->
                                    val newTime = "%02d:%02d".format(hour, minute)
                                    val np = table.periods.toMutableList()
                                    np[idx] = PeriodTime(period.start, newTime)
                                    saveTable(table.copy(periods = np))
                                    showEndPicker = false
                                },
                                initialHour = endH,
                                initialMinute = endM,
                                title = stringResource(R.string.settings_time_picker_title),
                                confirmLabel = stringResource(R.string.settings_confirm),
                                cancelLabel = stringResource(R.string.settings_cancel)
                            )
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
                            Icon(Icons.Rounded.Add, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.settings_add_period), fontSize = 13.sp)
                        }

                        OutlinedButton(
                            enabled = readOnlyMessage == null,
                            onClick = { saveTable(table.copy(periods = TableData.defaultPeriods())) },
                            modifier = Modifier.height(48.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                            Icon(Icons.Rounded.Restore, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.settings_restore_default), fontSize = 13.sp)
                        }
                    }
                },
            )

            // —— 数据备份与恢复 ——
            val backupItems = listOf(
                SettingsItem(
                    keywords = listOf(
                        stringResource(R.string.settings_export_full_backup),
                        stringResource(R.string.settings_export_full_backup_subtitle)
                    )
                ) {
                    SettingsActionRow(
                        icon = Icons.Rounded.Backup,
                        title = stringResource(R.string.settings_export_full_backup),
                        subtitle = stringResource(R.string.settings_export_full_backup_subtitle),
                        enabled = readOnlyMessage == null,
                        onClick = { onExportBackup(false) }
                    )
                },
                SettingsItem(
                    keywords = listOf(
                        stringResource(R.string.settings_export_encrypted_backup),
                        stringResource(R.string.settings_export_encrypted_backup_subtitle)
                    )
                ) {
                    SettingsActionRow(
                        icon = Icons.Rounded.Lock,
                        title = stringResource(R.string.settings_export_encrypted_backup),
                        subtitle = stringResource(R.string.settings_export_encrypted_backup_subtitle),
                        enabled = readOnlyMessage == null,
                        onClick = onExportEncryptedBackup
                    )
                },
                SettingsItem(
                    keywords = listOf(
                        stringResource(R.string.settings_export_sanitized),
                        stringResource(R.string.settings_export_sanitized_subtitle)
                    )
                ) {
                    SettingsActionRow(
                        icon = Icons.Rounded.Share,
                        title = stringResource(R.string.settings_export_sanitized),
                        subtitle = stringResource(R.string.settings_export_sanitized_subtitle),
                        enabled = readOnlyMessage == null,
                        onClick = { onExportBackup(true) }
                    )
                },
                SettingsItem(
                    keywords = listOf(
                        stringResource(R.string.settings_import_backup),
                        stringResource(R.string.settings_import_backup_subtitle)
                    )
                ) {
                    SettingsActionRow(
                        icon = Icons.Rounded.Restore,
                        title = stringResource(R.string.settings_import_backup),
                        subtitle = stringResource(R.string.settings_import_backup_subtitle),
                        onClick = onImportBackup
                    )
                },
                SettingsItem(
                    keywords = listOf(
                        stringResource(R.string.settings_history),
                        stringResource(R.string.settings_history_subtitle)
                    )
                ) {
                    SettingsActionRow(
                        icon = Icons.Rounded.History,
                        title = stringResource(R.string.settings_history),
                        subtitle = stringResource(R.string.settings_history_subtitle),
                        onClick = onOpenHistory
                    )
                },
                SettingsItem(
                    keywords = listOf(
                        stringResource(R.string.settings_paste_import),
                        stringResource(R.string.settings_paste_import_subtitle)
                    )
                ) {
                    SettingsActionRow(
                        icon = Icons.Rounded.ContentPaste,
                        title = stringResource(R.string.settings_paste_import),
                        subtitle = stringResource(R.string.settings_paste_import_subtitle),
                        enabled = readOnlyMessage == null,
                        onClick = onPasteImport
                    )
                },
                SettingsItem(
                    keywords = listOf(
                        stringResource(R.string.settings_export_excel_template),
                        stringResource(R.string.settings_export_excel_template_subtitle)
                    )
                ) {
                    SettingsActionRow(
                        icon = Icons.Rounded.Download,
                        title = stringResource(R.string.settings_export_excel_template),
                        subtitle = stringResource(R.string.settings_export_excel_template_subtitle),
                        onClick = onExportExcelTemplate
                    )
                },
                SettingsItem(
                    keywords = listOf(
                        stringResource(R.string.settings_export_calendar),
                        stringResource(R.string.settings_export_calendar_subtitle)
                    )
                ) {
                    SettingsActionRow(
                        icon = Icons.Rounded.CalendarMonth,
                        title = stringResource(R.string.settings_export_calendar),
                        subtitle = stringResource(R.string.settings_export_calendar_subtitle),
                        enabled = readOnlyMessage == null,
                        onClick = onExportCalendar
                    )
                },
            )

            // —— 数据诊断（只显示统计，不展示课程原文）——
            val diagnosticsItems = listOf(
                SettingsItem(
                    keywords = listOf(
                        stringResource(R.string.settings_diag_table_count),
                        stringResource(R.string.settings_diag_course_count),
                        stringResource(R.string.settings_diag_period_count),
                        stringResource(R.string.settings_diag_total_weeks),
                        stringResource(R.string.settings_excluded_weeks),
                        stringResource(R.string.settings_diag_conflicts)
                    )
                ) {
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
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
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
                },
                SettingsItem(
                    keywords = listOf(
                        stringResource(R.string.settings_export_diagnostics),
                        stringResource(R.string.settings_export_diagnostics_subtitle)
                    )
                ) {
                    SettingsActionRow(
                        icon = Icons.Rounded.BugReport,
                        title = stringResource(R.string.settings_export_diagnostics),
                        subtitle = stringResource(R.string.settings_export_diagnostics_subtitle),
                        onClick = onExportDiagnostics
                    )
                },
            )

            // —— 数据与版本：自动备份 + 检查更新 + 备份恢复 + 诊断 ——
            val dataItems = listOf(
            SettingsItem(
                keywords = listOf(
                    stringResource(R.string.settings_auto_backup),
                    stringResource(R.string.settings_auto_backup_subtitle)
                )
            ) {
                PreferenceSwitchRow(
                    title = stringResource(R.string.settings_auto_backup),
                    subtitle = stringResource(R.string.settings_auto_backup_subtitle),
                    checked = preferences.autoBackupEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled && preferences.autoBackupUri.isBlank()) {
                            onChooseAutoBackupLocation()
                        } else {
                            save(preferences.copy(autoBackupEnabled = enabled))
                        }
                    },
                    switchTestTag = "auto-backup-switch"
                )
                val chosenName = preferences.autoBackupUri.substringAfterLast('/')
                    .ifBlank { stringResource(R.string.settings_auto_backup_none) }
                SettingsActionRow(
                    icon = Icons.Rounded.FolderOpen,
                    title = stringResource(R.string.settings_auto_backup_choose),
                    subtitle = stringResource(R.string.settings_auto_backup_chosen, chosenName),
                    onClick = onChooseAutoBackupLocation
                )
            },
            createUpdateCheckSettingsItem(context),
            )

            val sections = listOf(

                SettingsSectionData(stringResource(R.string.settings_section_general), generalItems),
                SettingsSectionData(stringResource(R.string.settings_section_reminder), reminderItems),
                SettingsSectionData(stringResource(R.string.settings_section_semester), semesterItems),
                SettingsSectionData(stringResource(R.string.settings_section_data), dataItems + backupItems + diagnosticsItems),
            )

            val renderedSections: List<Pair<SettingsSectionData, List<SettingsItem>>> = if (!isSearching) {
                sections.map { it to it.items }
            } else {
                sections.mapNotNull { section ->
                    val matched = section.items.filter { item ->
                        item.keywords.any { it.contains(query, ignoreCase = true) }
                    }
                    when {
                        matched.isNotEmpty() -> section to matched
                        // 命中分区标题时显示整张卡
                        section.title.contains(query, ignoreCase = true) -> section to section.items
                        else -> null
                    }
                }
            }

            if (isSearching && renderedSections.isEmpty()) {
                SettingsSearchEmptyState(onClear = { searchText = "" })
            } else {
                renderedSections.forEach { (section, items) ->
                    SettingsCardSection(section = section, visibleItems = items)
                }
            }
        }
    }
}

@Composable
private fun PreferenceSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    switchTestTag: String? = null
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier.fillMaxWidth()
            .pressScale(interaction)
            .clickable(interactionSource = interaction, indication = null) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = if (switchTestTag == null) Modifier else Modifier.testTag(switchTestTag)
        )
    }
}

@Composable
internal fun SettingsActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier.fillMaxWidth()
            .pressScale(interaction)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
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
        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f))
    }
}

/** 卡片内部的小分组标题（合并卡内沿用原分区字符串）。 */
@Composable
private fun SettingsGroupHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)
    )
}

/** iOS 风格分组卡片：圆角面板 + 0.75dp 描边 + 条目间 0.5dp 分隔线。 */
@Composable
private fun SettingsCardSection(
    section: SettingsSectionData,
    visibleItems: List<SettingsItem>
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            section.title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(
                start = AppSpacing.screenH,
                end = AppSpacing.screenH,
                bottom = AppSpacing.sm
            )
        )
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.screenH),
            shape = AppShapes.panel,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Column {
                val columnScope = this
                visibleItems.forEachIndexed { index, item ->
                    if (index > 0) {
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                        )
                    }
                    item.content(columnScope)
                }
            }
        }
    }
}

@Composable
private fun SettingsSearchEmptyState(onClear: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Rounded.SearchOff,
            contentDescription = null,
            modifier = Modifier.size(AppSizes.compactControl),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.settings_search_no_results), fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.settings_search_no_results_hint),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onClear) { Text(stringResource(R.string.settings_search_clear)) }
    }
}
