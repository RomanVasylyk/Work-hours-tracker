package com.example.worktr.util

import java.util.Locale

/**
 * Canonical shift types stored in the database. Entries created before schema v8
 * (and restored from old backups or CSV files) may still hold localized labels,
 * so [fromStored] must keep accepting Ukrainian, English and Slovak spellings.
 * [labelIndex] matches the item order of R.array.shift_types.
 */
enum class ShiftType(val code: String, val labelIndex: Int) {
    MORNING("morning", 0),
    DAY("day", 1),
    NIGHT("night", 2);

    companion object {
        fun fromStored(value: String?): ShiftType {
            val normalized = value.orEmpty().trim().lowercase(Locale.ROOT)
            return when {
                normalized.startsWith("night") ||
                    normalized.startsWith("ніч") ||
                    normalized.startsWith("noč") -> NIGHT
                normalized.startsWith("morning") ||
                    normalized.startsWith("ранк") ||
                    normalized.startsWith("rann") -> MORNING
                else -> DAY
            }
        }
    }
}
