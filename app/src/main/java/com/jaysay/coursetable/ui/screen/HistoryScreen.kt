package com.jaysay.coursetable.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jaysay.coursetable.R
import com.jaysay.coursetable.data.history.CourseSnapshotDiff
import com.jaysay.coursetable.data.history.CourseSnapshotSummary
import com.jaysay.coursetable.ui.components.AppPanel
import com.jaysay.coursetable.ui.components.AppTopBar
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HistoryScreen(
    snapshots: List<CourseSnapshotSummary>,
    onRefresh: () -> Unit,
    onPreview: (String, (CourseSnapshotDiff) -> Unit) -> Unit,
    onRestore: (String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    var pending by remember { mutableStateOf<Pair<CourseSnapshotSummary, CourseSnapshotDiff>?>(null) }
    LaunchedEffect(Unit) { onRefresh() }

    pending?.let { (snapshot, diff) ->
        AlertDialog(
            onDismissRequest = { pending = null },
            icon = { Icon(Icons.Rounded.History, null) },
            title = { Text(stringResource(R.string.history_restore_title)) },
            text = {
                Text(stringResource(R.string.history_restore_summary, diff.addedCourses, diff.modifiedCourses, diff.deletedCourses))
            },
            confirmButton = {
                TextButton(onClick = { pending = null; onRestore(snapshot.id) }) { Text(stringResource(R.string.history_confirm_restore)) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(stringResource(R.string.history_cancel)) } }
        )
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.history_title),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.history_back)) } }
            )
        }
    ) { padding ->
        if (snapshots.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.history_empty_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { Text(stringResource(R.string.history_local_notice), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(snapshots, key = CourseSnapshotSummary::id) { snapshot ->
                    AppPanel {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(formatSnapshotTime(snapshot.createdAtMillis, stringResource(R.string.history_unknown_time)), style = MaterialTheme.typography.titleMedium)
                                Text(
                                    stringResource(R.string.history_course_summary, snapshot.tableCount, snapshot.courseCount),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = { onPreview(snapshot.id) { diff -> pending = snapshot to diff } }) {
                                Text(stringResource(R.string.history_preview_restore))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatSnapshotTime(millis: Long, fallback: String): String = runCatching {
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
}.getOrDefault(fallback)
