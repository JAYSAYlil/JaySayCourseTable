package com.jaysay.coursetable.data.backup

import org.json.JSONObject
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** 可移植的密码加密备份封装；密码和派生密钥从不写入文件。 */
object EncryptedBackupCodec {
    private const val FORMAT = "jaysay-course-table-encrypted-backup"
    private const val SCHEMA_VERSION = 1
    private const val KDF = "PBKDF2WithHmacSHA256"
    private const val CIPHER = "AES/GCM/NoPadding"
    private const val ITERATIONS = 210_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val MAX_ENVELOPE_BYTES = 12 * 1024 * 1024

    fun isEncrypted(text: String): Boolean = runCatching {
        JSONObject(text).optString("format") == FORMAT
    }.getOrDefault(false)

    fun encode(data: BackupData, password: CharArray, random: SecureRandom = SecureRandom()): String {
        require(password.size >= 6) { "备份密码至少需要 6 位" }
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val key = deriveKey(password, salt, ITERATIONS)
        val plaintext = BackupCodec.encode(data, sanitized = false).toByteArray(Charsets.UTF_8)
        val ciphertext = try {
            Cipher.getInstance(CIPHER).run {
                init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
                updateAAD(FORMAT.toByteArray(Charsets.UTF_8))
                doFinal(plaintext)
            }
        } finally {
            plaintext.fill(0)
            key.encoded?.fill(0)
        }
        return JSONObject()
            .put("format", FORMAT)
            .put("schemaVersion", SCHEMA_VERSION)
            .put("kdf", KDF)
            .put("iterations", ITERATIONS)
            .put("salt", Base64.getEncoder().encodeToString(salt))
            .put("cipher", CIPHER)
            .put("iv", Base64.getEncoder().encodeToString(iv))
            .put("ciphertext", Base64.getEncoder().encodeToString(ciphertext))
            .toString(2)
    }

    fun decode(text: String, password: CharArray): BackupData {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_ENVELOPE_BYTES) { "加密备份文件过大" }
        require(password.isNotEmpty()) { "请输入备份密码" }
        val root = JSONObject(text)
        require(root.optString("format") == FORMAT) { "不是 JaySay 加密备份" }
        require(root.optInt("schemaVersion", -1) == SCHEMA_VERSION) { "暂不支持此加密备份版本" }
        require(root.optString("kdf") == KDF && root.optString("cipher") == CIPHER) {
            "加密备份算法不受支持"
        }
        val iterations = root.optInt("iterations", 0)
        require(iterations in 100_000..1_000_000) { "加密备份参数无效" }
        val salt = decodeField(root, "salt", SALT_BYTES)
        val iv = decodeField(root, "iv", IV_BYTES)
        val ciphertext = runCatching { Base64.getDecoder().decode(root.getString("ciphertext")) }
            .getOrElse { throw IllegalArgumentException("加密备份内容损坏", it) }
        require(ciphertext.size in 17..MAX_ENVELOPE_BYTES) { "加密备份内容无效" }
        val key = deriveKey(password, salt, iterations)
        val plaintext = try {
            Cipher.getInstance(CIPHER).run {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
                updateAAD(FORMAT.toByteArray(Charsets.UTF_8))
                doFinal(ciphertext)
            }
        } catch (error: AEADBadTagException) {
            throw IllegalArgumentException("密码错误或备份文件已损坏", error)
        } finally {
            key.encoded?.fill(0)
        }
        return try {
            BackupCodec.decode(plaintext.toString(Charsets.UTF_8))
        } finally {
            plaintext.fill(0)
        }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        return try {
            SecretKeySpec(SecretKeyFactory.getInstance(KDF).generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun decodeField(root: JSONObject, name: String, expectedBytes: Int): ByteArray {
        val bytes = runCatching { Base64.getDecoder().decode(root.getString(name)) }
            .getOrElse { throw IllegalArgumentException("加密备份参数损坏", it) }
        require(bytes.size == expectedBytes) { "加密备份参数无效" }
        return bytes
    }
}
