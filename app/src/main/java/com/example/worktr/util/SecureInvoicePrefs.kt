package com.example.worktr.util

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object SecureInvoicePrefs {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "worktr_invoice_sensitive_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val PREFS = "secure_invoice_prefs"
    private const val INVOICE_PREFS = "invoice_prefs"
    private const val KEY_IBAN = "iban"
    private const val KEY_BIC = "bic"

    fun readIban(context: Context): String =
        read(context, KEY_IBAN).ifBlank { migratePlainValue(context, KEY_IBAN) }

    fun readBic(context: Context): String =
        read(context, KEY_BIC).ifBlank { migratePlainValue(context, KEY_BIC) }

    fun saveBank(context: Context, iban: String, bic: String) {
        save(context, KEY_IBAN, iban)
        save(context, KEY_BIC, bic)
        clearPlainBankValues(context)
    }

    fun toBackupJson(context: Context): JSONObject =
        JSONObject()
            .put(KEY_IBAN, readIban(context))
            .put(KEY_BIC, readBic(context))

    fun restoreFromBackupJson(context: Context, json: JSONObject) {
        saveBank(
            context = context,
            iban = json.optString(KEY_IBAN),
            bic = json.optString(KEY_BIC)
        )
    }

    fun isSensitiveKey(key: String): Boolean =
        key == KEY_IBAN || key == KEY_BIC

    private fun migratePlainValue(context: Context, key: String): String {
        val value = context.getSharedPreferences(INVOICE_PREFS, Context.MODE_PRIVATE)
            .getString(key, "")
            .orEmpty()
        if (value.isNotBlank()) {
            save(context, key, value)
            clearPlainBankValues(context)
        }
        return value
    }

    private fun clearPlainBankValues(context: Context) {
        context.getSharedPreferences(INVOICE_PREFS, Context.MODE_PRIVATE).edit {
            remove(KEY_IBAN)
            remove(KEY_BIC)
        }
    }

    private fun read(context: Context, key: String): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val iv = prefs.getString("${key}_iv", null)?.fromBase64() ?: return ""
        val encrypted = prefs.getString("${key}_cipher", null)?.fromBase64() ?: return ""
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        }.getOrDefault("")
    }

    private fun save(context: Context, key: String, value: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString("${key}_iv", cipher.iv.base64())
            putString("${key}_cipher", encrypted.base64())
        }
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existingKey = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        return existingKey?.secretKey ?: generateKey()
    }

    private fun generateKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return keyGenerator.generateKey()
    }

    private fun ByteArray.base64(): String =
        Base64.encodeToString(this, Base64.NO_WRAP)

    private fun String.fromBase64(): ByteArray =
        Base64.decode(this, Base64.NO_WRAP)
}
