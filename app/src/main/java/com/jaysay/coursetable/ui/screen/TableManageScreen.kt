package com.jaysay.coursetable.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jaysay.coursetable.R
import com.jaysay.coursetable.data.repository.TableData
import com.jaysay.coursetable.ui.components.AppPanel
import com.jaysay.coursetable.ui.components.AppTopBar
import com.jaysay.coursetable.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TableManageScreen(
    tables: List<TableData>,
    activeIndex: Int,
    onSelect: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onAdd: () -> Unit,
    onRename: (Int, String) -> Unit,
    onDuplicate: (Int) -> Unit,
    onArchive: (Int, Boolean) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    var pendingDeleteIndex by remember { mutableStateOf<Int?>(null) }

    pendingDeleteIndex?.let { index ->
        val table = tables.getOrNull(index)
        if (table != null) {
            AlertDialog(
                onDismissRequest = { pendingDeleteIndex = null },
                icon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                title = { Text(stringResource(R.string.table_delete_dialog_title)) },
                text = { Text(stringResource(R.string.table_delete_dialog_message, table.name, table.courses.size)) },
                confirmButton = {
                    TextButton(onClick = {
                        pendingDeleteIndex = null
                        onDelete(index)
                    }) { Text(stringResource(R.string.table_delete_confirm), color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeleteIndex = null }) { Text(stringResource(R.string.table_cancel)) }
                }
            )
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.table_manage_title),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.table_nav_back)) } }
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())
        ) {
            tables.forEachIndexed { idx, table ->
                // key(idx) 让每行的编辑状态与课表身份绑定，删除/插入行时状态不会串位
                key(idx) {
                    var editing by remember { mutableStateOf(false) }
                    var editName by remember { mutableStateOf(table.name) }

                    AppPanel(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
                        selected = idx == activeIndex
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable(enabled = !table.archived) { onSelect(idx) }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (idx == activeIndex) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                                null,
                                tint = if (idx == activeIndex) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(12.dp))

                            if (editing) {
                                OutlinedTextField(
                                    value = editName, onValueChange = { editName = it },
                                    singleLine = true, modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = {
                                    onRename(idx, editName)
                                    editing = false
                                }) { Icon(Icons.Default.Check, stringResource(R.string.table_confirm)) }
                            } else {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(table.name, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                    Text(
                                        stringResource(R.string.table_courses_count, table.courses.size) +
                                            if (table.archived) stringResource(R.string.table_archived_suffix) else "",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = {
                                    editName = table.name; editing = true
                                }) { Icon(Icons.Outlined.Edit, stringResource(R.string.table_edit), modifier = Modifier.size(20.dp)) }
                            }

                            if (!editing) {
                                IconButton(onClick = { onDuplicate(idx) }, modifier = Modifier.size(44.dp)) {
                                    Icon(Icons.Outlined.ContentCopy, stringResource(R.string.table_duplicate_table), modifier = Modifier.size(20.dp))
                                }
                                IconButton(onClick = { onArchive(idx, !table.archived) }, modifier = Modifier.size(44.dp)) {
                                    Icon(
                                        if (table.archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                                        if (table.archived) stringResource(R.string.table_unarchive) else stringResource(R.string.table_archive),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            if (tables.size > 1 && !editing) {
                                IconButton(onClick = { pendingDeleteIndex = idx }, modifier = Modifier.size(48.dp)) {
                                    Icon(Icons.Outlined.Delete, stringResource(R.string.table_delete), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // 添加课表按钮
            Button(
                onClick = onAdd,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(48.dp),
                shape = AppShapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.table_add_table), fontSize = 15.sp)
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
