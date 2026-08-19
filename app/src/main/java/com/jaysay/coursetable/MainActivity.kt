package com.jaysay.coursetable

import android.net.Uri
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModelProvider
import com.jaysay.coursetable.data.backup.BackupData
import com.jaysay.coursetable.data.backup.BackupCodec
import com.jaysay.coursetable.data.model.Course
import com.jaysay.coursetable.data.model.CourseConflict
import com.jaysay.coursetable.data.model.CourseImportAnalyzer
import com.jaysay.coursetable.data.model.CourseSeriesOperations
import com.jaysay.coursetable.data.model.CourseSeriesUndo
import com.jaysay.coursetable.data.parser.ExcelParser
import com.jaysay.coursetable.ui.screen.CourseDetailScreen
import com.jaysay.coursetable.ui.screen.CourseEditDialog
import com.jaysay.coursetable.ui.screen.CourseTableScreen
import com.jaysay.coursetable.ui.screen.ImportConfirmScreen
import com.jaysay.coursetable.ui.screen.SettingsScreen
import com.jaysay.coursetable.ui.screen.TableManageScreen
import com.jaysay.coursetable.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.time.LocalDate

private enum class Screen { MAIN, SETTINGS, IMPORT_CONFIRM, TABLE_MANAGE }

private data class PendingConflictChange(
    val courseName: String,
    val conflicts: List<CourseConflict>,
    val onConfirm: () -> Unit
)

