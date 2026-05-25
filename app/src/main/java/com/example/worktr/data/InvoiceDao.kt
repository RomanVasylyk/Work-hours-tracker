package com.example.worktr.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface InvoiceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(invoice: InvoiceRecord): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(invoices: List<InvoiceRecord>)

    @Update
    suspend fun update(invoice: InvoiceRecord)

    @Query("SELECT * FROM invoices ORDER BY createdAtMillis DESC")
    fun getAllInvoices(): Flow<List<InvoiceRecord>>

    @Query("SELECT * FROM invoices ORDER BY createdAtMillis DESC")
    suspend fun getAllInvoicesList(): List<InvoiceRecord>

    @Query("SELECT * FROM invoices WHERE invoiceId = :invoiceId LIMIT 1")
    suspend fun getInvoiceById(invoiceId: Long): InvoiceRecord?

    @Query("DELETE FROM invoices")
    suspend fun deleteAll()
}
