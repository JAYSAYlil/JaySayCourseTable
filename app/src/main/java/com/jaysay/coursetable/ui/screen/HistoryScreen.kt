package com.jaysay.coursetable.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
            icon = { Icon(Icons.Outlined.History, null) },
            title = { Text("恢复这个历史版本？") },
            text = {
                Text("恢复后将新增 ${diff.addedCourses} 条、修改 ${diff.modifiedCourses} 条、移除 ${diff.deletedCourses} 条课程。当前版本会先自动保存，可再次恢复。")
            },
            confirmButton = {
                TextButton(onClick = { pending = null; onRestore(snapshot.id) }) { Text("确认恢复") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("取消") } }
        )
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "历史版本",
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } }
            )
        }
    ) { padding ->
        if (snapshots.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                Text("修改课表后会自动保留最近 10 个历史版本", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { Text("历史记录只保存在本机应用私有目录中。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(snapshots, key = CourseSnapshotSummary::id) { snapshot ->
                    AppPanel {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(formatSnapshotTime(snapshot.createdAtMillis), style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${snapshot.tableCount} 张课表 · ${snapshot.courseCount} 条课程记录",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = { onPreview(snapshot.id) { diff -> pending = snapshot to diff } }) {
                                Text("查看并恢复")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatSnapshotTime(millis: Long): String = runCatching {
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
}.getOrDefault("未知时间")
