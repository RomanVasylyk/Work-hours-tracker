package com.example.worktr.util

import org.json.JSONObject
import java.time.LocalDate

object InvoiceInputJson {
    fun encode(input: InvoiceInput): String =
        JSONObject()
            .put("invoiceNumber", input.invoiceNumber)
            .put("supplier", input.supplier)
            .put("customer", input.customer)
            .put("note", input.note)
            .put("description", input.description)
            .put("extraItem", input.extraItem?.let(::extraItemToJson))
            .put("extraItems", extraItemsToJson(input.allExtraItems()))
            .put("currency", input.currency)
            .put("iban", input.iban)
            .put("bic", input.bic)
            .put("variableSymbol", input.variableSymbol)
            .put("issueDate", input.issueDate.toString())
            .put("dueDate", input.dueDate.toString())
            .toString()

    fun decode(value: String): InvoiceInput {
        val json = JSONObject(value)
        return InvoiceInput(
            invoiceNumber = json.optString("invoiceNumber"),
            supplier = json.optString("supplier"),
            customer = json.optString("customer"),
            note = json.optString("note"),
            description = json.optString("description"),
            extraItem = json.optJSONObject("extraItem")?.let(::extraItemFromJson),
            extraItems = json.optJSONArray("extraItems")?.let(::extraItemsFromJson).orEmpty(),
            currency = json.optString("currency", "EUR"),
            iban = json.optString("iban"),
            bic = json.optString("bic"),
            variableSymbol = json.optString("variableSymbol"),
            issueDate = LocalDate.parse(json.optString("issueDate")),
            dueDate = LocalDate.parse(json.optString("dueDate"))
        )
    }

    private fun extraItemToJson(item: InvoiceExtraItem): JSONObject =
        JSONObject()
            .put("name", item.name)
            .put("quantity", item.quantity)
            .put("unit", item.unit)
            .put("unitPrice", item.unitPrice)

    private fun extraItemsToJson(items: List<InvoiceExtraItem>) =
        org.json.JSONArray().apply {
            items.forEach { put(extraItemToJson(it)) }
        }

    private fun extraItemsFromJson(array: org.json.JSONArray): List<InvoiceExtraItem> =
        (0 until array.length()).map { index -> extraItemFromJson(array.getJSONObject(index)) }

    private fun extraItemFromJson(json: JSONObject): InvoiceExtraItem =
        InvoiceExtraItem(
            name = json.optString("name"),
            quantity = json.optDouble("quantity", 0.0),
            unit = json.optString("unit"),
            unitPrice = json.optDouble("unitPrice", 0.0)
        )

    private fun InvoiceInput.allExtraItems(): List<InvoiceExtraItem> =
        extraItems.ifEmpty { listOfNotNull(extraItem) }
}
