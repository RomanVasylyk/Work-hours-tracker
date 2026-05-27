package com.example.worktr.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.example.worktr.data.Job
import com.example.worktr.data.WorkEntry
import java.io.File
import java.io.FileOutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

data class InvoiceInput(
    val invoiceNumber: String,
    val supplier: String,
    val customer: String,
    val note: String,
    val description: String,
    val extraItem: InvoiceExtraItem?,
    val extraItems: List<InvoiceExtraItem> = emptyList(),
    val currency: String,
    val pdfLanguage: String = InvoiceLanguage.SLOVAK.code,
    val iban: String,
    val bic: String,
    val variableSymbol: String,
    val issueDate: LocalDate,
    val dueDate: LocalDate
)

data class InvoiceExtraItem(
    val name: String,
    val quantity: Double,
    val unit: String,
    val unitPrice: Double
) {
    val total: Double
        get() = quantity * unitPrice
}

class InvoicePdfGenerator(private val context: Context) {
    private val locale = Locale("sk", "SK")
    private val regularTypeface = Typeface.create("sans-serif", Typeface.NORMAL)
    private val boldTypeface = Typeface.create("sans-serif", Typeface.BOLD)
    private val dateFormatter = DateTimeFormatter.ofPattern("d.M.yyyy", locale)
    private val numberFormat = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }

    fun generate(job: Job, entries: List<WorkEntry>, period: YearMonth, input: InvoiceInput): File {
        val totals = calculateTotals(entries)
        val invoiceExtraItems = input.allExtraItems()
        val invoiceTotal = totals.total + invoiceExtraItems.sumOf { it.total }
        val payBySquareCode = createPayBySquareCode(input, invoiceTotal)
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = document.startPage(pageInfo)
        drawInvoice(page.canvas, job, period, input, totals, invoiceExtraItems, invoiceTotal, payBySquareCode)
        document.finishPage(page)

        val safeNumber = input.invoiceNumber
            .ifBlank { "invoice" }
            .replace(Regex("[^A-Za-z0-9_-]+"), "_")
            .trim('_')
            .ifBlank { "invoice" }
        val file = File(context.cacheDir, "fakrura-$safeNumber.pdf")
        FileOutputStream(file).use { output -> document.writeTo(output) }
        document.close()
        return file
    }

    private fun calculateTotals(entries: List<WorkEntry>): InvoiceTotals {
        val zone = ZoneId.systemDefault()
        var hours = 0.0
        var base = 0.0
        var night = 0.0
        var saturday = 0.0
        var sunday = 0.0
        var holiday = 0.0

        entries.forEach { entry ->
            val breakdown = entry.salaryBreakdown(zone)
            hours += entry.workedHours()
            base += breakdown.base
            night += breakdown.night
            saturday += breakdown.saturday
            sunday += breakdown.sunday
            holiday += breakdown.holiday
        }
        return InvoiceTotals(hours, base, night, saturday, sunday, holiday)
    }

    private fun drawInvoice(
        canvas: Canvas,
        job: Job,
        period: YearMonth,
        input: InvoiceInput,
        totals: InvoiceTotals,
        extraItems: List<InvoiceExtraItem>,
        invoiceTotal: Double,
        payBySquareCode: String
    ) {
        canvas.drawColor(Color.WHITE)
        val texts = PdfTexts.forLanguage(input.pdfLanguage)

        val black = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            strokeWidth = 1.2f
            style = Paint.Style.STROKE
        }
        val thin = Paint(black).apply {
            strokeWidth = 0.6f
        }
        val titlePaint = textPaint(21f)
        val invoiceNumberPaint = textPaint(18f).apply {
            textAlign = Paint.Align.RIGHT
        }
        val partyNamePaint = textPaint(17f)
        val customerNamePaint = textPaint(15f)
        val bodyPaint = textPaint(10.5f)
        val smallPaint = textPaint(8.6f)
        val datePaint = textPaint(13.5f).apply {
            textAlign = Paint.Align.CENTER
        }
        val rightPaint = textPaint(10.5f).apply {
            textAlign = Paint.Align.RIGHT
        }
        val unitPaint = textPaint(10.5f).apply {
            textAlign = Paint.Align.CENTER
        }
        val centerPaint = textPaint(10f).apply {
            textAlign = Paint.Align.CENTER
        }
        val bigTotalPaint = textPaint(22f).apply {
            typeface = boldTypeface
            textAlign = Paint.Align.RIGHT
        }

        drawBarcode(canvas, input.invoiceNumber, OUTER_LEFT, 8f, 96f, 16f)

        canvas.drawRect(RectF(OUTER_LEFT, OUTER_TOP, OUTER_RIGHT, OUTER_BOTTOM), black)
        canvas.drawLine(OUTER_LEFT, 58f, OUTER_RIGHT, 58f, black)
        canvas.drawText(texts.invoiceTitle, OUTER_LEFT + 4f, 50f, titlePaint)
        canvas.drawText(input.invoiceNumber, OUTER_RIGHT - 10f, 50f, invoiceNumberPaint)

        val splitX = 284f
        canvas.drawLine(splitX, 58f, splitX, 225f, black)
        canvas.drawLine(splitX, 78f, OUTER_RIGHT, 78f, black)
        canvas.drawLine(425f, 58f, 425f, 78f, thin)

        drawKeyValueRow(
            canvas = canvas,
            label = texts.paymentMethod,
            value = texts.bankTransfer,
            left = splitX + 4f,
            right = 425f - 4f,
            y = 71f,
            labelPaint = smallPaint,
            valuePaint = smallPaint
        )
        drawKeyValueRow(
            canvas = canvas,
            label = texts.variableSymbol,
            value = input.variableSymbol,
            left = 428f,
            right = OUTER_RIGHT - 6f,
            y = 71f,
            labelPaint = smallPaint,
            valuePaint = smallPaint
        )

        drawSupplier(canvas, input.supplier, OUTER_LEFT + 12f, 79f, 222f, partyNamePaint, bodyPaint)
        canvas.drawText(texts.customer, splitX + 4f, 95f, smallPaint)
        drawCustomer(canvas, input.customer, splitX + 32f, 119f, 214f, customerNamePaint, bodyPaint)

        val dateTop = 225f
        val dateBottom = 255f
        canvas.drawLine(OUTER_LEFT, dateTop, OUTER_RIGHT, dateTop, black)
        canvas.drawLine(OUTER_LEFT, dateBottom, OUTER_RIGHT, dateBottom, black)
        canvas.drawLine(212f, dateTop, 212f, dateBottom, thin)
        canvas.drawLine(382f, dateTop, 382f, dateBottom, thin)
        drawDateBox(canvas, texts.issueDate, input.issueDate, OUTER_LEFT, 212f, dateTop, smallPaint, datePaint, texts.locale)
        drawDateBox(canvas, texts.deliveryDate, period.atEndOfMonth(), 212f, 382f, dateTop, smallPaint, datePaint, texts.locale)
        drawDateBox(canvas, texts.dueDate, input.dueDate, 382f, OUTER_RIGHT, dateTop, smallPaint, datePaint, texts.locale)

        val tableTop = 274f
        val tableLeft = OUTER_LEFT + 14f
        val tableRight = OUTER_RIGHT - 14f
        val descRight = tableLeft + 270f
        val qtyRight = descRight + 63f
        val unitRight = qtyRight + 40f
        val priceRight = unitRight + 69f
        val headerBottom = tableTop + 15f
        val rowBottom = headerBottom + 15f
        val extraBottom = rowBottom + extraItems.size * 15f
        val totalBottom = extraBottom + 15f
        (listOf(tableTop, headerBottom, rowBottom, extraBottom, totalBottom) +
            extraItems.indices.map { rowBottom + (it + 1) * 15f }).distinct().forEach { y ->
            canvas.drawLine(tableLeft, y, tableRight, y, thin)
        }
        listOf(tableLeft, descRight, qtyRight, unitRight, priceRight, tableRight).forEach { x ->
            canvas.drawLine(x, tableTop, x, extraBottom, thin)
        }
        canvas.drawLine(tableLeft, extraBottom, tableLeft, totalBottom, thin)
        canvas.drawLine(priceRight, extraBottom, priceRight, totalBottom, thin)
        canvas.drawLine(tableRight, extraBottom, tableRight, totalBottom, thin)

        canvas.drawText(texts.itemDescription, (tableLeft + descRight) / 2f, tableTop + 11f, centerPaint)
        canvas.drawText(texts.quantity, (descRight + qtyRight) / 2f, tableTop + 11f, centerPaint)
        canvas.drawText("MJ", (qtyRight + unitRight) / 2f, tableTop + 11f, centerPaint)
        canvas.drawText(texts.unitPrice, (unitRight + priceRight) / 2f, tableTop + 11f, centerPaint)
        canvas.drawText(texts.totalPrice, (priceRight + tableRight) / 2f, tableTop + 11f, centerPaint)

        val description = input.description.ifBlank {
            texts.defaultDescription(monthName(period, texts.locale))
        }
        val itemPaint = textPaint(8.6f)
        wrapText(description, itemPaint, descRight - tableLeft - 8f).take(1).forEach { line ->
            canvas.drawText(line, tableLeft + 4f, headerBottom + 10.8f, itemPaint)
        }
        canvas.drawText(formatQuantity(totals.hours), qtyRight - 24f, headerBottom + 10.8f, rightPaint)
        canvas.drawText(texts.hourUnit, (qtyRight + unitRight) / 2f, headerBottom + 10.8f, unitPaint)
        canvas.drawText(formatNumber(unitPrice(totals)), priceRight - 10f, headerBottom + 10.8f, rightPaint)
        canvas.drawText(formatNumber(totals.total), tableRight - 20f, headerBottom + 10.8f, rightPaint)
        extraItems.forEachIndexed { index, extraItem ->
            val baseline = rowBottom + index * 15f + 10.8f
            canvas.drawText(extraItem.name, tableLeft + 4f, baseline, itemPaint)
            canvas.drawText(formatQuantity(extraItem.quantity), qtyRight - 24f, baseline, rightPaint)
            canvas.drawText(extraItem.unit, (qtyRight + unitRight) / 2f, baseline, unitPaint)
            canvas.drawText(formatNumber(extraItem.unitPrice), priceRight - 10f, baseline, rightPaint)
            canvas.drawText(formatNumber(extraItem.total), tableRight - 20f, baseline, rightPaint)
        }
        canvas.drawText(texts.total, priceRight - 3f, extraBottom + 10.8f, rightPaint)
        canvas.drawText(formatNumber(invoiceTotal), tableRight - 20f, extraBottom + 10.8f, rightPaint)

        drawPayBySquare(canvas, OUTER_LEFT + 14f, 584f, payBySquareCode)

        val footerTop = 712f
        canvas.drawLine(OUTER_LEFT, footerTop, OUTER_RIGHT, footerTop, black)
        canvas.drawLine(212f, footerTop, 212f, OUTER_BOTTOM, thin)
        canvas.drawLine(382f, footerTop, 382f, OUTER_BOTTOM, thin)
        canvas.drawText(texts.createdBy, OUTER_LEFT + 8f, footerTop + 14f, smallPaint)
        canvas.drawText(texts.receivedBy, 216f, footerTop + 14f, smallPaint)

        val totalsLabelX = 386f
        val totalsValueX = OUTER_RIGHT - 8f
        val summaryRightSplit = 482f
        listOf(footerTop + 15f, footerTop + 30f, footerTop + 45f).forEach { y ->
            canvas.drawLine(382f, y, OUTER_RIGHT, y, thin)
        }
        canvas.drawLine(summaryRightSplit, footerTop, summaryRightSplit, footerTop + 45f, thin)
        drawSummaryLine(canvas, texts.totalAmount, formatAmount(invoiceTotal, input.currency), totalsLabelX, totalsValueX, footerTop + 11f, smallPaint, rightPaint)
        drawSummaryLine(canvas, texts.paidByAdvances, formatAmount(0.0, input.currency), totalsLabelX, totalsValueX, footerTop + 26f, smallPaint, rightPaint)
        drawSummaryLine(canvas, texts.remaining, formatAmount(invoiceTotal, input.currency), totalsLabelX, totalsValueX, footerTop + 41f, smallPaint, rightPaint)
        val amountRightX = OUTER_RIGHT - 25f
        canvas.drawText(texts.amountDue, totalsLabelX, footerTop + 56f, smallPaint)
        canvas.drawText(formatAmount(invoiceTotal, input.currency), amountRightX, footerTop + 75f, bigTotalPaint)
        val wordsPaint = textPaint(8f).apply { textAlign = Paint.Align.RIGHT }
        drawWrappedRightText(
            canvas = canvas,
            text = texts.amountWords(invoiceTotal, input.currency, ::amountWordsSk),
            right = amountRightX,
            firstBaseline = footerTop + 89f,
            maxWidth = amountRightX - totalsLabelX,
            paint = wordsPaint,
            maxLines = 2,
            lineHeight = 8.7f
        )
    }

    private fun textPaint(size: Float): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG).apply {
            color = Color.BLACK
            textSize = size
            typeface = regularTypeface
            textScaleX = 1f
            letterSpacing = 0f
            hinting = Paint.HINTING_ON
        }

    private fun drawKeyValueRow(
        canvas: Canvas,
        label: String,
        value: String,
        left: Float,
        right: Float,
        y: Float,
        labelPaint: Paint,
        valuePaint: Paint
    ) {
        canvas.drawText(label, left, y, labelPaint)
        val alignedValue = Paint(valuePaint).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText(value, right, y, alignedValue)
    }

    private fun drawSupplier(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        width: Float,
        namePaint: Paint,
        bodyPaint: Paint
    ) {
        val lines = text.lines().filter { it.isNotBlank() }
        val name = lines.firstOrNull().orEmpty()
        if (name.isNotBlank()) {
            canvas.drawText(name, x, y, namePaint)
        }
        var lineY = y + 21f
        lines.drop(1).takeWhile { !it.startsWith("IČO:") && it != "Neplatiteľ DPH" && !it.startsWith("IBAN:") }
            .flatMap { wrapText(it, bodyPaint, width) }
            .take(4)
            .forEach { line ->
                canvas.drawText(line, x, lineY, bodyPaint)
                lineY += 14f
            }

        val ico = lines.firstOrNull { it.startsWith("IČO:") }?.substringAfter(":")?.trim().orEmpty()
        if (ico.isNotBlank()) {
            val labelPaint = Paint(bodyPaint).apply { textAlign = Paint.Align.LEFT }
            val valuePaint = Paint(bodyPaint).apply { textAlign = Paint.Align.LEFT }
            canvas.drawText("IČO:", x, y + 80f, labelPaint)
            canvas.drawText(ico, x + 90f, y + 80f, valuePaint)
        }
        if (lines.any { it == "Neplatiteľ DPH" }) {
            canvas.drawText("Neplatiteľ DPH", x, y + 94f, bodyPaint)
        }
        val iban = lines.firstOrNull { it.startsWith("IBAN:") }?.substringAfter(":")?.trim().orEmpty()
        if (iban.isNotBlank()) {
            val ibanPaint = Paint(bodyPaint).apply {
                typeface = boldTypeface
                textSize = 9.5f
            }
            canvas.drawText("IBAN:", x, y + 118f, bodyPaint)
            canvas.drawText(iban, x + 90f, y + 118f, ibanPaint)
        }
    }

    private fun drawCustomer(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        width: Float,
        namePaint: Paint,
        bodyPaint: Paint
    ) {
        val lines = text.lines().filter { it.isNotBlank() }
        val name = lines.firstOrNull().orEmpty()
        if (name.isNotBlank()) {
            canvas.drawText(name, x, y, namePaint)
        }

        var lineY = y + 16f
        lines.drop(1).takeWhile { !it.startsWith("IČO:") && !it.startsWith("DIČ:") && !it.startsWith("IČDPH:") }
            .flatMap { wrapText(it, bodyPaint, width) }
            .take(4)
            .forEach { line ->
                canvas.drawText(line, x, lineY, bodyPaint)
                lineY += 14f
            }

        val detailTop = y + 73f
        listOf("IČO:", "DIČ:", "IČDPH:").forEachIndexed { index, label ->
            val value = lines.firstOrNull { it.startsWith(label) }?.substringAfter(":")?.trim().orEmpty()
            if (value.isNotBlank()) {
                val line = detailTop + index * 12f
                canvas.drawText(label, x, line, bodyPaint)
                canvas.drawText(value, x + 80f, line, bodyPaint)
            }
        }
    }

    private fun drawDateBox(
        canvas: Canvas,
        label: String,
        date: LocalDate,
        left: Float,
        right: Float,
        y: Float,
        labelPaint: Paint,
        datePaint: Paint,
        locale: Locale
    ) {
        canvas.drawText(label, left + 4f, y + 11f, labelPaint)
        val dateX = left + (right - left) * 0.64f
        canvas.drawText(date.format(DateTimeFormatter.ofPattern("d.M.yyyy", locale)), dateX, y + 25f, datePaint)
    }

    private fun drawSummaryLine(
        canvas: Canvas,
        label: String,
        value: String,
        labelX: Float,
        valueX: Float,
        y: Float,
        labelPaint: Paint,
        valuePaint: Paint
    ) {
        canvas.drawText(label, labelX, y, labelPaint)
        canvas.drawText(value, valueX, y, valuePaint)
    }

    private fun drawWrappedRightText(
        canvas: Canvas,
        text: String,
        right: Float,
        firstBaseline: Float,
        maxWidth: Float,
        paint: Paint,
        maxLines: Int,
        lineHeight: Float
    ) {
        val fittedPaint = Paint(paint)
        var lines = wrapText(text, fittedPaint, maxWidth)
        while (lines.size > maxLines && fittedPaint.textSize > 6.4f) {
            fittedPaint.textSize -= 0.3f
            lines = wrapText(text, fittedPaint, maxWidth)
        }
        lines.take(maxLines).forEachIndexed { index, line ->
            canvas.drawText(line, right, firstBaseline + index * lineHeight, fittedPaint)
        }
    }

    private fun createPayBySquareCode(input: InvoiceInput, invoiceTotal: Double): String {
        val supplierLines = input.supplier.lines().filter { it.isNotBlank() }
        return PaymentValidation.createPayBySquareCode(
            invoiceNumber = input.invoiceNumber,
            supplierLines = supplierLines,
            amount = invoiceTotal,
            currency = input.currency,
            iban = input.iban,
            bic = input.bic,
            variableSymbol = input.variableSymbol,
            dueDate = input.dueDate
        )
    }

    private fun InvoiceInput.allExtraItems(): List<InvoiceExtraItem> =
        extraItems.ifEmpty { listOfNotNull(extraItem) }

    private fun drawBarcode(canvas: Canvas, value: String, x: Float, y: Float, width: Float, height: Float) {
        val matrix = MultiFormatWriter().encode(value.ifBlank { "0" }, BarcodeFormat.CODE_128, width.toInt(), height.toInt())
        drawBitMatrix(canvas, matrix, x, y, width, height, Color.BLACK)
    }

    private fun drawPayBySquare(canvas: Canvas, x: Float, y: Float, code: String) {
        val blue = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(116, 169, 221)
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }
        val whiteFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(116, 169, 221)
            typeface = boldTypeface
            textSize = 9f
            textScaleX = 1f
            letterSpacing = 0f
        }
        val greyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(160, 160, 160)
            typeface = boldTypeface
            textSize = 9f
            textScaleX = 1f
            letterSpacing = 0f
        }

        canvas.drawRoundRect(RectF(x, y, x + 102f, y + 102f), 1f, 1f, blue)
        canvas.drawRect(RectF(x + 10f, y + 10f, x + 89f, y + 89f), whiteFill)
        val matrix = MultiFormatWriter().encode(
            code,
            BarcodeFormat.QR_CODE,
            256,
            256,
            mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L,
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.MARGIN to 1
            )
        )
        drawBitMatrix(canvas, matrix, x + 12f, y + 12f, 76f, 76f, Color.BLACK)
        canvas.drawLine(x, y + 105f, x + 82f, y + 105f, blue)
        canvas.drawText("PAY", x + 22f, y + 118f, labelPaint)
        canvas.drawText("by square", x + 42f, y + 118f, greyPaint)
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(116, 169, 221)
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(RectF(x + 91f, y + 101f, x + 113f, y + 123f), 4f, 4f, cardPaint)
        val whiteStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            strokeWidth = 1.3f
            style = Paint.Style.STROKE
        }
        canvas.drawRoundRect(RectF(x + 95f, y + 106f, x + 109f, y + 117f), 1.5f, 1.5f, whiteStroke)
        canvas.drawLine(x + 97f, y + 110f, x + 107f, y + 108f, whiteStroke)
    }

    private fun drawBitMatrix(
        canvas: Canvas,
        matrix: BitMatrix,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        color: Int
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }
        val cellWidth = width / matrix.width
        val cellHeight = height / matrix.height
        for (row in 0 until matrix.height) {
            for (col in 0 until matrix.width) {
                if (matrix.get(col, row)) {
                    canvas.drawRect(
                        x + col * cellWidth,
                        y + row * cellHeight,
                        x + (col + 1) * cellWidth,
                        y + (row + 1) * cellHeight,
                        paint
                    )
                }
            }
        }
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isBlank()) return emptyList()

        val lines = mutableListOf<String>()
        text.lines().forEach { paragraph ->
            var current = ""
            paragraph.split(Regex("\\s+")).filter { it.isNotBlank() }.forEach { word ->
                val candidate = if (current.isBlank()) word else "$current $word"
                if (paint.measureText(candidate) <= maxWidth) {
                    current = candidate
                } else {
                    if (current.isNotBlank()) lines += current
                    val wordParts = splitLongWord(word, paint, maxWidth)
                    lines += wordParts.dropLast(1)
                    current = wordParts.lastOrNull().orEmpty()
                }
            }
            if (current.isNotBlank()) lines += current
        }
        return lines
    }

    private fun splitLongWord(word: String, paint: Paint, maxWidth: Float): List<String> {
        if (paint.measureText(word) <= maxWidth) return listOf(word)

        val parts = mutableListOf<String>()
        var remaining = word
        while (remaining.isNotEmpty()) {
            var end = remaining.length
            while (end > 1 && paint.measureText(remaining.substring(0, end)) > maxWidth) {
                end--
            }
            if (end <= 1 && paint.measureText(remaining.substring(0, 1)) > maxWidth) {
                parts += remaining.substring(0, 1)
                remaining = remaining.substring(1)
            } else {
                parts += remaining.substring(0, end)
                remaining = remaining.substring(end)
            }
        }
        return parts
    }

    private fun formatAmount(value: Double, currency: String): String =
        "${formatNumber(value)} ${currency.ifBlank { "EUR" }}"

    private fun formatQuantity(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else formatNumber(value)

    private fun formatNumber(value: Double): String = numberFormat.format(value)

    private fun unitPrice(totals: InvoiceTotals): Double =
        if (totals.hours > 0.0) totals.total / totals.hours else 0.0

    private fun monthName(period: YearMonth, locale: Locale): String =
        period.month.getDisplayName(TextStyle.FULL, locale)

    private fun amountWordsSk(value: Double, currency: String): String {
        val totalCents = BigDecimal.valueOf(value)
            .movePointRight(2)
            .setScale(0, RoundingMode.HALF_UP)
            .toLong()
        val euros = (totalCents / 100).toInt()
        val cents = (totalCents % 100).toInt()
        val euroWord = when {
            euros == 1 -> "euro"
            euros in 2..4 -> "eurá"
            else -> currency.lowercase(locale)
        }
        val centPart = if (cents > 0) " ${numberToWordsSk(cents)} centov" else ""
        return "${numberToWordsSk(euros)} $euroWord$centPart".trim()
    }

    private fun numberToWordsSk(number: Int): String {
        if (number == 0) return "nula"
        val parts = mutableListOf<String>()
        val thousands = number / 1000
        val rest = number % 1000
        if (thousands > 0) {
            parts += when (thousands) {
                1 -> "tisíc"
                2 -> "dvetisíc"
                3 -> "tritisíc"
                4 -> "štyritisíc"
                else -> "${underThousandToWordsSk(thousands)} tisíc"
            }
        }
        if (rest > 0) {
            parts += underThousandToWordsSk(rest)
        }
        return parts.joinToString(" ")
    }

    private fun underThousandToWordsSk(number: Int): String {
        val hundreds = number / 100
        val rest = number % 100
        val parts = mutableListOf<String>()
        if (hundreds > 0) {
            parts += when (hundreds) {
                1 -> "sto"
                2 -> "dvesto"
                3 -> "tristo"
                4 -> "štyristo"
                else -> "${unitWordSk(hundreds)}sto"
            }
        }
        if (rest > 0) {
            parts += underHundredToWordsSk(rest)
        }
        return parts.joinToString(" ")
    }

    private fun underHundredToWordsSk(number: Int): String =
        when {
            number < 10 -> unitWordSk(number)
            number == 10 -> "desať"
            number == 11 -> "jedenásť"
            number == 12 -> "dvanásť"
            number == 13 -> "trinásť"
            number == 14 -> "štrnásť"
            number == 15 -> "pätnásť"
            number == 16 -> "šestnásť"
            number == 17 -> "sedemnásť"
            number == 18 -> "osemnásť"
            number == 19 -> "devätnásť"
            number < 30 -> "dvadsať${if (number % 10 == 0) "" else " ${unitWordSk(number % 10)}"}"
            number < 40 -> "tridsať${if (number % 10 == 0) "" else " ${unitWordSk(number % 10)}"}"
            number < 50 -> "štyridsať${if (number % 10 == 0) "" else " ${unitWordSk(number % 10)}"}"
            number < 60 -> "päťdesiat${if (number % 10 == 0) "" else " ${unitWordSk(number % 10)}"}"
            number < 70 -> "šesťdesiat${if (number % 10 == 0) "" else " ${unitWordSk(number % 10)}"}"
            number < 80 -> "sedemdesiat${if (number % 10 == 0) "" else " ${unitWordSk(number % 10)}"}"
            number < 90 -> "osemdesiat${if (number % 10 == 0) "" else " ${unitWordSk(number % 10)}"}"
            else -> "deväťdesiat${if (number % 10 == 0) "" else " ${unitWordSk(number % 10)}"}"
        }

    private fun unitWordSk(number: Int): String =
        when (number) {
            1 -> "jeden"
            2 -> "dva"
            3 -> "tri"
            4 -> "štyri"
            5 -> "päť"
            6 -> "šesť"
            7 -> "sedem"
            8 -> "osem"
            9 -> "deväť"
            else -> ""
        }

    private data class InvoiceTotals(
        val hours: Double,
        val base: Double,
        val night: Double,
        val saturday: Double,
        val sunday: Double,
        val holiday: Double
    ) {
        val total: Double
            get() = base
    }

    private data class PdfTexts(
        val locale: Locale,
        val invoiceTitle: String,
        val paymentMethod: String,
        val bankTransfer: String,
        val variableSymbol: String,
        val customer: String,
        val issueDate: String,
        val deliveryDate: String,
        val dueDate: String,
        val itemDescription: String,
        val quantity: String,
        val unitPrice: String,
        val totalPrice: String,
        val hourUnit: String,
        val total: String,
        val createdBy: String,
        val receivedBy: String,
        val totalAmount: String,
        val paidByAdvances: String,
        val remaining: String,
        val amountDue: String,
        val defaultDescription: (String) -> String,
        val amountWords: (Double, String, (Double, String) -> String) -> String
    ) {
        companion object {
            fun forLanguage(code: String): PdfTexts =
                when (InvoiceLanguage.fromCode(code)) {
                    InvoiceLanguage.UKRAINIAN -> PdfTexts(
                        locale = Locale("uk", "UA"),
                        invoiceTitle = "ФАКТУРА",
                        paymentMethod = "Форма оплати:",
                        bankTransfer = "банківський переказ",
                        variableSymbol = "Варіабельний символ:",
                        customer = "Клієнт",
                        issueDate = "Дата виставлення",
                        deliveryDate = "Дата постачання",
                        dueDate = "Термін оплати",
                        itemDescription = "Опис позиції",
                        quantity = "Кількість",
                        unitPrice = "Ціна за од.",
                        totalPrice = "Сума",
                        hourUnit = "год",
                        total = "Разом:",
                        createdBy = "Виставив:",
                        receivedBy = "Прийняв:",
                        totalAmount = "Загальна сума:",
                        paidByAdvances = "Оплачено авансом:",
                        remaining = "Залишилось оплатити:",
                        amountDue = "До оплати:",
                        defaultDescription = { month -> "Виставляю оплату за виконану роботу за місяць $month" },
                        amountWords = { value, currency, _ -> "До оплати ${"%.2f".format(Locale.US, value)} $currency" }
                    )
                    InvoiceLanguage.ENGLISH -> PdfTexts(
                        locale = Locale.ENGLISH,
                        invoiceTitle = "INVOICE",
                        paymentMethod = "Payment method:",
                        bankTransfer = "bank transfer",
                        variableSymbol = "Variable symbol:",
                        customer = "Customer",
                        issueDate = "Issue date",
                        deliveryDate = "Delivery date",
                        dueDate = "Due date",
                        itemDescription = "Item description",
                        quantity = "Quantity",
                        unitPrice = "Unit price",
                        totalPrice = "Total price",
                        hourUnit = "hrs",
                        total = "Total:",
                        createdBy = "Issued by:",
                        receivedBy = "Received by:",
                        totalAmount = "Total amount:",
                        paidByAdvances = "Paid by advances:",
                        remaining = "Remaining:",
                        amountDue = "Amount due:",
                        defaultDescription = { month -> "Work performed in $month" },
                        amountWords = { value, currency, _ -> "Amount due ${"%.2f".format(Locale.US, value)} $currency" }
                    )
                    InvoiceLanguage.SLOVAK -> PdfTexts(
                        locale = Locale("sk", "SK"),
                        invoiceTitle = "FAKTÚRA",
                        paymentMethod = "Forma úhrady:",
                        bankTransfer = "peňažný prevod",
                        variableSymbol = "Variabilný symbol:",
                        customer = "Odberateľ",
                        issueDate = "Dátum vystavenia",
                        deliveryDate = "Dátum dodania",
                        dueDate = "Dátum splatnosti",
                        itemDescription = "Popis položky",
                        quantity = "Množstvo",
                        unitPrice = "Cena za MJ",
                        totalPrice = "Celková cena",
                        hourUnit = "hod",
                        total = "Spolu:",
                        createdBy = "Vyhotovil:",
                        receivedBy = "Prevzal:",
                        totalAmount = "Celková suma:",
                        paidByAdvances = "Uhradené zálohami:",
                        remaining = "Zostáva uhradiť:",
                        amountDue = "K úhrade:",
                        defaultDescription = { month -> "Fakturujem Vám za vykonanú prácu v mesiaci $month" },
                        amountWords = { value, currency, skWords -> skWords(value, currency) }
                    )
                }
        }
    }

    private companion object {
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842
        const val OUTER_LEFT = 28f
        const val OUTER_TOP = 28f
        const val OUTER_RIGHT = 567f
        const val OUTER_BOTTOM = 812f
    }
}
