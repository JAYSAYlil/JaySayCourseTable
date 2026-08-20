package com.jaysay.coursetable.data.backup

import androidx.compose.runtime.Immutable
import com.jaysay.coursetable.data.model.Course
import com.jaysay.coursetable.data.repository.TableData

@Immutable
data class BackupDiff(
    val tablesAdded: Int,
    val tablesRemoved: Int,
    val coursesAdded: Int,
    val coursesRemoved: Int,
    val coursesChanged: Int
) {
    val hasChanges: Boolean
        get() = tablesAdded + tablesRemoved + coursesAdded + coursesRemoved + coursesChanged > 0

    companion object {
        fun between(current: List<TableData>, incoming: List<TableData>): BackupDiff {
            val currentByName = current.groupBy(TableData::name).mapValues { it.value.first() }
            val incomingByName = incoming.groupBy(TableData::name).mapValues { it.value.first() }
            val sharedNames = currentByName.keys intersect incomingByName.keys
            var added = incoming.filter { it.name !in currentByName }.sumOf { it.courses.size }
            var removed = current.filter { it.name !in incomingByName }.sumOf { it.courses.size }
            var changed = 0
            sharedNames.forEach { name ->
                val oldCourses = currentByName.getValue(name).courses.associateBy(Course::seriesKey)
                val newCourses = incomingByName.getValue(name).courses.associateBy(Course::seriesKey)
                added += newCourses.keys.count { it !in oldCourses }
                removed += oldCourses.keys.count { it !in newCourses }
                changed += (oldCourses.keys intersect newCourses.keys).count { oldCourses[it] != newCourses[it] }
            }
            return BackupDiff(
                tablesAdded = incomingByName.keys.count { it !in currentByName },
                tablesRemoved = currentByName.keys.count { it !in incomingByName },
                coursesAdded = added,
                coursesRemoved = removed,
                coursesChanged = changed
            )
        }
    }
}
