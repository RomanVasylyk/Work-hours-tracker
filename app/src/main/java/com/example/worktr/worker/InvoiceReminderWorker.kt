package com.example.worktr.worker

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.worktr.MainActivity
import com.example.worktr.R
import com.example.worktr.WorkTrApp
import com.example.worktr.data.DatabaseProvider
import com.example.worktr.util.AutoBackupManager
import com.example.worktr.util.workedHours
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * Daily check that fires a notification on the last day of the month when
 * there are logged hours but no invoice for the current period yet.
 * Opt-in via the switch in Settings; silently does nothing without the
 * notification permission.
 */
class InvoiceReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val prefs = context.getSharedPreferences(AutoBackupManager.BACKUP_PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(PREF_INVOICE_REMINDER_ENABLED, false)) return Result.success()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }

        val today = LocalDate.now()
        val period = YearMonth.from(today)
        if (today.dayOfMonth != period.lengthOfMonth()) return Result.success()

        val zone = ZoneId.systemDefault()
        val start = period.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = period.atEndOfMonth().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        val db = DatabaseProvider.get(context)
        val hasHours = db.workEntryDao()
            .getAllEntriesList()
            .any { it.date in start..end && it.workedHours() > 0.0 }
        if (!hasHours) return Result.success()
        val hasInvoice = db.invoiceDao()
            .getAllInvoicesList()
            .any { it.periodYear == period.year && it.periodMonth == period.monthValue }
        if (hasInvoice) return Result.success()

        val openIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val monthLabel = context.resources.getStringArray(R.array.months)[period.monthValue - 1]
        val notification = NotificationCompat.Builder(context, WorkTrApp.REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_nav_invoice)
            .setContentTitle(context.getString(R.string.invoice_reminder_title))
            .setContentText(context.getString(R.string.invoice_reminder_text, monthLabel))
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "invoice_reminder"
        const val PREF_INVOICE_REMINDER_ENABLED = "invoice_reminder_enabled"
        private const val NOTIFICATION_ID = 1001
    }
}
