package com.example

import com.example.data.local.InvoiceEntity
import com.example.data.sync.LanSyncClient
import com.example.util.CurrencyFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun lanSyncUrlNormalization_addsHttpAndTrimsSlash() {
    assertEquals("http://192.168.1.50:8787", LanSyncClient.normalizeServerUrl(" 192.168.1.50:8787/ "))
    assertEquals("http://10.0.2.2:8787", LanSyncClient.normalizeServerUrl("http://10.0.2.2:8787/"))
    assertEquals("", LanSyncClient.normalizeServerUrl("   "))
  }

  @Test
  fun currencyFormatter_convertsUsdTo2DecimalsAndSspWholeFromSameRate() {
    val exact = CurrencyFormatter.convertSspToUsd(8125.0, 650.0)
    assertEquals(12.50, exact.usdAmount, 0.0001)
    assertEquals(8125L, exact.sspEquivalent)
    assertEquals("$12.50", exact.usdText)
    assertEquals("SSP 8,125", exact.sspText)
    assertEquals("$12.50 (SSP 8,125)", exact.combinedText)

    // SSP side is calculated from the rounded USD and same rate so USD and SSP always match
    val rounded = CurrencyFormatter.convertSspToUsd(1000.0, 650.0)
    assertEquals(1.54, rounded.usdAmount, 0.0001)
    assertEquals(1001L, rounded.sspEquivalent)
    assertEquals("$1.54 (SSP 1,001)", rounded.combinedText)
  }

  @Test
  fun currencyFormatter_prefersStoredInvoiceRateOverSettingsRate() {
    val usdInv = InvoiceEntity(
      id = "inv_1",
      invoiceNumber = "INV-1",
      saleType = "retail",
      customerId = "",
      customerName = "",
      itemsJson = "[]",
      itemsSummary = "",
      subtotal = 8125.0,
      discount = 0.0,
      total = 8125.0,
      costTotal = 4000.0,
      paymentMethod = "usd",
      status = "completed",
      businessDayId = "day_1",
      cashierUid = "u1",
      cashierName = "omini",
      currencyCode = "USD",
      usdRateSsp = 650.0,
      usdTotal = 12.50
    )
    assertTrue(CurrencyFormatter.isInvoiceUsd(usdInv))
    assertEquals(650.0, CurrencyFormatter.effectiveInvoiceRate(usdInv, 1400.0), 0.001)

    val sspInv = usdInv.copy(paymentMethod = "cash", currencyCode = "SSP", usdRateSsp = 0.0, usdTotal = 0.0)
    assertFalse(CurrencyFormatter.isInvoiceUsd(sspInv))
    assertEquals(1400.0, CurrencyFormatter.effectiveInvoiceRate(sspInv, 1400.0), 0.001)
  }
}
