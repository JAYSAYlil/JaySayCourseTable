package com.jaysay.coursetable.data.transfer

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.jaysay.coursetable.MainViewModel
import com.jaysay.coursetable.data.backup.BackupCodec
import com.jaysay.coursetable.data.backup.BackupData
import com.jaysay.coursetable.data.ical.IcsExporter
import com.jaysay.coursetable.data.parser.ExcelParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * 文件传输协调器：封装 Excel 导入、备份导出/导入、iCal 导出的后台线程调度
 * 与结果回调，避免全部逻辑堆在 MainActivity 中。
 */
class ImportExportCoordinator(
    private val activity: ComponentActivity,
    private val model: MainViewModel,
    private val onPendingImport: (ExcelParser.ParseResult) -> Unit,
    private val onPendingBackupRestore: (BackupData) -> Unit,
    private val showError: (String, Throwable) -> Unit,
    private val showToast: (String) -> Unit
) {

    fun handleFileImport(uri: Uri) {
        // 解析 Excel 放到后台线程，避免大文件阻塞主线程导致卡顿/ANR
        activity.lifecycleScope.launch {
            val result = try {
                withContext(Dispatchers.IO) { ExcelParser.parse(activity, uri) }
            } catch (e: Exception) {
                showToast("导入失败：" + e.message)
                return@launch
            }
            if (result.courses.isEmpty()) {
                val message = result.errors.firstOrNull() ?: "文件中未找到课程数据"
                showToast("导入失败：$message")
            } else {
                // 有个别坏行时仍允许确认导入正常课程，并在确认页展示警告。
                onPendingImport(result)
            }
        }
    }

    fun handleBackupExport(uri: Uri, sanitized: Boolean) {
        activity.lifecycleScope.launch {
            val result = runCatching {
                val text = withContext(Dispatchers.Default) {
                    BackupCodec.encode(model.backupSnapshot(), sanitized)
                }
                withContext(Dispatchers.IO) {
                    activity.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use {
                        it.write(text)
                    } ?: error("无法写入所选文件")
                }
            }
            result.onSuccess {
                showToast(if (sanitized) "脱敏副本已导出（不能用于恢复）" else "完整备份已导出")
            }.onFailure { showError("导出失败", it) }
        }
    }

    fun handleBackupImport(uri: Uri) {
        activity.lifecycleScope.launch {
            val decoded = runCatching {
                withContext(Dispatchers.IO) {
                    val stream = activity.contentResolver.openInputStream(uri) ?: error("无法读取所选文件")
                    stream.use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            require(output.size() <= 10 * 1024 * 1024) { "备份文件过大" }
                        }
                        BackupCodec.decode(output.toString(Charsets.UTF_8.name()))
                    }
                }
            }
            decoded.onSuccess { backup ->
                onPendingBackupRestore(backup)
            }.onFailure { showError("备份校验失败，原课表未被替换", it) }
        }
    }

    fun handleIcsExport(uri: Uri) {
        activity.lifecycleScope.launch {
            val result = runCatching {
                val text = withContext(Dispatchers.Default) { IcsExporter.export(model.state.activeTable) }
                withContext(Dispatchers.IO) {
                    activity.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use {
                        it.write(text)
                    } ?: error("无法写入所选文件")
                }
            }
            result.onSuccess {
                showToast("日历已导出")
            }.onFailure { showError("导出失败", it) }
        }
    }
}
