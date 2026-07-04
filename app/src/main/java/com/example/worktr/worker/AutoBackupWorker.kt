package com.example.worktr.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.worktr.data.DatabaseProvider
import com.example.worktr.util.AutoBackupManager

/**
 * Daily background check that runs the encrypted auto backup when it is due,
 * so backups happen even if the app is not opened for weeks.
 * [AutoBackupManager.runIfDue] itself decides whether the interval has passed
 * and whether a backup folder is configured.
 */
class AutoBackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = runCatching {
        AutoBackupManager.runIfDue(applicationContext, DatabaseProvider.get(applicationContext))
    }.fold(
        onSuccess = { Result.success() },
        // Folder permission may be temporarily unavailable (e.g. SD card); retry later.
        onFailure = { Result.retry() }
    )

    companion object {
        const val WORK_NAME = "auto_backup"
    }
}
