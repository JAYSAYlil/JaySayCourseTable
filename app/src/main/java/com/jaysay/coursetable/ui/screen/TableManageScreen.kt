package com.jaysay.coursetable.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                title = { Text("删除课表？") },
                text = { Text("将永久删除“${table.name}”及其中 ${table.courses.size} 门课程，此操作不会影响其他课表。") },
                confirmButton = {
                    TextButton(onClick = {
                        pendingDeleteIndex = null
                        onDelete(index)
                    }) { Text("确认删除", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeleteIndex = null }) { Text("取消") }
                }
            )
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "课表管理",
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } }
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
                            modifier = Modifier.fillMaxWidth().clickable { onSelect(idx) }.padding(16.dp),
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
                                }) { Icon(Icons.Default.Check, "确认") }
                            } else {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(table.name, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                    Text(table.courses.size.toString() + " 门课程", fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = {
                                    editName = table.name; editing = true
                                }) { Icon(Icons.Outlined.Edit, "编辑", modifier = Modifier.size(20.dp)) }
                            }

                            if (tables.size > 1 && !editing) {
                                IconButton(onClick = { pendingDeleteIndex = idx }, modifier = Modifier.size(48.dp)) {
                                    Icon(Icons.Outlined.Delete, "删除", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
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
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("添加课表", fontSize = 15.sp)
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
