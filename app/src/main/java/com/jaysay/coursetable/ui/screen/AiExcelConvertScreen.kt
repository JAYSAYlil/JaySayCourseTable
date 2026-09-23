package com.jaysay.coursetable.ui.screen

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.jaysay.coursetable.R
import com.jaysay.coursetable.data.parser.AiChatCompletionsClient
import com.jaysay.coursetable.data.parser.AiConversionException
import com.jaysay.coursetable.data.parser.AiEndpoint
import com.jaysay.coursetable.data.parser.AiConnectionCall
import com.jaysay.coursetable.data.parser.AiConnectionException
import com.jaysay.coursetable.data.parser.AiConnectionFailure
import com.jaysay.coursetable.data.parser.AiProviderConfig
import com.jaysay.coursetable.data.parser.AiProviderConfigStore
import com.jaysay.coursetable.data.parser.AiProviderPresets
import com.jaysay.coursetable.data.parser.AiProviderSecretStore
import com.jaysay.coursetable.data.parser.AiScheduleConversion
import com.jaysay.coursetable.data.parser.AiScheduleResult
import com.jaysay.coursetable.data.parser.WorkbookTextExtractor
import com.jaysay.coursetable.data.model.Course
import com.jaysay.coursetable.ui.components.AppOutlinedButton as OutlinedButton
import com.jaysay.coursetable.ui.components.AppTextField as OutlinedTextField
import com.jaysay.coursetable.ui.components.AppButton as Button
import com.jaysay.coursetable.ui.components.AppCheckbox as Checkbox
import com.jaysay.coursetable.ui.components.AppIconButton as IconButton
import com.jaysay.coursetable.ui.components.AppTextButton as TextButton
import com.jaysay.coursetable.ui.components.AppTopBar
import com.jaysay.coursetable.ui.components.AppPanel
import com.jaysay.coursetable.ui.theme.AppShapes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun AiExcelConvertScreen(
    totalWeeks: Int,
    canImport: Boolean,
    initialResult: AiScheduleResult?,
    onResultChanged: (AiScheduleResult?) -> Unit,
    onImport: (List<Course>) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var fileName by remember { mutableStateOf("") }
    var extracted by remember { mutableStateOf<WorkbookTextExtractor.Extracted?>(null) }
    var providerConfig by remember { mutableStateOf(AiProviderConfig()) }
    var advanced by remember { mutableStateOf(false) }
    var providerMenu by remember { mutableStateOf(false) }
    var savedKey by remember { mutableStateOf(false) }
    var savedKeyInvalid by remember { mutableStateOf(false) }
    var configLoaded by remember { mutableStateOf(false) }
    var configSaved by remember { mutableStateOf(false) }
    var saveKeyOptIn by remember { mutableStateOf(false) }
    var connectionStatus by remember { mutableStateOf<Int?>(null) }
    var connectionBusy by remember { mutableStateOf(false) }
    var connectionCall by remember { mutableStateOf<AiConnectionCall?>(null) }
    val providerConfigStore = remember(context) { AiProviderConfigStore(context) }
    val secretStore = remember(context) { AiProviderSecretStore(context) }
    val selectedProvider = AiProviderPresets.byId(providerConfig.selectedId)
    val endpoint = providerConfig.urlFor(selectedProvider.id)
    val model = providerConfig.modelFor(selectedProvider.id)
    // Deliberately not saveable: the user-provided key lives only in this composition's memory.
    var apiKey by remember { mutableStateOf("") }
    var consent by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf(initialResult) }
    var busy by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var errorLocation by remember { mutableStateOf(AiExcelErrorLocation.CONVERT) }
    var job by remember { mutableStateOf<Job?>(null) }
    val closeScreen = {
        job?.cancel()
        connectionCall?.cancel()
        apiKey = ""
        onBack()
    }

    LaunchedEffect(initialResult) { if (initialResult != null) result = initialResult }
    LaunchedEffect(Unit) {
        providerConfig = withContext(Dispatchers.IO) { providerConfigStore.load() }
        configLoaded = true
    }
    LaunchedEffect(providerConfig.selectedId, configLoaded) {
        if (!configLoaded) return@LaunchedEffect
        val selectedId = providerConfig.selectedId
        val existed = withContext(Dispatchers.IO) { secretStore.has(selectedId) }
        val saved = withContext(Dispatchers.IO) { secretStore.load(selectedId) }
        savedKey = saved != null
        saveKeyOptIn = saved != null
        savedKeyInvalid = existed && saved == null
        apiKey = saved.orEmpty()
    }

    BackHandler(onBack = closeScreen)

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            job = scope.launch {
                busy = true
                errorMessage = null
                result = null
                onResultChanged(null)
                try {
                    val name = withContext(Dispatchers.IO) {
                        val cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                        cursor?.use { if (it.moveToFirst()) it.getString(0).orEmpty() else "" }.orEmpty()
                    }
                    val data = withContext(Dispatchers.IO) {
                        val input = context.contentResolver.openInputStream(uri) ?: throw IllegalStateException("无法读取所选文件，请重新选择")
                        input.use(WorkbookTextExtractor::extract)
                    }
                    fileName = name.ifBlank { stringResourceText(context, R.string.ai_excel_selected_file_fallback) }
                    extracted = data
                    consent = false
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    errorMessage = context.getString(R.string.ai_excel_error_file)
                    errorLocation = AiExcelErrorLocation.FILE
                    extracted = null
                    fileName = ""
                } finally {
                    busy = false
                }
            }
        }
    }

    val savePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri: Uri? ->
        val bytes = result?.workbookBytes
        if (uri != null && bytes != null) {
            job = scope.launch {
                busy = true
                errorMessage = null
                try {
                    withContext(Dispatchers.IO) {
                        val output = context.contentResolver.openOutputStream(uri, "wt")
                            ?: throw IllegalStateException("无法写入所选位置")
                        output.use { it.write(bytes) }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    errorMessage = context.getString(R.string.ai_excel_error_save)
                    errorLocation = AiExcelErrorLocation.RESULT
                } finally {
                    busy = false
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        AppTopBar(title = stringResource(R.string.ai_excel_title), navigationIcon = {
            IconButton(onClick = closeScreen, modifier = Modifier.testTag("ai-excel-back")) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.main_action_back))
            }
        })
        Column(
            Modifier.weight(1f).imePadding().verticalScroll(rememberScrollState())
                .navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppPanel {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.ai_excel_section_file), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    OutlinedButton(onClick = { filePicker.launch(arrayOf("application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/octet-stream")) }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("ai-excel-select-file")) {
                        Icon(Icons.Rounded.FileOpen, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(if (fileName.isBlank()) R.string.ai_excel_select_file else R.string.ai_excel_change_file))
                    }
                    if (fileName.isNotBlank()) Text(stringResource(R.string.ai_excel_file_selected, fileName), fontWeight = FontWeight.Medium)
                    Text(stringResource(R.string.ai_excel_first_sheet_notice_short), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    if (errorLocation == AiExcelErrorLocation.FILE) errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("ai-excel-error"), style = MaterialTheme.typography.bodySmall) }
                }
            }
            AppPanel {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.ai_excel_section_service), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    OutlinedButton(onClick = { providerMenu = !providerMenu }, enabled = !busy && !connectionBusy, modifier = Modifier.fillMaxWidth().testTag("ai-excel-provider")) {
                            Text(stringResource(R.string.ai_provider_selected, stringResource(selectedProvider.titleRes)), Modifier.weight(1f))
                            Icon(Icons.Rounded.ExpandMore, contentDescription = null)
                    }
                    if (providerMenu) AppPanel(modifier = Modifier.padding(horizontal = 4.dp)) {
                        Column(Modifier.padding(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            AiProviderPresets.all.forEach { preset ->
                                val selected = preset.id == selectedProvider.id
                                Row(
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(AppShapes.small)
                                        .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                                        .selectable(selected, role = Role.RadioButton) {
                                            providerMenu = false
                                            providerConfig = providerConfig.copy(selectedId = preset.id); configSaved = false
                                            connectionStatus = null; consent = false; apiKey = ""; savedKey = false; savedKeyInvalid = false
                                        }.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(stringResource(preset.titleRes), modifier = Modifier.weight(1f))
                                    if (selected) Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                    if (selectedProvider.id == AiProviderPresets.CUSTOM_ID) OutlinedTextField(value = providerConfig.customUrl,
                        onValueChange = { value -> providerConfig = providerConfig.copy(customUrl = value); configSaved = false; connectionStatus = null; consent = false },
                        modifier = Modifier.fillMaxWidth().testTag("ai-excel-endpoint"), label = { Text(stringResource(R.string.ai_excel_endpoint_label)) }, singleLine = true, enabled = !busy && !connectionBusy)
                    OutlinedTextField(value = apiKey, onValueChange = { apiKey = it; savedKeyInvalid = false; connectionStatus = null; configSaved = false },
                        modifier = Modifier.fillMaxWidth().testTag("ai-excel-api-key"), label = { Text(stringResource(R.string.ai_excel_api_key_label)) },
                        visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !busy && !connectionBusy)
                    if (advanced || selectedProvider.id == AiProviderPresets.CUSTOM_ID) OutlinedTextField(value = model,
                        onValueChange = { value ->
                            providerConfig = if (selectedProvider.id == AiProviderPresets.CUSTOM_ID) providerConfig.copy(customModel = value) else providerConfig.copy(models = providerConfig.models + (selectedProvider.id to value))
                            configSaved = false; connectionStatus = null; consent = false
                        }, modifier = Modifier.fillMaxWidth().testTag("ai-excel-model"), label = { Text(stringResource(R.string.ai_excel_model_label)) }, singleLine = true, enabled = !busy && !connectionBusy)
                    if (selectedProvider.id != AiProviderPresets.CUSTOM_ID) TextButton(onClick = { advanced = !advanced }, enabled = !busy && !connectionBusy) {
                        Text(stringResource(if (advanced) R.string.ai_provider_advanced_hide else R.string.ai_provider_advanced_show))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().toggleable(
                            saveKeyOptIn,
                            enabled = !busy && !connectionBusy,
                            role = Role.Checkbox
                        ) { saveKeyOptIn = it; configSaved = false },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = saveKeyOptIn, onCheckedChange = null, enabled = !busy && !connectionBusy)
                        Text(stringResource(R.string.ai_provider_save_key_opt_in), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    }
                    Text(stringResource(R.string.ai_provider_key_privacy_short), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    if (savedKey) Text(stringResource(R.string.ai_provider_saved_key_uncheck_notice), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    if (savedKeyInvalid) Text(stringResource(R.string.ai_provider_saved_key_invalid), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            val cfg = providerConfig; val providerId = selectedProvider.id; val key = apiKey; val shouldSave = saveKeyOptIn
                            scope.launch {
                                errorMessage = null; errorLocation = AiExcelErrorLocation.SERVICE
                                busy = true
                                val ok = withContext(Dispatchers.IO) { runCatching { check(providerConfigStore.save(cfg)); if (shouldSave && key.isNotBlank()) secretStore.save(providerId, key) }.isSuccess }
                                if (ok && shouldSave && key.isNotBlank()) { savedKey = true; savedKeyInvalid = false }
                                if (ok) configSaved = true else errorMessage = context.getString(R.string.ai_provider_store_error)
                                busy = false
                            }
                        }, enabled = !busy && !connectionBusy, modifier = Modifier.weight(1f).testTag("ai-provider-save")) { Text(stringResource(R.string.ai_provider_save_config)) }
                        OutlinedButton(onClick = {
                            val call = AiConnectionCall(); connectionCall = call; connectionBusy = true; connectionStatus = null
                            job = scope.launch {
                                try { withContext(Dispatchers.IO) { AiChatCompletionsClient.testConnection(AiEndpoint(endpoint, model, apiKey), call) }; connectionStatus = R.string.ai_provider_test_success }
                                catch (cancelled: CancellationException) { throw cancelled }
                                catch (failure: Exception) { connectionStatus = when ((failure as? AiConnectionException)?.reason) {
                                    AiConnectionFailure.AUTH -> R.string.ai_provider_test_auth
                                    AiConnectionFailure.MODEL_OR_URL -> R.string.ai_provider_test_model
                                    AiConnectionFailure.CONFIG -> R.string.ai_provider_test_config
                                    AiConnectionFailure.RATE_LIMIT -> R.string.ai_provider_test_rate
                                    AiConnectionFailure.TIMEOUT -> R.string.ai_provider_test_timeout
                                    AiConnectionFailure.RESPONSE -> R.string.ai_provider_test_response
                                    else -> R.string.ai_provider_test_network
                                } } finally { connectionBusy = false; connectionCall = null }
                            }
                        }, enabled = !busy && !connectionBusy && endpoint.isNotBlank() && model.isNotBlank() && apiKey.isNotBlank(), modifier = Modifier.weight(1f).testTag("ai-provider-test")) {
                            Text(stringResource(if (connectionBusy) R.string.ai_provider_testing else R.string.ai_provider_test))
                        }
                    }
                    if (savedKey) TextButton(onClick = {
                        val providerId = selectedProvider.id
                        scope.launch {
                            errorMessage = null; errorLocation = AiExcelErrorLocation.SERVICE
                            busy = true
                            val ok = withContext(Dispatchers.IO) { runCatching { secretStore.delete(providerId) }.isSuccess }
                            if (ok) { savedKey = false; apiKey = ""; connectionStatus = null; configSaved = false } else errorMessage = context.getString(R.string.ai_provider_store_error)
                            busy = false
                        }
                    }, enabled = !busy && !connectionBusy, modifier = Modifier.testTag("ai-provider-delete-key")) { Text(stringResource(R.string.ai_provider_delete_key)) }
                    if (configSaved) Text(stringResource(R.string.ai_provider_config_saved), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                    if (errorLocation == AiExcelErrorLocation.SERVICE) errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("ai-excel-error"), style = MaterialTheme.typography.bodySmall) }
                    Text(stringResource(R.string.ai_provider_test_cost_short), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    if (connectionBusy) OutlinedButton(onClick = { connectionCall?.cancel(); job?.cancel(); connectionBusy = false }, modifier = Modifier.fillMaxWidth().testTag("ai-provider-test-cancel")) { Text(stringResource(R.string.import_cancel)) }
                    connectionStatus?.let { Text(stringResource(it), color = if (it == R.string.ai_provider_test_success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, modifier = Modifier.testTag("ai-provider-test-status"), style = MaterialTheme.typography.bodySmall) }
                }
            }
            AppPanel {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.ai_excel_section_convert), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    ConsentRow(consent, !busy, { consent = it })
                    Button(onClick = {
                        val selected = extracted ?: return@Button
                        job = scope.launch {
                            busy = true; errorMessage = null; errorLocation = AiExcelErrorLocation.CONVERT; result = null; onResultChanged(null)
                            try {
                                val converted = withContext(Dispatchers.IO) {
                                    val response = AiChatCompletionsClient.complete(AiEndpoint(endpoint, model, apiKey), selected.promptText)
                                    AiScheduleConversion.convert(response, selected)
                                }
                                result = converted; onResultChanged(converted); if (!savedKey) apiKey = ""
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (failure: Exception) { errorMessage = (failure as? AiConversionException)?.message ?: context.getString(R.string.ai_excel_error_conversion) }
                            finally { busy = false }
                        }
                    }, enabled = !busy && !connectionBusy && extracted != null && endpoint.isNotBlank() && model.isNotBlank() && apiKey.isNotBlank() && consent,
                        modifier = Modifier.fillMaxWidth().testTag("ai-excel-convert")) {
                        if (busy) CircularProgressIndicator(Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
                        Text(stringResource(if (busy) R.string.ai_excel_converting else R.string.ai_excel_start))
                    }
                    if (busy) {
                        Text(stringResource(R.string.ai_excel_busy_notice_short), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { job?.cancel(); busy = false }, modifier = Modifier.fillMaxWidth().testTag("ai-excel-cancel")) { Text(stringResource(R.string.import_cancel)) }
                    }
                    if (errorLocation == AiExcelErrorLocation.CONVERT) errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("ai-excel-error"), style = MaterialTheme.typography.bodySmall) }
                }
            }
            result?.let { current -> AppPanel {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.ai_excel_result_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Text(stringResource(R.string.ai_excel_result_summary, current.courses.size), fontWeight = FontWeight.Medium)
                    if (current.generatedCourseIds > 0) Text(stringResource(R.string.ai_excel_generated_ids, current.generatedCourseIds), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    if (current.maximumWeek > totalWeeks) Text(stringResource(R.string.ai_excel_weeks_exceed_term, current.maximumWeek, totalWeeks), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    current.courses.take(20).forEach { course -> Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text(course.courseName, fontWeight = FontWeight.Medium)
                        Text(stringResource(R.string.ai_excel_course_preview_details, course.dayOfWeek, course.startPeriod, course.endPeriod, course.weeks.joinToString(",")), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    } }
                    if (current.courses.size > 20) Text(stringResource(R.string.ai_excel_more_courses, current.courses.size - 20), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.ai_excel_confirmation_notice_short), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    if (errorLocation == AiExcelErrorLocation.RESULT) errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("ai-excel-error"), style = MaterialTheme.typography.bodySmall) }
                    OutlinedButton(onClick = { savePicker.launch("AI转换课表.xlsx") }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("ai-excel-save")) {
                        Icon(Icons.Rounded.SaveAlt, contentDescription = null); Text(stringResource(R.string.ai_excel_save_template))
                    }
                    Button(onClick = { onImport(current.courses) }, enabled = canImport && !busy, modifier = Modifier.fillMaxWidth().testTag("ai-excel-import")) { Text(stringResource(R.string.ai_excel_continue_import)) }
                }
            } }
        }
    }
}

@Composable
private fun ConsentRow(consent: Boolean, enabled: Boolean, onConsentChanged: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag("ai-excel-consent").toggleable(consent, enabled = enabled, role = Role.Checkbox, onValueChange = onConsentChanged),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Checkbox(checked = consent, onCheckedChange = null, enabled = enabled)
        Text(stringResource(R.string.ai_excel_consent), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
}

private enum class AiExcelErrorLocation { FILE, SERVICE, CONVERT, RESULT }

private fun stringResourceText(context: android.content.Context, id: Int): String = context.getString(id)
