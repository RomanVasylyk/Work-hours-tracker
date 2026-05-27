package com.example.worktr.util

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.example.worktr.data.AppDatabase
import java.time.LocalDate

object AutoBackupManager {
    const val BACKUP_PREFS = "backup_prefs"
    const val PREF_AUTO_BACKUP_TREE_URI = "auto_backup_tree_uri"
    const val PREF_AUTO_BACKUP_INTERVAL = "auto_backup_interval"
    const val PREF_LAST_AUTO_BACKUP_MILLIS = "last_auto_backup_millis"
    const val PREF_LAST_BACKUP_EXPORT_MILLIS = "last_backup_export_millis"
    const val INTERVAL_WEEKLY = "weekly"
    const val INTERVAL_MONTHLY = "monthly"

    suspend fun runIfDue(context: Context, db: AppDatabase): Boolean {
        val prefs = context.getSharedPreferences(BACKUP_PREFS, Context.MODE_PRIVATE)
        val treeUri = prefs.getString(PREF_AUTO_BACKUP_TREE_URI, null)?.let(Uri::parse)
            ?: return false
        val interval = prefs.getString(PREF_AUTO_BACKUP_INTERVAL, INTERVAL_WEEKLY) ?: INTERVAL_WEEKLY
        val now = System.currentTimeMillis()
        val last = prefs.getLong(PREF_LAST_AUTO_BACKUP_MILLIS, 0L)
        if (last > 0L && now - last < intervalMillis(interval)) return false

        exportNow(context, db, treeUri)
        prefs.edit()
            .putLong(PREF_LAST_AUTO_BACKUP_MILLIS, now)
            .putLong(PREF_LAST_BACKUP_EXPORT_MILLIS, now)
            .apply()
        return true
    }

    suspend fun exportNow(context: Context, db: AppDatabase, treeUri: Uri): BackupSummary {
        val backupUri = createBackupDocument(context, treeUri)
        val password = AutoBackupPasswordStore.read(context)
            ?: error("Auto backup password is not set.")
        return WorkBackupManager(context, db).exportEncryptedTo(backupUri, password)
    }

    fun intervalMillis(interval: String): Long =
        when (interval) {
            INTERVAL_MONTHLY -> 30L * 24L * 60L * 60L * 1000L
            else -> 7L * 24L * 60L * 60L * 1000L
        }

    private fun createBackupDocument(context: Context, treeUri: Uri): Uri {
        val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocumentId)
        val fileName = "worktr-auto-backup-${LocalDate.now()}.json"
        return DocumentsContract.createDocument(
            context.contentResolver,
            parentUri,
            "application/json",
            fileName
        ) ?: error("Cannot create backup file.")
    }
}
