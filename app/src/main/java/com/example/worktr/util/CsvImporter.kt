package com.example.worktr.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.withTransaction
import com.example.worktr.data.AppDatabase
import com.example.worktr.data.Job
import com.example.worktr.data.WorkEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.util.Locale

data class CsvImportSummary(
    val createdJobs: Int,
    val matchedJobs: Int,
    val importedEntries: Int
)

class CsvImporter(
    private val context: Context,
    private val database: AppDatabase
) {
    suspend fun import(uri: Uri, targetJobId: Int? = null): CsvImportSummary = withContext(Dispatchers.IO) {
        val fileName = resolveDisplayName(uri)
        val rows = context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                parseCsv(reader.readText())
            }
        } ?: throw IllegalArgumentException("Unable to open file")

        if (rows.size < 2) {
            throw IllegalArgumentException("CSV file is empty")
        }

        val headers = rows.first().map { it.trim().removePrefix("\uFEFF") }
        val importedRows = rows.drop(1)
            .filter { row -> row.any { it.isNotBlank() } }
            .mapIndexedNotNull { index, row -> parseRow(headers, row, fileName, index + 2) }

        if (importedRows.isEmpty()) {
            throw IllegalArgumentException("CSV file contains no valid rows")
        }

        database.withTransaction {
            if (targetJobId != null) {
                val targetJob = database.jobDao().getJobById(targetJobId)
                    ?: throw IllegalArgumentException("Selected job was not found")
                importedRows.forEach { row ->
                    database.workEntryDao().insert(
                        WorkEntry(
                            jobId = targetJob.jobId,
                            date = row.dateMillis,
                            hoursWorked = row.hoursWorked,
                            breakHours = row.breakHours,
                            shiftType = row.shiftType,
                            isHoliday = row.isHoliday,
                            hourlyRate = row.hourlyRate,
                            nightBonus = row.nightBonus,
                            saturdayBonus = row.saturdayBonus,
                            sundayBonus = row.sundayBonus,
                            holidayBonus = row.holidayBonus
                        )
                    )
                }
                return@withTransaction CsvImportSummary(
                    createdJobs = 0,
                    matchedJobs = 1,
                    importedEntries = importedRows.size
                )
            }

            val existingJobs = database.jobDao().getAllJobsList()
                .associateBy { normalizeKey(it.name) }
                .toMutableMap()
            val groupedRows = importedRows.groupBy { normalizeKey(it.jobName) }
            val jobIdByKey = mutableMapOf<String, Int>()
            var createdJobs = 0
            var matchedJobs = 0

            groupedRows.forEach { (key, rowsForJob) ->
                val existing = existingJobs[key]
                if (existing != null) {
                    jobIdByKey[key] = existing.jobId
                    matchedJobs++
                } else {
                    val latestRow = rowsForJob.maxByOrNull { it.dateMillis } ?: rowsForJob.first()
                    val newJobId = database.jobDao().insert(
                        Job(
                            name = latestRow.jobName,
                            hourlyRate = latestRow.hourlyRate,
                            nightBonus = latestRow.nightBonus,
                            saturdayBonus = latestRow.saturdayBonus,
                            sundayBonus = latestRow.sundayBonus,
                            holidayBonus = latestRow.holidayBonus
                        )
                    ).toInt()
                    jobIdByKey[key] = newJobId
                    existingJobs[key] = Job(
                        jobId = newJobId,
                        name = latestRow.jobName,
                        hourlyRate = latestRow.hourlyRate,
                        nightBonus = latestRow.nightBonus,
                        saturdayBonus = latestRow.saturdayBonus,
                        sundayBonus = latestRow.sundayBonus,
                        holidayBonus = latestRow.holidayBonus
                    )
                    createdJobs++
                }
            }

            importedRows.forEach { row ->
                val jobId = jobIdByKey.getValue(normalizeKey(row.jobName))
                database.workEntryDao().insert(
                    WorkEntry(
                        jobId = jobId,
                        date = row.dateMillis,
                        hoursWorked = row.hoursWorked,
                        breakHours = row.breakHours,
                        shiftType = row.shiftType,
                        isHoliday = row.isHoliday,
                        hourlyRate = row.hourlyRate,
                        nightBonus = row.nightBonus,
                        saturdayBonus = row.saturdayBonus,
                        sundayBonus = row.sundayBonus,
                        holidayBonus = row.holidayBonus
                    )
                )
            }

            CsvImportSummary(
                createdJobs = createdJobs,
                matchedJobs = matchedJobs,
                importedEntries = importedRows.size
            )
        }
    }

    private fun parseRow(
        headers: List<String>,
        values: List<String>,
        fileName: String,
        lineNumber: Int
    ): ImportedCsvRow? {
        val rowMap = headers.mapIndexed { index, header ->
            header to values.getOrElse(index) { "" }.trim()
        }.toMap()

        val dateValue = rowMap["date"].orEmpty()
        if (dateValue.isBlank()) return null

        val fallbackJobName = fileName.substringBeforeLast('.').ifBlank { "Imported job" }
        val jobName = rowMap["jobName"]
            ?.takeIf { it.isNotBlank() }
            ?: rowMap["job"]
                ?.takeIf { it.isNotBlank() }
            ?: fallbackJobName

        return ImportedCsvRow(
            jobName = jobName,
            dateMillis = parseDate(dateValue, lineNumber),
            hoursWorked = rowMap["hoursWorked"].toDoubleSafe(),
            breakHours = rowMap["breakHours"].toDoubleSafe(),
            shiftType = ShiftType.fromStored(rowMap["shiftType"]).code,
            isHoliday = rowMap["isHoliday"].toBooleanSafe(),
            hourlyRate = rowMap["hourlyRate"].toDoubleSafe(),
            nightBonus = rowMap["nightBonus"].toDoubleSafe(),
            saturdayBonus = rowMap["saturdayBonus"].toDoubleSafe(),
            sundayBonus = rowMap["sundayBonus"].toDoubleSafe(),
            holidayBonus = rowMap["holidayBonus"].toDoubleSafe()
        )
    }

    private fun parseDate(value: String, lineNumber: Int): Long {
        val zone = ZoneId.systemDefault()
        return try {
            when {
                value.all { it.isDigit() } && value.length >= 10 -> {
                    val numeric = value.toLong()
                    if (value.length > 11) numeric else Instant.ofEpochSecond(numeric).toEpochMilli()
                }
                else -> LocalDate.parse(value).atStartOfDay(zone).toInstant().toEpochMilli()
            }
        } catch (_: DateTimeParseException) {
            throw IllegalArgumentException("Invalid date at CSV line $lineNumber")
        } catch (_: NumberFormatException) {
            throw IllegalArgumentException("Invalid numeric date at CSV line $lineNumber")
        }
    }

    private fun parseCsv(content: String): List<List<String>> {
        val rows = mutableListOf<MutableList<String>>()
        var currentRow = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var index = 0
        val text = content.removePrefix("\uFEFF")

        while (index < text.length) {
            val char = text[index]
            when {
                char == '"' -> {
                    if (inQuotes && index + 1 < text.length && text[index + 1] == '"') {
                        field.append('"')
                        index++
                    } else {
                        inQuotes = !inQuotes
                    }
                }

                char == ',' && !inQuotes -> {
                    currentRow.add(field.toString())
                    field.clear()
                }

                (char == '\n' || char == '\r') && !inQuotes -> {
                    currentRow.add(field.toString())
                    field.clear()
                    if (currentRow.any { it.isNotBlank() }) {
                        rows.add(currentRow)
                    }
                    currentRow = mutableListOf()
                    if (char == '\r' && index + 1 < text.length && text[index + 1] == '\n') {
                        index++
                    }
                }

                else -> field.append(char)
            }
            index++
        }

        if (field.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(field.toString())
            if (currentRow.any { it.isNotBlank() }) {
                rows.add(currentRow)
            }
        }

        return rows
    }

    private fun resolveDisplayName(uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (columnIndex >= 0) {
                        return cursor.getString(columnIndex)
                    }
                }
            }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "imported_job.csv"
    }

    private fun normalizeKey(value: String): String =
        value.trim().lowercase(Locale.getDefault())

    private fun String?.toDoubleSafe(): Double =
        this?.replace(',', '.')?.toDoubleOrNull() ?: 0.0

    private fun String?.toBooleanSafe(): Boolean {
        val normalized = this?.trim()?.lowercase(Locale.getDefault()).orEmpty()
        return normalized == "true" || normalized == "1" || normalized == "yes"
    }
}

private data class ImportedCsvRow(
    val jobName: String,
    val dateMillis: Long,
    val hoursWorked: Double,
    val breakHours: Double,
    val shiftType: String,
    val isHoliday: Boolean,
    val hourlyRate: Double,
    val nightBonus: Double,
    val saturdayBonus: Double,
    val sundayBonus: Double,
    val holidayBonus: Double
)
