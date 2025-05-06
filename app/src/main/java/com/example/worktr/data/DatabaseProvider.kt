package com.example.worktr.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Job::class, WorkEntry::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun jobDao(): JobDao
    abstract fun workEntryDao(): WorkEntryDao
}

object DatabaseProvider {
    @Volatile
    private var INSTANCE: AppDatabase? = null

    fun get(context: Context): AppDatabase =
        INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "work_tracker.db"
            ).build().also { INSTANCE = it }
        }
}
