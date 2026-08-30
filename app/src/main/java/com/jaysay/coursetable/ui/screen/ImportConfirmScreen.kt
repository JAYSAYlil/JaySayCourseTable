package com.jaysay.coursetable.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jaysay.coursetable.R
import com.jaysay.coursetable.data.model.ConflictSource
import com.jaysay.coursetable.data.model.Course
import com.jaysay.coursetable.data.model.ImportItemStatus
import com.jaysay.coursetable.data.model.ImportPreview
import com.jaysay.coursetable.data.model.ImportPreviewItem
import com.jaysay.coursetable.ui.components.AppPanel
import com.jaysay.coursetable.ui.components.AppTopBar
import com.jaysay.coursetable.ui.theme.AppShapes
import com.jaysay.coursetable.util.TimeUtils

private enum class PreviewFilter(val labelRes: Int, val status: ImportItemStatus?) {
    ALL(R.string.import_filter_all, null),
    NEW(R.string.import_filter_new, ImportItemStatus.NEW),
    MERGE(R.string.import_filter_merge, ImportItemStatus.MERGE),
    CONFLICT(R.string.import_filter_conflict, ImportItemStatus.CONFLICT),
    DUPLICATE(R.string.import_filter_duplicate, ImportItemStatus.DUPLICATE)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportConfirmScreen(
    preview: ImportPreview,
    warnings: List<String>,
    onConfirm: (List<Course>) -> Unit,
    onCancel: () -> Unit
) {
    var selectedIndices by remember(preview) {
        mutableStateOf(preview.items.filter(ImportPreviewItem::selectedByDefault).map { it.index }.toSet())
    }
    var filter by remember { mutableStateOf(PreviewFilter.ALL) }
    val visibleItems = remember(preview, filter) {
        preview.items.filter { filter.status == null || it.status == filter.status }
    }
    val selectedCourses = remember(preview, selectedIndices) {
        preview.items.filter { it.index in selectedIndices }.map { it.course }
    }

    Scaffold(
        modifier = Modifier.testTag("import-confirm-screen"),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.import_confirm_title),
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, stringResource(R.string.import_cancel))
                    }
                },
                actions = {
                    TextButton(
                        modifier = Modifier.testTag("import-confirm-action"),
                        onClick = { onConfirm(selectedCourses) },
                        enabled = selectedCourses.isNotEmpty()
                    ) {
                        Text(
                            stringResource(R.string.import_import_count, selectedCourses.size),
                            color = if (selectedCourses.isNotEmpty()) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            )
        }
    ) { pad ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(pad).testTag("import-preview-list")) {
            item {
                PreviewSummary(
                    preview = preview,
                    warnings = warnings,
                    selectedCount = selectedCourses.size,
                    onSelectSafe = {
                        selectedIndices = preview.items.filter(ImportPreviewItem::selectedByDefault)
                            .map { it.index }.toSet()
                    },
                    onClear = { selectedIndices = emptySet() }
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .testTag("import-filter-row")
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PreviewFilter.entries.forEach { option ->
                        val count = option.status?.let(preview::count) ?: preview.items.size
                        FilterChip(
                            modifier = Modifier.testTag("import-filter-" + option.name.lowercase()),
                            selected = filter == option,
                            onClick = { filter = option },
                            label = { Text(stringResource(option.labelRes, count)) }
                        )
                    }
                }
            }
            if (warnings.isNotEmpty()) {
                item { WarningPanel(warnings) }
            }
            items(visibleItems, key = ImportPreviewItem::index) { item ->
                val selectable = item.status != ImportItemStatus.DUPLICATE
                val isSelected = item.index in selectedIndices
                ImportCourseItem(
                    item = item,
                    selected = isSelected,
                    selectable = selectable,
                    onSelectedChange = { checked ->
                        selectedIndices = if (checked) selectedIndices + item.index else selectedIndices - item.index
                    }
                )
            }
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun PreviewSummary(
    preview: ImportPreview,
    warnings: List<String>,
    selectedCount: Int,
    onSelectSafe: () -> Unit,
    onClear: () -> Unit
) {
    AppPanel(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                stringResource(R.string.import_summary_total, preview.items.size, selectedCount),
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(
                    R.string.import_summary_breakdown,
                    preview.count(ImportItemStatus.NEW),
                    preview.count(ImportItemStatus.MERGE),
                    preview.count(ImportItemStatus.CONFLICT),
                    preview.count(ImportItemStatus.DUPLICATE)
                ) + if (warnings.isNotEmpty()) stringResource(R.string.import_summary_warnings, warnings.size) else "",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(modifier = Modifier.testTag("import-safe-select"), onClick = onSelectSafe) {
                    Text(stringResource(R.string.import_select_safe))
                }
                TextButton(onClick = onClear) { Text(stringResource(R.string.import_clear)) }
            }
        }
    }
}

