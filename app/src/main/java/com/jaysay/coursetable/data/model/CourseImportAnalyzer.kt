package com.jaysay.coursetable.data.model

import androidx.compose.runtime.Immutable

enum class ImportItemStatus {
    NEW,
    MERGE,
    DUPLICATE,
    CONFLICT
}

enum class ConflictSource {
    EXISTING,
    IMPORTED
}

@Immutable
data class CourseConflict(
    val otherCourseName: String,
    val source: ConflictSource,
    val overlappingWeeks: List<Int>,
    val startPeriod: Int,
    val endPeriod: Int
)

@Immutable
data class ImportPreviewItem(
    val index: Int,
    val course: Course,
    val status: ImportItemStatus,
    val conflicts: List<CourseConflict> = emptyList(),
    val conflictCount: Int = conflicts.size
) {
    val selectedByDefault: Boolean
        get() = status == ImportItemStatus.NEW || status == ImportItemStatus.MERGE
}

@Immutable
data class ImportPreview(val items: List<ImportPreviewItem>) {
    fun count(status: ImportItemStatus): Int = items.count { it.status == status }
}

/**
 * 在写入前分析重复、可合并项与时间冲突。
 *
 * 索引以“星期 + 节次”为键，再精确检查周次交集，避免大课表做全量两两比较。
 */
object CourseImportAnalyzer {
    fun analyze(existing: List<Course>, imported: List<Course>): ImportPreview {
        val normalized = imported.map { course ->
            course.copy(weeks = course.weeks.filter { it > 0 }.distinct().sorted())
        }
        val knownWeeksByIdentity = existing.groupBy(Course::importIdentityKey)
            .mapValuesTo(mutableMapOf()) { (_, courses) ->
                courses.flatMap(Course::weeks).filter { it > 0 }.toMutableSet()
            }
        val preliminary = ArrayList<ImportItemStatus>(normalized.size)
        val effective = ArrayList<Course?>(normalized.size)

        normalized.forEach { incoming ->
            val knownWeeks = knownWeeksByIdentity[incoming.importIdentityKey]
            val newWeeks = incoming.weeks.filterNot { week -> knownWeeks?.contains(week) == true }
            val status = when {
                newWeeks.isEmpty() -> ImportItemStatus.DUPLICATE
                knownWeeks != null -> ImportItemStatus.MERGE
                else -> ImportItemStatus.NEW
            }
            preliminary += status
            effective += incoming.takeIf { newWeeks.isNotEmpty() }?.copy(weeks = newWeeks)
            knownWeeksByIdentity.getOrPut(incoming.importIdentityKey) { mutableSetOf() }
                .addAll(incoming.weeks)
        }

        val conflictBuckets = List(normalized.size) { ConflictBucket() }
        val existingBySlot = buildSlotIndex(existing)

        effective.forEachIndexed { index, candidate ->
            if (candidate == null) return@forEachIndexed
            candidate.periods().flatMapTo(linkedSetOf()) { period ->
                existingBySlot[slotKey(candidate.dayOfWeek, period)].orEmpty()
            }.forEach { existingIndex ->
                val other = existing[existingIndex]
                if (other.importIdentityKey == candidate.importIdentityKey) return@forEach
                buildConflict(candidate, other, ConflictSource.EXISTING)?.let { conflict ->
                    conflictBuckets[index].add("existing:$existingIndex", conflict)
                }
            }
        }

        val priorImportedBySlot = mutableMapOf<Int, MutableSet<Int>>()
        effective.forEachIndexed { index, candidate ->
            if (candidate == null) return@forEachIndexed
            val possiblePrior = candidate.periods().flatMapTo(linkedSetOf()) { period ->
                priorImportedBySlot[slotKey(candidate.dayOfWeek, period)].orEmpty()
            }
            possiblePrior.forEach { priorIndex ->
                val other = effective[priorIndex] ?: return@forEach
                if (other.importIdentityKey == candidate.importIdentityKey) return@forEach
                buildConflict(candidate, other, ConflictSource.IMPORTED)?.let { currentConflict ->
                    conflictBuckets[index].add("imported:$priorIndex", currentConflict)
                    buildConflict(other, candidate, ConflictSource.IMPORTED)?.let { priorConflict ->
                        conflictBuckets[priorIndex].add("imported:$index", priorConflict)
                    }
                }
            }
            candidate.periods().forEach { period ->
                priorImportedBySlot.getOrPut(slotKey(candidate.dayOfWeek, period)) { linkedSetOf() }
                    .add(index)
            }
        }

        return ImportPreview(normalized.mapIndexed { index, course ->
            val bucket = conflictBuckets[index]
            ImportPreviewItem(
                index = index,
                course = course,
                status = if (bucket.count > 0) ImportItemStatus.CONFLICT else preliminary[index],
                conflicts = bucket.details,
                conflictCount = bucket.count
            )
        })
    }

