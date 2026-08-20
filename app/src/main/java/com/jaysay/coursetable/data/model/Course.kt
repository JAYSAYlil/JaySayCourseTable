
package com.jaysay.coursetable.data.model

import androidx.compose.runtime.Immutable
import java.nio.charset.StandardCharsets
import java.util.UUID

enum class CourseReminderMode { INHERIT, ENABLED, DISABLED }

@Immutable
data class Course(
    val courseId: String,
    val courseName: String,
    val classNumber: String,
    val department: String,
    val credits: Float,
    val weeks: List<Int>,
    val dayOfWeek: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val teacher: String,
    val classroom: String,
    val courseType: String,
    val courseCategory: String,
    val isOnline: Boolean,
    val assessmentMethod: String,
    val customColor: Int? = null,  // 自定义颜色ARGB值
    val notes: String = "",        // 备注信息
    /**
     * 同一次课在不同周次拆分后的稳定系列标识。
     * 不参与界面展示；旧数据缺失时会由 [CourseSeriesIds] 自动补齐。
     */
    val seriesId: String = "",
    /** 是否沿用全局提醒、单独开启或单独关闭。 */
    val reminderMode: CourseReminderMode = CourseReminderMode.INHERIT,
    /** 单课提前分钟数；null 表示沿用全局设置。 */
    val reminderMinutesOverride: Int? = null,
    /** 是否在课程结束时再发一条本地通知。 */
    val endReminderEnabled: Boolean = false
) {
    /** 不包含周次和用户自定义字段的稳定身份，用于重复导入时合并周次。 */
    val importIdentityKey: String
        get() = listOf(
            courseId.ifBlank { courseName.trim() },
            classNumber.trim(), dayOfWeek.toString(), startPeriod.toString(), endPeriod.toString(),
            teacher.trim(), classroom.trim()
        ).joinToString("|") { it.lowercase() }

    val uniqueKey: String
        get() = "$courseId-$classNumber-$dayOfWeek-$startPeriod-$endPeriod-${weeks.joinToString(",")}"

    /** 用于“应用到全部周”等跨拆分记录操作，不随周次和用户编辑变化。 */
    val seriesKey: String
        get() = seriesId.ifBlank { CourseSeriesIds.idForSeed(legacySeriesSlotKey) }

    internal val legacySeriesFamilyKey: String
        get() = listOf(courseId.ifBlank { courseName.trim() }, classNumber.trim(), courseName.trim())
            .joinToString("|") { it.lowercase() }

    internal val legacySeriesSlotKey: String
        get() = listOf(legacySeriesFamilyKey, dayOfWeek.toString(), startPeriod.toString(), endPeriod.toString())
            .joinToString("|")

    val periodSpan: Int
        get() = endPeriod - startPeriod + 1

    fun withStableSeriesId(): Course = if (seriesId.isNotBlank()) this else copy(seriesId = seriesKey)

    /**
     * 返回移除指定周次后的课程副本；若所有周次都被移除则返回 null。
     * 用于“仅本周”编辑/删除时拆分课程。
     */
    fun withoutWeeks(weeksToRemove: Set<Int>): Course? {
        val remaining = weeks.filterNot { it in weeksToRemove }
        return if (remaining.isEmpty()) null else copy(weeks = remaining)
    }
}

/** 兼容旧数据：给课程补齐不可读、稳定且不会包含课程隐私文本的系列 ID。 */
object CourseSeriesIds {
    private const val MAX_LEGACY_SPLIT_GROUP = 200

    internal fun idForSeed(seed: String): String = UUID.nameUUIDFromBytes(
        "jaysay-course-series|$seed".toByteArray(StandardCharsets.UTF_8)
    ).toString()

    fun ensure(courses: List<Course>): List<Course> {
        if (courses.none { it.seriesId.isBlank() }) return courses

        val legacyIndexes = courses.indices.filter { courses[it].seriesId.isBlank() }
        val parent = IntArray(courses.size) { it }

        fun find(value: Int): Int {
            var current = value
            while (parent[current] != current) {
                parent[current] = parent[parent[current]]
                current = parent[current]
            }
            return current
        }

        fun union(left: Int, right: Int) {
            val leftRoot = find(left)
            val rightRoot = find(right)
            if (leftRoot != rightRoot) parent[rightRoot] = leftRoot
        }

        // 常规旧数据按上课时段线性分组，避免大量课程迁移时做全表平方级比较。
        legacyIndexes.groupBy { courses[it].legacySeriesSlotKey }.values.forEach { indexes ->
            val anchor = indexes.first()
            indexes.drop(1).forEach { union(anchor, it) }
        }

        // 兼容“仅本周”把日期/节次也改掉的旧拆分。超大异常分组只做上面的精确迁移，
        // 防止恶意或损坏备份造成长时间卡顿。
        legacyIndexes.groupBy { index ->
            val course = courses[index]
            listOf(course.legacySeriesFamilyKey, course.teacher.trim(), course.classroom.trim())
                .joinToString("|") { it.lowercase() }
        }.values.filter { it.size <= MAX_LEGACY_SPLIT_GROUP }.forEach { indexes ->
            for (leftPosition in indexes.indices) {
                val leftIndex = indexes[leftPosition]
                val left = courses[leftIndex]
                for (rightPosition in leftPosition + 1 until indexes.size) {
                    val rightIndex = indexes[rightPosition]
                    val right = courses[rightIndex]
                    if (left.legacySeriesSlotKey != right.legacySeriesSlotKey &&
                        left.weeks.none(right.weeks::contains)
                    ) {
                        union(leftIndex, rightIndex)
                    }
                }
            }
        }

        val idByRoot = legacyIndexes.groupBy(::find).mapValues { (_, indexes) ->
            val seed = indexes.map { courses[it].legacySeriesSlotKey }.distinct().sorted().joinToString("||")
            idForSeed(seed)
        }
        return courses.mapIndexed { index, course ->
            if (course.seriesId.isBlank()) course.copy(seriesId = idByRoot.getValue(find(index))) else course
        }
    }
}

