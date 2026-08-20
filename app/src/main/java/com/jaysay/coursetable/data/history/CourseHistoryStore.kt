package com.jaysay.coursetable.data.history

import com.jaysay.coursetable.data.model.Course
import com.jaysay.coursetable.data.repository.TableData
import com.jaysay.coursetable.data.storage.AtomicFileStore
import java.io.File
import java.util.UUID

/** 一份可恢复课表快照的轻量摘要，不暴露课程正文。 */
data class CourseSnapshotSummary(
    val id: String,
    val createdAtMillis: Long,
    val tableCount: Int,
    val courseCount: Int
)

/** 按“从当前状态恢复到目标快照”计算的课程变化数量。 */
data class CourseSnapshotDiff(
    val addedCourses: Int,
    val modifiedCourses: Int,
    val deletedCourses: Int
)

data class CourseSnapshot(
    val summary: CourseSnapshotSummary,
    val tables: List<TableData>
)

/**
 * 应用私有目录中的课表历史存储。
 *
 * 每份快照仍是 CourseRepository 可读取的完整 JSON 文档，并通过 [AtomicFileStore]
 * 原子落盘。文件名只保存时间和随机标识，不包含课程、教师或教室等隐私文本。
 */
class CourseHistoryStore(
    private val directory: File,
    private val maxSnapshots: Int = DEFAULT_MAX_SNAPSHOTS,
    private val clock: () -> Long = System::currentTimeMillis,
    private val suffixFactory: () -> String = {
        UUID.randomUUID().toString().replace("-", "").take(ID_SUFFIX_LENGTH)
    }
) {
    private val lock = Any()

    init {
        require(maxSnapshots > 0) { "快照保留数量必须大于 0" }
    }

    fun createSnapshot(content: String, tables: List<TableData>): CourseSnapshotSummary =
        synchronized(lock) {
            require(content.isNotBlank()) { "快照内容不能为空" }
            require(tables.isNotEmpty()) { "快照至少应包含一张课表" }
            ensureDirectory()

            // 上一次主文件写入若失败，重试时不重复保存相同旧状态。
            newestFile()?.takeIf { runCatching { it.readText(Charsets.UTF_8) == content }.getOrDefault(false) }
                ?.let { return@synchronized summary(it, tables) }

            val createdAt = clock()
            val file = uniqueSnapshotFile(createdAt)
            AtomicFileStore(file).write(content)
            try {
                pruneToLimit()
            } catch (error: Exception) {
                // 保留原有历史集合；新快照无法纳入保留策略时回滚本次新增。
                deleteSnapshotArtifacts(file)
                throw error
            }
            summary(file, tables)
        }

    fun listSnapshots(decode: (String) -> List<TableData>): List<CourseSnapshotSummary> =
        synchronized(lock) {
            snapshotFilesNewestFirst().mapNotNull { file ->
                runCatching {
                    val tables = requireNotNull(AtomicFileStore(file).read(decode))
                    summary(file, tables)
                }.getOrNull()
            }
        }

    fun loadSnapshot(id: String, decode: (String) -> List<TableData>): CourseSnapshot =
        synchronized(lock) {
            val file = resolveSnapshotFile(id)
            require(file.isFile) { "课表快照不存在" }
            val tables = requireNotNull(AtomicFileStore(file).read(decode)) { "课表快照不存在" }
            CourseSnapshot(summary(file, tables), tables)
        }

    private fun ensureDirectory() {
        if (!directory.exists() && !directory.mkdirs()) {
            throw IllegalStateException("无法创建课表历史目录")
        }
        require(directory.isDirectory) { "课表历史路径不是目录" }
    }

    private fun uniqueSnapshotFile(createdAt: Long): File {
        repeat(MAX_ID_ATTEMPTS) {
            val suffix = suffixFactory().lowercase().filter { it in '0'..'9' || it in 'a'..'f' }
                .take(ID_SUFFIX_LENGTH)
                .padEnd(ID_SUFFIX_LENGTH, '0')
            val candidate = File(directory, "$FILE_PREFIX$createdAt-$suffix$FILE_SUFFIX")
            if (!candidate.exists()) return candidate
        }
        throw IllegalStateException("无法生成唯一的课表快照标识")
    }

    private fun newestFile(): File? = snapshotFilesNewestFirst().firstOrNull()

    private fun pruneToLimit() {
        snapshotFilesNewestFirst().drop(maxSnapshots).forEach { file ->
            if (!deleteSnapshotArtifacts(file)) {
                throw IllegalStateException("无法清理过期课表快照")
            }
        }
    }

    private fun deleteSnapshotArtifacts(file: File): Boolean {
        val candidates = listOf(
            file,
            File(file.parentFile, "${file.name}.bak"),
            File(file.parentFile, "${file.name}.tmp"),
            File(file.parentFile, "${file.name}.bak.tmp")
        )
        return candidates.all { !it.exists() || it.delete() }
    }

    private fun snapshotFilesNewestFirst(): List<File> =
        directory.listFiles { file -> file.isFile && SNAPSHOT_FILE.matches(file.name) }
            .orEmpty()
            .sortedWith(compareByDescending<File>(::createdAtFrom).thenByDescending(File::getName))

    private fun resolveSnapshotFile(id: String): File {
        require(SNAPSHOT_ID.matches(id)) { "课表快照标识无效" }
        return File(directory, "$FILE_PREFIX$id$FILE_SUFFIX")
    }

    private fun summary(file: File, tables: List<TableData>) = CourseSnapshotSummary(
        id = file.name.removePrefix(FILE_PREFIX).removeSuffix(FILE_SUFFIX),
        createdAtMillis = createdAtFrom(file),
        tableCount = tables.size,
        courseCount = tables.sumOf { it.courses.size }
    )

    private fun createdAtFrom(file: File): Long =
        SNAPSHOT_FILE.matchEntire(file.name)?.groupValues?.get(1)?.toLongOrNull() ?: 0L

    companion object {
        const val DEFAULT_MAX_SNAPSHOTS = 10
        private const val FILE_PREFIX = "snapshot-"
        private const val FILE_SUFFIX = ".json"
        private const val ID_SUFFIX_LENGTH = 8
        private const val MAX_ID_ATTEMPTS = 20
        private val SNAPSHOT_ID = Regex("^[0-9]{1,19}-[0-9a-f]{8}$")
        private val SNAPSHOT_FILE = Regex("^snapshot-([0-9]{1,19})-[0-9a-f]{8}\\.json$")
    }
}

