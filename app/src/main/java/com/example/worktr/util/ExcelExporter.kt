package com.example.worktr.util

import android.content.Context
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExcelExporter(private val context: Context) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    fun export(jobId: Int): File {
        val db = context.openOrCreateDatabase("work_tracker.db", Context.MODE_PRIVATE, null)
        val cursor = db.rawQuery(
            "SELECT date, hoursWorked, breakHours, shiftType, isHoliday FROM work_entries WHERE jobId = ?",
            arrayOf(jobId.toString())
        )
        val fileName = "work_entries_${jobId}_${System.currentTimeMillis()}.csv"
        val file = File(context.cacheDir, fileName)
        FileWriter(file).use { writer ->
            writer.appendLine("date,hoursWorked,breakHours,shiftType,isHoliday")
            while (cursor.moveToNext()) {
                val dateMillis = cursor.getLong(0)
                val dateStr = dateFormat.format(Date(dateMillis))
                val hours = cursor.getDouble(1)
                val breakHours = cursor.getDouble(2)
                val shift = cursor.getString(3)
                val holiday = cursor.getInt(4) == 1
                writer.appendLine("$dateStr,$hours,$breakHours,$shift,$holiday")
            }
        }
        cursor.close()
        db.close()
        return file
    }
}