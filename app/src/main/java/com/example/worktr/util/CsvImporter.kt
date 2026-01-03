package com.example.worktr.util

import android.content.Context
import android.net.Uri
import com.example.worktr.data.DatabaseProvider
import com.example.worktr.data.WorkEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.LocalDate
import java.time.ZoneId

object CsvImporter {

    suspend fun importWorkEntriesCsv(context: Context, jobId: Int, uri: Uri): Int {
        val dao = DatabaseProvider.get(context).workEntryDao()
        val zone = ZoneId.systemDefault()

        return withContext(Dispatchers.IO) {
            val cr = context.contentResolver
            val input = cr.openInputStream(uri) ?: return@withContext 0
            val reader = BufferedReader(InputStreamReader(input))

            var imported = 0
            reader.useLines { lines ->
                lines.drop(1).forEach { line ->
                    val row = parseCsvLine(line) ?: return@forEach

                    val date = LocalDate.parse(row.date)
                    val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
                    val end = start + 86_399_999

                    val existing = dao.getEntryForDayOnce(jobId, start, end)

                    val entry = WorkEntry(
                        entryId = existing?.entryId ?: 0,
                        jobId = jobId,
                        date = start,
                        hoursWorked = row.hoursWorked,
                        breakHours = row.breakHours,
                        shiftType = row.shiftType,
                        isHoliday = row.isHoliday
                    )

                    dao.insert(entry)
                    imported++
                }
            }

            imported
        }
    }

    private data class CsvRow(
        val date: String,
        val hoursWorked: Double,
        val breakHours: Double,
        val shiftType: String,
        val isHoliday: Boolean
    )

    private fun parseCsvLine(line: String): CsvRow? {
        val parts = splitCsv(line)
        if (parts.size < 5) return null

        val date = parts[0].trim()
        val hours = parts[1].toNumber() ?: return null
        val br = parts[2].toNumber() ?: 0.0
        val shift = parts[3].trim()
        val hol = parts[4].trim().toBooleanLoose()

        return CsvRow(date, hours, br, shift, hol)
    }

    private fun String.toNumber(): Double? =
        trim().replace(',', '.').toDoubleOrNull()

    private fun String.toBooleanLoose(): Boolean {
        val s = trim().lowercase()
        return s == "true" || s == "1" || s == "yes" || s == "y"
    }

    private fun splitCsv(line: String): List<String> {
        val out = ArrayList<String>(8)
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when (c) {
                '"' -> inQuotes = !inQuotes
                ',' -> {
                    if (inQuotes) sb.append(c) else {
                        out.add(sb.toString())
                        sb.setLength(0)
                    }
                }
                else -> sb.append(c)
            }
            i++
        }
        out.add(sb.toString())
        return out
    }
}
