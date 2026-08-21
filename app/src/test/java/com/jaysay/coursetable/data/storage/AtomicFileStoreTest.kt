package com.jaysay.coursetable.data.storage

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AtomicFileStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun recoversLastValidVersionWhenPrimaryIsDamaged() {
        val file = temporaryFolder.newFile("state.json")
        file.delete()
        val store = AtomicFileStore(file)
        store.write("valid-v1")
        store.write("valid-v2")
        file.writeText("broken", Charsets.UTF_8)

        val recovered = store.read { content ->
            require(content.startsWith("valid"))
            content
        }

        assertEquals("valid-v1", recovered)
        assertEquals("valid-v1", file.readText(Charsets.UTF_8))
        val backup = requireNotNull(file.parentFile).resolve("state.json.bak")
        assertTrue(backup.exists())
        assertEquals("valid-v1", backup.readText(Charsets.UTF_8))
    }

    @Test
    fun ignoresInterruptedTemporaryFileWhenPrimaryIsValid() {
        val file = temporaryFolder.newFile("stable.json")
        file.delete()
        val store = AtomicFileStore(file)
        store.write("valid-primary")
        requireNotNull(file.parentFile).resolve("stable.json.tmp").writeText("partial-write")

        val value = store.read { content ->
            require(content.startsWith("valid"))
            content
        }

        assertEquals("valid-primary", value)
        assertEquals("valid-primary", file.readText())
    }

    @Test
    fun throwsExplicitCorruptionWhenPrimaryAndBackupAreBothInvalid() {
        val file = temporaryFolder.newFile("damaged.json")
        file.writeText("broken-primary")
        requireNotNull(file.parentFile).resolve("damaged.json.bak").writeText("broken-backup")

        val error = assertThrows(DataCorruptionException::class.java) {
            AtomicFileStore(file).read { content ->
                require(content.startsWith("valid"))
                content
            }
        }

        assertTrue(error.message.orEmpty().contains("damaged.json"))
    }

    @Test
    fun wrapsBackupReadFailureAsDataCorruption() {
        val file = temporaryFolder.newFile("unreadable-backup.json")
        file.writeText("broken-primary")
        val backup = requireNotNull(file.parentFile).resolve("unreadable-backup.json.bak")
        assertTrue(backup.mkdir())

        assertThrows(DataCorruptionException::class.java) {
            AtomicFileStore(file).read { content ->
                require(content.startsWith("valid"))
                content
            }
        }
    }

    @Test
    fun doesNotSwallowJvmErrorsFromParser() {
        val file = temporaryFolder.newFile("fatal.json")
        file.writeText("content")

        assertThrows(AssertionError::class.java) {
            AtomicFileStore(file).read<String> { throw AssertionError("fatal") }
        }
    }

    @Test
    fun validatedReplacementRebuildsPrimaryAndBackup() {
        val file = temporaryFolder.newFile("restore.json")
        file.writeText("broken-primary")
        requireNotNull(file.parentFile).resolve("restore.json.bak").writeText("broken-backup")

        AtomicFileStore(file).replaceWithValidated("validated-backup")

        assertEquals("validated-backup", file.readText())
        assertEquals("validated-backup", requireNotNull(file.parentFile).resolve("restore.json.bak").readText())
    }

    @Test
    fun missingPrimaryAndBackupReturnsNull() {
        val file = temporaryFolder.root.resolve("missing.json")

        val value = AtomicFileStore(file).read { it }

        assertNull(value)
    }

    @Test
    fun validatedReplacementKeepsNewPrimaryReadableWhenBackupReplaceFails() {
        val file = temporaryFolder.newFile("restore-fail.json")
        file.writeText("old-primary")
        val dir = requireNotNull(file.parentFile)
        // 把 .bak 位置占成非空目录，使备份替换阶段无法删除旧备份，
        // 模拟双文件重建过程中回退副本侧的故障。
        val backup = dir.resolve("restore-fail.json.bak")
        assertTrue(backup.mkdir())
        File(backup, "occupied").writeText("block")

        assertThrows(Exception::class.java) {
            AtomicFileStore(file).replaceWithValidated("new-validated")
        }

        // 主文件必须先于备份重建且保持可读，故障只影响回退副本。
        val value = AtomicFileStore(file).read { it }
        assertEquals("new-validated", value)
        assertEquals("new-validated", file.readText(Charsets.UTF_8))
    }

    @Test
    fun validatedReplacementCleansStaleBackupTemporaryBeforeRetry() {
        val file = temporaryFolder.newFile("restore-retry.json")
        file.writeText("old-primary")
        val dir = requireNotNull(file.parentFile)
        val backupTemporary = dir.resolve("restore-retry.json.bak.tmp")
        // 残留的备份临时目录不应阻塞恢复流程：写入前自动清理。
        assertTrue(backupTemporary.mkdir())
        AtomicFileStore(file).replaceWithValidated("first-attempt")

        assertEquals("first-attempt", file.readText(Charsets.UTF_8))
        assertEquals("first-attempt", dir.resolve("restore-retry.json.bak").readText(Charsets.UTF_8))
        assertTrue(!backupTemporary.exists())

        AtomicFileStore(file).replaceWithValidated("second-attempt")

        assertEquals("second-attempt", file.readText(Charsets.UTF_8))
        assertEquals("second-attempt", dir.resolve("restore-retry.json.bak").readText(Charsets.UTF_8))
    }
}