@Composable
private fun WarningPanel(warnings: List<String>) {
    AppPanel(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp).testTag("import-warning-panel")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                stringResource(R.string.import_warning_count, warnings.size),
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold
            )
            warnings.take(5).forEach { warning ->
                Text(stringResource(R.string.import_warning_item, warning), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (warnings.size > 5) {
                Text(stringResource(R.string.import_warning_more, warnings.size - 5), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ImportCourseItem(
    item: ImportPreviewItem,
    selected: Boolean,
    selectable: Boolean,
    onSelectedChange: (Boolean) -> Unit
) {
    AppPanel(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)
            .testTag("import-item-" + item.index)
            .clickable(enabled = selectable) { onSelectedChange(!selected) },
        selected = selected
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Checkbox(
                checked = selected,
                enabled = selectable,
                onCheckedChange = { onSelectedChange(it) }
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.course.courseName, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.width(8.dp))
                    StatusBadge(item.status)
                }
                Text(
                    stringResource(
                        R.string.import_item_period_info,
                        TimeUtils.getDayName(item.course.dayOfWeek),
                        item.course.startPeriod,
                        item.course.endPeriod,
                        compactWeeks(item.course.weeks),
                        if (item.course.teacher.isNotBlank()) "\n" + item.course.teacher else "",
                        if (item.course.classroom.isNotBlank()) " · " + item.course.classroom else ""
                    ),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (item.status == ImportItemStatus.CONFLICT) {
                    Spacer(Modifier.height(5.dp))
                    item.conflicts.take(3).forEach { conflict ->
                        val source = if (conflict.source == ConflictSource.EXISTING) {
                            stringResource(R.string.import_conflict_source_existing)
                        } else {
                            stringResource(R.string.import_conflict_source_imported)
                        }
                        Text(
                            stringResource(
                                R.string.import_conflict_detail,
                                source,
                                conflict.otherCourseName,
                                compactWeeks(conflict.overlappingWeeks),
                                conflict.startPeriod,
                                conflict.endPeriod
                            ),
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (item.conflictCount > item.conflicts.take(3).size) {
                        Text(
                            stringResource(R.string.import_conflict_more, item.conflictCount - item.conflicts.take(3).size),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: ImportItemStatus) {
    val (labelRes, color) = when (status) {
        ImportItemStatus.NEW -> R.string.import_status_new to MaterialTheme.colorScheme.primary
        ImportItemStatus.MERGE -> R.string.import_status_merge to MaterialTheme.colorScheme.tertiary
        ImportItemStatus.DUPLICATE -> R.string.import_status_duplicate to MaterialTheme.colorScheme.onSurfaceVariant
        ImportItemStatus.CONFLICT -> R.string.import_status_conflict to MaterialTheme.colorScheme.error
    }
    Surface(color = color.copy(alpha = 0.12f), shape = AppShapes.small) {
        Text(
            stringResource(labelRes),
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            color = color,
            fontSize = 11.sp
        )
    }
}

private fun compactWeeks(weeks: List<Int>): String {
    val sorted = weeks.distinct().sorted()
    if (sorted.isEmpty()) return "-"
    val parts = mutableListOf<String>()
    var start = sorted.first()
    var previous = start
    sorted.drop(1).forEach { week ->
        if (week == previous + 1) {
            previous = week
        } else {
            parts += if (start == previous) "$start" else "$start-$previous"
            start = week
            previous = week
        }
    }
    parts += if (start == previous) "$start" else "$start-$previous"
    return parts.joinToString("、")
}
