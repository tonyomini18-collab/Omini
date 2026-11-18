package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.data.local.InvoiceEntity
import java.io.File
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

    fun exportInvoicePdf(
        context: Context,
        storeName: String,
        invoice: InvoiceEntity,
        currency: String,
        isRtl: Boolean
    ): Result<Intent> = runCatching {
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val titlePaint = Paint().apply {
            color = Color.parseColor("#1B4D3E")
            textSize = 22f
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
        val linePaint = Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 1.5f
        }

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
        canvas.drawText("${if (isRtl) "الدفع" else "Payment"}: ${invoice.paymentMethod.uppercase()} | ${invoice.saleType.uppercase()}", 48f, y, bodyPaint)

        y += 22f
        canvas.drawLine(48f, y, 547f, y, linePaint)
        y += 24f

        invoice.itemsSummary.split("\n").forEach { line ->
            if (y < 720f) {
                canvas.drawText(line, 48f, y, bodyPaint)
                y += 20f
            }
        }

        y += 12f
        canvas.drawLine(48f, y, 547f, y, linePaint)
        y += 26f
        canvas.drawText("${if (isRtl) "المجموع الفرعي" else "Subtotal"}: ${String.format(Locale.US, "%.2f", invoice.subtotal)} $currency", 48f, y, bodyPaint)
        y += 20f
        canvas.drawText("${if (isRtl) "الخصم" else "Discount"}: -${String.format(Locale.US, "%.2f", invoice.discount)} $currency", 48f, y, bodyPaint)
        y += 24f
        canvas.drawText("${if (isRtl) "الإجمالي" else "TOTAL"}: ${String.format(Locale.US, "%.2f", invoice.total)} $currency", 48f, y, titlePaint)

        doc.finishPage(page)
        val outFile = File(exportsDir(context), "receipt_${invoice.invoiceNumber}.pdf")
        FileOutputStream(outFile).use { doc.writeTo(it) }
        doc.close()

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
        Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
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
