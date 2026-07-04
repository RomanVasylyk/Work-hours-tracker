package com.example.worktr.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.worktr.widget.MonthSummaryWidget

class WidgetRefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = runCatching {
        MonthSummaryWidget.update(applicationContext)
    }.fold(
        onSuccess = { Result.success() },
        onFailure = { Result.failure() }
    )

    companion object {
        const val WORK_NAME = "widget_refresh"
    }
}
