package com.example.worktr.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.worktr.MainActivity
import com.example.worktr.R
import com.example.worktr.data.DatabaseProvider
import com.example.worktr.util.WorkSummaries
import com.example.worktr.worker.WidgetRefreshWorker
import kotlinx.coroutines.flow.first
import java.text.NumberFormat
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

/**
 * Home-screen widget with the current month's hours, pay and worked days.
 * Refreshed hourly by the system, on app launch and via [WidgetRefreshWorker];
 * tapping it opens the app.
 */
class MonthSummaryWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        enqueueRefresh(context)
    }

    override fun onEnabled(context: Context) {
        enqueueRefresh(context)
    }

    companion object {
        fun enqueueRefresh(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                WidgetRefreshWorker.WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<WidgetRefreshWorker>().build()
            )
        }
    }
}

object MonthSummaryWidget {
    suspend fun update(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, MonthSummaryWidgetProvider::class.java))
        if (ids.isEmpty()) return

        val zone = ZoneId.systemDefault()
        val period = YearMonth.now()
        val start = period.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = period.atEndOfMonth().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        val entries = DatabaseProvider.get(context)
            .workEntryDao()
            .getAllEntriesForPeriod(start, end)
            .first()
        val summary = WorkSummaries.summarize(entries, zone)

        val hoursFormat = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 1
        }
        val moneyFormat = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
        val monthLabel = context.resources.getStringArray(R.array.months)[period.monthValue - 1]

        val views = RemoteViews(context.packageName, R.layout.widget_month_summary).apply {
            setTextViewText(R.id.widgetMonthTitle, "$monthLabel ${period.year}")
            if (entries.isEmpty()) {
                setTextViewText(R.id.widgetSummary, context.getString(R.string.widget_empty))
                setTextViewText(R.id.widgetDays, "")
            } else {
                setTextViewText(
                    R.id.widgetSummary,
                    context.getString(
                        R.string.widget_summary_line,
                        hoursFormat.format(summary.hours),
                        moneyFormat.format(summary.totalSalary)
                    )
                )
                setTextViewText(R.id.widgetDays, context.getString(R.string.widget_days_line, summary.daysWorked))
            }
            setOnClickPendingIntent(
                R.id.widgetRoot,
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
        }
        ids.forEach { id -> manager.updateAppWidget(id, views) }
    }
}
