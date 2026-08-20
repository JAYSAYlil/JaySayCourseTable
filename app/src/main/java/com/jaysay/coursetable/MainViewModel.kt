package com.jaysay.coursetable

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jaysay.coursetable.data.backup.BackupData
import com.jaysay.coursetable.data.history.CourseSnapshotDiff
import com.jaysay.coursetable.data.history.CourseSnapshotSummary
import com.jaysay.coursetable.data.model.Course
import com.jaysay.coursetable.data.model.CourseMerger
import com.jaysay.coursetable.data.model.ImportMergeResult
import com.jaysay.coursetable.data.model.ScheduleViewMode
import com.jaysay.coursetable.data.parser.ExcelParser
import com.jaysay.coursetable.data.preferences.AppPreferences
import com.jaysay.coursetable.data.preferences.PreferencesManager
import com.jaysay.coursetable.data.repository.CourseRepository
import com.jaysay.coursetable.data.repository.TableData
import com.jaysay.coursetable.data.storage.DataCorruptionException
import com.jaysay.coursetable.data.storage.WriteProtectionGate
import com.jaysay.coursetable.data.transfer.ImportDraftStore
import com.jaysay.coursetable.util.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

@Immutable
data class MainUiState(
    val tables: List<TableData> = listOf(TableData.placeholder()),
    val preferences: AppPreferences = AppPreferences(),
    val activeTableIndex: Int = 0,
    val currentWeek: Int = 1,
    val isLoading: Boolean = true,
    /** 非空时必须持续展示，且所有普通持久化入口处于只读保护。 */
    val persistentDataError: String? = null
) {
    val activeTable: TableData get() = tables.getOrElse(activeTableIndex) { TableData.placeholder() }
    val courses: List<Course> get() = activeTable.courses
    val isReadOnly: Boolean get() = persistentDataError != null
}