/** 集中实现跨周次编辑，避免界面层再次误用包含 weeks 的 uniqueKey。 */
object CourseSeriesOperations {
    fun deleteAll(courses: List<Course>, seriesKey: String): List<Course> =
        courses.filterNot { it.seriesKey == seriesKey }

    fun deleteWeek(courses: List<Course>, seriesKey: String, week: Int): List<Course> =
        courses.mapNotNull { course ->
            if (course.seriesKey == seriesKey) course.withoutWeeks(setOf(week)) else course
        }

    fun replaceAll(courses: List<Course>, seriesKey: String, replacement: Course): List<Course> {
        val firstIndex = courses.indexOfFirst { it.seriesKey == seriesKey }
        if (firstIndex < 0) return courses
        val result = courses.filterNot { it.seriesKey == seriesKey }.toMutableList()
        result.add(firstIndex.coerceAtMost(result.size), replacement.copy(seriesId = seriesKey))
        return result
    }

    fun replaceWeek(
        courses: List<Course>,
        seriesKey: String,
        week: Int,
        replacement: Course
    ): List<Course> {
        val firstIndex = courses.indexOfFirst { it.seriesKey == seriesKey }
        if (firstIndex < 0) return courses
        val result = deleteWeek(courses, seriesKey, week).toMutableList()
        result.add(
            firstIndex.coerceAtMost(result.size),
            replacement.copy(weeks = listOf(week), seriesId = seriesKey)
        )
        return result
    }
}

/**
 * 删除操作的定向撤销快照。只还原被操作的课程系列，并保留撤销前新增或修改的其他课程。
 * 若同一系列在删除后又被用户改动，则拒绝覆盖较新的结果。
 */
data class CourseSeriesUndo private constructor(
    private val seriesKey: String,
    private val beforeSeries: List<Course>,
    private val afterSeries: List<Course>,
    private val originalIndex: Int
) {
    fun restore(current: List<Course>): List<Course> {
        val currentSeries = current.filter { it.seriesKey == seriesKey }
        if (currentSeries != afterSeries) return current
        val result = current.filterNot { it.seriesKey == seriesKey }.toMutableList()
        result.addAll(originalIndex.coerceIn(0, result.size), beforeSeries)
        return result
    }

    companion object {
        fun capture(before: List<Course>, after: List<Course>, seriesKey: String) = CourseSeriesUndo(
            seriesKey = seriesKey,
            beforeSeries = before.filter { it.seriesKey == seriesKey },
            afterSeries = after.filter { it.seriesKey == seriesKey },
            originalIndex = before.indexOfFirst { it.seriesKey == seriesKey }.coerceAtLeast(0)
        )
    }
}

data class ImportMergeResult(
    val courses: List<Course>,
    val added: Int,
    val merged: Int,
    val skipped: Int
)

object CourseMerger {
    /**
     * 保留已有课程的备注、颜色等用户编辑，仅将新出现的周次合并进去。
     * 相同课程再次导入不会产生重复卡片。
     */
    fun mergeImported(existing: List<Course>, imported: List<Course>): ImportMergeResult {
        val result = CourseSeriesIds.ensure(existing).toMutableList()
        val indexByIdentity = result.mapIndexed { index, course -> course.importIdentityKey to index }
            .toMap().toMutableMap()
        var added = 0
        var merged = 0
        var skipped = 0

        imported.forEach { incoming ->
            val normalizedIncoming = incoming.copy(
                weeks = incoming.weeks.filter { it > 0 }.distinct().sorted()
            ).withStableSeriesId()
            val existingIndex = indexByIdentity[normalizedIncoming.importIdentityKey]
            if (existingIndex == null) {
                indexByIdentity[normalizedIncoming.importIdentityKey] = result.size
                result.add(normalizedIncoming)
                added++
            } else {
                val current = result[existingIndex]
                val mergedWeeks = (current.weeks + normalizedIncoming.weeks).filter { it > 0 }.distinct().sorted()
                if (mergedWeeks == current.weeks.distinct().sorted()) {
                    skipped++
                } else {
                    result[existingIndex] = current.copy(weeks = mergedWeeks)
                    merged++
                }
            }
        }

        return ImportMergeResult(result, added, merged, skipped)
    }
}
