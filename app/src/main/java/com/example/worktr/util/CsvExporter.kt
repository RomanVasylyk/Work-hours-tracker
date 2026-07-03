package com.example.worktr.util

import android.content.Context
import com.example.worktr.data.DatabaseProvider
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CsvExporter(private val context: Context) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    suspend fun export(jobId: Int): File = exportInternal(jobId)

    suspend fun exportAll(): File = exportInternal(null)

    private suspend fun exportInternal(jobId: Int?): File {
        val db = DatabaseProvider.get(context)
        val jobs = db.jobDao().getAllJobsList().associateBy { it.jobId }
        val entries = db.workEntryDao().getAllEntriesList()
            .filter { jobId == null || it.jobId == jobId }
            .sortedWith(compareBy({ it.date }, { it.jobId }))
        val fileName = if (jobId == null) {
            "work_entries_all_${System.currentTimeMillis()}.csv"
        } else {
            "work_entries_${jobId}_${System.currentTimeMillis()}.csv"
        }
        val file = File(context.cacheDir, fileName)
        FileWriter(file).use { writer ->
            writer.appendLine("jobId,jobName,date,hoursWorked,breakHours,shiftType,isHoliday,hourlyRate,nightBonus,saturdayBonus,sundayBonus,holidayBonus,totalSalary")
            entries.forEach { entry ->
                val exportJobName = sanitize(jobs[entry.jobId]?.name.orEmpty())
                val dateMillis = entry.date
                val dateStr = dateFormat.format(Date(dateMillis))
                val shift = sanitize(entry.shiftType)
                val totalSalary = entry.salaryBreakdown().total
                writer.appendLine(
                    "${entry.jobId},$exportJobName,$dateStr,${entry.hoursWorked},${entry.breakHours},$shift,${entry.isHoliday},${entry.hourlyRate},${entry.nightBonus},${entry.saturdayBonus},${entry.sundayBonus},${entry.holidayBonus},$totalSalary"
                )
            }
        }
        return file
    }

    private fun sanitize(value: String?): String =
        "\"${value.orEmpty().replace("\"", "\"\"")}\""
}
