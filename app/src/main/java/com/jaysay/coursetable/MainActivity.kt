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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModelProvider
import com.jaysay.coursetable.data.backup.BackupData
import com.jaysay.coursetable.data.backup.BackupCodec
import com.jaysay.coursetable.data.model.Course
import com.jaysay.coursetable.data.parser.ExcelParser
import com.jaysay.coursetable.ui.components.AppPanel
import com.jaysay.coursetable.ui.components.AppTopBar
import com.jaysay.coursetable.ui.screen.CourseDetailScreen
import com.jaysay.coursetable.ui.screen.CourseEditDialog
import com.jaysay.coursetable.ui.screen.CourseTableScreen
import com.jaysay.coursetable.ui.screen.SettingsScreen
import com.jaysay.coursetable.ui.screen.ScheduleViewMode
import com.jaysay.coursetable.ui.screen.TableManageScreen
import com.jaysay.coursetable.ui.theme.*
import com.jaysay.coursetable.util.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.time.LocalDate

private enum class Screen { MAIN, SETTINGS, IMPORT_CONFIRM, TABLE_MANAGE }

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
            var currentScreen by remember { mutableStateOf(Screen.MAIN) }
            var selectedCourse by remember { mutableStateOf<Course?>(null) }
            var importResult by remember { mutableStateOf(ExcelParser.ParseResult(emptyList(), emptyList())) }
            var showAddDialog by remember { mutableStateOf(false) }
            var showEditDialog by remember { mutableStateOf(false) }
            var editingCourse by remember { mutableStateOf<Course?>(null) }
            var addCoursePreset by remember { mutableStateOf<Pair<Int, Int>?>(null) }
            var pendingDetailDelete by remember { mutableStateOf<Course?>(null) }
            var scheduleViewMode by rememberSaveable { mutableStateOf(ScheduleViewMode.WEEK) }
            var scheduleFocusedDay by rememberSaveable { mutableIntStateOf(LocalDate.now().dayOfWeek.value) }
            val snackbarHostState = remember { SnackbarHostState() }
            val coroutineScope = rememberCoroutineScope()
            val state = model.state

            JaySayTheme(themeMode = state.preferences.themeMode) {
                if (state.isLoading) return@JaySayTheme

                val pend = pendingImport.value
                LaunchedEffect(pend) {
                    if (pend != null) {
                        importResult = pend
                        currentScreen = Screen.IMPORT_CONFIRM
                        pendingImport.value = null
                    }
                }

                val activeTable = state.activeTable
                val locateToday: () -> Unit = {
                    scheduleFocusedDay = LocalDate.now().dayOfWeek.value
                    model.locateToday()
                }
                val offerUndo: (List<Course>, String) -> Unit = { previousCourses, message ->
                    coroutineScope.launch {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        if (snackbarHostState.showSnackbar(message, actionLabel = "撤销", withDismissAction = true) == SnackbarResult.ActionPerformed) {
                            model.updateCourses({ previousCourses }, onError = ::showSaveError)
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
                                pendingDetailDelete = null
                                model.updateCourses(
                                    transform = { courses ->
                                        courses.mapNotNull { course ->
                                            if (course.uniqueKey == deleting.uniqueKey) course.withoutWeeks(setOf(week)) else course
                                        }
                                    },
                                    onComplete = {
                                        selectedCourse = null
                                        offerUndo(previous, "已从第 $week 周移除 ${deleting.courseName}")
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
                    CourseEditDialog(course = editingCourse, totalWeeks = activeTable.totalWeeks,
                        currentWeek = state.currentWeek, maxPeriods = activeTable.periods.size,
                        onSave = { updated, applyToAll ->
                            val oldKey = editingCourse!!.uniqueKey
                            val week = state.currentWeek
                            model.updateCourses({ courses ->
                                if (applyToAll) {
                                    courses.map { c -> if (c.uniqueKey == oldKey) updated else c }
                                } else {
                                    courses.flatMap { c ->
                                        if (c.uniqueKey == oldKey) {
                                            listOfNotNull(
                                                c.withoutWeeks(setOf(week)),
                                                updated.copy(weeks = listOf(week))
                                            )
                                        } else listOf(c)
                                    }
                                }
                            }, onComplete = {
                                showEditDialog = false; editingCourse = null
                            }, onError = ::showSaveError)
                        },
                        onDelete = { applyToAll ->
                            val oldKey = editingCourse!!.uniqueKey
                            val week = state.currentWeek
                            val deletedName = editingCourse!!.courseName
                            val previous = state.courses
                            model.updateCourses({ courses ->
                                if (applyToAll) {
                                    courses.filter { c -> c.uniqueKey != oldKey }
                                } else {
                                    courses.mapNotNull { c ->
                                        if (c.uniqueKey == oldKey) c.withoutWeeks(setOf(week)) else c
                                    }
                                }
                            }, onComplete = {
                                showEditDialog = false; editingCourse = null
                                offerUndo(previous, if (applyToAll) "已删除 $deletedName 的全部周次" else "已从第 $week 周移除 $deletedName")
                            }, onError = ::showSaveError)
                        },
                        onDismiss = { showEditDialog = false; editingCourse = null })
                }

                if (showAddDialog) {
                    CourseEditDialog(course = null, totalWeeks = activeTable.totalWeeks,
                        currentWeek = state.currentWeek,
                        maxPeriods = activeTable.periods.size,
                        initialDay = addCoursePreset?.first ?: 1,
                        initialStartPeriod = addCoursePreset?.second ?: 1,
                        onSave = { c, _ ->
                            model.updateCourses({ it + c }, onComplete = {
                                showAddDialog = false
                                addCoursePreset = null
                                coroutineScope.launch { snackbarHostState.showSnackbar("已添加 ${c.courseName}") }
                            }, onError = ::showSaveError)
                        }, onDelete = null, onDismiss = { showAddDialog = false; addCoursePreset = null })
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    AnimatedContent(
                        targetState = currentScreen,
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
                                onBack = { locateToday(); currentScreen = Screen.MAIN }
                            )

                            Screen.IMPORT_CONFIRM -> ImportConfirmScreen(
                                parsedCourses = importResult.courses,
                                warnings = importResult.errors,
                                onConfirm = { selected ->
                                    model.importCourses(selected, onComplete = { result ->
                                        locateToday()
                                        currentScreen = Screen.MAIN
                                        Toast.makeText(
                                            this@MainActivity,
                                            "导入完成：新增 ${result.added}，合并 ${result.merged}，跳过重复 ${result.skipped}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }, onError = ::showSaveError)
                                },
                                onCancel = { locateToday(); currentScreen = Screen.MAIN }
                            )

                            Screen.TABLE_MANAGE -> TableManageScreen(
                                tables = state.tables,
                                activeIndex = state.activeTableIndex,
                                onSelect = { idx ->
                                    model.selectTable(idx, ::showSaveError)
                                    currentScreen = Screen.MAIN
                                },
                                onDelete = { model.deleteTable(it, ::showSaveError) },
                                onAdd = { model.addTable(::showSaveError) },
                                onRename = { idx, name -> model.renameTable(idx, name, ::showSaveError) },
                                onBack = { locateToday(); currentScreen = Screen.MAIN }
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
                                        onClose = { selectedCourse = null },
                                        onEdit = { editing ->
                                            selectedCourse = null
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
                                        onCourseClick = { selectedCourse = it },
                                        onWeekChange = model::setWeek,
                                        onSettingsClick = { currentScreen = Screen.SETTINGS },
                                        onTableMenuClick = { currentScreen = Screen.TABLE_MANAGE },
                                        onAddCourseClick = { addCoursePreset = null; showAddDialog = true },
                                        onAddCourseAt = { day, period ->
                                            addCoursePreset = day to period
                                            showAddDialog = true
                                        },
                                        onLocateToday = locateToday,
                                        periodTimes = activeTable.periods,
                                        semesterStart = activeTable.semesterStart,
                                        totalWeeks = activeTable.totalWeeks,
                                        viewMode = scheduleViewMode,
                                        onViewModeChange = { scheduleViewMode = it },
                                        focusedDay = scheduleFocusedDay,
                                        onFocusedDayChange = { scheduleFocusedDay = it.coerceIn(1, 7) }
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

// ===== 导入确认界面 =====
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportConfirmScreen(
    parsedCourses: List<Course>,
    warnings: List<String>,
    onConfirm: (List<Course>) -> Unit,
    onCancel: () -> Unit
) {
    var selected by remember(parsedCourses) { mutableStateOf(parsedCourses.toSet()) }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "确认导入",
                navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.Default.Close, "取消") } },
                actions = {
                    TextButton(onClick = { onConfirm(selected.toList()) }, enabled = selected.isNotEmpty()) {
                        Text("导入 " + selected.size + " 条", color = Primary, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    ) { pad ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(pad)) {
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("已解析 ${parsedCourses.size} 条课程，点击可取消选择：",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (warnings.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "有 ${warnings.size} 条提示，正常课程仍可导入。首条：${warnings.first()}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            itemsIndexed(parsedCourses, key = { index, course -> "${course.uniqueKey}-$index" }) { _, course ->
                val isSel = course in selected
                AppPanel(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp).clickable {
                        selected = if (isSel) selected - course else selected + course
                    },
                    selected = isSel
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = isSel, onCheckedChange = {
                            selected = if (it) selected + course else selected - course
                        })
                        Column(modifier = Modifier.weight(1f)) {
                            Text(course.courseName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                TimeUtils.getDayName(course.dayOfWeek) + " " + course.startPeriod + "-" + course.endPeriod + "节" +
                                (if (course.teacher.isNotBlank()) " | " + course.teacher else "") +
                                (if (course.classroom.isNotBlank()) " | " + course.classroom else ""),
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}
