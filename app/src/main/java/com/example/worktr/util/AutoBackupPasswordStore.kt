package com.example.worktr.util

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object AutoBackupPasswordStore {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "worktr_auto_backup_password_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val PREF_PASSWORD_IV = "auto_backup_password_iv"
    private const val PREF_PASSWORD_CIPHER = "auto_backup_password_cipher"

    fun save(context: Context, password: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
        context.getSharedPreferences(AutoBackupManager.BACKUP_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_PASSWORD_IV, cipher.iv.base64())
            .putString(PREF_PASSWORD_CIPHER, encrypted.base64())
            .apply()
    }

    fun read(context: Context): String? {
        val prefs = context.getSharedPreferences(AutoBackupManager.BACKUP_PREFS, Context.MODE_PRIVATE)
        val iv = prefs.getString(PREF_PASSWORD_IV, null)?.fromBase64() ?: return null
        val encrypted = prefs.getString(PREF_PASSWORD_CIPHER, null)?.fromBase64() ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
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
