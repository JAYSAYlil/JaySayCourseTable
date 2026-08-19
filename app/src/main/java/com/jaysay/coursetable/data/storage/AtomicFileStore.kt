package com.jaysay.coursetable.data.storage

import java.io.File
import java.io.FileOutputStream

/** 已存在的数据文件和可用备份均无法通过调用方校验。 */
class DataCorruptionException(
    fileName: String,
    cause: Throwable? = null
) : IllegalStateException("数据文件 $fileName 及其备份已损坏，请从完整备份恢复", cause)

/** 带上一版本备份的原子文本文件，避免进程中断留下半截 JSON。 */
class AtomicFileStore(private val file: File) {
    private val backup = File(file.parentFile, "${file.name}.bak")
    private val temporary = File(file.parentFile, "${file.name}.tmp")
    private val backupTemporary = File(file.parentFile, "${file.name}.bak.tmp")

    fun <T> read(parse: (String) -> T): T? {
        if (!file.exists() && !backup.exists()) return null

        val failures = mutableListOf<Exception>()
        val primary = readCandidate(file, parse, failures)
        if (primary != null) return primary.value

        val recovered = readCandidate(backup, parse, failures)
            ?: throw DataCorruptionException(file.name, failures.firstOrNull())
        // 恢复主文件时保留已经验证有效的 .bak，避免把损坏主文件轮换成新备份。
        replacePrimary(recovered.content)
        return recovered.value
    }

    fun write(content: String) {
        file.parentFile?.mkdirs()
        writeSynced(temporary, content)

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

    /** 用一份已经过上层完整校验的数据同时重建主文件与回退副本。 */
    fun replaceWithValidated(content: String) {
        file.parentFile?.mkdirs()
        replacePrimary(content)
        writeSynced(backupTemporary, content)
        replaceFile(backupTemporary, backup)
    }

    private fun replacePrimary(content: String) {
        file.parentFile?.mkdirs()
        writeSynced(temporary, content)
        replaceFile(temporary, file)
    }

    private fun replaceFile(source: File, target: File) {
        if (target.exists() && !target.delete()) {
            source.delete()
            throw IllegalStateException("无法替换数据文件 ${target.name}")
        }
        if (!source.renameTo(target)) {
            try {
                source.copyTo(target, overwrite = true)
            } finally {
                source.delete()
            }
        }
    }

    private fun writeSynced(target: File, content: String) {
        FileOutputStream(target, false).use { stream ->
            stream.write(content.toByteArray(Charsets.UTF_8))
            stream.fd.sync()
        }
    }

    private data class ParsedCandidate<T>(val value: T, val content: String)

    private fun <T> readCandidate(
        candidate: File,
        parse: (String) -> T,
        failures: MutableList<Exception>
    ): ParsedCandidate<T>? {
        if (!candidate.exists()) return null
        return try {
            val content = candidate.readText(Charsets.UTF_8)
            ParsedCandidate(parse(content), content)
        } catch (error: Exception) {
            failures += error
            null
        }
    }
}
