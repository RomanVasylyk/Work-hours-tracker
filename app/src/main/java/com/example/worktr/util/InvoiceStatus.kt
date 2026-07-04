package com.example.worktr.util

import com.example.worktr.data.InvoiceRecord
import org.json.JSONObject
import java.time.LocalDate

enum class InvoiceStatus(val value: String) {
    CREATED("created"),
    SENT("sent"),
    PAID("paid"),
    OVERDUE("overdue");

    companion object {
        fun fromValue(value: String): InvoiceStatus =
            entries.firstOrNull { it.value == value } ?: CREATED

        /**
         * Stored status, except unpaid invoices whose due date already passed
         * are shown as OVERDUE without requiring a manual status change.
         */
        fun effective(record: InvoiceRecord, today: LocalDate = LocalDate.now()): InvoiceStatus {
            val stored = fromValue(record.status)
            if (stored == PAID || stored == OVERDUE) return stored
            val dueDate = runCatching {
                LocalDate.parse(JSONObject(record.inputJson).optString("dueDate"))
            }.getOrNull() ?: return stored
            return if (today.isAfter(dueDate)) OVERDUE else stored
        }
    }
}
