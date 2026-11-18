package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.i18n.AppLanguage
import com.example.ui.components.BarDatum
import com.example.ui.components.HorizontalBarChart
import com.example.ui.components.KpiCard
import com.example.ui.theme.DangerCrimson
import com.example.ui.theme.LocalStoreStrings
import com.example.ui.theme.SuccessTeal
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.MainTab
import com.example.ui.viewmodel.PeriodFilter
import com.example.ui.viewmodel.StoreUiState
import com.example.ui.viewmodel.StoreViewModel
import java.util.Locale

@Composable
fun DashboardScreen(
    state: StoreUiState,
    viewModel: StoreViewModel
) {
    val strings = LocalStoreStrings.current
    val isRtl = state.language == AppLanguage.AR
    val role = state.effectiveRole
    val a = state.analytics

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    PeriodFilter.DAY to strings.periodDay,
                    PeriodFilter.WEEK to strings.periodWeek,
                    PeriodFilter.MONTH to strings.periodMonth,
                    PeriodFilter.YEAR to strings.periodYear
                ).forEach { (filter, label) ->
                    FilterChip(
                        selected = state.periodFilter == filter,
                        onClick = { viewModel.setPeriodFilter(filter) },
                        label = { Text(label) },
                        modifier = Modifier.testTag("dash_period_${filter.name.lowercase()}")
                    )
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KpiCard(
                    title = strings.todaySales,
                    value = "${String.format(Locale.US, "%.2f", a.todaySales)} ${strings.currencyCode}",
                    accentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                KpiCard(
                    title = strings.monthSales,
                    value = "${String.format(Locale.US, "%.2f", a.monthSales)} ${strings.currencyCode}",
                    accentColor = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (role.canViewProfitAndReports()) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KpiCard(
                        title = strings.netProfit,
                        value = "${String.format(Locale.US, "%.2f", a.periodProfit)} ${strings.currencyCode}",
                        accentColor = SuccessTeal,
                        modifier = Modifier.weight(1f)
                    )
                    KpiCard(
                        title = strings.lowStockAlerts,
                        value = "${a.lowStockAlerts.size}",
                        accentColor = if (a.lowStockAlerts.isEmpty()) SuccessTeal else WarningAmber,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item {
            HorizontalBarChart(
                title = strings.bestSelling,
                data = a.bestSelling.map { m ->
                    BarDatum(
                        label = if (isRtl) m.nameAr else m.nameEn,
                        value = m.unitsSold.toFloat(),
                        valueLabel = "${m.unitsSold} (${String.format(Locale.US, "%.0f", m.revenue)})"
                    )
                },
                barColor = MaterialTheme.colorScheme.primary,
                emptyText = strings.noDataYet
            )
        }

        item {
            HorizontalBarChart(
                title = strings.worstSelling,
                data = a.worstSelling.map { m ->
                    BarDatum(
                        label = if (isRtl) m.nameAr else m.nameEn,
                        value = m.unitsSold.toFloat().coerceAtLeast(0.1f),
                        valueLabel = "${m.unitsSold}"
                    )
                },
                barColor = WarningAmber,
                emptyText = strings.noDataYet
            )
        }

        item {
            HorizontalBarChart(
                title = strings.highestStock,
                data = a.highestStock.map { pw ->
                    BarDatum(
                        label = if (isRtl) pw.product.nameAr else pw.product.nameEn,
                        value = pw.currentStock.toFloat(),
                        valueLabel = "${pw.currentStock}"
                    )
                },
                barColor = MaterialTheme.colorScheme.secondary,
                emptyText = strings.noDataYet
            )
        }

        if (a.lowStockAlerts.isNotEmpty()) {
            item {
                Text(strings.lowStockAlerts, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            items(a.lowStockAlerts, key = { it.product.id }) { pw ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = DangerCrimson.copy(alpha = 0.08f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = DangerCrimson)
                            Column {
                                Text(
                                    if (isRtl) pw.product.nameAr else pw.product.nameEn,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    "${strings.stockLabel}: ${pw.currentStock} / ${pw.product.lowStockThreshold}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = DangerCrimson
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = { viewModel.selectTab(MainTab.INVENTORY) }) {
                            Text(strings.restockAction)
                        }
                    }
                }
            }
        }
    }
}
