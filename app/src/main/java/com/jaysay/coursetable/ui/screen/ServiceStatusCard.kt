package com.jaysay.coursetable.ui.screen

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jaysay.coursetable.R
import com.jaysay.coursetable.data.diagnostics.ServiceStatusStore
import com.jaysay.coursetable.ui.theme.AppShapes
import java.text.DateFormat
import java.util.Date

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
    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).testTag("service-status-card"),
        shape = AppShapes.card, color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.service_status_title), fontWeight = FontWeight.SemiBold)
            Text(reminderText, style = MaterialTheme.typography.bodySmall)
            if (fixReminder != null) TextButton(onClick = fixReminder) { Text(stringResource(R.string.service_fix_reminder)) }
            HorizontalDivider()
            Text(stringResource(R.string.service_backup, stringResource(if (backupEnabled) R.string.service_enabled else R.string.service_disabled)), style = MaterialTheme.typography.bodyMedium)
            Text(receipt("backup"), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = chooseBackup) { Text(stringResource(R.string.service_backup_location)) }
            HorizontalDivider()
            Text(stringResource(if (widgetPresent) R.string.service_widget_present else R.string.service_widget_absent), style = MaterialTheme.typography.bodyMedium)
            if (widgetPresent) {
                Text(receipt("widget"), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = refreshWidget, modifier = Modifier.testTag("service-refresh-widget")) { Text(stringResource(R.string.service_refresh_widget)) }
            }
        }
    }
}