/**
 * 统一管理课表状态和串行化磁盘写入，避免界面重建丢状态或连续操作互相覆盖。
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = CourseRepository(application)
    private val preferencesManager = PreferencesManager(application)
    private val writeMutex = Mutex()
    private val writeProtection = WriteProtectionGate()
    private val importDraftStore = ImportDraftStore(application)

    var state by mutableStateOf(MainUiState())
        private set

    /** 配置变更期间保留待确认导入；不写入磁盘，也不塞进系统 Bundle。 */
    var stagedCourseImport by mutableStateOf<ExcelParser.ParseResult?>(null)
        private set

    var historySnapshots by mutableStateOf<List<CourseSnapshotSummary>>(emptyList())
        private set

    init {
        viewModelScope.launch {
            val tablesResult = runCatching { repository.loadAllTables() }
            tablesResult.onSuccess { tables ->
                // 偏好文件损坏不代表课表损坏；退回默认偏好，避免误锁课程数据。
                val preferences = runCatching { preferencesManager.load() }.getOrDefault(AppPreferences())
                val requested = preferences.activeTableIndex.coerceIn(tables.indices)
                val active = requested.takeIf { !tables[it].archived }
                    ?: tables.indexOfFirst { !it.archived }.takeIf { it >= 0 }
                    ?: requested
                state = MainUiState(
                    tables = tables,
                    preferences = preferences.copy(activeTableIndex = active),
                    activeTableIndex = active,
                    currentWeek = TimeUtils.todayWeek(tables[active].semesterStart, tables[active].totalWeeks),
                    isLoading = false
                )
                stagedCourseImport = runCatching { importDraftStore.load() }.getOrNull()
            }.onFailure { error ->
                val message = when (error) {
                    is DataCorruptionException -> error.message ?: "课表数据及其备份均无法读取"
                    else -> "课表数据读取失败：${error.message ?: "未知错误"}"
                }
                writeProtection.lock(message)
                state = MainUiState(isLoading = false, persistentDataError = message)
            }
        }
    }

    fun setWeek(week: Int) {
        state = state.copy(currentWeek = week.coerceIn(1, state.activeTable.totalWeeks))
    }

    fun stageCourseImport(result: ExcelParser.ParseResult) {
        stagedCourseImport = result
        viewModelScope.launch(Dispatchers.IO) { runCatching { importDraftStore.save(result) } }
    }

    fun clearStagedCourseImport() {
        stagedCourseImport = null
        viewModelScope.launch(Dispatchers.IO) { runCatching { importDraftStore.clear() } }
    }

    fun locateToday() {
        val table = state.activeTable
        state = state.copy(currentWeek = TimeUtils.todayWeek(table.semesterStart, table.totalWeeks))
    }

    fun updatePreferences(preferences: AppPreferences, onError: (Throwable) -> Unit = {}) = launchWrite(onError) {
        val safe = preferences.copy(activeTableIndex = state.activeTableIndex)
        preferencesManager.save(safe)
        state = state.copy(preferences = safe)
    }

    fun updateActiveTable(table: TableData, onError: (Throwable) -> Unit = {}) {
        val targetIndex = state.activeTableIndex
        launchWrite(onError) {
            // 设置页只负责学期和节次配置；保留可能在排队写入期间变化的课程、名称和视图状态。
            val updated = mutateTable(targetIndex) { current ->
                table.copy(
                    name = current.name,
                    courses = current.courses,
                    viewMode = current.viewMode
                )
            } ?: return@launchWrite
            state = if (state.activeTableIndex == targetIndex) {
                val safeTable = updated[targetIndex]
                state.copy(tables = updated, currentWeek = state.currentWeek.coerceIn(1, safeTable.totalWeeks))
            } else {
                state.copy(tables = updated)
            }
        }
    }

    fun setScheduleViewMode(mode: ScheduleViewMode, onError: (Throwable) -> Unit = {}) {
        val targetIndex = state.activeTableIndex
        launchWrite(onError) {
            val current = state.tables.getOrNull(targetIndex) ?: return@launchWrite
            if (current.viewMode == mode) return@launchWrite
            val updated = mutateTable(targetIndex) { it.copy(viewMode = mode) } ?: return@launchWrite
            state = state.copy(tables = updated)
        }
    }

    fun updateCourses(
        transform: (List<Course>) -> List<Course>,
        onComplete: () -> Unit = {},
        onError: (Throwable) -> Unit = {}
    ) {
        val targetIndex = state.activeTableIndex
        launchWrite(onError) {
            val updated = mutateTable(targetIndex) { it.copy(courses = transform(it.courses)) }
                ?: return@launchWrite
            state = state.copy(tables = updated)
            onComplete()
        }
    }

    fun importCourses(
        imported: List<Course>,
        onComplete: (ImportMergeResult) -> Unit,
        onError: (Throwable) -> Unit = {}
    ) {
        val targetIndex = state.activeTableIndex
        launchWrite(onError) {
            val courses = state.tables.getOrNull(targetIndex)?.courses ?: return@launchWrite
            val result = CourseMerger.mergeImported(courses, imported)
            val updated = mutateTable(targetIndex) { it.copy(courses = result.courses) }
                ?: return@launchWrite
            state = state.copy(tables = updated)
            onComplete(result)
        }
    }

    fun selectTable(index: Int, onError: (Throwable) -> Unit = {}) = launchWrite(onError) {
        if (index !in state.tables.indices || state.tables[index].archived) return@launchWrite
        val preferences = state.preferences.copy(activeTableIndex = index)
        preferencesManager.save(preferences)
        val table = state.tables[index]
        state = state.copy(
            preferences = preferences,
            activeTableIndex = index,
            currentWeek = TimeUtils.todayWeek(table.semesterStart, table.totalWeeks)
        )
    }

    fun deleteTable(index: Int, onError: (Throwable) -> Unit = {}) = launchWrite(onError) {
        if (state.tables.size <= 1 || index !in state.tables.indices) return@launchWrite
        if (!state.tables[index].archived && state.tables.count { !it.archived } <= 1) {
            throw IllegalStateException("至少需要保留一张使用中的课表")
        }
        val tables = state.tables.toMutableList().also { it.removeAt(index) }
        val preferredActive = when {
            index < state.activeTableIndex -> state.activeTableIndex - 1
            state.activeTableIndex >= tables.size -> tables.lastIndex
            else -> state.activeTableIndex
        }.coerceAtLeast(0)
        val active = preferredActive.takeIf { it in tables.indices && !tables[it].archived }
            ?: tables.indexOfFirst { !it.archived }
        val preferences = state.preferences.copy(activeTableIndex = active)
        repository.saveAllTables(tables)
        preferencesManager.save(preferences)
        state = state.copy(tables = tables, preferences = preferences, activeTableIndex = active)
        locateToday()
    }

    fun addTable(onError: (Throwable) -> Unit = {}) = launchWrite(onError) {
        val tables = state.tables + TableData(
            name = "课表${state.tables.size + 1}",
            courses = emptyList(),
            semesterStart = TimeUtils.todayDate()
        )
        repository.saveAllTables(tables)
        state = state.copy(tables = tables)
    }

    fun renameTable(index: Int, name: String, onError: (Throwable) -> Unit = {}) = launchWrite(onError) {
        if (index !in state.tables.indices) return@launchWrite
        val tables = state.tables.toMutableList()
        tables[index] = tables[index].copy(name = name.trim().ifEmpty { "课表${index + 1}" })
        repository.saveAllTables(tables)
        state = state.copy(tables = tables)
    }

    fun duplicateTable(index: Int, onError: (Throwable) -> Unit = {}) = launchWrite(onError) {
        val source = state.tables.getOrNull(index) ?: return@launchWrite
        val copy = source.copy(
            name = "${source.name} 副本",
            archived = false,
            archivedAt = null
        )
        val tables = state.tables + copy
        repository.saveAllTables(tables)
        state = state.copy(tables = tables)
    }

    fun setTableArchived(index: Int, archived: Boolean, onError: (Throwable) -> Unit = {}) = launchWrite(onError) {
        val target = state.tables.getOrNull(index) ?: return@launchWrite
        if (target.archived == archived) return@launchWrite
        if (archived && state.tables.count { !it.archived } <= 1) {
            throw IllegalStateException("至少需要保留一张使用中的课表")
        }
        val tables = state.tables.toMutableList()
        tables[index] = target.copy(
            archived = archived,
            archivedAt = if (archived) Instant.now().toString() else null
        )
        var active = state.activeTableIndex
        if (archived && active == index) active = tables.indexOfFirst { !it.archived }
        val preferences = state.preferences.copy(activeTableIndex = active)
        repository.saveAllTables(tables)
        preferencesManager.save(preferences)
        state = state.copy(tables = tables, preferences = preferences, activeTableIndex = active)
        locateToday()
    }

    fun refreshHistory(onError: (Throwable) -> Unit = {}) = viewModelScope.launch {
        runCatching { repository.listSnapshots() }
            .onSuccess { historySnapshots = it }
            .onFailure(onError)
    }

    fun previewHistory(id: String, onResult: (CourseSnapshotDiff) -> Unit, onError: (Throwable) -> Unit = {}) =
        viewModelScope.launch {
            runCatching { repository.previewSnapshot(id) }.onSuccess(onResult).onFailure(onError)
        }

    fun restoreHistory(id: String, onComplete: () -> Unit = {}, onError: (Throwable) -> Unit = {}) =
        launchWrite(onError, allowWhenReadOnly = true) {
            val tables = repository.restoreSnapshot(id)
            val active = state.activeTableIndex.coerceIn(tables.indices).takeIf { !tables[it].archived }
                ?: tables.indexOfFirst { !it.archived }.takeIf { it >= 0 } ?: 0
            val preferences = state.preferences.copy(activeTableIndex = active)
            preferencesManager.save(preferences)
            writeProtection.unlockAfterValidatedRestore()
            state = state.copy(
                tables = tables,
                preferences = preferences,
                activeTableIndex = active,
                currentWeek = TimeUtils.todayWeek(tables[active].semesterStart, tables[active].totalWeeks),
                persistentDataError = null
            )
            historySnapshots = repository.listSnapshots()
            onComplete()
        }

    fun backupSnapshot(): BackupData = BackupData(state.tables, state.preferences)

    fun restoreBackup(
        backup: BackupData,
        onComplete: () -> Unit,
        onError: (Throwable) -> Unit = {}
    ) = launchWrite(onError, allowWhenReadOnly = true) {
        val previous = state
        val wasReadOnly = writeProtection.isReadOnly
        val active = backup.preferences.activeTableIndex.coerceIn(backup.tables.indices)
        val preferences = backup.preferences.copy(activeTableIndex = active)
        try {
            repository.restoreValidatedTables(backup.tables)
            preferencesManager.save(preferences)
        } catch (error: Throwable) {
            // 两个文件任一写入失败时尽力回滚，避免出现“课表已换、偏好未换”的半恢复状态。
            // 数据损坏保护中绝不能用内存中的空占位课表覆盖磁盘；此时保留已恢复结果供重启读取。
            if (!wasReadOnly) {
                runCatching { repository.saveAllTables(previous.tables) }
                    .onFailure(error::addSuppressed)
                runCatching { preferencesManager.save(previous.preferences) }
                    .onFailure(error::addSuppressed)
            }
            throw error
        }
        val table = backup.tables[active]
        writeProtection.unlockAfterValidatedRestore()
        state = state.copy(
            tables = backup.tables,
            preferences = preferences,
            activeTableIndex = active,
            currentWeek = TimeUtils.todayWeek(table.semesterStart, table.totalWeeks),
            persistentDataError = null
        )
        onComplete()
    }

    /**
     * 修改并持久化指定课表的共用入口：变换 → 保存 → 返回更新后的列表。
     * 调用方必须在 launchWrite 的互斥区内调用。
     */
    private suspend fun mutateTable(index: Int, transform: (TableData) -> TableData): List<TableData>? {
        val updated = state.tables.toMutableList()
        if (index !in updated.indices) return null
        updated[index] = transform(updated[index])
        repository.saveAllTables(updated)
        return updated
    }

    private fun launchWrite(
        onError: (Throwable) -> Unit,
        allowWhenReadOnly: Boolean = false,
        block: suspend () -> Unit
    ) = viewModelScope.launch {
        runCatching {
            writeMutex.withLock {
                if (!allowWhenReadOnly) writeProtection.requireWritable()
                block()
            }
        }.onFailure(onError)
    }
}
