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
    fun currentMonthSelectionStaysCurrentMonth() {
        val period = InvoiceRules.selectedInvoicePeriod(
            today = LocalDate.of(2026, 5, 1),
            selected = YearMonth.of(2026, 5)
        )

        assertEquals(YearMonth.of(2026, 5), period)
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
    fun futureSelectionStaysSelectedMonth() {
        val period = InvoiceRules.selectedInvoicePeriod(
            today = LocalDate.of(2026, 5, 26),
            selected = YearMonth.of(2026, 12)
        )

        assertEquals(YearMonth.of(2026, 12), period)
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
                invoiceNumber = "faktura-20260407",
                currentSequence = 1
            )
        )
    }

    @Test
    fun customInvoiceNumberDoesNotPoisonSequence() {
        assertEquals(
            4,
            InvoiceRules.sequenceFromInvoiceNumber(
                period = YearMonth.of(2026, 7),
                invoiceNumber = "FA-2026-001",
                currentSequence = 3
            )
        )
        assertEquals(
            1,
            InvoiceRules.sequenceFromInvoiceNumber(
                period = YearMonth.of(2026, 7),
                invoiceNumber = "custom",
                currentSequence = 0
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

    @Test
    fun bonusExtraItemsContainOnlyAdditionalIncome() {
        val zone = ZoneId.of("Europe/Bratislava")
        val entries = listOf(
            entry(
                LocalDate.of(2026, 4, 11),
                hours = 8.0,
                breakHours = 0.0,
                hourlyRate = 10.0,
                zone = zone,
                shiftType = "нічна",
                nightBonus = 1.5,
                saturdayBonus = 2.0
            ),
            entry(
                LocalDate.of(2026, 4, 12),
                hours = 6.0,
                breakHours = 0.0,
                hourlyRate = 10.0,
                zone = zone,
                sundayBonus = 3.0,
                holidayBonus = 4.0,
                isHoliday = true
            )
        )

        val extras = InvoiceRules.bonusExtraItems(entries, "uk", zone)
        val totals = InvoiceRules.calculateTotals(entries, extras)

        assertEquals(4, extras.size)
        assertTrue(extras.any { it.name == "Доплата нічна" && it.total == 12.0 })
        assertTrue(extras.any { it.name == "Доплата субота" && it.total == 16.0 })
        assertTrue(extras.any { it.name == "Доплата неділя" && it.total == 18.0 })
        assertTrue(extras.any { it.name == "Доплата свято" && it.total == 24.0 })
        assertEquals(140.0, totals.servicesTotal, 0.0001)
        assertEquals(210.0, totals.total, 0.0001)
    }

    @Test
    fun editableServiceQuantityRecalculatesOnlyServiceAmount() {
        val zone = ZoneId.of("Europe/Bratislava")
        val entries = listOf(
            entry(LocalDate.of(2026, 4, 10), hours = 10.0, breakHours = 0.0, hourlyRate = 12.0, zone = zone),
            entry(LocalDate.of(2026, 4, 11), hours = 10.0, breakHours = 0.0, hourlyRate = 12.0, zone = zone)
        )
        val extras = listOf(InvoiceExtraItem(name = "Doprava", quantity = 1.0, unit = "", unitPrice = 30.0))

        val totals = InvoiceRules.calculateTotals(
            entries = entries,
            extraItems = extras,
            serviceQuantityOverride = 15.0
        )

        assertEquals(15.0, totals.hours, 0.0001)
        assertEquals(180.0, totals.servicesTotal, 0.0001)
        assertEquals(30.0, totals.extraTotal, 0.0001)
        assertEquals(210.0, totals.total, 0.0001)
        assertEquals(12.0, totals.unitPrice, 0.0001)
    }

    private fun entry(
        date: LocalDate,
        hours: Double,
        breakHours: Double,
        hourlyRate: Double,
        zone: ZoneId,
        shiftType: String = "day",
        nightBonus: Double = 0.0,
        saturdayBonus: Double = 0.0,
        sundayBonus: Double = 0.0,
        holidayBonus: Double = 0.0,
        isHoliday: Boolean = false
    ): WorkEntry =
        WorkEntry(
            jobId = 1,
            date = date.atStartOfDay(zone).toInstant().toEpochMilli(),
            hoursWorked = hours,
            breakHours = breakHours,
            shiftType = shiftType,
            isHoliday = isHoliday,
            hourlyRate = hourlyRate,
            nightBonus = nightBonus,
            saturdayBonus = saturdayBonus,
            sundayBonus = sundayBonus,
            holidayBonus = holidayBonus
        )
}
