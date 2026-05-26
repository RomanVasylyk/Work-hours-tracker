package com.example.worktr.util

import com.example.worktr.data.WorkEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class InvoiceRulesTest {
    @Test
    fun firstDayOfMonthUsesLastClosedMonth() {
        val period = InvoiceRules.selectedInvoicePeriod(
            today = LocalDate.of(2026, 5, 1),
            selected = YearMonth.of(2026, 5)
        )

        assertEquals(YearMonth.of(2026, 4), period)
    }

    @Test
    fun previousMonthSelectionStaysPreviousMonth() {
        val period = InvoiceRules.selectedInvoicePeriod(
            today = LocalDate.of(2026, 5, 26),
            selected = YearMonth.of(2026, 4)
        )

        assertEquals(YearMonth.of(2026, 4), period)
    }

    @Test
    fun futureSelectionIsClampedToLastClosedMonth() {
        val period = InvoiceRules.selectedInvoicePeriod(
            today = LocalDate.of(2026, 5, 26),
            selected = YearMonth.of(2026, 12)
        )

        assertEquals(YearMonth.of(2026, 4), period)
    }

    @Test
    fun invoiceNumberUsesYearMonthAndNextSequence() {
        assertEquals(
            "20260401",
            InvoiceRules.defaultInvoiceNumber(YearMonth.of(2026, 4), currentSequence = 0)
        )
        assertEquals(
            "20260402",
            InvoiceRules.defaultInvoiceNumber(YearMonth.of(2026, 4), currentSequence = 1)
        )
    }

    @Test
    fun sequenceIsReadFromInvoiceNumber() {
        assertEquals(
            7,
            InvoiceRules.sequenceFromInvoiceNumber(
                period = YearMonth.of(2026, 4),
                invoiceNumber = "fakrura-20260407",
                currentSequence = 1
            )
        )
    }

    @Test
    fun totalsIncludeHoursBaseAmountAndDoprava() {
        val zone = ZoneId.of("Europe/Bratislava")
        val entries = listOf(
            entry(LocalDate.of(2026, 4, 10), hours = 8.0, breakHours = 0.5, hourlyRate = 10.0, zone = zone),
            entry(LocalDate.of(2026, 4, 11), hours = 7.5, breakHours = 0.0, hourlyRate = 10.0, zone = zone)
        )
        val extra = InvoiceExtraItem(name = "Doprava", quantity = 2.0, unit = "", unitPrice = 12.5)

        val totals = InvoiceRules.calculateTotals(entries, extra)

        assertEquals(15.0, totals.hours, 0.0001)
        assertEquals(150.0, totals.servicesTotal, 0.0001)
        assertEquals(25.0, totals.extraTotal, 0.0001)
        assertEquals(175.0, totals.total, 0.0001)
        assertEquals(10.0, totals.unitPrice, 0.0001)
    }

    private fun entry(
        date: LocalDate,
        hours: Double,
        breakHours: Double,
        hourlyRate: Double,
        zone: ZoneId
    ): WorkEntry =
        WorkEntry(
            jobId = 1,
            date = date.atStartOfDay(zone).toInstant().toEpochMilli(),
            hoursWorked = hours,
            breakHours = breakHours,
            shiftType = "day",
            isHoliday = false,
            hourlyRate = hourlyRate
        )
}
