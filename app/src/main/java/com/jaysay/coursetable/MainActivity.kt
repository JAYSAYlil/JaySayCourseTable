package com.jaysay.coursetable

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.jaysay.coursetable.data.backup.BackupData
import com.jaysay.coursetable.data.backup.BackupDiff
import com.jaysay.coursetable.data.model.Course
import com.jaysay.coursetable.data.model.CourseConflict
import com.jaysay.coursetable.data.model.CourseImportAnalyzer
import com.jaysay.coursetable.data.model.CourseSeriesOperations
import com.jaysay.coursetable.data.model.CourseSeriesUndo
import com.jaysay.coursetable.data.parser.ExcelParser
import com.jaysay.coursetable.data.parser.MappedTextScheduleParser
import com.jaysay.coursetable.data.parser.TextScheduleParser
import com.jaysay.coursetable.data.parser.TextColumnMapping
import com.jaysay.coursetable.data.reminder.ReminderScheduler
import com.jaysay.coursetable.data.reminder.ReminderSuppression
import com.jaysay.coursetable.data.transfer.ImportExportCoordinator
import com.jaysay.coursetable.ui.screen.CourseDetailScreen
import com.jaysay.coursetable.ui.screen.CourseEditDialog
import com.jaysay.coursetable.ui.screen.CourseTableScreen
import com.jaysay.coursetable.ui.screen.AgendaScreen
import com.jaysay.coursetable.ui.screen.CalendarExceptionScreen
import com.jaysay.coursetable.ui.screen.HistoryScreen
import com.jaysay.coursetable.ui.screen.ImportConfirmScreen
import com.jaysay.coursetable.ui.screen.SettingsScreen
import com.jaysay.coursetable.ui.screen.TableManageScreen
import com.jaysay.coursetable.ui.theme.*
import com.jaysay.coursetable.widget.CourseWidgetProvider
import com.jaysay.coursetable.ui.components.AppTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

private enum class Screen { MAIN, SETTINGS, IMPORT_CONFIRM, TABLE_MANAGE, AGENDA, HISTORY, CALENDAR }

private data class PendingConflictChange(
    val courseName: String,
    val conflicts: List<CourseConflict>,
    val onConfirm: () -> Unit
)

