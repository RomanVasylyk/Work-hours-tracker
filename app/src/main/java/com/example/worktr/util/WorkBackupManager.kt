package com.example.worktr.util

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.core.content.edit
import androidx.room.withTransaction
import com.example.worktr.data.AppDatabase
import com.example.worktr.data.InvoiceRecord
import com.example.worktr.data.Job
import com.example.worktr.data.WorkEntry
import org.json.JSONArray
import org.json.JSONObject

data class BackupSummary(
    val jobs: Int,
    val entries: Int,
    val invoices: Int
)

class WorkBackupManager(
    private val context: Context,
    private val db: AppDatabase
) {
    suspend fun exportTo(uri: Uri): BackupSummary {
        val jobs = db.jobDao().getAllJobsList()
        val entries = db.workEntryDao().getAllEntriesList()
        val invoices = db.invoiceDao().getAllInvoicesList()
        val json = JSONObject()
            .put("schemaVersion", BACKUP_SCHEMA_VERSION)
            .put("exportedAtMillis", System.currentTimeMillis())
            .put("jobs", jobsToJson(jobs))
            .put("workEntries", entriesToJson(entries))
            .put("invoices", invoicesToJson(invoices))
            .put("invoicePrefs", preferencesToJson(INVOICE_PREFS))

        context.contentResolver.openOutputStream(uri)?.use { output ->
            output.write(json.toString(2).toByteArray(Charsets.UTF_8))
        } ?: error("Cannot open backup file.")

        return BackupSummary(jobs.size, entries.size, invoices.size)
    }

    suspend fun restoreFrom(uri: Uri): BackupSummary {
        val json = readBackupJson(uri)
        val jobs = jobsFromJson(json.optJSONArray("jobs") ?: JSONArray())
        val entries = entriesFromJson(json.optJSONArray("workEntries") ?: JSONArray())
        val invoices = invoicesFromJson(json.optJSONArray("invoices") ?: JSONArray())

        db.withTransaction {
            db.invoiceDao().deleteAll()
            db.workEntryDao().deleteAll()
            db.jobDao().deleteAll()
            db.jobDao().insertAll(jobs)
            db.workEntryDao().insertAll(entries)
            db.invoiceDao().insertAll(invoices)
        }
        restoreInvoiceFiles(json.optJSONArray("invoices") ?: JSONArray())
        restorePreferences(INVOICE_PREFS, json.optJSONObject("invoicePrefs") ?: JSONObject())

        return BackupSummary(jobs.size, entries.size, invoices.size)
    }

    suspend fun previewFrom(uri: Uri): BackupSummary {
        val json = readBackupJson(uri)
        return BackupSummary(
            jobs = json.optJSONArray("jobs")?.length() ?: 0,
            entries = json.optJSONArray("workEntries")?.length() ?: 0,
            invoices = json.optJSONArray("invoices")?.length() ?: 0
        )
    }

    private fun readBackupJson(uri: Uri): JSONObject {
        val text = context.contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes().toString(Charsets.UTF_8)
        } ?: error("Cannot read backup file.")
        return JSONObject(text)
    }

    private fun jobsToJson(jobs: List<Job>): JSONArray =
        JSONArray().apply {
            jobs.forEach { job ->
                put(
                    JSONObject()
                        .put("jobId", job.jobId)
                        .put("name", job.name)
                        .put("hourlyRate", job.hourlyRate)
                        .put("nightBonus", job.nightBonus)
                        .put("saturdayBonus", job.saturdayBonus)
                        .put("sundayBonus", job.sundayBonus)
                        .put("holidayBonus", job.holidayBonus)
                )
            }
        }

    private fun entriesToJson(entries: List<WorkEntry>): JSONArray =
        JSONArray().apply {
            entries.forEach { entry ->
                put(
                    JSONObject()
                        .put("entryId", entry.entryId)
                        .put("jobId", entry.jobId)
                        .put("date", entry.date)
                        .put("hoursWorked", entry.hoursWorked)
                        .put("breakHours", entry.breakHours)
                        .put("shiftType", entry.shiftType)
                        .put("isHoliday", entry.isHoliday)
                        .put("hourlyRate", entry.hourlyRate)
                        .put("nightBonus", entry.nightBonus)
                        .put("saturdayBonus", entry.saturdayBonus)
                        .put("sundayBonus", entry.sundayBonus)
                        .put("holidayBonus", entry.holidayBonus)
                )
            }
        }

    private fun invoicesToJson(invoices: List<InvoiceRecord>): JSONArray =
        JSONArray().apply {
            invoices.forEach { invoice ->
                val pdfFile = InvoiceFiles.fileFor(context, invoice.fileName)
                put(
                    JSONObject()
                        .put("invoiceId", invoice.invoiceId)
                        .put("invoiceNumber", invoice.invoiceNumber)
                        .put("jobId", invoice.jobId)
                        .put("jobName", invoice.jobName)
                        .put("customerName", invoice.customerName)
                        .put("periodYear", invoice.periodYear)
                        .put("periodMonth", invoice.periodMonth)
                        .put("totalAmount", invoice.totalAmount)
                        .put("currency", invoice.currency)
                        .put("issueDate", invoice.issueDate)
                        .put("createdAtMillis", invoice.createdAtMillis)
                        .put("fileName", invoice.fileName)
                        .put("inputJson", invoice.inputJson)
                        .put("status", invoice.status)
                        .put(
                            "pdfBase64",
                            if (pdfFile.exists()) {
                                Base64.encodeToString(pdfFile.readBytes(), Base64.NO_WRAP)
                            } else {
                                ""
                            }
                        )
                )
            }
        }

    private fun jobsFromJson(array: JSONArray): List<Job> =
        (0 until array.length()).map { index ->
            val json = array.getJSONObject(index)
            Job(
                jobId = json.optInt("jobId"),
                name = json.optString("name"),
                hourlyRate = json.optDouble("hourlyRate", 0.0),
                nightBonus = json.optDouble("nightBonus", 0.0),
                saturdayBonus = json.optDouble("saturdayBonus", 0.0),
                sundayBonus = json.optDouble("sundayBonus", 0.0),
                holidayBonus = json.optDouble("holidayBonus", 0.0)
            )
        }

    private fun entriesFromJson(array: JSONArray): List<WorkEntry> =
        (0 until array.length()).map { index ->
            val json = array.getJSONObject(index)
            WorkEntry(
                entryId = json.optInt("entryId"),
                jobId = json.optInt("jobId"),
                date = json.optLong("date"),
                hoursWorked = json.optDouble("hoursWorked", 0.0),
                breakHours = json.optDouble("breakHours", 0.0),
                shiftType = json.optString("shiftType"),
                isHoliday = json.optBoolean("isHoliday", false),
                hourlyRate = json.optDouble("hourlyRate", 0.0),
                nightBonus = json.optDouble("nightBonus", 0.0),
                saturdayBonus = json.optDouble("saturdayBonus", 0.0),
                sundayBonus = json.optDouble("sundayBonus", 0.0),
                holidayBonus = json.optDouble("holidayBonus", 0.0)
            )
        }

    private fun invoicesFromJson(array: JSONArray): List<InvoiceRecord> =
        (0 until array.length()).map { index ->
            val json = array.getJSONObject(index)
            InvoiceRecord(
                invoiceId = json.optLong("invoiceId"),
                invoiceNumber = json.optString("invoiceNumber"),
                jobId = json.optInt("jobId"),
                jobName = json.optString("jobName"),
                customerName = json.optString("customerName"),
                periodYear = json.optInt("periodYear"),
                periodMonth = json.optInt("periodMonth"),
                totalAmount = json.optDouble("totalAmount", 0.0),
                currency = json.optString("currency", "EUR"),
                issueDate = json.optString("issueDate"),
                createdAtMillis = json.optLong("createdAtMillis"),
                fileName = json.optString("fileName"),
                inputJson = json.optString("inputJson"),
                status = json.optString("status", "created")
            )
        }

    private fun restoreInvoiceFiles(invoices: JSONArray) {
        InvoiceFiles.directory(context)
        for (index in 0 until invoices.length()) {
            val json = invoices.getJSONObject(index)
            val fileName = json.optString("fileName")
            val pdfBase64 = json.optString("pdfBase64")
            if (fileName.isBlank() || pdfBase64.isBlank()) continue
            InvoiceFiles.fileFor(context, fileName).writeBytes(Base64.decode(pdfBase64, Base64.DEFAULT))
        }
    }

    private fun preferencesToJson(name: String): JSONObject =
        JSONObject().apply {
            context.getSharedPreferences(name, Context.MODE_PRIVATE).all.forEach { (key, value) ->
                put(
                    key,
                    JSONObject().apply {
                        when (value) {
                            is String -> {
                                put("type", "string")
                                put("value", value)
                            }
                            is Int -> {
                                put("type", "int")
                                put("value", value)
                            }
                            is Long -> {
                                put("type", "long")
                                put("value", value)
                            }
                            is Float -> {
                                put("type", "float")
                                put("value", value.toDouble())
                            }
                            is Boolean -> {
                                put("type", "boolean")
                                put("value", value)
                            }
                        }
                    }
                )
            }
        }

    private fun restorePreferences(name: String, json: JSONObject) {
        context.getSharedPreferences(name, Context.MODE_PRIVATE).edit {
            clear()
            json.keys().forEach { key ->
                val item = json.getJSONObject(key)
                when (item.optString("type")) {
                    "string" -> putString(key, item.optString("value"))
                    "int" -> putInt(key, item.optInt("value"))
                    "long" -> putLong(key, item.optLong("value"))
                    "float" -> putFloat(key, item.optDouble("value").toFloat())
                    "boolean" -> putBoolean(key, item.optBoolean("value"))
                }
            }
        }
    }

    private companion object {
        const val BACKUP_SCHEMA_VERSION = 1
        const val INVOICE_PREFS = "invoice_prefs"
    }
}
