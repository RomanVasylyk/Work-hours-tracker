package com.example.worktr.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Job::class, WorkEntry::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun jobDao(): JobDao
    abstract fun workEntryDao(): WorkEntryDao
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
                .build()
                .also { INSTANCE = it }
        }
}
