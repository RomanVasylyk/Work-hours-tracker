package com.example.worktr.util

import com.example.worktr.data.WorkEntry
import java.time.DayOfWeek
import java.time.ZoneId

/**
 * Aggregated numbers for a list of entries. Pure and unit-testable — the
 * fragments only render what this returns.
 */
data class PeriodWorkSummary(
    val hours: Double = 0.0,
    val baseSalary: Double = 0.0,
    val bonusNight: Double = 0.0,
    val bonusSaturday: Double = 0.0,
    val bonusSunday: Double = 0.0,
    val bonusHoliday: Double = 0.0,
    val daysWorked: Int = 0,
    val morningShifts: Int = 0,
    val dayShifts: Int = 0,
    val nightShifts: Int = 0,
    val saturdays: Int = 0,
    val sundays: Int = 0,
    val holidayDays: Int = 0
) {
    val totalSalary: Double
        get() = baseSalary + bonusNight + bonusSaturday + bonusSunday + bonusHoliday
}

object WorkSummaries {
    fun summarize(entries: List<WorkEntry>, zone: ZoneId = ZoneId.systemDefault()): PeriodWorkSummary {
        var hours = 0.0
        var base = 0.0
        var night = 0.0
        var saturday = 0.0
        var sunday = 0.0
        var holiday = 0.0
        var morning = 0
        var dayCount = 0
        var nightCount = 0
        var saturdayDays = 0
        var sundayDays = 0
        val dates = mutableSetOf<java.time.LocalDate>()
        val holidayDates = mutableSetOf<java.time.LocalDate>()

        entries.forEach { entry ->
            hours += entry.workedHours()

            when (ShiftType.fromStored(entry.shiftType)) {
                ShiftType.MORNING -> morning++
                ShiftType.DAY -> dayCount++
                ShiftType.NIGHT -> nightCount++
            }

            val date = entry.localDate(zone)
            if (dates.add(date)) {
                if (date.dayOfWeek == DayOfWeek.SATURDAY) saturdayDays++
                if (date.dayOfWeek == DayOfWeek.SUNDAY) sundayDays++
            }
            if (entry.isHoliday) holidayDates.add(date)

            val breakdown = entry.salaryBreakdown(zone)
            base += breakdown.base
            night += breakdown.night
            saturday += breakdown.saturday
            sunday += breakdown.sunday
            holiday += breakdown.holiday
        }

        return PeriodWorkSummary(
            hours = hours,
            baseSalary = base,
            bonusNight = night,
            bonusSaturday = saturday,
            bonusSunday = sunday,
            bonusHoliday = holiday,
            daysWorked = dates.size,
            morningShifts = morning,
            dayShifts = dayCount,
            nightShifts = nightCount,
            saturdays = saturdayDays,
            sundays = sundayDays,
            holidayDays = holidayDates.size
        )
    }
}
