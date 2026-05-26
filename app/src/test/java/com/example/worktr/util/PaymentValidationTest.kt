package com.example.worktr.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PaymentValidationTest {
    @Test
    fun validatesSlovakIbanAndInfersBic() {
        val result = PaymentValidation.validatePayment("SK71 1100 0000 0012 3456 7890", "")

        assertTrue(result is PaymentValidationResult.Valid)
        result as PaymentValidationResult.Valid
        assertEquals("SK7111000000001234567890", result.iban)
        assertEquals("TATRSKBX", result.bic)
    }

    @Test
    fun rejectsInvalidIbanChecksum() {
        val result = PaymentValidation.validatePayment("SK0011000000001234567890", "TATRSKBX")

        assertEquals(PaymentValidationResult.InvalidIban, result)
    }

    @Test
    fun requiresBicWhenItCannotBeInferred() {
        val result = PaymentValidation.validatePayment("DE89370400440532013000", "")

        assertEquals(PaymentValidationResult.MissingBic, result)
    }

    @Test
    fun rejectsInvalidBicFormat() {
        val result = PaymentValidation.validatePayment("SK7111000000001234567890", "BAD")

        assertEquals(PaymentValidationResult.InvalidBic, result)
    }

    @Test
    fun createsNonEmptyPayBySquareCodeForValidPayment() {
        val code = PaymentValidation.createPayBySquareCode(
            invoiceNumber = "20260401",
            supplierLines = listOf("Ukážkový dodávateľ", "Hlavná 12", "94901 Nitra", "Slovensko"),
            amount = 1500.0,
            currency = "EUR",
            iban = "SK7111000000001234567890",
            bic = "",
            variableSymbol = "20260401",
            dueDate = LocalDate.of(2026, 5, 16)
        )

        assertTrue(code.length > 20)
    }
}