class MainActivity : ComponentActivity() {
    private lateinit var model: MainViewModel
    private val pendingImport = mutableStateOf<ExcelParser.ParseResult?>(null)
    private val pendingBackupRestore = mutableStateOf<BackupData?>(null)
    private var exportSanitized = false

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let { handleFileImport(it) } }

    private val backupExportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? -> uri?.let { handleBackupExport(it, exportSanitized) } }

    private val backupImportLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let { handleBackupImport(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model = ViewModelProvider(this)[MainViewModel::class.java]

        setContent {
            // 屏幕位置持久化，Activity 重建（旋转/进程回收）后不会跳回主界面。
            var currentScreenOrdinal by rememberSaveable { mutableIntStateOf(Screen.MAIN.ordinal) }
            fun currentScreen(): Screen = Screen.values().getOrNull(currentScreenOrdinal) ?: Screen.MAIN
            // 详情页用 uniqueKey 保存恢复依据，数据加载完成后在 LaunchedEffect 中还原课程实例。
            var selectedCourseKey by rememberSaveable { mutableStateOf<String?>(null) }
            var selectedCourse by remember { mutableStateOf<Course?>(null) }
            var importResult by remember { mutableStateOf(ExcelParser.ParseResult(emptyList(), emptyList())) }
            var showAddDialog by rememberSaveable { mutableStateOf(false) }
            var showEditDialog by rememberSaveable { mutableStateOf(false) }
            var editingCourse by remember { mutableStateOf<Course?>(null) }
            // 新增弹窗的星期/节次预设；0 表示未预设（day 有效范围 1-7，period 1-30）
            var addCoursePresetDay by rememberSaveable { mutableIntStateOf(0) }
            var addCoursePresetPeriod by rememberSaveable { mutableIntStateOf(0) }
            var pendingDetailDelete by remember { mutableStateOf<Course?>(null) }
            var pendingConflictChange by remember { mutableStateOf<PendingConflictChange?>(null) }
            var scheduleFocusedDay by rememberSaveable { mutableIntStateOf(LocalDate.now().dayOfWeek.value) }
            val snackbarHostState = remember { SnackbarHostState() }
            val coroutineScope = rememberCoroutineScope()
            val state = model.state

            JaySayTheme(themeMode = state.preferences.themeMode) {
                if (state.isLoading) return@JaySayTheme

                // 数据就绪后按保存的 uniqueKey 还原详情页课程；课程已不存在则关闭详情。
                LaunchedEffect(state.isLoading, selectedCourseKey) {
                    if (!state.isLoading && selectedCourseKey != null) {
                        selectedCourse = state.courses.firstOrNull { it.uniqueKey == selectedCourseKey }
                            ?: run { selectedCourseKey = null; null }
                    }
                }

                val pend = pendingImport.value
                LaunchedEffect(pend) {
                    if (pend != null) {
                        importResult = pend
                        currentScreenOrdinal = Screen.IMPORT_CONFIRM.ordinal
                        pendingImport.value = null
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

                pendingBackupRestore.value?.let { backup ->
                    val courseCount = backup.tables.sumOf { it.courses.size }
                    AlertDialog(
                        onDismissRequest = { pendingBackupRestore.value = null },
                        title = { Text("确认恢复备份") },
                        text = { Text("将用备份中的 ${backup.tables.size} 个课表、$courseCount 条课程替换当前数据。备份已通过完整性校验。") },
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

                Box(modifier = Modifier.fillMaxSize()) {
                    AnimatedContent(
                        targetState = currentScreen(),
                        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                        transitionSpec = {
                            if (targetState.ordinal > initialState.ordinal) {
                                (slideInHorizontally(tween(220)) { it / 4 } + fadeIn(tween(180))) togetherWith
                                    (slideOutHorizontally(tween(180)) { -it / 5 } + fadeOut(tween(130)))
                            } else {
                                (slideInHorizontally(tween(220)) { -it / 4 } + fadeIn(tween(180))) togetherWith
                                    (slideOutHorizontally(tween(180)) { it / 5 } + fadeOut(tween(130)))
                            }
                        },
                        label = "screen"
                    ) { screen ->
                        when (screen) {
                            Screen.SETTINGS -> SettingsScreen(
                                tableData = state.activeTable,
                                preferences = state.preferences,
                                onUpdatePrefs = { model.updatePreferences(it, ::showSaveError) },
                                onUpdateTable = { model.updateActiveTable(it, ::showSaveError) },
                                onExportBackup = { sanitized ->
                                    exportSanitized = sanitized
                                    val suffix = if (sanitized) "脱敏副本" else "完整备份"
                                    backupExportLauncher.launch("JaySay课表-$suffix-${LocalDate.now()}.json")
                                },
                                onImportBackup = {
                                    backupImportLauncher.launch(arrayOf("application/json", "text/json", "text/plain"))
                                },
                                readOnlyMessage = state.persistentDataError,
                                onBack = { locateToday(); currentScreenOrdinal = Screen.MAIN.ordinal }
                            )

                            Screen.IMPORT_CONFIRM -> {
                                val preview = remember(importResult, state.courses) {
                                    CourseImportAnalyzer.analyze(state.courses, importResult.courses)
                                }
                                ImportConfirmScreen(
                                    preview = preview,
                                    warnings = importResult.errors,
                                    onConfirm = { selected ->
                                        model.importCourses(selected, onComplete = { result ->
                                            locateToday()
                                            currentScreenOrdinal = Screen.MAIN.ordinal
                                            Toast.makeText(
                                                this@MainActivity,
                                                "导入完成：新增 ${result.added}，合并 ${result.merged}，跳过重复 ${result.skipped}",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }, onError = ::showSaveError)
                                    },
                                    onCancel = { locateToday(); currentScreenOrdinal = Screen.MAIN.ordinal }
                                )
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
                                onBack = { locateToday(); currentScreenOrdinal = Screen.MAIN.ordinal }
                            )

                            Screen.MAIN -> AnimatedContent(
                                targetState = selectedCourse,
                                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                                transitionSpec = {
                                    if (targetState != null) {
                                        (slideInHorizontally(tween(220)) { it / 3 } + fadeIn(tween(180))) togetherWith
                                            (slideOutHorizontally(tween(180)) { -it / 5 } + fadeOut(tween(140)))
                                    } else {
                                        (slideInHorizontally(tween(220)) { -it / 5 } + fadeIn(tween(180))) togetherWith
                                            (slideOutHorizontally(tween(180)) { it / 3 } + fadeOut(tween(140)))
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
                                        viewMode = activeTable.viewMode,
                                        onViewModeChange = { model.setScheduleViewMode(it, ::showSaveError) },
                                        focusedDay = scheduleFocusedDay,
                                        onFocusedDayChange = { scheduleFocusedDay = it.coerceIn(1, 7) },
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

    private fun handleFileImport(uri: Uri) {
        // 解析 Excel 放到后台线程，避免大文件阻塞主线程导致卡顿/ANR
        lifecycleScope.launch {
            // 任何意外异常都只提示、不崩溃
            val result = try {
                withContext(Dispatchers.IO) { ExcelParser.parse(this@MainActivity, uri) }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "导入失败：" + e.message, Toast.LENGTH_LONG).show()
                return@launch
            }
            if (result.courses.isEmpty()) {
                val message = result.errors.firstOrNull() ?: "文件中未找到课程数据"
                Toast.makeText(this@MainActivity, "导入失败：$message", Toast.LENGTH_LONG).show()
            } else {
                // 有个别坏行时仍允许确认导入正常课程，并在确认页展示警告。
                pendingImport.value = result
            }
        }
    }

    private fun handleBackupExport(uri: Uri, sanitized: Boolean) {
        lifecycleScope.launch {
            val result = runCatching {
                val text = withContext(Dispatchers.Default) {
                    BackupCodec.encode(model.backupSnapshot(), sanitized)
                }
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use {
                        it.write(text)
                    } ?: error("无法写入所选文件")
                }
            }
            result.onSuccess {
                val message = if (sanitized) "脱敏副本已导出（不能用于恢复）" else "完整备份已导出"
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
            }.onFailure { showError("导出失败", it) }
        }
    }

    private fun handleBackupImport(uri: Uri) {
        lifecycleScope.launch {
            val decoded = runCatching {
                withContext(Dispatchers.IO) {
                    val stream = contentResolver.openInputStream(uri) ?: error("无法读取所选文件")
                    stream.use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            require(output.size() <= 10 * 1024 * 1024) { "备份文件过大" }
                        }
                        BackupCodec.decode(output.toString(Charsets.UTF_8.name()))
                    }
                }
            }
            decoded.onSuccess { backup ->
                pendingBackupRestore.value = backup
            }.onFailure { showError("备份校验失败，原课表未被替换", it) }
        }
    }

    private fun showSaveError(error: Throwable) = showError("保存失败", error)

    private fun showError(prefix: String, error: Throwable) {
        Toast.makeText(this, "$prefix：${error.message ?: "未知错误"}", Toast.LENGTH_LONG).show()
    }
}
