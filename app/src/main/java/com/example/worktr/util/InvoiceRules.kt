package com.example.worktr.util

import com.example.worktr.data.WorkEntry
import java.time.YearMonth
import java.time.LocalDate

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
    ): InvoiceCalculation {
        val hours = entries.sumOf { it.workedHours() }
        val servicesTotal = entries.sumOf { it.workedHours() * it.hourlyRate }
        val extraTotal = extraItem?.total ?: 0.0
        return InvoiceCalculation(
            hours = hours,
            servicesTotal = servicesTotal,
            extraTotal = extraTotal,
            total = servicesTotal + extraTotal,
            unitPrice = if (hours > 0.0) servicesTotal / hours else 0.0
        )
    }

    fun serviceDescription(template: String, slovakMonthName: String): String {
        val base = template.ifBlank { DEFAULT_SERVICE_TEMPLATE }
        return base
            .replace("{month}", slovakMonthName)
            .replace("{mesiac}", slovakMonthName)
    }

    const val DEFAULT_SERVICE_TEMPLATE =
        "Fakturujem Vám za vykonanú prácu – kontrolu kvality v mesiaci {month}"
}

data class InvoiceCalculation(
    val hours: Double,
    val servicesTotal: Double,
    val extraTotal: Double,
    val total: Double,
    val unitPrice: Double
)