/** 课程记录级差异；课表顺序和课程 seriesKey 共同定义记录所属位置。 */
object CourseSnapshotDiffer {
    fun compare(current: List<TableData>, target: List<TableData>): CourseSnapshotDiff {
        val currentGroups = groupCourses(current)
        val targetGroups = groupCourses(target)
        var added = 0
        var modified = 0
        var deleted = 0

        (currentGroups.keys + targetGroups.keys).forEach { key ->
            val currentCourses = currentGroups[key].orEmpty().toMutableList()
            val targetCourses = targetGroups[key].orEmpty().toMutableList()

            // 先消去完全相同的记录，避免同一系列拆分成多条时产生虚假的“修改”。
            var currentIndex = currentCourses.lastIndex
            while (currentIndex >= 0) {
                val exactTargetIndex = targetCourses.indexOf(currentCourses[currentIndex])
                if (exactTargetIndex >= 0) {
                    currentCourses.removeAt(currentIndex)
                    targetCourses.removeAt(exactTargetIndex)
                }
                currentIndex--
            }

            val pairedChanges = minOf(currentCourses.size, targetCourses.size)
            modified += pairedChanges
            deleted += currentCourses.size - pairedChanges
            added += targetCourses.size - pairedChanges
        }
        return CourseSnapshotDiff(added, modified, deleted)
    }

    private fun groupCourses(tables: List<TableData>): Map<CourseLocation, List<Course>> =
        tables.flatMapIndexed { tableIndex, table ->
            table.courses.map { course -> CourseLocation(tableIndex, course.seriesKey) to course }
        }.groupBy({ it.first }, { it.second })

    private data class CourseLocation(val tableIndex: Int, val seriesKey: String)
}
