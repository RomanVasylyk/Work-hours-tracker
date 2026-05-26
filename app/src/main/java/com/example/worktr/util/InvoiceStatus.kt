package com.example.worktr.util

enum class InvoiceStatus(val value: String) {
    CREATED("created"),
    SENT("sent"),
    PAID("paid"),
    OVERDUE("overdue");

    companion object {
        fun fromValue(value: String): InvoiceStatus =
            entries.firstOrNull { it.value == value } ?: CREATED
    }
}
