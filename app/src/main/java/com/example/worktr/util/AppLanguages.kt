package com.example.worktr.util

enum class AppLanguage(val code: String, val label: String) {
    UKRAINIAN("uk", "Українська"),
    SLOVAK("sk", "Slovenčina"),
    ENGLISH("en", "English");

    companion object {
        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code == code } ?: UKRAINIAN

        fun fromLabel(label: String): AppLanguage =
            entries.firstOrNull { it.label == label } ?: UKRAINIAN
    }
}

enum class InvoiceLanguage(val code: String, val label: String) {
    SLOVAK("sk", "Slovenčina"),
    UKRAINIAN("uk", "Українська"),
    ENGLISH("en", "English");

    companion object {
        fun fromCode(code: String?): InvoiceLanguage =
            entries.firstOrNull { it.code == code } ?: SLOVAK

        fun fromLabel(label: String): InvoiceLanguage =
            entries.firstOrNull { it.label == label } ?: SLOVAK
    }
}
