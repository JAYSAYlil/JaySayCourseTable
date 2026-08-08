package com.jaysay.coursetable.data.storage

import java.io.File
import java.io.FileOutputStream

/** 带上一版本备份的原子文本文件，避免进程中断留下半截 JSON。 */
class AtomicFileStore(private val file: File) {
    private val backup = File(file.parentFile, "${file.name}.bak")
    private val temporary = File(file.parentFile, "${file.name}.tmp")

    fun <T> read(parse: (String) -> T): T? {
        val primary = readCandidate(file, parse)
        if (primary != null) return primary

        val recovered = readCandidate(backup, parse) ?: return null
        runCatching { write(backup.readText(Charsets.UTF_8)) }
        return recovered
    }

    fun write(content: String) {
        file.parentFile?.mkdirs()
        FileOutputStream(temporary, false).use { stream ->
            stream.write(content.toByteArray(Charsets.UTF_8))
            stream.fd.sync()
        }

        if (file.exists()) {
            if (backup.exists() && !backup.delete()) {
                throw IllegalStateException("无法更新数据备份")
            }
            if (!file.renameTo(backup)) file.copyTo(backup, overwrite = true)
        }

        if (!temporary.renameTo(file)) {
            try {
                temporary.copyTo(file, overwrite = true)
            } catch (error: Exception) {
                if (!file.exists() && backup.exists()) backup.copyTo(file, overwrite = true)
                throw error
            } finally {
                temporary.delete()
            }
        }
    }

    private fun <T> readCandidate(candidate: File, parse: (String) -> T): T? {
        if (!candidate.exists()) return null
        return runCatching { parse(candidate.readText(Charsets.UTF_8)) }.getOrNull()
    }
}
