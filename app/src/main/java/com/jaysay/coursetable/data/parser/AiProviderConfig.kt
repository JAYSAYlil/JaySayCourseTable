package com.jaysay.coursetable.data.parser

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class AiProviderPreset(
    val id: String,
    val titleRes: Int,
    val url: String?,
    val defaultModel: String?
)

object AiProviderPresets {
    const val CUSTOM_ID = "custom"
    val all = listOf(
        AiProviderPreset("openai", com.jaysay.coursetable.R.string.ai_provider_openai, "https://api.openai.com/v1/chat/completions", "gpt-4.1-mini"),
        AiProviderPreset("deepseek", com.jaysay.coursetable.R.string.ai_provider_deepseek, "https://api.deepseek.com/chat/completions", "deepseek-flash"),
        AiProviderPreset("zhipu", com.jaysay.coursetable.R.string.ai_provider_zhipu, "https://open.bigmodel.cn/api/paas/v4/chat/completions", "glm-4.5"),
        AiProviderPreset("aliyun", com.jaysay.coursetable.R.string.ai_provider_aliyun, "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions", "qwen-plus"),
        AiProviderPreset(CUSTOM_ID, com.jaysay.coursetable.R.string.ai_provider_custom, null, null)
    )
    fun byId(id: String): AiProviderPreset = all.firstOrNull { it.id == id } ?: all.first()
}

data class AiProviderConfig(
    val selectedId: String = "openai",
    val models: Map<String, String> = AiProviderPresets.all.mapNotNull { p -> p.defaultModel?.let { p.id to it } }.toMap(),
    val customUrl: String = "",
    val customModel: String = ""
) {
    fun modelFor(id: String) = if (id == AiProviderPresets.CUSTOM_ID) customModel else models[id] ?: AiProviderPresets.byId(id).defaultModel.orEmpty()
    fun urlFor(id: String) = if (id == AiProviderPresets.CUSTOM_ID) customUrl else AiProviderPresets.byId(id).url.orEmpty()

    fun encode(): String = JSONObject().put("selected", selectedId).put("models", JSONObject(models))
        .put("customUrl", customUrl).put("customModel", customModel).toString()

    companion object {
        fun decode(value: String): AiProviderConfig = runCatching {
            val json = JSONObject(value)
            val modelsJson = json.optJSONObject("models") ?: JSONObject()
            val models = AiProviderPresets.all.associate { it.id to modelsJson.optString(it.id, it.defaultModel.orEmpty()) }
            AiProviderConfig(
                selectedId = AiProviderPresets.byId(json.optString("selected")).id,
                models = models,
                customUrl = json.optString("customUrl"),
                customModel = json.optString("customModel")
            )
        }.getOrDefault(AiProviderConfig())
    }
}

class AiProviderConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences("ai_provider_config", Context.MODE_PRIVATE)
    fun load() = AiProviderConfig.decode(prefs.getString("config", "").orEmpty())
    fun save(config: AiProviderConfig): Boolean = prefs.edit().putString("config", config.encode()).commit()
}

/** Per-provider AES-GCM secrets in noBackupFilesDir; plaintext is never persisted. */
class AiProviderSecretStore(context: Context, private val namespace: String = "prod") {
    init { require(namespace.matches(Regex("[a-zA-Z0-9-]{1,32}"))) }
    private val dir = File(context.noBackupFilesDir, "ai-provider-secrets-$namespace").apply { mkdirs() }
    private val keyStore: KeyStore get() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private fun alias(id: String) = "com.jaysay.coursetable.ai.key.$namespace.$id"
    private fun file(id: String) = File(dir, "$id.bin")

    private fun requireValidId(id: String) = require(AiProviderPresets.all.any { it.id == id })

    @Synchronized fun has(id: String): Boolean { requireValidId(id); return file(id).isFile || File(dir, "$id.bin.bak").isFile }

    @Synchronized fun save(id: String, secret: String) {
        requireValidId(id)
        require(secret.isNotBlank() && secret.length <= 512 && secret.none { it.isISOControl() })
        val name = alias(id)
        val store = keyStore
        val key = (store.getKey(name, null) as? SecretKey) ?: run {
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                init(KeyGenParameterSpec.Builder(name, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true).build())
            }.generateKey()
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(id.toByteArray(Charsets.UTF_8))
        val encrypted = cipher.doFinal(secret.toByteArray(Charsets.UTF_8))
        val atomic = AtomicFile(file(id))
        var output: java.io.FileOutputStream? = null
        try {
            output = atomic.startWrite()
            output.write(cipher.iv + encrypted)
            atomic.finishWrite(output)
        } catch (failure: Exception) {
            if (output != null) atomic.failWrite(output)
            throw failure
        }
    }

    @Synchronized fun load(id: String): String? {
        requireValidId(id)
        val item = file(id)
        if (!item.isFile && !File(dir, "$id.bin.bak").isFile) return null
        return try {
            val data = AtomicFile(item).openRead().use { it.readBytes() }
            require(data.size in 29..4096)
            require(data.size > 12)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, keyStore.getKey(alias(id), null), GCMParameterSpec(128, data.copyOfRange(0, 12)))
            cipher.updateAAD(id.toByteArray(Charsets.UTF_8))
            cipher.doFinal(data.copyOfRange(12, data.size)).toString(Charsets.UTF_8)
        } catch (_: Exception) {
            delete(id)
            null
        }
    }

    @Synchronized fun delete(id: String) {
        requireValidId(id)
        AtomicFile(file(id)).delete()
        runCatching { keyStore.deleteEntry(alias(id)) }
    }
}