    /**
     * 手工新增/编辑时的单候选冲突检查：直接线性扫描，避免为单个候选
     * 重建整个星期/节次槽位索引（O(课程数) 而不是 O(课程数 × 节次数)）。
     */
    fun findConflicts(
        existing: List<Course>,
        candidate: Course,
        excludedUniqueKeys: Set<String> = emptySet()
    ): List<CourseConflict> {
        val result = mutableListOf<CourseConflict>()
        existing.forEach { other ->
            if (other.importIdentityKey == candidate.importIdentityKey) return@forEach
            if (other.uniqueKey in excludedUniqueKeys) return@forEach
            buildConflict(candidate, other, ConflictSource.EXISTING)?.let(result::add)
        }
        return result
    }

    /**
     * 分析当前课表内部所有课程两两冲突（数据诊断用）。
     * 使用星期/节次槽位索引，仅对同槽课程做周次交集检查，避免全表平方级比较。
     */
    fun findConflictsAmong(courses: List<Course>): List<CourseConflict> {
        val bySlot = buildSlotIndex(courses)
        val result = mutableListOf<CourseConflict>()
        val seen = mutableSetOf<String>()
        bySlot.forEach { (_, indexes) ->
            for (i in indexes.indices) {
                for (j in i + 1 until indexes.size) {
                    val first = indexes[i]
                    val second = indexes[j]
                    val a = courses[first]
                    val b = courses[second]
                    if (a.importIdentityKey == b.importIdentityKey) continue
                    buildConflict(a, b, ConflictSource.EXISTING)?.let { conflict ->
                        val key = if (first < second) "$first|$second" else "$second|$first"
                        if (seen.add(key)) result += conflict
                    }
                }
            }
        }
        return result
    }

    private fun buildSlotIndex(courses: List<Course>): Map<Int, List<Int>> {
        val index = mutableMapOf<Int, MutableList<Int>>()
        courses.forEachIndexed { courseIndex, course ->
            course.periods().forEach { period ->
                index.getOrPut(slotKey(course.dayOfWeek, period)) { mutableListOf() }
                    .add(courseIndex)
            }
        }
        return index
    }

    private fun buildConflict(
        candidate: Course,
        other: Course,
        source: ConflictSource
    ): CourseConflict? {
        if (candidate.dayOfWeek != other.dayOfWeek) return null
        val start = maxOf(candidate.startPeriod, other.startPeriod)
        val end = minOf(candidate.endPeriod, other.endPeriod)
        if (start > end) return null
        val overlapWeeks = candidate.weeks.toSet().intersect(other.weeks.toSet()).sorted()
        if (overlapWeeks.isEmpty()) return null
        return CourseConflict(
            otherCourseName = other.courseName.ifBlank { "未命名课程" },
            source = source,
            overlappingWeeks = overlapWeeks,
            startPeriod = start,
            endPeriod = end
        )
    }

    private fun Course.periods(): IntRange = startPeriod..endPeriod

    private fun slotKey(dayOfWeek: Int, period: Int): Int = dayOfWeek * 100 + period

    private class ConflictBucket {
        private val keys = linkedSetOf<String>()
        private val mutableDetails = mutableListOf<CourseConflict>()
        val count: Int get() = keys.size
        val details: List<CourseConflict> get() = mutableDetails

        fun add(key: String, conflict: CourseConflict) {
            if (!keys.add(key)) return
            if (mutableDetails.size < MAX_CONFLICT_DETAILS) mutableDetails += conflict
        }
    }

    private const val MAX_CONFLICT_DETAILS = 8
}
