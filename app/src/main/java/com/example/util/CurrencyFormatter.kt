package com.example.util

import com.example.data.local.InvoiceEntity
import org.json.JSONArray
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

data class ConvertedAmount(
    val usdAmount: Double,
    val sspEquivalent: Long,
    val usdText: String,
    val sspText: String
) {
    val combinedText: String get() = "$usdText ($sspText)"
}

data class InvoiceLineItem(
    val productId: String,
    val nameAr: String,
    val nameEn: String,
    val qty: Int,
    val unitPriceSsp: Double,
    val wholesale: Boolean
) {
    val lineTotalSsp: Double get() = unitPriceSsp * qty
}

object CurrencyFormatter {
    fun roundUsd(value: Double): Double {
        val rounded = round(value * 100.0) / 100.0
        return if (abs(rounded) < 0.005) 0.0 else rounded
    }

    fun formatUsdValue(usdValue: Double, prefixMinus: Boolean = false): String {
        val nf = NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
            isGroupingUsed = true
        }
        val rounded = roundUsd(usdValue)
        val sign = if (prefixMinus || rounded < 0.0) "-" else ""
        return "$sign$${nf.format(abs(rounded))}"
    }

    fun formatSspWhole(sspValue: Long): String {
        val nf = NumberFormat.getIntegerInstance(Locale.US).apply {
            isGroupingUsed = true
        }
        return "SSP ${nf.format(abs(sspValue))}"
    }

    fun formatSspStandard(sspValue: Double, currencyCode: String = "SSP"): String {
        return "${String.format(Locale.US, "%.2f", sspValue)} $currencyCode"
    }

    fun convertSspToUsd(
        sspAmount: Double,
        rateSspPerUsd: Double,
        negativePrefix: Boolean = false
    ): ConvertedAmount {
        if (rateSspPerUsd <= 0.0) {
            return ConvertedAmount(
                usdAmount = 0.0,
                sspEquivalent = 0L,
                usdText = formatUsdValue(0.0, prefixMinus = negativePrefix),
                sspText = formatSspWhole(0L)
            )
        }
        val isNegative = negativePrefix || sspAmount < 0.0
        val absSsp = abs(sspAmount)
        val usdAbs = roundUsd(absSsp / rateSspPerUsd)
        // Keep the SSP side value calculated from the same rate so USD and SSP always match.
        val sspWholeAbs = round(usdAbs * rateSspPerUsd).toLong()
        return ConvertedAmount(
            usdAmount = if (isNegative && usdAbs > 0.0) -usdAbs else usdAbs,
            sspEquivalent = sspWholeAbs,
            usdText = formatUsdValue(usdAbs, prefixMinus = isNegative && usdAbs > 0.0),
            sspText = formatSspWhole(sspWholeAbs)
        )
    }

    fun formatRateWholeOrDecimal(rate: Double): String {
        return if (rate == rate.toLong().toDouble()) {
            NumberFormat.getIntegerInstance(Locale.US).format(rate.toLong())
        } else {
            String.format(Locale.US, "%.2f", rate)
        }
    }

    fun isInvoiceUsd(invoice: InvoiceEntity): Boolean {
        return invoice.currencyCode.equals("USD", ignoreCase = true) ||
            invoice.paymentMethod.equals("usd", ignoreCase = true)
    }

    fun effectiveInvoiceRate(invoice: InvoiceEntity, settingsRate: Double): Double {
        return if (invoice.usdRateSsp > 0.0) invoice.usdRateSsp else settingsRate
    }

    fun parseInvoiceItems(itemsJson: String): List<InvoiceLineItem> {
        if (itemsJson.isBlank() || !itemsJson.trimStart().startsWith("[")) return emptyList()
        return runCatching {
            val arr = JSONArray(itemsJson)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    add(
                        InvoiceLineItem(
                            productId = obj.optString("productId", ""),
                            nameAr = obj.optString("nameAr", ""),
                            nameEn = obj.optString("nameEn", ""),
                            qty = obj.optInt("qty", 1).coerceAtLeast(1),
                            unitPriceSsp = obj.optDouble("unitPrice", 0.0),
                            wholesale = obj.optBoolean("wholesale", false)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}
