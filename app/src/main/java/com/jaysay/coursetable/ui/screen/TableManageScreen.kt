package com.jaysay.coursetable.ui.screen
import com.jaysay.coursetable.ui.components.AppModalBottomSheet as ModalBottomSheet
import com.jaysay.coursetable.ui.components.AppButton as Button
import com.jaysay.coursetable.ui.components.AppTextField as OutlinedTextField
import com.jaysay.coursetable.ui.components.AppTextButton as TextButton
import com.jaysay.coursetable.ui.components.AppIconButton as IconButton

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
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
import com.jaysay.coursetable.ui.components.rememberSheetDismiss
import com.jaysay.coursetable.ui.theme.*

// 相邻课表面板之间的净间距（每项上下各留一半）。它同时是顶栏与第一张面板之间的目标间距。
private val TablePanelGap = 10.dp
private val TablePanelInset = TablePanelGap / 2

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
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            val dismiss = rememberSheetDismiss(sheetState)
            // 与课程详情的删除确认保持同一材质：底部抽屉 + AppShapes.sheet + 整宽操作按钮。
            ModalBottomSheet(
                onDismissRequest = { pendingDeleteIndex = null },
                sheetState = sheetState,
                shape = AppShapes.sheet,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        stringResource(R.string.table_delete_dialog_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        stringResource(R.string.table_delete_dialog_message, table.name, table.courses.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = {
                            dismiss {
                            pendingDeleteIndex = null
                            onDelete(index)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = AppShapes.small,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text(stringResource(R.string.table_delete_confirm))
                    }
                    TextButton(
                        onClick = { dismiss { pendingDeleteIndex = null } },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.table_cancel))
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.table_manage_title),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.table_nav_back)) } }
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())
        ) {
            // 首张面板自带的 TablePanelInset 上内边距已经提供了一半间距，这里补上另一半，
            // 于是「顶栏 → 第一张面板」的净间距 = 「相邻两张面板」的净间距 = TablePanelGap。
            Spacer(Modifier.height(TablePanelInset))
            tables.forEachIndexed { idx, table ->
                // key(idx) 让每行的编辑状态与课表身份绑定，删除/插入行时状态不会串位
                key(idx) {
                    var editing by remember { mutableStateOf(false) }
                    var editName by remember { mutableStateOf(table.name) }

                    AppPanel(
                        modifier = Modifier.padding(horizontal = AppSpacing.lg, vertical = TablePanelInset),
                        selected = idx == activeIndex
                    ) {
                        val rowInteraction = remember { MutableInteractionSource() }
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .pressScale(rowInteraction)
                                .clickable(
                                    interactionSource = rowInteraction,
                                    indication = null,
                                    enabled = !table.archived
                                ) { onSelect(idx) }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (idx == activeIndex) Icons.Rounded.CheckCircle else Icons.Rounded.Circle,
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
                                }) { Icon(Icons.Rounded.Check, stringResource(R.string.table_confirm)) }
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
                                }) { Icon(Icons.Rounded.Edit, stringResource(R.string.table_edit), modifier = Modifier.size(20.dp)) }
                            }

                            if (!editing) {
                                IconButton(onClick = { onDuplicate(idx) }, modifier = Modifier.size(44.dp)) {
                                    Icon(Icons.Rounded.ContentCopy, stringResource(R.string.table_duplicate_table), modifier = Modifier.size(20.dp))
                                }
                                IconButton(onClick = { onArchive(idx, !table.archived) }, modifier = Modifier.size(44.dp)) {
                                    Icon(
                                        if (table.archived) Icons.Rounded.Unarchive else Icons.Rounded.Archive,
                                        if (table.archived) stringResource(R.string.table_unarchive) else stringResource(R.string.table_archive),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            if (tables.size > 1 && !editing) {
                                IconButton(onClick = { pendingDeleteIndex = idx }, modifier = Modifier.size(48.dp)) {
                                    Icon(Icons.Rounded.Delete, stringResource(R.string.table_delete), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
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
                Icon(Icons.Rounded.Add, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.table_add_table), fontSize = 15.sp)
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
