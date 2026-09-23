package com.jaysay.coursetable.data.parser

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiProviderSecretStoreTest {
    @Test fun providerSecretsAreEncryptedIsolatedAndDeletable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = AiProviderSecretStore(context, namespace = "instrumentation-test")
        val secret = "synthetic-test-key-DO-NOT-USE"
        store.save("openai", secret)
        assertTrue(store.has("openai"))
        assertEqualsSecret(secret, store.load("openai"))
        assertNull(store.load("deepseek"))
        val encryptedFile = File(File(context.noBackupFilesDir, "ai-provider-secrets-instrumentation-test"), "openai.bin")
        val bytes = encryptedFile.readBytes()
        assertFalse(bytes.toString(Charsets.UTF_8).contains(secret))
        assertTrue(encryptedFile.canonicalPath.startsWith(context.noBackupFilesDir.canonicalPath))
        File(encryptedFile.parentFile, "openai.bin.bak").writeBytes(bytes)
        encryptedFile.writeBytes(byteArrayOf(0, 1, 2))
        assertEqualsSecret(secret, store.load("openai"))
        store.delete("openai")
        assertFalse(store.has("openai"))
        assertNull(store.load("openai"))
    }

    private fun assertEqualsSecret(expected: String, actual: String?) = assertArrayEquals(expected.toByteArray(), actual?.toByteArray())
}
