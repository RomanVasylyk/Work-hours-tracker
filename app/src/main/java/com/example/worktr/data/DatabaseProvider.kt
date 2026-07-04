package com.example.worktr.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.worktr.util.ShiftType
import java.time.Instant
import java.time.ZoneId

@Database(entities = [Job::class, WorkEntry::class, InvoiceRecord::class, Client::class], version = 9, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun jobDao(): JobDao
    abstract fun workEntryDao(): WorkEntryDao
    abstract fun invoiceDao(): InvoiceDao
    abstract fun clientDao(): ClientDao
}

object DatabaseProvider {
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                DELETE FROM work_entries
                WHERE entryId NOT IN (
                    SELECT MAX(entryId)
                    FROM work_entries
                    GROUP BY jobId, date
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_work_entries_jobId_date ON work_entries(jobId, date)"
            )
        }
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE work_entries ADD COLUMN hourlyRate REAL NOT NULL DEFAULT 0.0")
            db.execSQL("ALTER TABLE work_entries ADD COLUMN nightBonus REAL NOT NULL DEFAULT 0.0")
            db.execSQL("ALTER TABLE work_entries ADD COLUMN saturdayBonus REAL NOT NULL DEFAULT 0.0")
            db.execSQL("ALTER TABLE work_entries ADD COLUMN sundayBonus REAL NOT NULL DEFAULT 0.0")
            db.execSQL("ALTER TABLE work_entries ADD COLUMN holidayBonus REAL NOT NULL DEFAULT 0.0")
            db.execSQL(
                """
                UPDATE work_entries
                SET
                    hourlyRate = COALESCE((SELECT hourlyRate FROM jobs WHERE jobs.jobId = work_entries.jobId), 0.0),
                    nightBonus = COALESCE((SELECT nightBonus FROM jobs WHERE jobs.jobId = work_entries.jobId), 0.0),
                    saturdayBonus = COALESCE((SELECT saturdayBonus FROM jobs WHERE jobs.jobId = work_entries.jobId), 0.0),
                    sundayBonus = COALESCE((SELECT sundayBonus FROM jobs WHERE jobs.jobId = work_entries.jobId), 0.0),
                    holidayBonus = COALESCE((SELECT holidayBonus FROM jobs WHERE jobs.jobId = work_entries.jobId), 0.0)
                """.trimIndent()
            )
        }
    }

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // No schema changes; keep v3-to-v4 installs compatible with current builds.
        }
    }

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS invoices (
                    invoiceId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    invoiceNumber TEXT NOT NULL,
                    jobId INTEGER NOT NULL,
                    jobName TEXT NOT NULL,
                    customerName TEXT NOT NULL,
                    periodYear INTEGER NOT NULL,
                    periodMonth INTEGER NOT NULL,
                    totalAmount REAL NOT NULL,
                    currency TEXT NOT NULL,
                    issueDate TEXT NOT NULL,
                    createdAtMillis INTEGER NOT NULL,
                    fileName TEXT NOT NULL,
                    inputJson TEXT NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_invoices_invoiceNumber ON invoices(invoiceNumber)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_invoices_jobId ON invoices(jobId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_invoices_periodYear_periodMonth ON invoices(periodYear, periodMonth)")
        }
    }

    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE invoices ADD COLUMN status TEXT NOT NULL DEFAULT 'created'")
        }
    }

    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS clients (
                    clientId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    jobId INTEGER NOT NULL,
                    name TEXT NOT NULL,
                    street TEXT NOT NULL,
                    city TEXT NOT NULL,
                    zip TEXT NOT NULL,
                    country TEXT NOT NULL,
                    ico TEXT NOT NULL,
                    dic TEXT NOT NULL,
                    icdph TEXT NOT NULL,
                    serviceTemplate TEXT NOT NULL,
                    FOREIGN KEY(jobId) REFERENCES jobs(jobId) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_clients_jobId ON clients(jobId)")
        }
    }

    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Normalize localized shiftType values ("Нічна"/"Night"/"Nočná", …)
            // to the canonical codes from ShiftType.
            val remapped = mutableMapOf<String, String>()
            db.query("SELECT DISTINCT shiftType FROM work_entries").use { cursor ->
                while (cursor.moveToNext()) {
                    val stored = cursor.getString(0) ?: continue
                    val canonical = ShiftType.fromStored(stored).code
                    if (canonical != stored) {
                        remapped[stored] = canonical
                    }
                }
            }
            remapped.forEach { (stored, canonical) ->
                db.execSQL(
                    "UPDATE work_entries SET shiftType = ? WHERE shiftType = ?",
                    arrayOf(canonical, stored)
                )
            }
        }
    }

    private val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Legacy CSV imports could store mid-day timestamps. Normalize every
            // entry to the start of its local day and keep only the newest entry
            // per (jobId, day) so the unique index really means one entry per
            // calendar day.
            val zone = ZoneId.systemDefault()
            val rows = mutableListOf<Triple<Long, Long, Long>>() // entryId, jobId, date
            db.query("SELECT entryId, jobId, date FROM work_entries").use { cursor ->
                while (cursor.moveToNext()) {
                    rows += Triple(cursor.getLong(0), cursor.getLong(1), cursor.getLong(2))
                }
            }
            rows.groupBy { (_, jobId, date) ->
                jobId to Instant.ofEpochMilli(date).atZone(zone).toLocalDate()
            }.forEach { (key, group) ->
                val normalized = key.second.atStartOfDay(zone).toInstant().toEpochMilli()
                val keeperId = group.maxOf { (entryId, _, _) -> entryId }
                group.forEach { (entryId, _, date) ->
                    when {
                        entryId != keeperId ->
                            db.execSQL("DELETE FROM work_entries WHERE entryId = ?", arrayOf(entryId))
                        date != normalized ->
                            db.execSQL(
                                "UPDATE work_entries SET date = ? WHERE entryId = ?",
                                arrayOf(normalized, entryId)
                            )
                    }
                }
            }
        }
    }

    @Volatile
    private var INSTANCE: AppDatabase? = null

    fun get(context: Context): AppDatabase =
        INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "work_tracker.db"
            )
                .addMigrations(MIGRATION_1_2)
                .addMigrations(MIGRATION_2_3)
                .addMigrations(MIGRATION_3_4)
                .addMigrations(MIGRATION_4_5)
                .addMigrations(MIGRATION_5_6)
                .addMigrations(MIGRATION_6_7)
                .addMigrations(MIGRATION_7_8)
                .addMigrations(MIGRATION_8_9)
                .build()
                .also { INSTANCE = it }
        }
}
