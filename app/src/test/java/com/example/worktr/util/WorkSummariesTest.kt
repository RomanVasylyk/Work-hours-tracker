package com.example.worktr.util

import com.example.worktr.data.WorkEntry
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class WorkSummariesTest {
    private val zone = ZoneId.of("Europe/Bratislava")

    @Test
    fun countsShiftsDaysAndBonusesAcrossLocales() {
        val entries = listOf(
            // Friday, Slovak morning shift
            entry(LocalDate.of(2026, 7, 3), hours = 8.0, shiftType = "Ranná"),
            // Saturday, English night shift
            entry(LocalDate.of(2026, 7, 4), hours = 8.0, shiftType = "Night"),
            // Sunday holiday, canonical day shift
            entry(LocalDate.of(2026, 7, 5), hours = 6.0, shiftType = "day", isHoliday = true)
        )

        val summary = WorkSummaries.summarize(entries, zone)

        assertEquals(22.0, summary.hours, 0.0001)
        assertEquals(3, summary.daysWorked)
        assertEquals(1, summary.morningShifts)
        assertEquals(1, summary.dayShifts)
        assertEquals(1, summary.nightShifts)
        assertEquals(1, summary.saturdays)
        assertEquals(1, summary.sundays)
        assertEquals(1, summary.holidayDays)
        assertEquals(220.0, summary.baseSalary, 0.0001)
        assertEquals(16.0, summary.bonusNight, 0.0001)   // 8h night * 2.0
        assertEquals(24.0, summary.bonusSaturday, 0.0001) // 8h saturday * 3.0
        assertEquals(24.0, summary.bonusSunday, 0.0001)   // 6h sunday * 4.0
        assertEquals(30.0, summary.bonusHoliday, 0.0001)  // 6h holiday * 5.0
        assertEquals(220.0 + 16.0 + 24.0 + 24.0 + 30.0, summary.totalSalary, 0.0001)
    }

    @Test
    fun breakHoursReduceWorkedTime() {
        val summary = WorkSummaries.summarize(
            listOf(entry(LocalDate.of(2026, 7, 1), hours = 8.0, breakHours = 0.5, shiftType = "day")),
            zone
        )

        assertEquals(7.5, summary.hours, 0.0001)
        assertEquals(75.0, summary.baseSalary, 0.0001)
    }

    @Test
    fun emptyListGivesZeroSummary() {
        assertEquals(PeriodWorkSummary(), WorkSummaries.summarize(emptyList(), zone))
    }

    private fun entry(
        date: LocalDate,
        hours: Double,
        shiftType: String,
        breakHours: Double = 0.0,
        isHoliday: Boolean = false
    ): WorkEntry =
        WorkEntry(
            jobId = 1,
            date = date.atStartOfDay(zone).toInstant().toEpochMilli(),
            hoursWorked = hours,
            breakHours = breakHours,
            shiftType = shiftType,
            isHoliday = isHoliday,
            hourlyRate = 10.0,
            nightBonus = 2.0,
            saturdayBonus = 3.0,
            sundayBonus = 4.0,
            holidayBonus = 5.0
        )
}
