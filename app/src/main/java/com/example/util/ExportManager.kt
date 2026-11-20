package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.core.content.FileProvider
import com.example.data.local.InvoiceEntity
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExportManager {
    private fun exportsDir(context: Context): File {
        val dir = File(context.cacheDir, "exports")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun drawDualAmount(
        canvas: Canvas,
        labelPrefix: String,
        usdText: String,
        sspText: String,
        x: Float,
        y: Float,
        primaryPaint: Paint,
        secondaryPaint: Paint
    ) {
        val mainStr = if (labelPrefix.isNotEmpty()) "$labelPrefix $usdText" else usdText
        canvas.drawText(mainStr, x, y, primaryPaint)
        val offset = primaryPaint.measureText("$mainStr ")
        canvas.drawText("($sspText)", x + offset, y, secondaryPaint)
    }

    fun createInvoicePdfFile(
        context: Context,
        storeName: String,
        invoice: InvoiceEntity,
        currency: String,
        isRtl: Boolean,
        fallbackUsdRate: Double = 0.0
    ): File {
        val doc = PdfDocument()
        try {
            var pageNum = 1
            var page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNum).create())
            var canvas: Canvas = page.canvas

            val titlePaint = Paint().apply {
                color = Color.parseColor("#1B4D3E")
                textSize = 21f
                isFakeBoldText = true
            }
            val headerPaint = Paint().apply {
                color = Color.DKGRAY
                textSize = 13f
                isFakeBoldText = true
            }
            val bodyPaint = Paint().apply {
                color = Color.BLACK
                textSize = 12f
            }
            val bodyBoldPaint = Paint().apply {
                color = Color.BLACK
                textSize = 12f
                isFakeBoldText = true
            }
            val subPaint = Paint().apply {
                color = Color.parseColor("#666666")
                textSize = 9.5f
            }
            val totalSubPaint = Paint().apply {
                color = Color.parseColor("#666666")
                textSize = 12.5f
            }
            val linePaint = Paint().apply {
                color = Color.LTGRAY
                strokeWidth = 1.5f
            }

            val isUsd = CurrencyFormatter.isInvoiceUsd(invoice)
            val effectiveRate = CurrencyFormatter.effectiveInvoiceRate(invoice, fallbackUsdRate)
            val showUsdDual = isUsd && effectiveRate > 0.0

            val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            var y = 60f
            canvas.drawText(storeName, 48f, y, titlePaint)
            y += 28f
            canvas.drawText("${if (isRtl) "فاتورة رقم" else "Invoice"}: ${invoice.invoiceNumber}", 48f, y, headerPaint)
            y += 20f
            canvas.drawText("${if (isRtl) "التاريخ" else "Date"}: ${fmt.format(Date(invoice.createdAtMillis))}", 48f, y, bodyPaint)
            y += 18f
            canvas.drawText("${if (isRtl) "الكاشير" else "Cashier"}: ${invoice.cashierName}", 48f, y, bodyPaint)
            y += 18f
            val cust = invoice.customerName.ifBlank { if (isRtl) "عميل نقدي" else "Walk-in" }
            canvas.drawText("${if (isRtl) "العميل" else "Customer"}: $cust", 48f, y, bodyPaint)
            y += 18f
            val payLabel = if (isUsd) "USD" else invoice.paymentMethod.uppercase()
            canvas.drawText("${if (isRtl) "الدفع" else "Payment"}: $payLabel | ${invoice.saleType.uppercase()}", 48f, y, bodyPaint)
            if (showUsdDual) {
                y += 18f
                val rateStr = CurrencyFormatter.formatRateWholeOrDecimal(effectiveRate)
                canvas.drawText("${if (isRtl) "سعر الصرف" else "Exchange Rate"}: 1 USD = $rateStr SSP", 48f, y, subPaint)
            }

            y += 20f
            canvas.drawLine(48f, y, 547f, y, linePaint)
            y += 24f

            fun ensureSpace(needed: Float) {
                if (y + needed > 780f) {
                    doc.finishPage(page)
                    pageNum++
                    page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNum).create())
                    canvas = page.canvas
                    y = 56f
                }
            }

            val parsedItems = CurrencyFormatter.parseInvoiceItems(invoice.itemsJson)
            if (showUsdDual && parsedItems.isNotEmpty()) {
                parsedItems.forEach { item ->
                    ensureSpace(42f)
                    val name = if (isRtl) item.nameAr.ifBlank { item.nameEn } else item.nameEn.ifBlank { item.nameAr }
                    val tier = if (item.wholesale) "W" else "R"
                    canvas.drawText("$name x${item.qty} ($tier)", 48f, y, bodyBoldPaint)
                    y += 16f
                    val unitConv = CurrencyFormatter.convertSspToUsd(item.unitPriceSsp, effectiveRate)
                    val lineConv = CurrencyFormatter.convertSspToUsd(item.lineTotalSsp, effectiveRate)
                    drawDualAmount(
                        canvas = canvas,
                        labelPrefix = "${if (isRtl) "السعر" else "Price"}:",
                        usdText = unitConv.usdText,
                        sspText = unitConv.sspText,
                        x = 56f,
                        y = y,
                        primaryPaint = bodyPaint,
                        secondaryPaint = subPaint
                    )
                    drawDualAmount(
                        canvas = canvas,
                        labelPrefix = "${if (isRtl) "الإجمالي" else "Total"}:",
                        usdText = lineConv.usdText,
                        sspText = lineConv.sspText,
                        x = 320f,
                        y = y,
                        primaryPaint = bodyBoldPaint,
                        secondaryPaint = subPaint
                    )
                    y += 20f
                }
            } else if (!showUsdDual && parsedItems.isNotEmpty()) {
                parsedItems.forEach { item ->
                    ensureSpace(24f)
                    val name = if (isRtl) item.nameAr.ifBlank { item.nameEn } else item.nameEn.ifBlank { item.nameAr }
                    val tier = if (item.wholesale) "W" else "R"
                    val lineText = "$name x${item.qty} ($tier) @ ${String.format(Locale.US, "%.2f", item.unitPriceSsp)} = ${String.format(Locale.US, "%.2f", item.lineTotalSsp)} $currency"
                    canvas.drawText(lineText, 48f, y, bodyPaint)
                    y += 20f
                }
            } else {
                invoice.itemsSummary.split("\n").forEach { line ->
                    ensureSpace(22f)
                    canvas.drawText(line, 48f, y, bodyPaint)
                    y += 20f
                }
            }

            ensureSpace(100f)
            y += 10f
            canvas.drawLine(48f, y, 547f, y, linePaint)
            y += 26f

            if (showUsdDual) {
                val subConv = CurrencyFormatter.convertSspToUsd(invoice.subtotal, effectiveRate)
                val discConv = CurrencyFormatter.convertSspToUsd(invoice.discount, effectiveRate, negativePrefix = invoice.discount > 0.0)
                val totConv = CurrencyFormatter.convertSspToUsd(invoice.total, effectiveRate)

                drawDualAmount(
                    canvas = canvas,
                    labelPrefix = "${if (isRtl) "المجموع الفرعي" else "Subtotal"}:",
                    usdText = subConv.usdText,
                    sspText = subConv.sspText,
                    x = 48f,
                    y = y,
                    primaryPaint = bodyPaint,
                    secondaryPaint = subPaint
                )
                y += 22f
                drawDualAmount(
                    canvas = canvas,
                    labelPrefix = "${if (isRtl) "الخصم" else "Discount"}:",
                    usdText = discConv.usdText,
                    sspText = discConv.sspText,
                    x = 48f,
                    y = y,
                    primaryPaint = bodyPaint,
                    secondaryPaint = subPaint
                )
                y += 28f
                drawDualAmount(
                    canvas = canvas,
                    labelPrefix = "${if (isRtl) "الإجمالي" else "TOTAL"}:",
                    usdText = totConv.usdText,
                    sspText = totConv.sspText,
                    x = 48f,
                    y = y,
                    primaryPaint = titlePaint,
                    secondaryPaint = totalSubPaint
                )
            } else {
                canvas.drawText("${if (isRtl) "المجموع الفرعي" else "Subtotal"}: ${String.format(Locale.US, "%.2f", invoice.subtotal)} $currency", 48f, y, bodyPaint)
                y += 20f
                canvas.drawText("${if (isRtl) "الخصم" else "Discount"}: -${String.format(Locale.US, "%.2f", invoice.discount)} $currency", 48f, y, bodyPaint)
                y += 24f
                canvas.drawText("${if (isRtl) "الإجمالي" else "TOTAL"}: ${String.format(Locale.US, "%.2f", invoice.total)} $currency", 48f, y, titlePaint)
            }

            doc.finishPage(page)
            val outFile = File(exportsDir(context), "receipt_${invoice.invoiceNumber}.pdf")
            FileOutputStream(outFile).use { doc.writeTo(it) }
            return outFile
        } finally {
            doc.close()
        }
    }

    fun exportInvoicePdf(
        context: Context,
        storeName: String,
        invoice: InvoiceEntity,
        currency: String,
        isRtl: Boolean,
        fallbackUsdRate: Double = 0.0
    ): Result<Intent> = runCatching {
        val outFile = createInvoicePdfFile(context, storeName, invoice, currency, isRtl, fallbackUsdRate)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
        Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun printInvoicePdf(
        context: Context,
        storeName: String,
        invoice: InvoiceEntity,
        currency: String,
        isRtl: Boolean,
        fallbackUsdRate: Double = 0.0
    ): Result<Unit> = runCatching {
        val pdfFile = createInvoicePdfFile(context, storeName, invoice, currency, isRtl, fallbackUsdRate)
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            ?: error("Print service unavailable")
        val jobName = "$storeName - ${invoice.invoiceNumber}"
        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onLayoutCancelled()
                    return
                }
                val info = PrintDocumentInfo.Builder("receipt_${invoice.invoiceNumber}.pdf")
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .build()
                callback.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onWriteCancelled()
                    return
                }
                runCatching {
                    FileInputStream(pdfFile).use { input ->
                        FileOutputStream(destination.fileDescriptor).use { output ->
                            input.copyTo(output)
                        }
                    }
                }.onSuccess {
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                }.onFailure { err ->
                    callback.onWriteFailed(err.message)
                }
            }
        }
        printManager.print(jobName, adapter, null)
    }

    fun exportReportPdf(
        context: Context,
        storeName: String,
        title: String,
        lines: List<String>
    ): Result<Intent> = runCatching {
        val doc = PdfDocument()
        var pageNumber = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
        var canvas = page.canvas

        val titlePaint = Paint().apply {
            color = Color.parseColor("#1B4D3E")
            textSize = 20f
            isFakeBoldText = true
        }
        val bodyPaint = Paint().apply {
            color = Color.BLACK
            textSize = 11.5f
        }

        var y = 56f
        canvas.drawText(storeName, 44f, y, titlePaint)
        y += 26f
        canvas.drawText(title, 44f, y, titlePaint.apply { textSize = 15f })
        y += 28f

        lines.forEach { line ->
            if (y > 790f) {
                doc.finishPage(page)
                pageNumber++
                page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
                canvas = page.canvas
                y = 56f
            }
            canvas.drawText(line, 44f, y, bodyPaint)
            y += 18f
        }
        doc.finishPage(page)

        val outFile = File(exportsDir(context), "aroma_report_${System.currentTimeMillis()}.pdf")
        FileOutputStream(outFile).use { doc.writeTo(it) }
        doc.close()

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
        Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun exportExcelCsv(
        context: Context,
        fileNamePrefix: String,
        headers: List<String>,
        rows: List<List<String>>
    ): Result<Intent> = runCatching {
        val outFile = File(exportsDir(context), "${fileNamePrefix}_${System.currentTimeMillis()}.csv")
        FileOutputStream(outFile).use { fos ->
            // UTF-8 BOM so Microsoft Excel opens Arabic headers & data cleanly
            fos.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
            val sb = StringBuilder()
            sb.appendLine(headers.joinToString(",") { escapeCsv(it) })
            rows.forEach { row ->
                sb.appendLine(row.joinToString(",") { escapeCsv(it) })
            }
            fos.write(sb.toString().toByteArray(Charsets.UTF_8))
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
        Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun escapeCsv(value: String): String {
        val clean = value.replace("\"", "\"\"")
        return "\"$clean\""
    }
}
