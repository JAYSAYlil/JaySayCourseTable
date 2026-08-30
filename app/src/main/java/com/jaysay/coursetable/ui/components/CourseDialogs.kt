package com.jaysay.coursetable.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.jaysay.coursetable.R
import com.jaysay.coursetable.data.backup.BackupData
import com.jaysay.coursetable.data.backup.BackupDiff
import com.jaysay.coursetable.data.model.CourseConflict
import com.jaysay.coursetable.data.parser.ExcelParser
import com.jaysay.coursetable.data.parser.MappedTextScheduleParser
import com.jaysay.coursetable.data.parser.TextColumnMapping
import com.jaysay.coursetable.data.parser.TextScheduleParser
import com.jaysay.coursetable.data.repository.TableData
import com.jaysay.coursetable.ui.theme.AppShapes

/** 手工新增/编辑课程时待用户确认的冲突信息；确认后执行原保存动作。 */
internal data class PendingConflictChange(
    val courseName: String,
    val conflicts: List<CourseConflict>,
    val onConfirm: () -> Unit
)

/** 课程详情页"从本周移除课程"确认弹窗：内容少、操作单一，使用底部抽屉。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DetailDeleteConfirmDialog(
    courseName: String,
    week: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = AppShapes.sheet,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.DeleteOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                stringResource(R.string.dialog_title_remove_from_week),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                stringResource(R.string.dialog_text_remove_from_week, week, courseName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth(),
                shape = AppShapes.small,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text(stringResource(R.string.dialog_button_confirm_remove))
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.dialog_button_cancel))
            }
        }
    }
}

/** 密码加密备份导出：设置密码弹窗（含两个输入项，保留 AlertDialog）。 */
@Composable
internal fun EncryptedExportPasswordDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val passwordValid = password.length >= 6 && password == confirmation
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(stringResource(R.string.dialog_title_set_backup_password)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.dialog_text_backup_password_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it.take(128) },
                    label = { Text(stringResource(R.string.dialog_label_backup_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    shape = AppShapes.input
                )
                OutlinedTextField(
                    value = confirmation,
                    onValueChange = { confirmation = it.take(128) },
                    label = { Text(stringResource(R.string.dialog_label_confirm_backup_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    isError = confirmation.isNotEmpty() && confirmation != password,
                    shape = AppShapes.input
                )
            }
        },
        confirmButton = {
            TextButton(enabled = passwordValid, onClick = { onConfirm(password) }) {
                Text(stringResource(R.string.dialog_button_choose_save_location))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_button_cancel)) } }
    )
}

