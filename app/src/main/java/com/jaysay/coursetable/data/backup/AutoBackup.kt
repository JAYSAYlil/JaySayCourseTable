package com.jaysay.coursetable.data.backup

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 启动时自动备份：向用户通过 SAF 选择过的文件位置静默覆盖写入完整备份。
 * 成功不提示，失败由调用方决定是否提示——自动行为不打扰是第一原则。
 */
object AutoBackup {
    suspend fun write(context: Context, uri: Uri, data: BackupData): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val content = BackupCodec.encode(data, sanitized = false)
                val stream = context.contentResolver.openOutputStream(uri, "wt")
                    ?: error("备份位置不可写")
                stream.use { it.write(content.toByteArray(Charsets.UTF_8)) }
            }
        }
}
