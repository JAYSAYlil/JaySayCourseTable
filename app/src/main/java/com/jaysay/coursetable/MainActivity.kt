package com.jaysay.coursetable

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.jaysay.coursetable.data.backup.AutoBackup
import com.jaysay.coursetable.data.backup.BackupData
import com.jaysay.coursetable.data.model.Course
import com.jaysay.coursetable.data.model.CourseImportAnalyzer
import com.jaysay.coursetable.data.model.CourseSeriesOperations
import com.jaysay.coursetable.data.model.CourseSeriesUndo
import com.jaysay.coursetable.data.parser.ExcelParser
import com.jaysay.coursetable.data.preferences.CustomBackgroundStore
import com.jaysay.coursetable.data.reminder.AutostartHelper
import com.jaysay.coursetable.data.reminder.ReminderPermissions
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
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import com.jaysay.coursetable.widget.CourseWidgetProvider
import com.jaysay.coursetable.ui.components.AppTopBar
import com.jaysay.coursetable.ui.components.BackupRestoreConfirmDialog
import com.jaysay.coursetable.ui.components.ConflictConfirmDialog
import com.jaysay.coursetable.ui.components.DetailDeleteConfirmDialog
import com.jaysay.coursetable.ui.components.EncryptedExportPasswordDialog
import com.jaysay.coursetable.ui.components.EncryptedImportPasswordDialog
import com.jaysay.coursetable.ui.components.PasteImportDialog
import com.jaysay.coursetable.ui.components.PendingConflictChange
import com.jaysay.coursetable.ui.components.rememberCustomBackground
import com.jaysay.coursetable.data.repository.TableData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private lateinit var model: MainViewModel
    private val pendingBackupRestore = mutableStateOf<BackupData?>(null)
    private val pendingEncryptedBackupImport = mutableStateOf<Uri?>(null)
    private var pendingEncryptedExportPassword: CharArray? = null
    private val requestedCourseSeries = mutableStateOf<String?>(null)
    private val requestedTableIndex = mutableIntStateOf(-1)
    private val requestedShortcutAction = mutableStateOf<String?>(null)
    /** 从系统设置/权限弹窗返回后递增，触发提醒状态重新检查。 */
    private val reminderStatusTick = mutableIntStateOf(0)
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

    private val excelTemplateExportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri: Uri? -> uri?.let(fileTransfer::handleExcelTemplateExport) }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { reminderStatusTick.intValue++ }

    private val autoBackupLocationLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        // CreateDocument 授予的写权限默认只在本次进程内有效；
        // 申请持久化后重启应用仍可向同一位置自动覆盖备份。
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        model.updatePreferences(
            model.state.preferences.copy(autoBackupUri = uri.toString(), autoBackupEnabled = true),
            ::showSaveError
        )
    }

    private val backgroundImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        lifecycleScope.launch {
            runCatching { CustomBackgroundStore.import(this@MainActivity, uri) }
                .onSuccess { storedRevision ->
                    val current = model.state.preferences.customBackgroundRevision
                    model.updatePreferences(
                        model.state.preferences.copy(
                            customBackgroundRevision = maxOf(storedRevision, current + 1L)
                        ),
                        ::showSaveError
                    )
                    Toast.makeText(this@MainActivity, getString(R.string.main_toast_custom_background_applied), Toast.LENGTH_SHORT).show()
                }
                .onFailure { showError(getString(R.string.main_toast_background_set_failed), it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // targetSdk 35 起系统强制 edge-to-edge；显式启用后系统栏始终透明，
        // 具体栏位背景由 JaySayTheme 的 decorView 底色与 Compose 图层控制。
        enableEdgeToEdge()
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
            fun currentScreen(): Screen = Screen.entries.getOrNull(currentScreenOrdinal) ?: Screen.MAIN
            // 详情页使用跨编辑稳定的 seriesKey 保存恢复依据，并记录从主课表还是日程列表进入。
            var selectedCourseSeriesKey by rememberSaveable { mutableStateOf<String?>(null) }
            var detailOriginOrdinal by rememberSaveable { mutableIntStateOf(Screen.MAIN.ordinal) }
            var calendarOriginOrdinal by rememberSaveable { mutableIntStateOf(Screen.SETTINGS.ordinal) }
            var selectedCourse by remember { mutableStateOf<Course?>(null) }
            var showAddDialog by rememberSaveable { mutableStateOf(false) }
            var showEditDialog by rememberSaveable { mutableStateOf(false) }
            // 编辑目标只按 seriesKey 持久化；课程本体从当前课表恢复，旋转后弹窗不再出现“开关在、内容丢”。
            var editingSeriesKey by rememberSaveable { mutableStateOf<String?>(null) }
            // 新增弹窗的星期/节次预设；0 表示未预设（day 有效范围 1-7，period 1-30）
            var addCoursePresetDay by rememberSaveable { mutableIntStateOf(0) }
            var addCoursePresetPeriod by rememberSaveable { mutableIntStateOf(0) }
            // 详情删除确认同理只持久化 seriesKey，课程对象在数据就绪后按系列还原。
            var pendingDeleteSeriesKey by rememberSaveable { mutableStateOf<String?>(null) }
            var pendingConflictChange by remember { mutableStateOf<PendingConflictChange?>(null) }
            var showPasteImportDialog by rememberSaveable { mutableStateOf(false) }
            var showEncryptedExportDialog by rememberSaveable { mutableStateOf(false) }
            var scheduleFocusedDay by rememberSaveable { mutableIntStateOf(LocalDate.now().dayOfWeek.value) }
            val snackbarHostState = remember { SnackbarHostState() }
            val coroutineScope = rememberCoroutineScope()
            val screenStateHolder = rememberSaveableStateHolder()
            val state = model.state
            val customBackground = rememberCustomBackground(state.preferences.customBackgroundRevision)
            val customBackgroundActive = customBackground != null

            JaySayTheme(
                themeMode = state.preferences.themeMode,
                highContrast = state.preferences.highContrast,
                transparentSystemBars = customBackgroundActive && currentScreen() == Screen.MAIN
            ) {
                if (state.isLoading) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                    return@JaySayTheme
                }

                fun detailOrigin(): Screen = Screen.entries.getOrNull(detailOriginOrdinal)
                    ?.takeIf { it == Screen.MAIN || it == Screen.AGENDA }
                    ?: Screen.MAIN
                fun calendarOrigin(): Screen = Screen.entries.getOrNull(calendarOriginOrdinal)
                    ?.takeIf { it == Screen.MAIN || it == Screen.SETTINGS }
                    ?: Screen.SETTINGS
                val closeCourseDetail: () -> Unit = {
                    selectedCourse = null
                    selectedCourseSeriesKey = null
                    currentScreenOrdinal = detailOrigin().ordinal
                }
                val openCourseDetail: (Course, Screen) -> Unit = { course, origin ->
                    selectedCourse = course
                    selectedCourseSeriesKey = course.seriesKey
                    detailOriginOrdinal = origin.ordinal
                    currentScreenOrdinal = Screen.COURSE_DETAIL.ordinal
                }

                // 数据就绪后按稳定系列标识还原/刷新详情；课程已不存在则回到真实来源页。
                LaunchedEffect(state.isLoading, state.courses, selectedCourseSeriesKey, currentScreenOrdinal) {
                    if (state.isLoading) return@LaunchedEffect
                    val seriesKey = selectedCourseSeriesKey
                    if (seriesKey != null) {
                        selectedCourse = state.courses.firstOrNull { it.seriesKey == seriesKey }
                        if (selectedCourse == null) closeCourseDetail()
                    } else if (currentScreen() == Screen.COURSE_DETAIL) {
                        closeCourseDetail()
                    }
                }

                val editTarget = if (showEditDialog) {
                    editingSeriesKey?.let { key -> state.courses.firstOrNull { it.seriesKey == key } }
                } else null

                // 持久化的编辑目标在数据变化后已不存在（如恢复备份、删除课程）时，自动收起编辑弹窗。
                LaunchedEffect(showEditDialog, editingSeriesKey, state.courses) {
                    if (showEditDialog && editingSeriesKey != null && editTarget == null) {
                        showEditDialog = false
                        editingSeriesKey = null
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
                            openCourseDetail(course, Screen.MAIN)
                        }
                        requestedCourseSeries.value = null
                        requestedTableIndex.intValue = -1
                    }
                }

                // 课表或提醒相关偏好变化后重新调度上课提醒并刷新桌面小组件（覆盖式，频率低）。
                // 只监听影响闹钟排布的字段，切主题、换背景等纯外观改动不再触发整轮取消重建。
                val appContext = LocalContext.current.applicationContext
                LaunchedEffect(
                    state.isLoading,
                    state.tables,
                    state.preferences.activeTableIndex,
                    state.preferences.reminderEnabled,
                    state.preferences.reminderMinutes
                ) {
                    if (!state.isLoading) {
                        withContext(Dispatchers.IO) {
                            ReminderScheduler.rescheduleAll(appContext, state.tables, state.preferences)
                        }
                        CourseWidgetProvider.requestUpdate(appContext)
                    }
                }

                // 启动自动备份：数据就绪后每个启动只执行一次，静默覆盖写入用户选择的位置。
                var autoBackupRanForLaunch by rememberSaveable { mutableStateOf(false) }
                LaunchedEffect(state.isLoading) {
                    val prefs = state.preferences
                    if (state.isLoading || autoBackupRanForLaunch) return@LaunchedEffect
                    if (!prefs.autoBackupEnabled || prefs.autoBackupUri.isBlank()) return@LaunchedEffect
                    autoBackupRanForLaunch = true
                    val uri = runCatching { Uri.parse(prefs.autoBackupUri) }.getOrNull()
                        ?: return@LaunchedEffect
                    AutoBackup.write(this@MainActivity, uri, model.backupSnapshot())
                        .onFailure { error ->
                            Toast.makeText(
                                this@MainActivity,
                                getString(R.string.main_error_format, getString(R.string.settings_auto_backup), error.message ?: getString(R.string.main_error_unknown)),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                }

                // 提醒暂停状态与权限检查涉及 SharedPreferences 读取，按触发时机缓存而非每次重组都读盘。
                val reminderPauseStatus = remember(                    reminderStatusTick.intValue,
                    state.activeTableIndex,
                    state.currentWeek
                ) {
                    ReminderSuppression.activeLabel(
                        this@MainActivity,
                        state.activeTableIndex,
                        state.currentWeek,
                        LocalDate.now()
                    )
                }
                val reminderBlockers = if (state.preferences.reminderEnabled) {
                    remember(reminderStatusTick.intValue) {
                        ReminderPermissions.blockers(this@MainActivity)
                    }
                } else emptyList()
                val widgetPresent = remember(reminderStatusTick.intValue) {
                    AppWidgetManager.getInstance(this@MainActivity)
                        .getAppWidgetIds(ComponentName(this@MainActivity, CourseWidgetProvider::class.java))
                        .isNotEmpty()
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
                val navigateBack: () -> Unit = {
                    val screen = currentScreen()
                    when (screen) {
                        Screen.IMPORT_CONFIRM -> {
                            model.clearStagedCourseImport()
                            currentScreenOrdinal = Screen.MAIN.ordinal
                        }
                        Screen.COURSE_DETAIL -> closeCourseDetail()
                        else -> screen.backDestination(detailOrigin(), calendarOrigin())
                            ?.let { currentScreenOrdinal = it.ordinal }
                    }
                }
                BackHandler(enabled = currentScreen().backDestination(detailOrigin(), calendarOrigin()) != null) {
                    navigateBack()
                }
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
                            AppShortcuts.reportUsed(this@MainActivity, AppShortcuts.ACTION_TODAY)
                            locateToday()
                            currentScreenOrdinal = Screen.MAIN.ordinal
                        }
                        AppShortcuts.ACTION_ADD_COURSE -> {
                            AppShortcuts.reportUsed(this@MainActivity, AppShortcuts.ACTION_ADD_COURSE)
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
                        if (snackbarHostState.showSnackbar(message, actionLabel = getString(R.string.main_snackbar_action_undo), withDismissAction = true) == SnackbarResult.ActionPerformed) {
                            model.updateCourses(undo::restore, onError = ::showSaveError)
                        }
                    }
                }

                pendingDeleteSeriesKey?.let { deletingKey ->
                    state.courses.firstOrNull { it.seriesKey == deletingKey }?.let { deleting ->
                    DetailDeleteConfirmDialog(
                        courseName = deleting.courseName,
                        week = state.currentWeek,
                        onConfirm = {
                            val previous = state.courses
                            val week = state.currentWeek
                            val seriesKey = deleting.seriesKey
                            val after = CourseSeriesOperations.deleteWeek(previous, seriesKey, week)
                            pendingDeleteSeriesKey = null
                            model.updateCourses(
                                transform = { courses -> CourseSeriesOperations.deleteWeek(courses, seriesKey, week) },
                                onComplete = {
                                    closeCourseDetail()
                                    offerUndo(
                                        CourseSeriesUndo.capture(previous, after, seriesKey),
                                        getString(R.string.main_snackbar_removed_from_week, week, deleting.courseName)
                                    )
                                },
                                onError = ::showSaveError
                            )
                        },
                        onDismiss = { pendingDeleteSeriesKey = null }
                    )
                }
                }

                if (showEncryptedExportDialog) {
                    EncryptedExportPasswordDialog(
                        onConfirm = { password ->
                            pendingEncryptedExportPassword?.fill('\u0000')
                            pendingEncryptedExportPassword = password.toCharArray()
                            showEncryptedExportDialog = false
                            encryptedBackupExportLauncher.launch("JaySay课表-密码加密备份-${LocalDate.now()}.json")
                        },
                        onDismiss = { showEncryptedExportDialog = false }
                    )
                }

                pendingEncryptedBackupImport.value?.let { uri ->
                    EncryptedImportPasswordDialog(
                        onConfirm = { password ->
                            pendingEncryptedBackupImport.value = null
                            fileTransfer.handleBackupImport(uri, password.toCharArray())
                        },
                        onDismiss = { pendingEncryptedBackupImport.value = null }
                    )
                }

                pendingBackupRestore.value?.let { backup ->
                    BackupRestoreConfirmDialog(
                        backup = backup,
                        currentTables = state.tables,
                        onConfirm = {
                            pendingBackupRestore.value = null
                            model.restoreBackup(
                                backup,
                                onComplete = { Toast.makeText(this@MainActivity, getString(R.string.main_toast_backup_restored), Toast.LENGTH_LONG).show() },
                                onError = { showError(getString(R.string.main_toast_restore_failed), it) }
                            )
                        },
                        onDismiss = { pendingBackupRestore.value = null }
                    )
                }

                if (showEditDialog && editTarget != null) {
                    val selected = editTarget
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
                                    showEditDialog = false; editingSeriesKey = null
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
                                showEditDialog = false; editingSeriesKey = null
                                if (currentScreen() == Screen.COURSE_DETAIL) closeCourseDetail()
                                offerUndo(
                                    CourseSeriesUndo.capture(previous, after, oldSeriesKey),
                                    if (applyToAll) getString(R.string.main_snackbar_deleted_all_weeks, deletedName)
                                    else getString(R.string.main_snackbar_removed_from_week, week, deletedName)
                                )
                            }, onError = ::showSaveError)
                        },
                        onDismiss = { showEditDialog = false; editingSeriesKey = null })
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
                                    coroutineScope.launch { snackbarHostState.showSnackbar(getString(R.string.main_snackbar_course_added, c.courseName)) }
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
                    ConflictConfirmDialog(
                        pending = pending,
                        onDismiss = { pendingConflictChange = null }
                    )
                }

                if (showPasteImportDialog) {
                    PasteImportDialog(
                        totalWeeks = state.activeTable.totalWeeks,
                        onParsed = { result ->
                            model.stageCourseImport(result)
                            showPasteImportDialog = false
                        },
                        onParseFailed = { message ->
                            Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                        },
                        onDismiss = { showPasteImportDialog = false }
                    )
                }

                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    AnimatedContent(
                        targetState = currentScreen(),
                        modifier = Modifier.fillMaxSize().background(
                            if (customBackgroundActive && currentScreen() == Screen.MAIN) Color.Transparent
                            else MaterialTheme.colorScheme.background
                        ),
                        transitionSpec = {
                            if (initialState == Screen.COURSE_DETAIL) {
                                // 仅课程详情返回一级课表/日程时使用短淡化，避免详情页返回时产生明显位移。
                                fadeIn(tween(Motion.DURATION_SHORT, easing = Motion.standard)) togetherWith
                                    fadeOut(tween(90, easing = Motion.exit))
                            } else if (targetState.ordinal > initialState.ordinal) {
                                // 进出同路径、同弹簧（可中断）：符合空间一致性准则。
                                (slideInHorizontally(Motion.page()) { it / 4 } +
                                    fadeIn(tween(Motion.DURATION_SHORT, easing = Motion.standard))) togetherWith
                                    (slideOutHorizontally(Motion.page()) { -it / 4 } +
                                        fadeOut(tween(110, easing = Motion.exit)))
                            } else {
                                (slideInHorizontally(Motion.page()) { -it / 4 } +
                                    fadeIn(tween(Motion.DURATION_SHORT, easing = Motion.standard))) togetherWith
                                    (slideOutHorizontally(Motion.page()) { it / 4 } +
                                        fadeOut(tween(110, easing = Motion.exit)))
                            }
                        },
                        label = "screen"
                    ) { screen ->
                        screenStateHolder.SaveableStateProvider(screen.name) {
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
                                customBackground = customBackground,
                                onChooseCustomBackground = { backgroundImageLauncher.launch("image/*") },
                                onClearCustomBackground = {
                                    coroutineScope.launch {
                                        runCatching { CustomBackgroundStore.clear(this@MainActivity) }
                                            .onSuccess {
                                                model.updatePreferences(
                                                    state.preferences.copy(customBackgroundRevision = 0L),
                                                    ::showSaveError
                                                )
                                                Toast.makeText(this@MainActivity, getString(R.string.main_toast_default_background_restored), Toast.LENGTH_SHORT).show()
                                            }
                                            .onFailure { showError(getString(R.string.main_toast_default_background_restore_failed), it) }
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
                                onExportExcelTemplate = {
                                    excelTemplateExportLauncher.launch("JaySay课表-导入模板.xlsx")
                                },
                                onExportDiagnostics = {
                                    diagnosticsExportLauncher.launch("JaySay课表-脱敏诊断-${LocalDate.now()}.txt")
                                },
                                onOpenHistory = { currentScreenOrdinal = Screen.HISTORY.ordinal },
                                onOpenCalendarExceptions = {
                                    calendarOriginOrdinal = Screen.SETTINGS.ordinal
                                    currentScreenOrdinal = Screen.CALENDAR.ordinal
                                },
                                reminderPauseStatus = reminderPauseStatus,
                                onClearReminderPause = {
                                    ReminderSuppression.clear(this@MainActivity)
                                    coroutineScope.launch(Dispatchers.IO) {
                                        ReminderScheduler.rescheduleAll(this@MainActivity, state.tables, state.preferences)
                                    }
                                    Toast.makeText(this@MainActivity, getString(R.string.main_toast_reminder_pause_cleared), Toast.LENGTH_SHORT).show()
                                },
                                reminderBlockers = reminderBlockers,
                                onRequestNotificationPermission = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                },
                                onOpenExactAlarmSettings = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        startActivity(
                                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                                data = ("package:" + packageName).toUri()
                                            }
                                        )
                                    }
                                },
                                onOpenChannelSettings = {
                                    startActivity(
                                        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                                            putExtra(Settings.EXTRA_CHANNEL_ID, ReminderScheduler.CHANNEL_ID)
                                            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                                        }
                                    )
                                },
                                onOpenAutostartSettings = {
                                    AutostartHelper.launch(this@MainActivity)
                                },
                                widgetPresent = widgetPresent,
                                onChooseAutoBackupLocation = {
                                    autoBackupLocationLauncher.launch("JaySay课表-自动备份.json")
                                },
                                tablesCount = state.tables.size,
                                readOnlyMessage = state.persistentDataError,
                                onBack = navigateBack
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
                                                    getString(R.string.main_toast_import_done, result.added, result.merged, result.skipped),
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
                                onBack = navigateBack
                            )

                            Screen.AGENDA -> Scaffold(
                                topBar = {
                                    AppTopBar(
                                        title = stringResource(R.string.main_topbar_title_agenda),
                                        navigationIcon = {
                                            IconButton(onClick = navigateBack) {
                                                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.main_action_back))
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
                                        val selected = state.courses.firstOrNull { it.seriesKey == course.seriesKey } ?: course
                                        openCourseDetail(selected, Screen.AGENDA)
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
                                        Toast.makeText(this@MainActivity, getString(R.string.main_toast_history_restored), Toast.LENGTH_SHORT).show()
                                        currentScreenOrdinal = Screen.MAIN.ordinal
                                    }, onError = ::showSaveError)
                                },
                                onBack = { currentScreenOrdinal = Screen.SETTINGS.ordinal }
                            )

                            Screen.CALENDAR -> CalendarExceptionScreen(
                                tableData = activeTable,
                                onUpdate = { model.updateActiveTable(it, ::showSaveError) },
                                onBack = { currentScreenOrdinal = calendarOrigin().ordinal }
                            )

                            Screen.COURSE_DETAIL -> selectedCourse?.let { course ->
                                CourseDetailScreen(
                                    course = course,
                                    allCourses = state.courses,
                                    onClose = closeCourseDetail,
                                    onEdit = { editing ->
                                        editingSeriesKey = editing.seriesKey
                                        showEditDialog = true
                                    },
                                    onDelete = { pendingDeleteSeriesKey = course.seriesKey }
                                )
                            }

                            Screen.MAIN -> CourseTableScreen(
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
                                        onCourseClick = { openCourseDetail(it, Screen.MAIN) },
                                        onWeekChange = model::setWeek,
                                        onSettingsClick = { currentScreenOrdinal = Screen.SETTINGS.ordinal },
                                        onCalendarContextClick = {
                                            calendarOriginOrdinal = Screen.MAIN.ordinal
                                            currentScreenOrdinal = Screen.CALENDAR.ordinal
                                        },
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
                                        customBackground = customBackground,
                                        customBackgroundOverlayEnabled = state.preferences.customBackgroundOverlayEnabled,
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
                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp)
                    )
                }
            }
        }
    }

    private fun showSaveError(error: Throwable) = showError(getString(R.string.main_toast_save_failed), error)

    override fun onResume() {
        super.onResume()
        // 用户可能刚从系统设置授予/撤销通知或精确闹钟权限，刷新提醒状态提示。
        reminderStatusTick.intValue++
    }

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
        Toast.makeText(this, getString(R.string.main_error_format, prefix, error.message ?: getString(R.string.main_error_unknown)), Toast.LENGTH_LONG).show()
    }
}
