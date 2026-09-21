package com.jaysay.coursetable.ui.screen
import com.jaysay.coursetable.ui.components.AppTextButton as TextButton

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jaysay.coursetable.R
import com.jaysay.coursetable.data.diagnostics.ServiceStatusStore
import com.jaysay.coursetable.ui.theme.AppShapes
import com.jaysay.coursetable.ui.theme.AppSpacing
import java.text.DateFormat
import java.util.Date

/**
 * 服务状态卡：与设置页其它分组卡同一套 iOS 风格（分组标题在上、
 * panel 圆角 + 0.75dp 描边 + 0.5dp 分隔线），行结构复用设置行的
 * “图标 + 标题/状态 + 行尾操作”节奏，替代旧的灰底自由排版。
 */
@Composable
internal fun ServiceStatusCard(
    reminderText: String, backupEnabled: Boolean, widgetPresent: Boolean,
    fixReminder: (() -> Unit)?, chooseBackup: () -> Unit, refreshWidget: () -> Unit
) {
    val context = LocalContext.current
    val receipts = remember(context) { context.getSharedPreferences(ServiceStatusStore.FILE, Context.MODE_PRIVATE) }
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(receipts) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision++ }
        receipts.registerOnSharedPreferenceChangeListener(listener)
        onDispose { receipts.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    fun receipt(operation: String): String {
        @Suppress("UNUSED_VARIABLE") val observed = revision
        val timestamp = receipts.getLong("${operation}_success", 0L)
        val last = if (timestamp == 0L) context.getString(R.string.service_no_receipt)
            else context.getString(R.string.service_last_success, DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestamp)))
        return if (receipts.getBoolean("${operation}_failed", false)) context.getString(R.string.service_failed, last) else last
    }
    Column(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.service_status_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(
                start = AppSpacing.screenH, end = AppSpacing.screenH, bottom = AppSpacing.sm
            )
        )
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.screenH).testTag("service-status-card"),
            shape = AppShapes.panel,
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Column {
                ServiceStatusRow(
                    icon = Icons.Rounded.Notifications,
                    title = stringResource(R.string.service_reminder_label),
                    subtitle = reminderText,
                    trailing = {
                        if (fixReminder != null) {
                            TextButton(onClick = fixReminder, contentPadding = PaddingValues(horizontal = 8.dp)) {
                                Text(stringResource(R.string.service_fix_reminder), fontSize = 12.sp)
                            }
                        }
                    }
                )
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                )
                ServiceStatusRow(
                    icon = Icons.Rounded.Backup,
                    title = stringResource(R.string.settings_auto_backup),
                    subtitle = stringResource(
                        if (backupEnabled) R.string.service_enabled else R.string.service_disabled
                    ) + " · " + receipt("backup"),
                    trailing = {
                        TextButton(onClick = chooseBackup, contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Text(stringResource(R.string.service_change_location), fontSize = 12.sp)
                        }
                    }
                )
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                )
                ServiceStatusRow(
                    icon = Icons.Rounded.Widgets,
                    title = stringResource(R.string.service_widget_label),
                    subtitle = if (widgetPresent) receipt("widget")
                    else stringResource(R.string.service_widget_absent),
                    trailing = {
                        if (widgetPresent) {
                            TextButton(onClick = refreshWidget, contentPadding = PaddingValues(horizontal = 8.dp)) {
                                Text(stringResource(R.string.service_refresh_widget), fontSize = 12.sp)
                            }
                        }
                    },
                    trailingTag = if (widgetPresent) "service-refresh-widget" else null
                )
            }
        }
    }
}

@Composable
private fun ServiceStatusRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit,
    trailingTag: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(modifier = if (trailingTag == null) Modifier else Modifier.testTag(trailingTag)) { trailing() }
    }
}
