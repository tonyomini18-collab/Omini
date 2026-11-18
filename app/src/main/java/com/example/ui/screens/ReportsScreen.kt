package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.i18n.AppLanguage
import com.example.ui.theme.LocalStoreStrings
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.SuccessTeal
import com.example.ui.viewmodel.PeriodFilter
import com.example.ui.viewmodel.StoreUiState
import com.example.ui.viewmodel.StoreViewModel
import com.example.util.ExportManager
import java.util.Locale

@Composable
fun ReportsScreen(
    state: StoreUiState,
    viewModel: StoreViewModel
) {
    val strings = LocalStoreStrings.current
    val context = LocalContext.current
    val isRtl = state.language == AppLanguage.AR
    val storeName = if (isRtl) state.settings.storeNameAr else state.settings.storeNameEn
    val a = state.analytics

    if (!state.effectiveRole.canViewProfitAndReports()) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text(strings.accessDeniedMsg, color = MaterialTheme.colorScheme.error)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(strings.reportsTitle, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    PeriodFilter.DAY to strings.periodDay,
                    PeriodFilter.WEEK to strings.periodWeek,
                    PeriodFilter.MONTH to strings.periodMonth,
                    PeriodFilter.YEAR to strings.periodYear
                ).forEach { (filter, label) ->
                    FilterChip(
                        selected = state.periodFilter == filter,
                        onClick = { viewModel.setPeriodFilter(filter) },
                        label = { Text(label) }
                    )
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        val lines = buildList {
                            add("${strings.reportSales}: ${String.format(Locale.US, "%.2f", a.periodSales)} ${strings.currencyCode}")
                            add("${strings.reportPurchases}: ${String.format(Locale.US, "%.2f", a.periodPurchases)} ${strings.currencyCode}")
                            add("${strings.reportProfit}: ${String.format(Locale.US, "%.2f", a.periodProfit)} ${strings.currencyCode}")
                            add("${strings.reportStockValue}: ${String.format(Locale.US, "%.2f", a.stockTotalValueCost)} ${strings.currencyCode}")
                            add("")
                            add("--- ${strings.reportUserPerf} ---")
                            a.userPerformance.forEach { u ->
                                add("${u.userName}: ${u.invoiceCount} inv | ${String.format(Locale.US, "%.2f", u.totalSales)}")
                            }
                            add("")
                            add("--- ${strings.bestSelling} ---")
                            a.bestSelling.forEach { m ->
                                add("${if (isRtl) m.nameAr else m.nameEn}: ${m.unitsSold} units (${String.format(Locale.US, "%.2f", m.revenue)})")
                            }
                        }
                        ExportManager.exportReportPdf(context, storeName, strings.reportsTitle, lines)
                            .onSuccess { intent -> context.startActivity(IntentChooserHelper.create(intent, strings.exportPdf)) }
                    },
                    modifier = Modifier.weight(1f).testTag("export_pdf_report_btn")
                ) {
                    Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.exportPdf)
                }

                OutlinedButton(
                    onClick = {
                        val headers = listOf("Invoice", "Type", "Payment", "Status", "Cashier", "Subtotal", "Discount", "Total", "Cost")
                        val rows = state.invoices.map { inv ->
                            listOf(
                                inv.invoiceNumber,
                                inv.saleType,
                                inv.paymentMethod,
                                inv.status,
                                inv.cashierName,
                                String.format(Locale.US, "%.2f", inv.subtotal),
                                String.format(Locale.US, "%.2f", inv.discount),
                                String.format(Locale.US, "%.2f", inv.total),
                                String.format(Locale.US, "%.2f", inv.costTotal)
                            )
                        }
                        ExportManager.exportExcelCsv(context, "aroma_sales_report", headers, rows)
                            .onSuccess { intent -> context.startActivity(IntentChooserHelper.create(intent, strings.exportExcel)) }
                    },
                    modifier = Modifier.weight(1f).testTag("export_excel_report_btn")
                ) {
                    Icon(Icons.Filled.TableChart, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.exportExcel)
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReportLine(strings.reportSales, "${String.format(Locale.US, "%.2f", a.periodSales)} ${strings.currencyCode}")
                    ReportLine(strings.reportPurchases, "${String.format(Locale.US, "%.2f", a.periodPurchases)} ${strings.currencyCode}")
                    ReportLine(strings.reportProfit, "${String.format(Locale.US, "%.2f", a.periodProfit)} ${strings.currencyCode}", SuccessTeal)
                    HorizontalDivider()
                    ReportLine(strings.reportStockValue, "${String.format(Locale.US, "%.2f", a.stockTotalValueCost)} ${strings.currencyCode}")
                }
            }
        }

        item {
            Text(strings.reportUserPerf, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        items(a.userPerformance, key = { it.userId }) { u ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(u.userName, fontWeight = FontWeight.SemiBold)
                        Text("${u.invoiceCount} ${strings.recentInvoices}", style = MaterialTheme.typography.labelMedium)
                    }
                    Text(
                        "${String.format(Locale.US, "%.2f", u.totalSales)} ${strings.currencyCode}",
                        fontFamily = MonoFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ReportLine(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge.copy(fontFamily = MonoFontFamily), fontWeight = FontWeight.Bold, color = valueColor)
    }
}
