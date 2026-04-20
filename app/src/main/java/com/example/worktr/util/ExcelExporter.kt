package com.example.worktr.util

import android.content.Context
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExcelExporter(private val context: Context) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    fun export(jobId: Int): File = exportInternal(jobId)

    fun exportAll(): File = exportInternal(null)

    private fun exportInternal(jobId: Int?): File {
        val db = context.openOrCreateDatabase("work_tracker.db", Context.MODE_PRIVATE, null)
        val whereClause = if (jobId != null) "WHERE w.jobId = ?" else ""
        val queryArgs = if (jobId != null) arrayOf(jobId.toString()) else emptyArray()
        val cursor = db.rawQuery(
            """
            SELECT
                w.jobId,
                COALESCE(j.name, ''),
                w.date,
                w.hoursWorked,
                w.breakHours,
                w.shiftType,
                w.isHoliday,
                w.hourlyRate,
                w.nightBonus,
                w.saturdayBonus,
                w.sundayBonus,
                w.holidayBonus
            FROM work_entries w
            LEFT JOIN jobs j ON j.jobId = w.jobId
            $whereClause
            ORDER BY w.date, w.jobId
            """.trimIndent(),
            queryArgs
        )
        val fileName = if (jobId == null) {
            "work_entries_all_${System.currentTimeMillis()}.csv"
        } else {
            "work_entries_${jobId}_${System.currentTimeMillis()}.csv"
        }
        val file = File(context.cacheDir, fileName)
        FileWriter(file).use { writer ->
            writer.appendLine("jobId,jobName,date,hoursWorked,breakHours,shiftType,isHoliday,hourlyRate,nightBonus,saturdayBonus,sundayBonus,holidayBonus,totalSalary")
            while (cursor.moveToNext()) {
                val exportJobId = cursor.getInt(0)
                val exportJobName = sanitize(cursor.getString(1))
                val dateMillis = cursor.getLong(2)
                val dateStr = dateFormat.format(Date(dateMillis))
                val hours = cursor.getDouble(3)
                val breakHours = cursor.getDouble(4)
                val shift = sanitize(cursor.getString(5))
                val holiday = cursor.getInt(6) == 1
                val hourlyRate = cursor.getDouble(7)
                val nightBonus = cursor.getDouble(8)
                val saturdayBonus = cursor.getDouble(9)
                val sundayBonus = cursor.getDouble(10)
                val holidayBonus = cursor.getDouble(11)
                val workedHours = hours - breakHours
                var totalSalary = workedHours * hourlyRate
                if (shift.equals("нічна", ignoreCase = true) || shift.equals("night", ignoreCase = true)) {
                    totalSalary += workedHours * nightBonus
                }
                val dayOfWeek = java.time.Instant.ofEpochMilli(dateMillis)
                    .atZone(java.time.ZoneId.systemDefault())
                    .dayOfWeek
                if (dayOfWeek == java.time.DayOfWeek.SATURDAY) {
                    totalSalary += workedHours * saturdayBonus
                }
                if (dayOfWeek == java.time.DayOfWeek.SUNDAY) {
                    totalSalary += workedHours * sundayBonus
                }
                if (holiday) {
                    totalSalary += workedHours * holidayBonus
                }
                writer.appendLine(
                    "$exportJobId,$exportJobName,$dateStr,$hours,$breakHours,$shift,$holiday,$hourlyRate,$nightBonus,$saturdayBonus,$sundayBonus,$holidayBonus,$totalSalary"
                )
            }
        }
        cursor.close()
        db.close()
        return file
    }

    private fun sanitize(value: String?): String =
        "\"${value.orEmpty().replace("\"", "\"\"")}\""
}
