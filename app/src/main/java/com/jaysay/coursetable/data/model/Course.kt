
package com.jaysay.coursetable.data.model

import androidx.compose.runtime.Immutable

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
    val notes: String = ""         // 备注信息
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
    val periodSpan: Int
        get() = endPeriod - startPeriod + 1

    /**
     * 返回移除指定周次后的课程副本；若所有周次都被移除则返回 null。
     * 用于“仅本周”编辑/删除时拆分课程。
     */
    fun withoutWeeks(weeksToRemove: Set<Int>): Course? {
        val remaining = weeks.filterNot { it in weeksToRemove }
        return if (remaining.isEmpty()) null else copy(weeks = remaining)
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
        val result = existing.toMutableList()
        val indexByIdentity = result.mapIndexed { index, course -> course.importIdentityKey to index }
            .toMap().toMutableMap()
        var added = 0
        var merged = 0
        var skipped = 0

        imported.forEach { incoming ->
            val normalizedIncoming = incoming.copy(weeks = incoming.weeks.filter { it > 0 }.distinct().sorted())
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
