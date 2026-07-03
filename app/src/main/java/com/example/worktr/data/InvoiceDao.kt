package com.example.worktr.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface InvoiceDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(invoice: InvoiceRecord): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(invoices: List<InvoiceRecord>)

    @Update
    suspend fun update(invoice: InvoiceRecord)

    @Query("UPDATE invoices SET status = :status WHERE invoiceId = :invoiceId")
    suspend fun updateStatus(invoiceId: Long, status: String)

    @Query("DELETE FROM invoices WHERE invoiceId = :invoiceId")
    suspend fun deleteById(invoiceId: Long)

    @Query("SELECT * FROM invoices ORDER BY createdAtMillis DESC")
    fun getAllInvoices(): Flow<List<InvoiceRecord>>

    @Query("SELECT * FROM invoices ORDER BY createdAtMillis DESC")
    suspend fun getAllInvoicesList(): List<InvoiceRecord>

    @Query("SELECT * FROM invoices WHERE invoiceId = :invoiceId LIMIT 1")
    suspend fun getInvoiceById(invoiceId: Long): InvoiceRecord?

    @Query("SELECT * FROM invoices WHERE invoiceNumber = :invoiceNumber LIMIT 1")
    suspend fun getInvoiceByNumber(invoiceNumber: String): InvoiceRecord?

    @Query("DELETE FROM invoices")
    suspend fun deleteAll()
}
