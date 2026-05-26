package com.example.worktr.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "invoices",
    indices = [
        Index(value = ["invoiceNumber"], unique = true),
        Index(value = ["jobId"]),
        Index(value = ["periodYear", "periodMonth"])
    ]
)
data class InvoiceRecord(
    @PrimaryKey(autoGenerate = true) val invoiceId: Long = 0,
    val invoiceNumber: String,
    val jobId: Int,
    val jobName: String,
    val customerName: String,
    val periodYear: Int,
    val periodMonth: Int,
    val totalAmount: Double,
    val currency: String,
    val issueDate: String,
    val createdAtMillis: Long,
    val fileName: String,
    val inputJson: String,
    @ColumnInfo(defaultValue = "created") val status: String = "created"
)
