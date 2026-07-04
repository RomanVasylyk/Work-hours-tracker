package com.example.worktr.util

import com.example.worktr.data.InvoiceRecord
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class InvoiceStatusTest {
    private val today = LocalDate.of(2026, 7, 4)

    @Test
    fun unpaidInvoicePastDueDateShowsOverdue() {
        assertEquals(
            InvoiceStatus.OVERDUE,
            InvoiceStatus.effective(record(status = "created", dueDate = "2026-07-01"), today)
        )
        assertEquals(
            InvoiceStatus.OVERDUE,
            InvoiceStatus.effective(record(status = "sent", dueDate = "2026-07-03"), today)
        )
    }

    @Test
    fun unpaidInvoiceBeforeDueDateKeepsStoredStatus() {
        assertEquals(
            InvoiceStatus.SENT,
            InvoiceStatus.effective(record(status = "sent", dueDate = "2026-07-04"), today)
        )
        assertEquals(
            InvoiceStatus.CREATED,
            InvoiceStatus.effective(record(status = "created", dueDate = "2026-08-01"), today)
        )
    }

    @Test
    fun paidInvoiceNeverBecomesOverdue() {
        assertEquals(
            InvoiceStatus.PAID,
            InvoiceStatus.effective(record(status = "paid", dueDate = "2020-01-01"), today)
        )
    }

    @Test
    fun brokenInputJsonFallsBackToStoredStatus() {
        assertEquals(
            InvoiceStatus.SENT,
            InvoiceStatus.effective(record(status = "sent", inputJson = "not json"), today)
        )
    }

    private fun record(
        status: String,
        dueDate: String? = null,
        inputJson: String = """{"dueDate":"$dueDate"}"""
    ): InvoiceRecord =
        InvoiceRecord(
            invoiceNumber = "20260701",
            jobId = 1,
            jobName = "Job",
            customerName = "Customer",
            periodYear = 2026,
            periodMonth = 6,
            totalAmount = 100.0,
            currency = "EUR",
            issueDate = "2026-06-30",
            createdAtMillis = 0L,
            fileName = "faktura.pdf",
            inputJson = inputJson,
            status = status
        )
}
