package com.jaysay.coursetable

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jaysay.coursetable.data.backup.BackupData
import com.jaysay.coursetable.data.model.Course
import com.jaysay.coursetable.data.model.CourseMerger
import com.jaysay.coursetable.data.model.ImportMergeResult
import com.jaysay.coursetable.data.model.ScheduleViewMode
import com.jaysay.coursetable.data.preferences.AppPreferences
import com.jaysay.coursetable.data.preferences.PreferencesManager
import com.jaysay.coursetable.data.repository.CourseRepository
import com.jaysay.coursetable.data.repository.TableData
import com.jaysay.coursetable.util.TimeUtils
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Immutable
data class MainUiState(
    val tables: List<TableData> = listOf(TableData.placeholder()),
    val preferences: AppPreferences = AppPreferences(),
    val activeTableIndex: Int = 0,
    val currentWeek: Int = 1,
    val isLoading: Boolean = true
) {
    val activeTable: TableData get() = tables.getOrElse(activeTableIndex) { TableData.placeholder() }
    val courses: List<Course> get() = activeTable.courses
}

/**
 * 统一管理课表状态和串行化磁盘写入，避免界面重建丢状态或连续操作互相覆盖。
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = CourseRepository(application)
    private val preferencesManager = PreferencesManager(application)
    private val writeMutex = Mutex()

    var state by mutableStateOf(MainUiState())
        private set

    init {
        viewModelScope.launch {
            runCatching {
                val preferences = preferencesManager.load()
                val tables = repository.loadAllTables()
                val active = preferences.activeTableIndex.coerceIn(tables.indices)
                MainUiState(
                    tables = tables,
                    preferences = preferences.copy(activeTableIndex = active),
                    activeTableIndex = active,
                    currentWeek = TimeUtils.todayWeek(tables[active].semesterStart, tables[active].totalWeeks),
                    isLoading = false
                )
            }.onSuccess { state = it }
                .onFailure { state = MainUiState(isLoading = false) }
        }
    }

    fun setWeek(week: Int) {
        state = state.copy(currentWeek = week.coerceIn(1, state.activeTable.totalWeeks))
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
        val updated = state.tables.toMutableList()
        if (targetIndex !in updated.indices) return@launchWrite
        val current = updated[targetIndex]
        // 设置页只负责学期和节次配置；保留可能在排队写入期间变化的课程、名称和视图状态。
        val safeTable = table.copy(
            name = current.name,
            courses = current.courses,
            viewMode = current.viewMode
        )
        updated[targetIndex] = safeTable
        repository.saveAllTables(updated)
        state = if (state.activeTableIndex == targetIndex) {
            state.copy(tables = updated, currentWeek = state.currentWeek.coerceIn(1, safeTable.totalWeeks))
        } else {
            state.copy(tables = updated)
        }
        }
    }

    fun setScheduleViewMode(mode: ScheduleViewMode, onError: (Throwable) -> Unit = {}) {
        val targetIndex = state.activeTableIndex
        launchWrite(onError) {
            val updatedTables = state.tables.toMutableList()
            if (targetIndex !in updatedTables.indices) return@launchWrite
            val current = updatedTables[targetIndex]
            if (current.viewMode == mode) return@launchWrite
            updatedTables[targetIndex] = current.copy(viewMode = mode)
            repository.saveAllTables(updatedTables)
            state = state.copy(tables = updatedTables)
        }
    }

    fun updateCourses(
        transform: (List<Course>) -> List<Course>,
        onComplete: () -> Unit = {},
        onError: (Throwable) -> Unit = {}
    ) {
        val targetIndex = state.activeTableIndex
        launchWrite(onError) {
        val updatedTables = state.tables.toMutableList()
        if (targetIndex !in updatedTables.indices) return@launchWrite
        val table = updatedTables[targetIndex]
        updatedTables[targetIndex] = table.copy(courses = transform(table.courses))
        repository.saveAllTables(updatedTables)
        state = state.copy(tables = updatedTables)
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
        val updatedTables = state.tables.toMutableList()
        if (targetIndex !in updatedTables.indices) return@launchWrite
        val table = updatedTables[targetIndex]
        val result = CourseMerger.mergeImported(table.courses, imported)
        updatedTables[targetIndex] = table.copy(courses = result.courses)
        repository.saveAllTables(updatedTables)
        state = state.copy(tables = updatedTables)
        onComplete(result)
        }
    }

    fun selectTable(index: Int, onError: (Throwable) -> Unit = {}) = launchWrite(onError) {
        if (index !in state.tables.indices) return@launchWrite
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
        val tables = state.tables.toMutableList().also { it.removeAt(index) }
        val active = when {
            index < state.activeTableIndex -> state.activeTableIndex - 1
            state.activeTableIndex >= tables.size -> tables.lastIndex
            else -> state.activeTableIndex
        }.coerceAtLeast(0)
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

    fun backupSnapshot(): BackupData = BackupData(state.tables, state.preferences)

    fun restoreBackup(
        backup: BackupData,
        onComplete: () -> Unit,
        onError: (Throwable) -> Unit = {}
    ) = launchWrite(onError) {
        val previous = state
        val active = backup.preferences.activeTableIndex.coerceIn(backup.tables.indices)
        val preferences = backup.preferences.copy(activeTableIndex = active)
        try {
            repository.saveAllTables(backup.tables)
            preferencesManager.save(preferences)
        } catch (error: Throwable) {
            // 两个文件任一写入失败时尽力回滚，避免出现“课表已换、偏好未换”的半恢复状态。
            runCatching { repository.saveAllTables(previous.tables) }
                .onFailure(error::addSuppressed)
            runCatching { preferencesManager.save(previous.preferences) }
                .onFailure(error::addSuppressed)
            throw error
        }
        val table = backup.tables[active]
        state = state.copy(
            tables = backup.tables,
            preferences = preferences,
            activeTableIndex = active,
            currentWeek = TimeUtils.todayWeek(table.semesterStart, table.totalWeeks)
        )
        onComplete()
    }

    private fun launchWrite(
        onError: (Throwable) -> Unit,
        block: suspend () -> Unit
    ) = viewModelScope.launch {
        runCatching { writeMutex.withLock { block() } }.onFailure(onError)
    }
}
