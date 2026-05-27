package com.example.worktr.util

import com.example.worktr.data.WorkEntry
import java.time.YearMonth
import java.time.LocalDate
import java.time.ZoneId

object InvoiceRules {
    fun selectedInvoicePeriod(today: LocalDate, selected: YearMonth): YearMonth {
        val lastClosedMonth = YearMonth.from(today).minusMonths(1)
        return if (selected > lastClosedMonth) lastClosedMonth else selected
    }

    fun defaultInvoiceNumber(period: YearMonth, currentSequence: Int): String =
        "${period.year}${period.monthValue.toString().padStart(2, '0')}${(currentSequence + 1).toString().padStart(2, '0')}"

    fun sequencePrefKey(period: YearMonth): String =
        "invoice_sequence_${period.year}${period.monthValue.toString().padStart(2, '0')}"

    fun sequenceFromInvoiceNumber(period: YearMonth, invoiceNumber: String, currentSequence: Int): Int {
        val prefix = "${period.year}${period.monthValue.toString().padStart(2, '0')}"
        val digits = invoiceNumber.filter { it.isDigit() }
        return digits.removePrefix(prefix).toIntOrNull() ?: (currentSequence + 1)
    }

    fun calculateTotals(
        entries: List<WorkEntry>,
        extraItem: InvoiceExtraItem? = null
    ): InvoiceCalculation =
        calculateTotals(entries, listOfNotNull(extraItem))

    fun calculateTotals(
        entries: List<WorkEntry>,
        extraItems: List<InvoiceExtraItem>
    ): InvoiceCalculation {
        val hours = entries.sumOf { it.workedHours() }
        val servicesTotal = entries.sumOf { it.workedHours() * it.hourlyRate }
        val extraTotal = extraItems.sumOf { it.total }
        return InvoiceCalculation(
            hours = hours,
            servicesTotal = servicesTotal,
            extraTotal = extraTotal,
            total = servicesTotal + extraTotal,
            unitPrice = if (hours > 0.0) servicesTotal / hours else 0.0
        )
    }

    fun bonusExtraItems(
        entries: List<WorkEntry>,
        languageCode: String,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<InvoiceExtraItem> {
        val language = InvoiceLanguage.fromCode(languageCode)
        val totals = entries
            .map { it.salaryBreakdown(zone) }
            .fold(BonusTotals()) { acc, breakdown ->
                acc.copy(
                    night = acc.night + breakdown.night,
                    saturday = acc.saturday + breakdown.saturday,
                    sunday = acc.sunday + breakdown.sunday,
                    holiday = acc.holiday + breakdown.holiday
                )
            }
        return listOf(
            invoiceBonusItem(bonusLabel(language, BonusType.NIGHT), totals.night),
            invoiceBonusItem(bonusLabel(language, BonusType.SATURDAY), totals.saturday),
            invoiceBonusItem(bonusLabel(language, BonusType.SUNDAY), totals.sunday),
            invoiceBonusItem(bonusLabel(language, BonusType.HOLIDAY), totals.holiday)
        ).filter { it.total > 0.0 }
    }

    fun serviceDescription(template: String, slovakMonthName: String): String {
        val base = template.ifBlank { DEFAULT_SERVICE_TEMPLATE }
        return base
            .replace("{month}", slovakMonthName)
            .replace("{mesiac}", slovakMonthName)
    }

    const val DEFAULT_SERVICE_TEMPLATE =
        "Fakturujem Vám za vykonanú prácu – kontrolu kvality v mesiaci {month}"

    private fun invoiceBonusItem(name: String, amount: Double): InvoiceExtraItem =
        InvoiceExtraItem(
            name = name,
            quantity = 1.0,
            unit = "",
            unitPrice = amount
        )

    private fun bonusLabel(language: InvoiceLanguage, type: BonusType): String =
        when (language) {
            InvoiceLanguage.SLOVAK -> when (type) {
                BonusType.NIGHT -> "Príplatok nočná"
                BonusType.SATURDAY -> "Príplatok sobota"
                BonusType.SUNDAY -> "Príplatok nedeľa"
                BonusType.HOLIDAY -> "Príplatok sviatok"
            }
            InvoiceLanguage.UKRAINIAN -> when (type) {
                BonusType.NIGHT -> "Доплата нічна"
                BonusType.SATURDAY -> "Доплата субота"
                BonusType.SUNDAY -> "Доплата неділя"
                BonusType.HOLIDAY -> "Доплата свято"
            }
            InvoiceLanguage.ENGLISH -> when (type) {
                BonusType.NIGHT -> "Night bonus"
                BonusType.SATURDAY -> "Saturday bonus"
                BonusType.SUNDAY -> "Sunday bonus"
                BonusType.HOLIDAY -> "Holiday bonus"
            }
        }

    private data class BonusTotals(
        val night: Double = 0.0,
        val saturday: Double = 0.0,
        val sunday: Double = 0.0,
        val holiday: Double = 0.0
    )

    private enum class BonusType {
        NIGHT,
        SATURDAY,
        SUNDAY,
        HOLIDAY
    }
}

data class InvoiceCalculation(
    val hours: Double,
    val servicesTotal: Double,
    val extraTotal: Double,
    val total: Double,
    val unitPrice: Double
)