/** 密码加密备份导入：输入密码弹窗（单输入、单操作，使用底部抽屉）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EncryptedImportPasswordDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = AppShapes.sheet,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.LockOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                stringResource(R.string.dialog_title_input_backup_password),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it.take(128) },
                label = { Text(stringResource(R.string.dialog_label_backup_password)) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                shape = AppShapes.input,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = { onConfirm(password) },
                enabled = password.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                shape = AppShapes.small
            ) {
                Text(stringResource(R.string.dialog_button_decrypt_and_verify))
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.dialog_button_cancel))
            }
        }
    }
}

/** 完整备份恢复确认弹窗：差异摘要 + 二次确认，内容单一，使用底部抽屉。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BackupRestoreConfirmDialog(
    backup: BackupData,
    currentTables: List<TableData>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val courseCount = backup.tables.sumOf { it.courses.size }
    val diff = remember(backup, currentTables) { BackupDiff.between(currentTables, backup.tables) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = AppShapes.sheet,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(R.string.dialog_title_confirm_restore_backup),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                stringResource(
                    R.string.dialog_text_restore_backup_summary,
                    backup.tables.size,
                    courseCount,
                    diff.coursesAdded,
                    diff.coursesChanged,
                    diff.coursesRemoved,
                    diff.tablesAdded,
                    diff.tablesRemoved
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth(),
                shape = AppShapes.small
            ) {
                Text(stringResource(R.string.dialog_button_confirm_restore))
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.dialog_button_cancel))
            }
        }
    }
}

/** 手工新增/编辑课程时的冲突确认弹窗（冲突列表，保留 AlertDialog，统一视觉）。 */
@Composable
internal fun ConflictConfirmDialog(
    pending: PendingConflictChange,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.WarningAmber, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(R.string.dialog_title_course_conflict)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.dialog_text_conflict_overlap, pending.courseName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                pending.conflicts.take(4).forEach { conflict ->
                    Text(
                        stringResource(
                            R.string.dialog_text_conflict_item,
                            conflict.otherCourseName,
                            conflict.overlappingWeeks.joinToString("、"),
                            conflict.startPeriod,
                            conflict.endPeriod
                        ),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (pending.conflicts.size > 4) {
                    Text(
                        stringResource(R.string.dialog_text_more_conflicts, pending.conflicts.size - 4),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            // 安全操作（返回编辑）作主视觉，破坏性"仍然保存"以错误色次级按钮呈现
            TextButton(onClick = {
                val action = pending.onConfirm
                onDismiss()
                action()
            }) { Text(stringResource(R.string.dialog_button_save_anyway), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = {
            FilledTonalButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_button_back_to_edit)) }
        }
    )
}

/** 网页课表文本粘贴导入弹窗：大段文本 + 列映射，保留 AlertDialog，统一间距与输入样式。 */
@Composable
internal fun PasteImportDialog(
    totalWeeks: Int,
    onParsed: (ExcelParser.ParseResult) -> Unit,
    onParseFailed: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pasteText by rememberSaveable { mutableStateOf("") }
    var mappingMode by rememberSaveable { mutableStateOf(false) }
    var nameColumn by rememberSaveable { mutableStateOf("1") }
    var teacherColumn by rememberSaveable { mutableStateOf("2") }
    var roomColumn by rememberSaveable { mutableStateOf("3") }
    var dayColumn by rememberSaveable { mutableStateOf("4") }
    var periodColumn by rememberSaveable { mutableStateOf("5") }
    var weekColumn by rememberSaveable { mutableStateOf("6") }
    val columnIndexInvalidMessage = stringResource(R.string.dialog_toast_column_index_invalid)
    val noCourseParsedPrefix = stringResource(R.string.dialog_toast_no_course_parsed_prefix)
    val checkFormatMessage = stringResource(R.string.dialog_toast_check_format)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.ContentPaste, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(stringResource(R.string.dialog_title_paste_import)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (mappingMode) stringResource(R.string.dialog_text_paste_mapping_hint)
                    else stringResource(R.string.dialog_text_paste_line_format),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    stringResource(R.string.dialog_text_paste_example),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        stringResource(R.string.dialog_label_manual_column_mapping),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(checked = mappingMode, onCheckedChange = { mappingMode = it })
                }
                if (mappingMode) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ColumnIndexField(stringResource(R.string.dialog_label_column_course), nameColumn, { nameColumn = it }, Modifier.weight(1f))
                            ColumnIndexField(stringResource(R.string.dialog_label_column_teacher), teacherColumn, { teacherColumn = it }, Modifier.weight(1f))
                            ColumnIndexField(stringResource(R.string.dialog_label_column_classroom), roomColumn, { roomColumn = it }, Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ColumnIndexField(stringResource(R.string.dialog_label_column_day), dayColumn, { dayColumn = it }, Modifier.weight(1f))
                            ColumnIndexField(stringResource(R.string.dialog_label_column_period), periodColumn, { periodColumn = it }, Modifier.weight(1f))
                            ColumnIndexField(stringResource(R.string.dialog_label_column_week), weekColumn, { weekColumn = it }, Modifier.weight(1f))
                        }
                    }
                    Text(
                        stringResource(R.string.dialog_text_zero_means_no_column),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedTextField(
                    value = pasteText,
                    onValueChange = { if (it.length <= 50_000) pasteText = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp).testTag("paste-import-input"),
                    placeholder = { Text(stringResource(R.string.dialog_placeholder_paste_text)) },
                    maxLines = 8,
                    shape = AppShapes.input
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val result = if (mappingMode) {
                    val required = listOf(nameColumn, dayColumn, periodColumn, weekColumn)
                        .mapNotNull(String::toIntOrNull)
                    if (required.size != 4 || required.any { it <= 0 }) {
                        onParseFailed(columnIndexInvalidMessage)
                        return@TextButton
                    }
                    MappedTextScheduleParser.parse(
                        pasteText,
                        TextColumnMapping(
                            courseName = required[0] - 1,
                            dayOfWeek = required[1] - 1,
                            periods = required[2] - 1,
                            weeks = required[3] - 1,
                            teacher = teacherColumn.toIntOrNull()?.takeIf { it > 0 }?.minus(1),
                            classroom = roomColumn.toIntOrNull()?.takeIf { it > 0 }?.minus(1)
                        ),
                        totalWeeks = totalWeeks
                    )
                } else {
                    TextScheduleParser.parse(pasteText, totalWeeks = totalWeeks)
                }
                if (result.courses.isEmpty()) {
                    onParseFailed(noCourseParsedPrefix + (result.errors.firstOrNull() ?: checkFormatMessage))
                } else {
                    onParsed(ExcelParser.ParseResult(result.courses, result.errors))
                }
            }, modifier = Modifier.testTag("paste-import-confirm")) { Text(stringResource(R.string.dialog_button_parse_and_import)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_button_cancel)) }
        }
    )
}

/** 粘贴导入弹窗里的单列号输入框。 */
@Composable
private fun ColumnIndexField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter(Char::isDigit).take(2)) },
        label = { Text(label) },
        supportingText = { Text(stringResource(R.string.dialog_label_column_index)) },
        singleLine = true,
        shape = AppShapes.input,
        modifier = modifier
    )
}
