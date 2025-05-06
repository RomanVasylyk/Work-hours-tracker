package com.example.worktr.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkEntryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: WorkEntry)

    @Update
    suspend fun update(entry: WorkEntry)

    @Delete
    suspend fun delete(entry: WorkEntry)

    @Query("SELECT * FROM work_entries WHERE jobId = :jobId AND date BETWEEN :start AND :end ORDER BY date")
    fun getEntriesForPeriod(jobId: Int, start: Long, end: Long): Flow<List<WorkEntry>>

    @Query("SELECT * FROM work_entries WHERE jobId = :jobId AND date BETWEEN :start AND :end LIMIT 1")
    fun getEntryForDay(jobId: Int, start: Long, end: Long): Flow<WorkEntry?>
}

