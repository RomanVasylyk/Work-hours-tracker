package com.example.worktr.util

import com.example.worktr.data.WorkEntry
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class SalaryBreakdown(
    val base: Double,
    val night: Double,
    val saturday: Double,
    val sunday: Double,
    val holiday: Double
) {
    val total: Double
        get() = base + night + saturday + sunday + holiday
}

fun WorkEntry.localDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochMilli(date).atZone(zone).toLocalDate()

fun WorkEntry.workedHours(): Double = (hoursWorked - breakHours).coerceAtLeast(0.0)

fun WorkEntry.salaryBreakdown(zone: ZoneId = ZoneId.systemDefault()): SalaryBreakdown {
    val workedHours = workedHours()
    val date = localDate(zone)
    val base = workedHours * hourlyRate
    val night = if (ShiftType.fromStored(shiftType) == ShiftType.NIGHT) {
        workedHours * nightBonus
    } else {
        0.0
    }
    val saturday = if (date.dayOfWeek == DayOfWeek.SATURDAY) workedHours * saturdayBonus else 0.0
    val sunday = if (date.dayOfWeek == DayOfWeek.SUNDAY) workedHours * sundayBonus else 0.0
    val holiday = if (isHoliday) workedHours * holidayBonus else 0.0
    return SalaryBreakdown(
        base = base,
        night = night,
        saturday = saturday,
        sunday = sunday,
        holiday = holiday
    )
}
