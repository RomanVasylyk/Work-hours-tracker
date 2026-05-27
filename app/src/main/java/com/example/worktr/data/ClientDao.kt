package com.example.worktr.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ClientDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(client: Client): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(clients: List<Client>)

    @Update
    suspend fun update(client: Client)

    @Query("SELECT * FROM clients ORDER BY name")
    fun getAllClients(): Flow<List<Client>>

    @Query("SELECT * FROM clients ORDER BY name")
    suspend fun getAllClientsList(): List<Client>

    @Query("SELECT * FROM clients WHERE jobId = :jobId LIMIT 1")
    suspend fun getClientForJob(jobId: Int): Client?

    @Query("DELETE FROM clients")
    suspend fun deleteAll()
}
