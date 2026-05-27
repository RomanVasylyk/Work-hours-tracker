package com.example.worktr.util

import android.util.Base64
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object BackupCrypto {
    private const val WRAPPER_VERSION = 1
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KDF = "PBKDF2WithHmacSHA256"

    fun encrypt(json: JSONObject, password: String): JSONObject {
        require(password.length >= 6) { "Backup password must contain at least 6 characters." }
        val salt = randomBytes(SALT_BYTES)
        val iv = randomBytes(IV_BYTES)
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        }
        val encrypted = cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        return JSONObject()
            .put("schemaVersion", 1)
            .put("encrypted", true)
            .put("wrapperVersion", WRAPPER_VERSION)
            .put("kdf", KDF)
            .put("iterations", ITERATIONS)
            .put("salt", salt.base64())
            .put("iv", iv.base64())
            .put("cipher", "AES-256-GCM")
            .put("payload", encrypted.base64())
    }

    fun decrypt(wrapper: JSONObject, password: String): JSONObject {
        require(password.isNotBlank()) { "Backup password is required." }
        val salt = wrapper.getString("salt").fromBase64()
        val iv = wrapper.getString("iv").fromBase64()
        val payload = wrapper.getString("payload").fromBase64()
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        }
        val plain = cipher.doFinal(payload).toString(Charsets.UTF_8)
        return JSONObject(plain)
    }

    fun isEncrypted(json: JSONObject): Boolean =
        json.optBoolean("encrypted", false) && json.has("payload")

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS)
        val bytes = SecretKeyFactory.getInstance(KDF).generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }

    private fun randomBytes(size: Int): ByteArray =
        ByteArray(size).also { SecureRandom().nextBytes(it) }

    private fun ByteArray.base64(): String =
        Base64.encodeToString(this, Base64.NO_WRAP)

    private fun String.fromBase64(): ByteArray =
        Base64.decode(this, Base64.DEFAULT)
}
