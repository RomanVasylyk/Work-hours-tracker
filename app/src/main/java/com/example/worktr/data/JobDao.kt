package com.example.worktr.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface JobDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(job: Job): Long

    @Update
    suspend fun update(job: Job)

    @Delete
    suspend fun delete(job: Job)

    @Query("SELECT * FROM jobs ORDER BY name")
    fun getAllJobs(): Flow<List<Job>>

    @Query("SELECT * FROM jobs ORDER BY name")
    suspend fun getAllJobsList(): List<Job>

    @Query("SELECT * FROM jobs WHERE jobId = :jobId LIMIT 1")
    suspend fun getJobById(jobId: Int): Job?

    @Query("SELECT * FROM jobs WHERE name = :name LIMIT 1")
    suspend fun getJobByName(name: String): Job?
}
