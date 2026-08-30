package com.jaysay.coursetable.ui.screen

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import com.jaysay.coursetable.R
import com.jaysay.coursetable.util.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.stringResource

/** 设置页更新检查条目，独立于设置页其它偏好分区，避免网络状态与表单状态互相耦合。 */
internal fun createUpdateCheckSettingsItem(context: Context): SettingsItem = SettingsItem(
    keywords = listOf(
        context.getString(R.string.settings_check_update),
        context.getString(R.string.settings_check_update_subtitle)
    )
) {
    UpdateCheckSettingsContent(context)
}

@Composable
private fun UpdateCheckSettingsContent(context: Context) {
    val scope = rememberCoroutineScope()
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<UpdateChecker.Result?>(null) }
    SettingsActionRow(
        icon = Icons.Outlined.SystemUpdateAlt,
        title = stringResource(R.string.settings_check_update),
        subtitle = stringResource(R.string.settings_check_update_subtitle),
        enabled = !checkingUpdate,
        onClick = {
            checkingUpdate = true
            scope.launch {
                val result = withContext(Dispatchers.IO) { UpdateChecker.check() }
                updateResult = result
                checkingUpdate = false
            }
        }
    )
    updateResult?.let { result ->
        val currentVersion = remember {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        }
        val hasNewer = result.error == null &&
            UpdateChecker.isNewer(result.latestVersion.orEmpty(), currentVersion)
        AlertDialog(
            onDismissRequest = { updateResult = null },
            title = {
                Text(
                    when {
                        result.error != null -> stringResource(R.string.settings_update_failed, result.error)
                        hasNewer -> stringResource(R.string.settings_update_available, result.latestVersion.orEmpty(), currentVersion)
                        else -> stringResource(R.string.settings_update_latest, currentVersion)
                    },
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(if (result.error != null) result.error else stringResource(R.string.settings_check_update_subtitle))
            },
            confirmButton = {
                if (hasNewer && result.releaseUrl != null) {
                    TextButton(onClick = {
                        updateResult = null
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(result.releaseUrl))) }
                    }) { Text(stringResource(R.string.settings_update_open)) }
                } else {
                    TextButton(onClick = { updateResult = null }) {
                        Text(stringResource(R.string.overview_dialog_got_it))
                    }
                }
            },
            dismissButton = {
                if (hasNewer && result.releaseUrl != null) {
                    TextButton(onClick = { updateResult = null }) {
                        Text(stringResource(R.string.edit_button_cancel))
                    }
                }
            }
        )
    }
}
