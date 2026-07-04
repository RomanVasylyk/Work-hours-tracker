package com.example.worktr

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.worktr.worker.AutoBackupWorker
import com.example.worktr.worker.InvoiceReminderWorker
import java.util.concurrent.TimeUnit

class WorkTrApp : Application() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        scheduleWorkers()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                REMINDER_CHANNEL_ID,
                getString(R.string.reminder_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun scheduleWorkers() {
        val workManager = WorkManager.getInstance(this)
        workManager.enqueueUniquePeriodicWork(
            AutoBackupWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<AutoBackupWorker>(1, TimeUnit.DAYS).build()
        )
        workManager.enqueueUniquePeriodicWork(
            InvoiceReminderWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<InvoiceReminderWorker>(1, TimeUnit.DAYS).build()
        )
    }

    companion object {
        const val REMINDER_CHANNEL_ID = "reminders"
    }
}
