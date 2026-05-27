package com.example.worktr.util

import io.github.janhalasa.paybysquare.service.PayBySquareEncoder
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object PaymentValidation {
    fun normalizeBankValue(value: String): String =
        value.replace(Regex("\\s+"), "").uppercase(Locale.ROOT)

    fun isValidIban(value: String): Boolean {
        val iban = normalizeBankValue(value)
        if (iban.length !in 15..34) return false
        if (!iban.take(2).all { it in 'A'..'Z' }) return false
        if (!iban.drop(2).take(2).all { it.isDigit() }) return false
        if (!iban.all { it.isDigit() || it in 'A'..'Z' }) return false
        val rearranged = iban.drop(4) + iban.take(4)
        val numeric = buildString {
            rearranged.forEach { char ->
                when {
                    char.isDigit() -> append(char)
                    char in 'A'..'Z' -> append(char.code - 'A'.code + 10)
                    else -> return false
                }
            }
        }
        return runCatching { BigInteger(numeric).mod(BigInteger.valueOf(97L)) == BigInteger.ONE }
            .getOrDefault(false)
    }

    fun isValidBic(value: String): Boolean {
        val bic = normalizeBankValue(value)
        return Regex("^[A-Z]{4}[A-Z]{2}[A-Z0-9]{2}([A-Z0-9]{3})?$").matches(bic)
    }

    fun inferSlovakBic(iban: String): String? {
        val normalized = normalizeBankValue(iban)
        if (!normalized.startsWith("SK") || normalized.length < 8) return null
        return SLOVAK_BIC_BY_BANK_CODE[normalized.substring(4, 8)]
    }

    fun validatePayment(iban: String, bic: String): PaymentValidationResult {
        val normalizedIban = normalizeBankValue(iban)
        val normalizedBic = normalizeBankValue(bic).ifBlank { inferSlovakBic(normalizedIban).orEmpty() }
        return when {
            !isValidIban(normalizedIban) -> PaymentValidationResult.InvalidIban
            normalizedBic.isBlank() -> PaymentValidationResult.MissingBic
            !isValidBic(normalizedBic) -> PaymentValidationResult.InvalidBic
            else -> PaymentValidationResult.Valid(normalizedIban, normalizedBic)
        }
    }

    fun createPayBySquareCode(
        invoiceNumber: String,
        supplierLines: List<String>,
        amount: Double,
        currency: String,
        iban: String,
        bic: String,
        variableSymbol: String,
        dueDate: LocalDate
    ): String {
        val paymentValidation = validatePayment(iban, bic)
        require(paymentValidation is PaymentValidationResult.Valid) { "Invalid payment data" }

        val serialized = serializePayBySquarePayload(
            invoiceNumber = invoiceNumber,
            supplierLines = supplierLines,
            amount = BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP),
            currency = currency,
            iban = paymentValidation.iban,
            bic = paymentValidation.bic,
            variableSymbol = variableSymbol,
            dueDate = dueDate
        )
        return PayBySquareEncoder().encode(serialized)
    }

    internal fun serializePayBySquarePayload(
        invoiceNumber: String,
        supplierLines: List<String>,
        amount: BigDecimal,
        currency: String,
        iban: String,
        bic: String,
        variableSymbol: String,
        dueDate: LocalDate
    ): String = buildString {
        appendField(invoiceNumber)
        appendField("1")
        appendField("1")
        appendField(formatPayBySquareAmount(amount))
        appendField(normalizeCurrency(currency))
        appendField(dueDate.format(DateTimeFormatter.BASIC_ISO_DATE))
        appendField(normalizeVariableSymbol(variableSymbol))
        appendField("")
        appendField("")
        appendField("")
        appendField("Faktura $invoiceNumber")
        appendField("1")
        appendField(normalizeBankValue(iban))
        appendField(normalizeBankValue(bic))
        appendField("0")
        appendField("0")
        appendField(supplierLines.firstOrNull().orEmpty())
        appendField(supplierLines.drop(1).take(2).joinToString(", "))
        appendField(supplierLines.drop(3).take(2).joinToString(", "))
    }

    private fun StringBuilder.appendField(value: String) {
        append(value)
        append('\t')
    }

    private fun normalizeCurrency(value: String): String {
        val currency = value.trim().uppercase(Locale.ROOT)
        return if (Regex("^[A-Z]{3}$").matches(currency)) currency else "EUR"
    }

    private fun normalizeVariableSymbol(value: String): String =
        value.filter { it.isDigit() }.take(10)

    private fun formatPayBySquareAmount(value: BigDecimal): String =
        value.setScale(2, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()

    val SLOVAK_BIC_BY_BANK_CODE = mapOf(
        "0200" to "SUBASKBX",
        "0720" to "NBSBSKBX",
        "0900" to "GIBASKBX",
        "1100" to "TATRSKBX",
        "1111" to "UNCRSKBX",
        "3000" to "SLZBSKBA",
        "3100" to "LUBASKBX",
        "5200" to "OTPVSKBX",
        "5600" to "KOMASK2X",
        "5900" to "PRVASKBA",
        "6500" to "POBNSKBA",
        "7300" to "INGBSKBX",
        "7500" to "CEKOSKBX",
        "7930" to "WUSTSKBA",
        "8050" to "COBASKBX",
        "8120" to "BSLOSK22",
        "8130" to "CITISKBA",
        "8170" to "KBSPSKBX",
        "8180" to "SPSRSKBA",
        "8330" to "FIOZSKBA",
        "8360" to "BREXSKBX",
        "8400" to "BFKKSKBB",
        "8420" to "BFKKSKBB",
        "9950" to "TPAYSKBX"
    )
}

sealed class PaymentValidationResult {
    data class Valid(val iban: String, val bic: String) : PaymentValidationResult()
    data object InvalidIban : PaymentValidationResult()
    data object MissingBic : PaymentValidationResult()
    data object InvalidBic : PaymentValidationResult()
}
