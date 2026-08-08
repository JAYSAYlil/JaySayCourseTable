package com.jaysay.coursetable.data.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        assertTrue(requireNotNull(file.parentFile).resolve("state.json.bak").exists())
    }
}
