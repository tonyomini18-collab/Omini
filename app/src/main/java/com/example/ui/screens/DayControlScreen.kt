package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.local.HolidayEntity
import com.example.i18n.AppLanguage
import com.example.ui.components.StatusBadge
import com.example.ui.theme.DangerCrimson
import com.example.ui.theme.LocalStoreStrings
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.SuccessTeal
import com.example.ui.viewmodel.StoreUiState
import com.example.ui.viewmodel.StoreViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DayControlScreen(
    state: StoreUiState,
    viewModel: StoreViewModel
) {
    val strings = LocalStoreStrings.current
    val isRtl = state.language == AppLanguage.AR
    val role = state.effectiveRole

    var openingCashStr by remember { mutableStateOf("0") }
    var holidayOverrideCandidate by remember { mutableStateOf<HolidayEntity?>(null) }
    var showAddHolidayDialog by remember { mutableStateOf(false) }

    val openDay = state.openBusinessDay
    val summaryDay = openDay ?: state.lastClosedDay

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(strings.dayControlTitle, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusBadge(
                            label = if (openDay != null) strings.dayStatusOpen else strings.dayStatusClosed,
                            containerColor = if (openDay != null) SuccessTeal.copy(alpha = 0.16f) else DangerCrimson.copy(alpha = 0.14f),
                            contentColor = if (openDay != null) SuccessTeal else DangerCrimson
                        )
                        Text(
                            openDay?.dateStr ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = MonoFontFamily)
                        )
                    }

                    if (openDay == null) {
                        OutlinedTextField(
                            value = openingCashStr,
                            onValueChange = { openingCashStr = it },
                            label = { Text(strings.openingCashLabel) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("opening_cash_input")
                        )
                        Button(
                            onClick = {
                                val holiday = viewModel.isTodayHoliday()
                                if (holiday != null) {
                                    holidayOverrideCandidate = holiday
                                } else {
                                    viewModel.openBusinessDay(openingCashStr.toDoubleOrNull() ?: 0.0)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("open_day_btn")
                        ) {
                            Icon(Icons.Filled.LockOpen, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(strings.openBusinessDay)
                        }
                    } else {
                        Button(
                            onClick = { viewModel.closeBusinessDay() },
                            colors = ButtonDefaults.buttonColors(containerColor = DangerCrimson),
                            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("close_day_btn")
                        ) {
                            Icon(Icons.Filled.Lock, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(strings.closeBusinessDay)
                        }
                    }
                }
            }
        }

        if (summaryDay != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "${strings.daySummaryTitle} (${summaryDay.dateStr})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        HorizontalDivider()
                        val usdSales = state.invoices
                            .filter { it.businessDayId == summaryDay.id && it.status == "completed" && it.paymentMethod == "usd" }
                            .sumOf { it.total }
                        SummaryRow(strings.openingCashLabel, "${String.format(Locale.US, "%.2f", summaryDay.openingCash)} ${strings.currencyCode}")
                        SummaryRow(strings.totalSalesLabel, "${String.format(Locale.US, "%.2f", summaryDay.totalSales)} ${strings.currencyCode}")
                        SummaryRow(strings.cashSalesLabel, "${String.format(Locale.US, "%.2f", summaryDay.cashSales)} ${strings.currencyCode}")
                        SummaryRow(strings.transferSalesLabel, "${String.format(Locale.US, "%.2f", summaryDay.transferSales)} ${strings.currencyCode}")
                        SummaryRow(strings.creditSalesLabel, "${String.format(Locale.US, "%.2f", summaryDay.creditSales)} ${strings.currencyCode}")
                        SummaryRow(strings.usdSalesLabel, "${String.format(Locale.US, "%.2f", usdSales)} ${strings.currencyCode}")
                        SummaryRow(strings.itemsSoldLabel, "${summaryDay.itemsSoldCount}")
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(strings.holidaysTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                if (role.canManageCatalog()) {
                    OutlinedButton(onClick = { showAddHolidayDialog = true }, modifier = Modifier.testTag("add_holiday_btn")) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(strings.addHoliday)
                    }
                }
            }
        }

        items(state.holidays, key = { it.id }) { h ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(if (isRtl) h.titleAr else h.titleEn, style = MaterialTheme.typography.titleMedium)
                        val sub = if (h.isWeeklyOffDay) {
                            "${strings.weeklyOffDay}: ${dayOfWeekLabel(h.dayOfWeek, isRtl)}"
                        } else {
                            h.dateStr
                        }
                        Text(sub, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (role.canManageCatalog()) {
                        IconButton(onClick = { viewModel.deleteHoliday(h.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = strings.deleteProduct, tint = DangerCrimson)
                        }
                    }
                }
            }
        }
    }

    holidayOverrideCandidate?.let { holiday ->
        AlertDialog(
            onDismissRequest = { holidayOverrideCandidate = null },
            title = { Text(strings.holidayBlockTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (isRtl) holiday.titleAr else holiday.titleEn, fontWeight = FontWeight.Bold)
                    Text(strings.holidayBlockMessage)
                }
            },
            confirmButton = {
                if (role.canOverrideHoliday()) {
                    Button(
                        onClick = {
                            viewModel.openBusinessDay(openingCashStr.toDoubleOrNull() ?: 0.0, holidayOverride = true)
                            holidayOverrideCandidate = null
                        },
                        modifier = Modifier.testTag("override_holiday_btn")
                    ) {
                        Text(strings.overrideHoliday)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { holidayOverrideCandidate = null }) { Text(strings.closeLabel) }
            }
        )
    }

    if (showAddHolidayDialog) {
        var dateStr by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) }
        var titleAr by remember { mutableStateOf("") }
        var titleEn by remember { mutableStateOf("") }
        var isWeekly by remember { mutableStateOf(false) }
        var dayOfWeek by remember { mutableIntStateOf(5) } // Friday default

        AlertDialog(
            onDismissRequest = { showAddHolidayDialog = false },
            title = { Text(strings.addHoliday) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isWeekly, onCheckedChange = { isWeekly = it })
                        Text(strings.weeklyOffDay)
                    }
                    if (isWeekly) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(5, 6, 7).forEach { dow ->
                                FilterChip(
                                    selected = dayOfWeek == dow,
                                    onClick = { dayOfWeek = dow },
                                    label = { Text(dayOfWeekLabel(dow, isRtl)) }
                                )
                            }
                        }
                    } else {
                        OutlinedTextField(value = dateStr, onValueChange = { dateStr = it }, label = { Text(strings.holidayDateLabel) }, singleLine = true)
                    }
                    OutlinedTextField(value = titleAr, onValueChange = { titleAr = it }, label = { Text(strings.holidayTitleAr) }, singleLine = true)
                    OutlinedTextField(value = titleEn, onValueChange = { titleEn = it }, label = { Text(strings.holidayTitleEn) }, singleLine = true)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addHoliday(dateStr, titleAr, titleEn, isWeekly, dayOfWeek)
                        showAddHolidayDialog = false
                    },
                    enabled = titleAr.isNotBlank() || titleEn.isNotBlank()
                ) { Text(strings.saveLabel) }
            },
            dismissButton = {
                TextButton(onClick = { showAddHolidayDialog = false }) { Text(strings.cancelLabel) }
            }
        )
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily), fontWeight = FontWeight.SemiBold)
    }
}

private fun dayOfWeekLabel(dow: Int, isRtl: Boolean): String = when (dow) {
    1 -> if (isRtl) "الاثنين" else "Mon"
    2 -> if (isRtl) "الثلاثاء" else "Tue"
    3 -> if (isRtl) "الأربعاء" else "Wed"
    4 -> if (isRtl) "الخميس" else "Thu"
    5 -> if (isRtl) "الجمعة" else "Fri"
    6 -> if (isRtl) "السبت" else "Sat"
    else -> if (isRtl) "الأحد" else "Sun"
}
