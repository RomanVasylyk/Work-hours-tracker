package com.example.worktr.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Verifies the data-normalizing migrations on a real SQLite database:
 * 7→8 rewrites localized shift types to canonical codes,
 * 8→9 normalizes dates to start of day and deduplicates per calendar day.
 * Runs on a device/emulator (connectedDebugAndroidTest).
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrate7To9NormalizesShiftTypesAndDates() {
        val zone = ZoneId.systemDefault()
        val day = LocalDate.of(2026, 3, 10)
        val startOfDay = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val midDay = startOfDay + 12 * 60 * 60 * 1000L

        helper.createDatabase(DB_NAME, 7).use { db ->
            db.execSQL("INSERT INTO jobs (jobId, name, hourlyRate, nightBonus, saturdayBonus, sundayBonus, holidayBonus) VALUES (1, 'Job', 10, 2, 3, 4, 5)")
            // Localized shift types from all app languages.
            insertEntry(db, entryId = 1, date = startOfDay, shiftType = "Nočná")
            insertEntry(db, entryId = 2, date = midDay, shiftType = "Денна")
            insertEntry(db, entryId = 3, date = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), shiftType = "Morning")
        }

        helper.runMigrationsAndValidate(DB_NAME, 9, true).use { db ->
            db.query("SELECT entryId, date, shiftType FROM work_entries ORDER BY entryId").use { cursor ->
                // Entries 1 and 2 were on the same calendar day: only the
                // newest (entryId 2) survives, normalized to start of day.
                assertEquals(2, cursor.count)

                cursor.moveToFirst()
                assertEquals(2L, cursor.getLong(0))
                assertEquals(startOfDay, cursor.getLong(1))
                assertEquals("day", cursor.getString(2))

                cursor.moveToNext()
                assertEquals(3L, cursor.getLong(0))
                assertEquals("morning", cursor.getString(2))
            }
        }
    }

    private fun insertEntry(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        entryId: Long,
        date: Long,
        shiftType: String
    ) {
        db.execSQL(
            """
            INSERT INTO work_entries
                (entryId, jobId, date, hoursWorked, breakHours, shiftType, isHoliday,
                 hourlyRate, nightBonus, saturdayBonus, sundayBonus, holidayBonus)
            VALUES ($entryId, 1, $date, 8.0, 0.0, '$shiftType', 0, 10.0, 2.0, 3.0, 4.0, 5.0)
            """.trimIndent()
        )
    }

    private companion object {
        const val DB_NAME = "migration-test"
    }
}
