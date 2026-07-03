package com.example.worktr.util

import com.example.worktr.data.WorkEntry
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class WorkEntryCalculationsTest {
    private val zone = ZoneId.of("Europe/Bratislava")

    @Test
    fun nightBonusCountsForEveryLocaleLabel() {
        listOf("night", "Night", "Нічна", "Nočná").forEach { label ->
            val breakdown = entry(shiftType = label).salaryBreakdown(zone)
            assertEquals("night bonus for \"$label\"", 16.0, breakdown.night, 0.0001)
        }
    }

    @Test
    fun dayShiftGetsNoNightBonus() {
        listOf("day", "Day", "Денна", "Denná").forEach { label ->
            val breakdown = entry(shiftType = label).salaryBreakdown(zone)
            assertEquals("night bonus for \"$label\"", 0.0, breakdown.night, 0.0001)
        }
    }

    private fun entry(shiftType: String): WorkEntry =
        WorkEntry(
            jobId = 1,
            // Tuesday, so no Saturday/Sunday bonuses interfere with the assertions.
            date = LocalDate.of(2026, 6, 30).atStartOfDay(zone).toInstant().toEpochMilli(),
            hoursWorked = 8.0,
            breakHours = 0.0,
            shiftType = shiftType,
            isHoliday = false,
            hourlyRate = 10.0,
            nightBonus = 2.0
        )
}