class MainActivity : ComponentActivity() {
    private lateinit var model: MainViewModel
    private val pendingBackupRestore = mutableStateOf<BackupData?>(null)
    private val pendingEncryptedBackupImport = mutableStateOf<Uri?>(null)
    private var pendingEncryptedExportPassword: CharArray? = null
    private val requestedCourseSeries = mutableStateOf<String?>(null)
    private val requestedTableIndex = mutableIntStateOf(-1)
    private val requestedShortcutAction = mutableStateOf<String?>(null)
    private lateinit var fileTransfer: ImportExportCoordinator

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let { fileTransfer.handleFileImport(it) } }

    private val fullBackupExportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? -> uri?.let { fileTransfer.handleBackupExport(it, sanitized = false) } }

    private val sanitizedBackupExportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? -> uri?.let { fileTransfer.handleBackupExport(it, sanitized = true) } }

    private val encryptedBackupExportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        val password = pendingEncryptedExportPassword
        pendingEncryptedExportPassword = null
        if (uri != null && password != null) fileTransfer.handleBackupExport(uri, sanitized = false, password = password)
        else password?.fill('\u0000')
    }

    private val backupImportLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let { fileTransfer.handleBackupImport(it) } }

    private val icsExportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/calendar")
    ) { uri: Uri? -> uri?.let { fileTransfer.handleIcsExport(it) } }

    private val diagnosticsExportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? -> uri?.let(fileTransfer::handleDiagnosticsExport) }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 结果不强制处理：用户可在系统设置中开启通知权限 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model = ViewModelProvider(this)[MainViewModel::class.java]
        captureCourseRequest(intent)
        AppShortcuts.install(this)
        fileTransfer = ImportExportCoordinator(
            activity = this,
            model = model,
            onPendingImport = model::stageCourseImport,
            onPendingBackupRestore = { pendingBackupRestore.value = it },
            onEncryptedBackupPasswordRequired = { pendingEncryptedBackupImport.value = it },
            showError = ::showError,
            showToast = { message -> Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
        )

        setContent {
            // 屏幕位置持久化，Activity 重建（旋转/进程回收）后不会跳回主界面。
            var currentScreenOrdinal by rememberSaveable { mutableIntStateOf(Screen.MAIN.ordinal) }
            fun currentScreen(): Screen = Screen.values().getOrNull(currentScreenOrdinal) ?: Screen.MAIN
            // 详情页用 uniqueKey 保存恢复依据，数据加载完成后在 LaunchedEffect 中还原课程实例。
            var selectedCourseKey by rememberSaveable { mutableStateOf<String?>(null) }
            var selectedCourse by remember { mutableStateOf<Course?>(null) }
            var showAddDialog by rememberSaveable { mutableStateOf(false) }
            var showEditDialog by rememberSaveable { mutableStateOf(false) }
            var editingCourse by remember { mutableStateOf<Course?>(null) }
            // 新增弹窗的星期/节次预设；0 表示未预设（day 有效范围 1-7，period 1-30）
            var addCoursePresetDay by rememberSaveable { mutableIntStateOf(0) }
            var addCoursePresetPeriod by rememberSaveable { mutableIntStateOf(0) }
            var pendingDetailDelete by remember { mutableStateOf<Course?>(null) }
            var pendingConflictChange by remember { mutableStateOf<PendingConflictChange?>(null) }
            var showPasteImportDialog by rememberSaveable { mutableStateOf(false) }
            var showEncryptedExportDialog by remember { mutableStateOf(false) }
            var scheduleFocusedDay by rememberSaveable { mutableIntStateOf(LocalDate.now().dayOfWeek.value) }
            val snackbarHostState = remember { SnackbarHostState() }
            val coroutineScope = rememberCoroutineScope()
            val state = model.state

            JaySayTheme(
                themeMode = state.preferences.themeMode,
                highContrast = state.preferences.highContrast
            ) {
                if (state.isLoading) return@JaySayTheme

                // 数据就绪后按保存的 uniqueKey 还原详情页课程；课程已不存在则关闭详情。
                LaunchedEffect(state.isLoading, selectedCourseKey) {
                    if (!state.isLoading && selectedCourseKey != null) {
                        selectedCourse = state.courses.firstOrNull { it.uniqueKey == selectedCourseKey }
                            ?: run { selectedCourseKey = null; null }
                    }
                }

                LaunchedEffect(state.isLoading, state.activeTableIndex, requestedCourseSeries.value) {
                    val requestedSeries = requestedCourseSeries.value ?: return@LaunchedEffect
                    if (state.isLoading) return@LaunchedEffect
                    val targetTable = requestedTableIndex.intValue
                    if (targetTable in state.tables.indices && targetTable != state.activeTableIndex) {
                        model.selectTable(targetTable, ::showSaveError)
                    } else {
                        state.courses.firstOrNull { it.seriesKey == requestedSeries }?.let { course ->
                            selectedCourse = course
                            selectedCourseKey = course.uniqueKey
                            currentScreenOrdinal = Screen.MAIN.ordinal
                        }
                        requestedCourseSeries.value = null
                        requestedTableIndex.intValue = -1
                    }
                }

                // 课表或偏好变化后重新调度上课提醒并刷新桌面小组件（覆盖式，频率低）。
                val appContext = LocalContext.current.applicationContext
                LaunchedEffect(state.isLoading, state.tables, state.preferences) {
                    if (!state.isLoading) {
                        withContext(Dispatchers.IO) {
                            ReminderScheduler.rescheduleAll(appContext, state.tables, state.preferences)
                        }
                        CourseWidgetProvider.requestUpdate(appContext)
                    }
                }

                val stagedImport = model.stagedCourseImport
                LaunchedEffect(stagedImport, currentScreenOrdinal) {
                    if (stagedImport != null) {
                        currentScreenOrdinal = Screen.IMPORT_CONFIRM.ordinal
                    } else if (currentScreen() == Screen.IMPORT_CONFIRM) {
                        // 进程重建不会保存大批课程到 Bundle；没有暂存数据时安全返回主界面。
                        currentScreenOrdinal = Screen.MAIN.ordinal
                    }
                }

                val activeTable = state.activeTable
                val runAfterConflictCheck: (Course, List<Course>, () -> Unit) -> Unit =
                    { candidate, comparisonCourses, action ->
                        val conflicts = CourseImportAnalyzer.findConflicts(comparisonCourses, candidate)
                        if (conflicts.isEmpty()) {
                            action()
                        } else {
                            pendingConflictChange = PendingConflictChange(candidate.courseName, conflicts, action)
                        }
                    }
                val locateToday: () -> Unit = {
                    scheduleFocusedDay = LocalDate.now().dayOfWeek.value
                    model.locateToday()
                }
                LaunchedEffect(state.isLoading, requestedShortcutAction.value) {
                    if (state.isLoading) return@LaunchedEffect
                    when (requestedShortcutAction.value) {
                        AppShortcuts.ACTION_TODAY -> {
                            locateToday()
                            currentScreenOrdinal = Screen.MAIN.ordinal
                        }
                        AppShortcuts.ACTION_ADD_COURSE -> {
                            currentScreenOrdinal = Screen.MAIN.ordinal
                            addCoursePresetDay = LocalDate.now().dayOfWeek.value
                            addCoursePresetPeriod = 0
                            showAddDialog = true
                        }
                    }
                    requestedShortcutAction.value = null
                }
                val offerUndo: (CourseSeriesUndo, String) -> Unit = { undo, message ->
                    coroutineScope.launch {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        if (snackbarHostState.showSnackbar(message, actionLabel = "撤销", withDismissAction = true) == SnackbarResult.ActionPerformed) {
                            model.updateCourses(undo::restore, onError = ::showSaveError)
                        }
                    }
                }

                pendingDetailDelete?.let { deleting ->
                    AlertDialog(
                        onDismissRequest = { pendingDetailDelete = null },
                        icon = { Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                        title = { Text("从本周移除课程？") },
                        text = { Text("只从第 ${state.currentWeek} 周移除“${deleting.courseName}”，其他周次不受影响。") },
                        confirmButton = {
                            TextButton(onClick = {
                                val previous = state.courses
                                val week = state.currentWeek
                                val seriesKey = deleting.seriesKey
                                val after = CourseSeriesOperations.deleteWeek(previous, seriesKey, week)
                                pendingDetailDelete = null
                                model.updateCourses(
                                    transform = { courses -> CourseSeriesOperations.deleteWeek(courses, seriesKey, week) },
                                    onComplete = {
                                        selectedCourse = null
                                        selectedCourseKey = null
                                        offerUndo(
                                            CourseSeriesUndo.capture(previous, after, seriesKey),
                                            "已从第 $week 周移除 ${deleting.courseName}"
                                        )
                                    },
                                    onError = ::showSaveError
                                )
                            }) { Text("确认移除", color = MaterialTheme.colorScheme.error) }
                        },
                        dismissButton = {
                            TextButton(onClick = { pendingDetailDelete = null }) { Text("取消") }
                        }
                    )
                }

                if (showEncryptedExportDialog) {
                    var password by remember { mutableStateOf("") }
                    var confirmation by remember { mutableStateOf("") }
                    val passwordValid = password.length >= 6 && password == confirmation
                    AlertDialog(
                        onDismissRequest = { showEncryptedExportDialog = false },
                        icon = { Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary) },
                        title = { Text("设置备份密码") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("密码至少 6 位。密码无法找回，请妥善保存。",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { password = it.take(128) },
                                    label = { Text("备份密码") },
                                    visualTransformation = PasswordVisualTransformation(),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = confirmation,
                                    onValueChange = { confirmation = it.take(128) },
                                    label = { Text("再次输入密码") },
                                    visualTransformation = PasswordVisualTransformation(),
                                    singleLine = true,
                                    isError = confirmation.isNotEmpty() && confirmation != password
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(enabled = passwordValid, onClick = {
                                pendingEncryptedExportPassword?.fill('\u0000')
                                pendingEncryptedExportPassword = password.toCharArray()
                                showEncryptedExportDialog = false
                                encryptedBackupExportLauncher.launch("JaySay课表-密码加密备份-${LocalDate.now()}.json")
                            }) { Text("选择保存位置") }
                        },
                        dismissButton = { TextButton(onClick = { showEncryptedExportDialog = false }) { Text("取消") } }
                    )
                }

                pendingEncryptedBackupImport.value?.let { uri ->
                    var password by remember(uri) { mutableStateOf("") }
                    AlertDialog(
                        onDismissRequest = { pendingEncryptedBackupImport.value = null },
                        icon = { Icon(Icons.Default.LockOpen, null, tint = MaterialTheme.colorScheme.primary) },
                        title = { Text("输入备份密码") },
                        text = {
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it.take(128) },
                                label = { Text("备份密码") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true
                            )
                        },
                        confirmButton = {
                            TextButton(enabled = password.isNotEmpty(), onClick = {
                                pendingEncryptedBackupImport.value = null
                                fileTransfer.handleBackupImport(uri, password.toCharArray())
                            }) { Text("解密并校验") }
                        },
                        dismissButton = {
                            TextButton(onClick = { pendingEncryptedBackupImport.value = null }) { Text("取消") }
                        }
                    )
                }

                pendingBackupRestore.value?.let { backup ->
                    val courseCount = backup.tables.sumOf { it.courses.size }
                    val diff = remember(backup, state.tables) { BackupDiff.between(state.tables, backup.tables) }
                    AlertDialog(
                        onDismissRequest = { pendingBackupRestore.value = null },
                        title = { Text("确认恢复备份") },
                        text = {
                            Text(
                                "将用备份中的 ${backup.tables.size} 个课表、$courseCount 条课程替换当前数据。\n\n" +
                                    "差异：新增 ${diff.coursesAdded}、修改 ${diff.coursesChanged}、删除 ${diff.coursesRemoved} 门课程；" +
                                    "新增 ${diff.tablesAdded}、删除 ${diff.tablesRemoved} 张课表。\n\n" +
                                    "恢复前会自动保存当前课表历史。"
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                pendingBackupRestore.value = null
                                model.restoreBackup(
                                    backup,
                                    onComplete = { Toast.makeText(this@MainActivity, "备份恢复成功", Toast.LENGTH_LONG).show() },
                                    onError = { showError("恢复失败", it) }
                                )
                            }) { Text("确认恢复") }
                        },
                        dismissButton = {
                            TextButton(onClick = { pendingBackupRestore.value = null }) { Text("取消") }
                        }
                    )
                }

                if (showEditDialog && editingCourse != null) {
                    val selected = editingCourse!!
                    val oldSeriesKey = selected.seriesKey
                    val editorCourse = selected.copy(
                        weeks = state.courses.filter { it.seriesKey == oldSeriesKey }
                            .flatMap(Course::weeks).distinct().sorted(),
                        seriesId = oldSeriesKey
                    )
                    CourseEditDialog(course = editorCourse, totalWeeks = activeTable.totalWeeks,
                        currentWeek = state.currentWeek, maxPeriods = activeTable.periods.size,
                        onSave = { updated, applyToAll ->
                            val week = state.currentWeek
                            val candidate = updated.copy(
                                weeks = if (applyToAll) updated.weeks else listOf(week),
                                seriesId = oldSeriesKey
                            )
                            val comparisonCourses = if (applyToAll) {
                                state.courses.filter { it.seriesKey != oldSeriesKey }
                            } else {
                                state.courses.mapNotNull { course ->
                                    if (course.seriesKey == oldSeriesKey) course.withoutWeeks(setOf(week)) else course
                                }
                            }
                            val saveAction = {
                                model.updateCourses({ courses ->
                                    if (applyToAll) {
                                        CourseSeriesOperations.replaceAll(courses, oldSeriesKey, candidate)
                                    } else {
                                        CourseSeriesOperations.replaceWeek(courses, oldSeriesKey, week, candidate)
                                    }
                                }, onComplete = {
                                    showEditDialog = false; editingCourse = null
                                }, onError = ::showSaveError)
                            }
                            runAfterConflictCheck(candidate, comparisonCourses, saveAction)
                        },
                        onDelete = { applyToAll ->
                            val week = state.currentWeek
                            val deletedName = selected.courseName
                            val previous = state.courses
                            val after = if (applyToAll) {
                                CourseSeriesOperations.deleteAll(previous, oldSeriesKey)
                            } else {
                                CourseSeriesOperations.deleteWeek(previous, oldSeriesKey, week)
                            }
                            model.updateCourses({ courses ->
                                if (applyToAll) {
                                    CourseSeriesOperations.deleteAll(courses, oldSeriesKey)
                                } else {
                                    CourseSeriesOperations.deleteWeek(courses, oldSeriesKey, week)
                                }
                            }, onComplete = {
                                showEditDialog = false; editingCourse = null
                                offerUndo(
                                    CourseSeriesUndo.capture(previous, after, oldSeriesKey),
                                    if (applyToAll) "已删除 $deletedName 的全部周次" else "已从第 $week 周移除 $deletedName"
                                )
                            }, onError = ::showSaveError)
                        },
                        onDismiss = { showEditDialog = false; editingCourse = null })
                }

                if (showAddDialog) {
                    CourseEditDialog(course = null, totalWeeks = activeTable.totalWeeks,
                        currentWeek = state.currentWeek,
                        maxPeriods = activeTable.periods.size,
                        initialDay = addCoursePresetDay.takeIf { it > 0 } ?: 1,
                        initialStartPeriod = addCoursePresetPeriod.takeIf { it > 0 } ?: 1,
                        onSave = { c, _ ->
                            val saveAction = {
                                model.updateCourses({ it + c }, onComplete = {
                                    showAddDialog = false
                                    addCoursePresetDay = 0
                                    addCoursePresetPeriod = 0
                                    coroutineScope.launch { snackbarHostState.showSnackbar("已添加 ${c.courseName}") }
                                }, onError = ::showSaveError)
                            }
                            runAfterConflictCheck(c, state.courses, saveAction)
                        }, onDelete = null, onDismiss = {
                            showAddDialog = false
                            addCoursePresetDay = 0
                            addCoursePresetPeriod = 0
                        })
                }

                // 保持在编辑/新增弹窗之后组合，确保冲突确认始终位于最上层。
                pendingConflictChange?.let { pending ->
                    AlertDialog(
                        onDismissRequest = { pendingConflictChange = null },
                        icon = { Icon(Icons.Default.WarningAmber, null, tint = MaterialTheme.colorScheme.error) },
                        title = { Text("检测到课程冲突") },
                        text = {
                            Column {
                                Text("“${pending.courseName}”与现有课程在相同周次和节次重叠：")
                                Spacer(Modifier.height(8.dp))
                                pending.conflicts.take(4).forEach { conflict ->
                                    Text(
                                        "• ${conflict.otherCourseName}：第${conflict.overlappingWeeks.joinToString("、")}周，" +
                                            "${conflict.startPeriod}-${conflict.endPeriod}节",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                if (pending.conflicts.size > 4) {
                                    Text("另有 ${pending.conflicts.size - 4} 项冲突", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                val action = pending.onConfirm
                                pendingConflictChange = null
                                action()
                            }) { Text("仍然保存", color = MaterialTheme.colorScheme.error) }
                        },
                        dismissButton = {
                            TextButton(onClick = { pendingConflictChange = null }) { Text("返回修改") }
                        }
                    )
                }

                if (showPasteImportDialog) {
                    var pasteText by rememberSaveable { mutableStateOf("") }
                    var mappingMode by rememberSaveable { mutableStateOf(false) }
                    var nameColumn by rememberSaveable { mutableStateOf("1") }
                    var teacherColumn by rememberSaveable { mutableStateOf("2") }
                    var roomColumn by rememberSaveable { mutableStateOf("3") }
                    var dayColumn by rememberSaveable { mutableStateOf("4") }
                    var periodColumn by rememberSaveable { mutableStateOf("5") }
                    var weekColumn by rememberSaveable { mutableStateOf("6") }
                    AlertDialog(
                        onDismissRequest = { showPasteImportDialog = false },
                        icon = { Icon(Icons.Default.ContentPaste, null, tint = MaterialTheme.colorScheme.primary) },
                        title = { Text("粘贴导入课程") },
                        text = {
                            Column {
                                Text(
                                    if (mappingMode) "按制表符、竖线或逗号分列，填写各字段所在列号"
                                    else "每行一条：星期 节次 课程名 [教室] [教师] [周次]",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "示例：周一 1-2节 高等数学 教1-101 张老师 1-16周",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("手动列映射", fontSize = 13.sp)
                                    Switch(checked = mappingMode, onCheckedChange = { mappingMode = it })
                                }
                                if (mappingMode) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        ColumnIndexField("课程", nameColumn, { nameColumn = it }, Modifier.weight(1f))
                                        ColumnIndexField("教师", teacherColumn, { teacherColumn = it }, Modifier.weight(1f))
                                        ColumnIndexField("教室", roomColumn, { roomColumn = it }, Modifier.weight(1f))
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        ColumnIndexField("星期", dayColumn, { dayColumn = it }, Modifier.weight(1f))
                                        ColumnIndexField("节次", periodColumn, { periodColumn = it }, Modifier.weight(1f))
                                        ColumnIndexField("周次", weekColumn, { weekColumn = it }, Modifier.weight(1f))
                                    }
                                    Text("教师或教室填 0 表示没有该列", fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                OutlinedTextField(
                                    value = pasteText,
                                    onValueChange = { if (it.length <= 50_000) pasteText = it },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp).testTag("paste-import-input"),
                                    placeholder = { Text("粘贴课表文本…") },
                                    maxLines = 8
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                val result = if (mappingMode) {
                                    val required = listOf(nameColumn, dayColumn, periodColumn, weekColumn)
                                        .mapNotNull(String::toIntOrNull)
                                    if (required.size != 4 || required.any { it <= 0 }) {
                                        Toast.makeText(this@MainActivity, "课程、星期、节次和周次列号必须大于 0", Toast.LENGTH_LONG).show()
                                        return@TextButton
                                    }
                                    MappedTextScheduleParser.parse(
                                        pasteText,
                                        TextColumnMapping(
                                            courseName = required[0] - 1,
                                            dayOfWeek = required[1] - 1,
                                            periods = required[2] - 1,
                                            weeks = required[3] - 1,
                                            teacher = teacherColumn.toIntOrNull()?.takeIf { it > 0 }?.minus(1),
                                            classroom = roomColumn.toIntOrNull()?.takeIf { it > 0 }?.minus(1)
                                        ),
                                        totalWeeks = state.activeTable.totalWeeks
                                    )
                                } else {
                                    TextScheduleParser.parse(pasteText, totalWeeks = state.activeTable.totalWeeks)
                                }
                                if (result.courses.isEmpty()) {
                                    val reason = result.errors.firstOrNull() ?: "请检查格式"
                                    Toast.makeText(this@MainActivity, "未解析到课程：$reason", Toast.LENGTH_LONG).show()
                                } else {
                                    model.stageCourseImport(ExcelParser.ParseResult(result.courses, result.errors))
                                    showPasteImportDialog = false
                                }
                            }, modifier = Modifier.testTag("paste-import-confirm")) { Text("解析并导入") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showPasteImportDialog = false }) { Text("取消") }
                        }
                    )
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    AnimatedContent(
                        targetState = currentScreen(),
                        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                        transitionSpec = {
                            val enterMs = if (state.preferences.reduceMotion) 0 else 220
                            val fadeMs = if (state.preferences.reduceMotion) 0 else 180
                            if (targetState.ordinal > initialState.ordinal) {
                                (slideInHorizontally(tween(enterMs)) { it / 4 } + fadeIn(tween(fadeMs))) togetherWith
                                    (slideOutHorizontally(tween(fadeMs)) { -it / 5 } + fadeOut(tween(if (state.preferences.reduceMotion) 0 else 130)))
                            } else {
                                (slideInHorizontally(tween(enterMs)) { -it / 4 } + fadeIn(tween(fadeMs))) togetherWith
                                    (slideOutHorizontally(tween(fadeMs)) { it / 5 } + fadeOut(tween(if (state.preferences.reduceMotion) 0 else 130)))
                            }
                        },
                        label = "screen"
                    ) { screen ->
                        when (screen) {
                            Screen.SETTINGS -> SettingsScreen(
                                tableData = state.activeTable,
                                preferences = state.preferences,
                                onUpdatePrefs = { prefs ->
                                    model.updatePreferences(prefs, ::showSaveError)
                                    // Android 13+ 开启提醒时请求通知权限（拒绝后仍可用，通知由系统设置控制）。
                                    if (prefs.reminderEnabled &&
                                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                        ContextCompat.checkSelfPermission(
                                            this@MainActivity,
                                            Manifest.permission.POST_NOTIFICATIONS
                                        ) != PackageManager.PERMISSION_GRANTED
                                    ) {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                },
                                onUpdateTable = { model.updateActiveTable(it, ::showSaveError) },
                                onExportBackup = { sanitized ->
                                    val suffix = if (sanitized) "脱敏副本" else "完整备份"
                                    val fileName = "JaySay课表-$suffix-${LocalDate.now()}.json"
                                    if (sanitized) sanitizedBackupExportLauncher.launch(fileName)
                                    else fullBackupExportLauncher.launch(fileName)
                                },
                                onExportEncryptedBackup = { showEncryptedExportDialog = true },
                                onImportBackup = {
                                    backupImportLauncher.launch(arrayOf("application/json", "text/json", "text/plain"))
                                },
                                onPasteImport = { showPasteImportDialog = true },
                                onExportCalendar = {
                                    icsExportLauncher.launch("JaySay课表-日历-${LocalDate.now()}.ics")
                                },
                                onExportDiagnostics = {
                                    diagnosticsExportLauncher.launch("JaySay课表-脱敏诊断-${LocalDate.now()}.txt")
                                },
                                onOpenHistory = { currentScreenOrdinal = Screen.HISTORY.ordinal },
                                onOpenCalendarExceptions = { currentScreenOrdinal = Screen.CALENDAR.ordinal },
                                onClearReminderPause = {
                                    ReminderSuppression.clear(this@MainActivity)
                                    coroutineScope.launch(Dispatchers.IO) {
                                        ReminderScheduler.rescheduleAll(this@MainActivity, state.tables, state.preferences)
                                    }
                                    Toast.makeText(this@MainActivity, "已恢复今天和本周的课程提醒", Toast.LENGTH_SHORT).show()
                                },
                                tablesCount = state.tables.size,
                                readOnlyMessage = state.persistentDataError,
                                onBack = { locateToday(); currentScreenOrdinal = Screen.MAIN.ordinal }
                            )

                            Screen.IMPORT_CONFIRM -> {
                                stagedImport?.let { importResult ->
                                    val preview = remember(importResult, state.courses) {
                                        CourseImportAnalyzer.analyze(state.courses, importResult.courses)
                                    }
                                    ImportConfirmScreen(
                                        preview = preview,
                                        warnings = importResult.errors,
                                        onConfirm = { selected ->
                                            model.importCourses(selected, onComplete = { result ->
                                                model.clearStagedCourseImport()
                                                locateToday()
                                                currentScreenOrdinal = Screen.MAIN.ordinal
                                                Toast.makeText(
                                                    this@MainActivity,
                                                    "导入完成：新增 ${result.added}，合并 ${result.merged}，跳过重复 ${result.skipped}",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }, onError = ::showSaveError)
                                        },
                                        onCancel = {
                                            model.clearStagedCourseImport()
                                            locateToday()
                                            currentScreenOrdinal = Screen.MAIN.ordinal
                                        }
                                    )
                                }
                            }

                            Screen.TABLE_MANAGE -> TableManageScreen(
                                tables = state.tables,
                                activeIndex = state.activeTableIndex,
                                onSelect = { idx ->
                                    model.selectTable(idx, ::showSaveError)
                                    currentScreenOrdinal = Screen.MAIN.ordinal
                                },
                                onDelete = { model.deleteTable(it, ::showSaveError) },
                                onAdd = { model.addTable(::showSaveError) },
                                onRename = { idx, name -> model.renameTable(idx, name, ::showSaveError) },
                                onDuplicate = { model.duplicateTable(it, ::showSaveError) },
                                onArchive = { index, archived -> model.setTableArchived(index, archived, ::showSaveError) },
                                onBack = { locateToday(); currentScreenOrdinal = Screen.MAIN.ordinal }
                            )

                            Screen.AGENDA -> Scaffold(
                                topBar = {
                                    AppTopBar(
                                        title = "日程列表",
                                        navigationIcon = {
                                            IconButton(onClick = { currentScreenOrdinal = Screen.MAIN.ordinal }) {
                                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                                            }
                                        }
                                    )
                                }
                            ) { padding ->
                                AgendaScreen(
                                    courses = state.courses,
                                    periodTimes = activeTable.periods,
                                    semesterStart = activeTable.semesterStart,
                                    totalWeeks = activeTable.totalWeeks,
                                    excludedWeeks = activeTable.excludedWeeks,
                                    dateExceptions = activeTable.dateExceptions,
                                    onCourseClick = { course ->
                                        selectedCourse = state.courses.firstOrNull { it.seriesKey == course.seriesKey } ?: course
                                        selectedCourseKey = selectedCourse?.uniqueKey
                                        currentScreenOrdinal = Screen.MAIN.ordinal
                                    },
                                    modifier = Modifier.padding(padding)
                                )
                            }

                            Screen.HISTORY -> HistoryScreen(
                                snapshots = model.historySnapshots,
                                onRefresh = { model.refreshHistory(::showSaveError) },
                                onPreview = { id, callback -> model.previewHistory(id, callback, ::showSaveError) },
                                onRestore = { id ->
                                    model.restoreHistory(id, onComplete = {
                                        Toast.makeText(this@MainActivity, "历史版本已恢复", Toast.LENGTH_SHORT).show()
                                        currentScreenOrdinal = Screen.MAIN.ordinal
                                    }, onError = ::showSaveError)
                                },
                                onBack = { currentScreenOrdinal = Screen.SETTINGS.ordinal }
                            )

                            Screen.CALENDAR -> CalendarExceptionScreen(
                                tableData = activeTable,
                                onUpdate = { model.updateActiveTable(it, ::showSaveError) },
                                onBack = { currentScreenOrdinal = Screen.SETTINGS.ordinal }
                            )

                            Screen.MAIN -> AnimatedContent(
                                targetState = selectedCourse,
                                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                                transitionSpec = {
                                    val enterMs = if (state.preferences.reduceMotion) 0 else 220
                                    val fadeMs = if (state.preferences.reduceMotion) 0 else 180
                                    if (targetState != null) {
                                        (slideInHorizontally(tween(enterMs)) { it / 3 } + fadeIn(tween(fadeMs))) togetherWith
                                            (slideOutHorizontally(tween(fadeMs)) { -it / 5 } + fadeOut(tween(if (state.preferences.reduceMotion) 0 else 140)))
                                    } else {
                                        (slideInHorizontally(tween(enterMs)) { -it / 5 } + fadeIn(tween(fadeMs))) togetherWith
                                            (slideOutHorizontally(tween(fadeMs)) { it / 3 } + fadeOut(tween(if (state.preferences.reduceMotion) 0 else 140)))
                                    }
                                },
                                label = "courseDetail"
                            ) { course ->
                                if (course != null) {
                                    CourseDetailScreen(
                                        course = course,
                                        allCourses = state.courses,
                                        onClose = { selectedCourse = null; selectedCourseKey = null },
                                        onEdit = { editing ->
                                            selectedCourse = null
                                            selectedCourseKey = null
                                            editingCourse = editing
                                            showEditDialog = true
                                        },
                                        onDelete = { pendingDetailDelete = course }
                                    )
                                } else {
                                    CourseTableScreen(
                                        courses = state.courses,
                                        currentWeek = state.currentWeek,
                                        tableName = activeTable.name,
                                        onImportClick = {
                                            importLauncher.launch(
                                                arrayOf(
                                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                                    "application/vnd.ms-excel",
                                                    "application/octet-stream"
                                                )
                                            )
                                        },
                                        onCourseClick = { selectedCourse = it; selectedCourseKey = it.uniqueKey },
                                        onWeekChange = model::setWeek,
                                        onSettingsClick = { currentScreenOrdinal = Screen.SETTINGS.ordinal },
                                        onTableMenuClick = { currentScreenOrdinal = Screen.TABLE_MANAGE.ordinal },
                                        onAddCourseClick = { addCoursePresetDay = 0; addCoursePresetPeriod = 0; showAddDialog = true },
                                        onAddCourseAt = { day, period ->
                                            addCoursePresetDay = day
                                            addCoursePresetPeriod = period
                                            showAddDialog = true
                                        },
                                        onLocateToday = locateToday,
                                        periodTimes = activeTable.periods,
                                        semesterStart = activeTable.semesterStart,
                                        totalWeeks = activeTable.totalWeeks,
                                        excludedWeeks = activeTable.excludedWeeks,
                                        dateExceptions = activeTable.dateExceptions,
                                        weekLabels = activeTable.weekLabels,
                                        displayDensity = state.preferences.displayDensity,
                                        reduceMotion = state.preferences.reduceMotion,
                                        viewMode = activeTable.viewMode,
                                        onViewModeChange = { model.setScheduleViewMode(it, ::showSaveError) },
                                        focusedDay = scheduleFocusedDay,
                                        onFocusedDayChange = { scheduleFocusedDay = it.coerceIn(1, 7) },
                                        onAgendaClick = { currentScreenOrdinal = Screen.AGENDA.ordinal },
                                        readOnlyMessage = state.persistentDataError,
                                        onRecoveryClick = { currentScreenOrdinal = Screen.SETTINGS.ordinal }
                                    )
                                }
                            }
                        }
                    }
                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp)
                    )
                }
            }
        }
    }

    private fun showSaveError(error: Throwable) = showError("保存失败", error)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureCourseRequest(intent)
    }

    private fun captureCourseRequest(intent: Intent?) {
        requestedCourseSeries.value = intent?.getStringExtra(ReminderScheduler.EXTRA_SERIES_KEY)
        requestedTableIndex.intValue = intent?.getIntExtra(ReminderScheduler.EXTRA_TABLE_INDEX, -1) ?: -1
        requestedShortcutAction.value = intent?.action?.takeIf {
            it == AppShortcuts.ACTION_TODAY || it == AppShortcuts.ACTION_ADD_COURSE
        }
    }

    private fun showError(prefix: String, error: Throwable) {
        Toast.makeText(this, "$prefix：${error.message ?: "未知错误"}", Toast.LENGTH_LONG).show()
    }
}

@Composable
private fun ColumnIndexField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter(Char::isDigit).take(2)) },
        label = { Text(label) },
        supportingText = { Text("列号") },
        singleLine = true,
        modifier = modifier
    )
}
