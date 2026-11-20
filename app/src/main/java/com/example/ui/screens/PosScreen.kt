package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.InvoiceEntity
import com.example.i18n.AppLanguage
import com.example.ui.components.ProductThumbnail
import com.example.ui.components.StatusBadge
import com.example.ui.theme.DangerCrimson
import com.example.ui.theme.LocalStoreStrings
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.SuccessTeal
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.CartItem
import com.example.ui.viewmodel.MainTab
import com.example.ui.viewmodel.ProductWithStock
import com.example.ui.viewmodel.StoreUiState
import com.example.ui.viewmodel.StoreViewModel
import com.example.util.BarcodeScannerDialog
import com.example.util.CurrencyFormatter
import com.example.util.ExportManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PosScreen(
    state: StoreUiState,
    viewModel: StoreViewModel
) {
    val strings = LocalStoreStrings.current
    val context = LocalContext.current
    val isRtl = state.language == AppLanguage.AR

    if (!state.effectiveRole.canSell()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(12.dp))
            Text(
                strings.cashierNoSaleMsg,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = { viewModel.selectTab(MainTab.INVENTORY) }) {
                Text(strings.navInventory)
            }
        }
        return
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("all") }
    var showScanner by remember { mutableStateOf(false) }
    var showMobileCartSheet by remember { mutableStateOf(false) }
    var showInvoicesDialog by remember { mutableStateOf(false) }
    var selectedInvoiceForView by remember { mutableStateOf<InvoiceEntity?>(null) }
    var pendingReturnInvoice by remember { mutableStateOf<InvoiceEntity?>(null) }

    val filteredProducts = remember(state.products, searchQuery, selectedCategory) {
        state.products.filter { pw ->
            val p = pw.product
            val matchesCat = selectedCategory == "all" || p.category == selectedCategory
            val q = searchQuery.trim()
            val matchesSearch = q.isBlank() ||
                p.nameAr.contains(q, ignoreCase = true) ||
                p.nameEn.contains(q, ignoreCase = true) ||
                p.sku.contains(q, ignoreCase = true) ||
                p.barcode.contains(q, ignoreCase = true)
            matchesCat && matchesSearch
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 760.dp

        Column(modifier = Modifier.fillMaxSize()) {
            if (state.openBusinessDay == null) {
                Surface(
                    color = WarningAmber.copy(alpha = 0.16f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.Lock, contentDescription = null, tint = WarningAmber)
                            Text(
                                strings.dayClosedBanner,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { viewModel.selectTab(MainTab.DAY_CONTROL) },
                            modifier = Modifier.testTag("pos_open_day_banner_btn")
                        ) {
                            Text(strings.openDayNow)
                        }
                    }
                }
            }

            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .weight(1.3f)
                        .fillMaxHeight()
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text(strings.searchPlaceholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pos_search_input")
                        )
                        Button(
                            onClick = { showScanner = true },
                            modifier = Modifier
                                .height(54.dp)
                                .testTag("pos_scan_barcode_btn")
                        ) {
                            Icon(Icons.Filled.QrCodeScanner, contentDescription = strings.scanBarcode)
                        }
                        OutlinedButton(
                            onClick = { showInvoicesDialog = true },
                            modifier = Modifier
                                .height(54.dp)
                                .testTag("pos_recent_invoices_btn")
                        ) {
                            Icon(Icons.Filled.History, contentDescription = strings.recentInvoices)
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val cats = listOf(
                            "all" to strings.allCategories,
                            "perfume" to strings.catPerfume,
                            "cream" to strings.catCream,
                            "other" to strings.catOther
                        )
                        items(cats) { (key, label) ->
                            FilterChip(
                                selected = selectedCategory == key,
                                onClick = { selectedCategory = key },
                                label = { Text(label) },
                                modifier = Modifier.testTag("pos_cat_$key")
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 168.dp),
                        contentPadding = PaddingValues(bottom = if (isWide) 16.dp else 88.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(filteredProducts, key = { it.product.id }) { pw ->
                            PosProductCard(
                                item = pw,
                                isRtl = isRtl,
                                onClick = { viewModel.addToCart(pw) }
                            )
                        }
                    }
                }

                if (isWide) {
                    HorizontalDivider(modifier = Modifier.fillMaxHeight().width(1.dp))
                    CartPanel(
                        state = state,
                        viewModel = viewModel,
                        modifier = Modifier
                            .weight(0.9f)
                            .fillMaxHeight()
                            .padding(12.dp)
                    )
                }
            }
        }

        if (!isWide) {
            val itemCount = state.cartItems.sumOf { it.quantity }
            val cartTotal = (state.cartItems.sumOf { it.lineTotal } - state.discountAmount).coerceAtLeast(0.0)
            Surface(
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(12.dp)
                    .clickable { showMobileCartSheet = true }
                    .testTag("pos_mobile_cart_bar")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Filled.ShoppingCart, contentDescription = strings.cartTitle)
                        Text(
                            "${strings.cartTitle} ($itemCount)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    val activeRate = state.usdRateSsp.takeIf { it > 0.0 } ?: state.settings.usdRateSsp
                    DualCurrencyAmountText(
                        sspAmount = cartTotal,
                        isUsd = state.selectedPaymentMethod == "usd",
                        usdRateSsp = activeRate,
                        currencyCode = strings.currencyCode,
                        primaryStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    if (showMobileCartSheet) {
        ModalBottomSheet(onDismissRequest = { showMobileCartSheet = false }) {
            CartPanel(
                state = state,
                viewModel = viewModel,
                onCheckoutDone = { showMobileCartSheet = false },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 580.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }

    if (showScanner) {
        BarcodeScannerDialog(
            onBarcodeDetected = { code ->
                showScanner = false
                viewModel.addToCartByBarcode(code)
            },
            onDismiss = { showScanner = false }
        )
    }

    val activeReceipt = state.lastCompletedInvoice ?: selectedInvoiceForView
    activeReceipt?.let { receipt ->
        val storeName = if (isRtl) state.settings.storeNameAr else state.settings.storeNameEn
        val fallbackRate = state.usdRateSsp.takeIf { it > 0.0 } ?: state.settings.usdRateSsp
        val isUsdReceipt = CurrencyFormatter.isInvoiceUsd(receipt)
        val receiptRate = CurrencyFormatter.effectiveInvoiceRate(receipt, fallbackRate)
        val parsedItems = remember(receipt.id, receipt.itemsJson) {
            CurrencyFormatter.parseInvoiceItems(receipt.itemsJson)
        }
        val dateFmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }

        AlertDialog(
            onDismissRequest = {
                if (state.lastCompletedInvoice != null) viewModel.clearLastReceipt()
                selectedInvoiceForView = null
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.ReceiptLong, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(strings.receiptTitle)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("${strings.invoiceNumber}: ${receipt.invoiceNumber}", fontWeight = FontWeight.Bold, fontFamily = MonoFontFamily)
                    Text(dateFmt.format(Date(receipt.createdAtMillis)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "${if (isRtl) "الدفع" else "Payment"}: ${if (isUsdReceipt) "USD" else receipt.paymentMethod.uppercase()} • ${receipt.saleType.uppercase()}",
                        style = MaterialTheme.typography.labelMedium
                    )
                    if (isUsdReceipt && receiptRate > 0.0) {
                        Text(
                            "1 USD = ${CurrencyFormatter.formatRateWholeOrDecimal(receiptRate)} ${strings.currencyCode}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HorizontalDivider()

                    if (parsedItems.isNotEmpty()) {
                        parsedItems.forEach { item ->
                            val itemName = if (isRtl) item.nameAr.ifBlank { item.nameEn } else item.nameEn.ifBlank { item.nameAr }
                            val tier = if (item.wholesale) "W" else "R"
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text("$itemName x${item.qty} ($tier)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    DualCurrencyAmountText(
                                        sspAmount = item.unitPriceSsp,
                                        isUsd = isUsdReceipt,
                                        usdRateSsp = receiptRate,
                                        currencyCode = strings.currencyCode,
                                        primaryStyle = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily)
                                    )
                                    DualCurrencyAmountText(
                                        sspAmount = item.lineTotalSsp,
                                        isUsd = isUsdReceipt,
                                        usdRateSsp = receiptRate,
                                        currencyCode = strings.currencyCode,
                                        primaryStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    } else {
                        Text(receipt.itemsSummary, style = MaterialTheme.typography.bodyMedium)
                    }

                    HorizontalDivider()
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(strings.subtotal, style = MaterialTheme.typography.bodyMedium)
                        DualCurrencyAmountText(
                            sspAmount = receipt.subtotal,
                            isUsd = isUsdReceipt,
                            usdRateSsp = receiptRate,
                            currencyCode = strings.currencyCode,
                            primaryStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily)
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(strings.discount, style = MaterialTheme.typography.bodyMedium)
                        DualCurrencyAmountText(
                            sspAmount = receipt.discount,
                            isUsd = isUsdReceipt,
                            usdRateSsp = receiptRate,
                            currencyCode = strings.currencyCode,
                            primaryStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily),
                            negativePrefix = receipt.discount > 0.0
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(strings.total, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        DualCurrencyAmountText(
                            sspAmount = receipt.total,
                            isUsd = isUsdReceipt,
                            usdRateSsp = receiptRate,
                            currencyCode = strings.currencyCode,
                            primaryStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily),
                            primaryColor = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            ExportManager.printInvoicePdf(context, storeName, receipt, strings.currencyCode, isRtl, fallbackRate)
                        },
                        modifier = Modifier.testTag("print_receipt_btn")
                    ) {
                        Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.printReceipt)
                    }
                    Button(
                        onClick = {
                            ExportManager.exportInvoicePdf(context, storeName, receipt, strings.currencyCode, isRtl, fallbackRate)
                                .onSuccess { intent -> context.startActivity(IntentChooserHelper.create(intent, strings.sharePdfReceipt)) }
                        },
                        modifier = Modifier.testTag("share_receipt_pdf_btn")
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.sharePdfReceipt)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (state.lastCompletedInvoice != null) viewModel.clearLastReceipt()
                        selectedInvoiceForView = null
                    }
                ) {
                    Text(strings.closeLabel)
                }
            }
        )
    }

    if (showInvoicesDialog) {
        val fallbackRate = state.usdRateSsp.takeIf { it > 0.0 } ?: state.settings.usdRateSsp
        AlertDialog(
            onDismissRequest = { showInvoicesDialog = false },
            title = { Text(strings.recentInvoices) },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.invoices.take(25), key = { it.id }) { inv ->
                        val invIsUsd = CurrencyFormatter.isInvoiceUsd(inv)
                        val invRate = CurrencyFormatter.effectiveInvoiceRate(inv, fallbackRate)
                        Card(
                            modifier = Modifier.clickable {
                                showInvoicesDialog = false
                                selectedInvoiceForView = inv
                            },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(inv.invoiceNumber, fontWeight = FontWeight.Bold, fontFamily = MonoFontFamily)
                                    StatusBadge(
                                        label = if (inv.status == "returned") strings.returnedBadge else strings.completedBadge,
                                        containerColor = if (inv.status == "returned") DangerCrimson.copy(alpha = 0.15f) else SuccessTeal.copy(alpha = 0.15f),
                                        contentColor = if (inv.status == "returned") DangerCrimson else SuccessTeal
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    DualCurrencyAmountText(
                                        sspAmount = inv.total,
                                        isUsd = invIsUsd,
                                        usdRateSsp = invRate,
                                        currencyCode = strings.currencyCode,
                                        primaryStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text("• ${inv.paymentMethod.uppercase()}", style = MaterialTheme.typography.labelSmall)
                                }
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            showInvoicesDialog = false
                                            selectedInvoiceForView = inv
                                        }
                                    ) {
                                        Text(strings.viewInvoice)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            val storeName = if (isRtl) state.settings.storeNameAr else state.settings.storeNameEn
                                            ExportManager.printInvoicePdf(context, storeName, inv, strings.currencyCode, isRtl, fallbackRate)
                                        }
                                    ) {
                                        Text(strings.printReceipt)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            val storeName = if (isRtl) state.settings.storeNameAr else state.settings.storeNameEn
                                            ExportManager.exportInvoicePdf(context, storeName, inv, strings.currencyCode, isRtl, fallbackRate)
                                                .onSuccess { intent -> context.startActivity(IntentChooserHelper.create(intent, strings.sharePdfReceipt)) }
                                        }
                                    ) {
                                        Text(strings.sharePdfReceipt)
                                    }
                                    if (inv.status == "completed") {
                                        TextButton(
                                            onClick = { pendingReturnInvoice = inv },
                                            colors = ButtonDefaults.textButtonColors(contentColor = DangerCrimson)
                                        ) {
                                            Text(strings.returnInvoice)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showInvoicesDialog = false }) { Text(strings.closeLabel) }
            }
        )
    }

    pendingReturnInvoice?.let { inv ->
        AlertDialog(
            onDismissRequest = { pendingReturnInvoice = null },
            title = { Text(strings.returnInvoice) },
            text = { Text(strings.returnConfirmMsg) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.returnInvoice(inv)
                        pendingReturnInvoice = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerCrimson)
                ) {
                    Text(strings.confirmLabel)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingReturnInvoice = null }) { Text(strings.cancelLabel) }
            }
        )
    }
}

@Composable
private fun PosProductCard(
    item: ProductWithStock,
    isRtl: Boolean,
    onClick: () -> Unit
) {
    val strings = LocalStoreStrings.current
    val p = item.product
    val title = if (isRtl) p.nameAr else p.nameEn
    val unit = if (isRtl) p.unitAr else p.unitEn

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("pos_product_card_${p.id}"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                ProductThumbnail(imageUri = p.imageUri, category = p.category, contentDescription = title, size = 54.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${p.sku} • $unit",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "${strings.retailPrice}: ${String.format(Locale.US, "%.2f", p.retailPrice)}",
                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = MonoFontFamily),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "${strings.wholesalePrice} (${p.wholesaleMinQty}+): ${String.format(Locale.US, "%.2f", p.wholesalePrice)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val badgeColor = when {
                    item.currentStock <= 0 -> DangerCrimson
                    item.isLowStock -> WarningAmber
                    else -> SuccessTeal
                }
                StatusBadge(
                    label = "${item.currentStock}",
                    containerColor = badgeColor.copy(alpha = 0.15f),
                    contentColor = badgeColor
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CartPanel(
    state: StoreUiState,
    viewModel: StoreViewModel,
    onCheckoutDone: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val strings = LocalStoreStrings.current
    val isRtl = state.language == AppLanguage.AR
    val subtotal = state.cartItems.sumOf { it.lineTotal }
    val appliedDiscount = state.discountAmount.coerceIn(0.0, subtotal)
    val total = (subtotal - appliedDiscount).coerceAtLeast(0.0)
    val isUsdMode = state.selectedPaymentMethod == "usd"
    var discountInput by remember(state.discountAmount) {
        mutableStateOf(if (state.discountAmount == 0.0) "" else state.discountAmount.toString())
    }
    var usdRateInput by remember(state.usdRateSsp) {
        mutableStateOf(
            if (state.usdRateSsp == state.usdRateSsp.toLong().toDouble()) {
                state.usdRateSsp.toLong().toString()
            } else {
                String.format(Locale.US, "%.2f", state.usdRateSsp)
            }
        )
    }
    val activeRate = (usdRateInput.toDoubleOrNull()?.takeIf { it > 0.0 })
        ?: state.usdRateSsp.takeIf { it > 0.0 }
        ?: state.settings.usdRateSsp
    var customerExpanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(strings.cartTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        if (state.cartItems.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    strings.emptyCart,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(18.dp)
                )
            }
        } else {
            state.cartItems.forEach { cartItem ->
                CartLineRow(
                    item = cartItem,
                    isRtl = isRtl,
                    isUsdMode = isUsdMode,
                    usdRateSsp = activeRate,
                    currencyCode = strings.currencyCode,
                    onIncrement = { viewModel.updateCartItemQuantity(cartItem.product.id, cartItem.quantity + 1) },
                    onDecrement = { viewModel.updateCartItemQuantity(cartItem.product.id, cartItem.quantity - 1) },
                    onSetQuantity = { q -> viewModel.updateCartItemQuantity(cartItem.product.id, q) },
                    onToggleWholesale = { viewModel.toggleCartItemWholesale(cartItem.product.id) }
                )
            }

            OutlinedTextField(
                value = discountInput,
                onValueChange = { raw ->
                    discountInput = raw
                    viewModel.setDiscount(raw.toDoubleOrNull() ?: 0.0)
                },
                label = { Text(strings.discount) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("pos_discount_input")
            )

            Text(strings.paymentMethod, style = MaterialTheme.typography.labelLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    "cash" to strings.payCash,
                    "transfer" to strings.payTransfer,
                    "credit" to strings.payCredit,
                    "usd" to strings.payUsd
                ).forEach { (key, label) ->
                    FilterChip(
                        selected = state.selectedPaymentMethod == key,
                        onClick = { viewModel.setPaymentMethod(key) },
                        label = { Text(label) },
                        modifier = Modifier.testTag("pos_pay_$key")
                    )
                }
            }

            if (isUsdMode) {
                OutlinedTextField(
                    value = usdRateInput,
                    onValueChange = { raw ->
                        usdRateInput = raw
                        raw.toDoubleOrNull()?.let { parsed ->
                            if (parsed > 0.0) viewModel.setUsdRateSsp(parsed)
                        }
                    },
                    label = { Text(strings.usdExchangeRateLabel) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("pos_usd_rate_input")
                )
            }

            ExposedDropdownMenuBox(
                expanded = customerExpanded,
                onExpandedChange = { customerExpanded = !customerExpanded }
            ) {
                val selectedName = state.customers.find { it.id == state.selectedCustomerId }?.name ?: strings.walkInCustomer
                OutlinedTextField(
                    value = selectedName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(strings.selectCustomer) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = customerExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = customerExpanded, onDismissRequest = { customerExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(strings.walkInCustomer) },
                        onClick = {
                            viewModel.setSelectedCustomer(null)
                            customerExpanded = false
                        }
                    )
                    state.customers.forEach { cust ->
                        DropdownMenuItem(
                            text = { Text("${cust.name} (${String.format(Locale.US, "%.2f", cust.balance)})") },
                            onClick = {
                                viewModel.setSelectedCustomer(cust.id)
                                customerExpanded = false
                            }
                        )
                    }
                }
            }

            HorizontalDivider()

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(strings.subtotal)
                DualCurrencyAmountText(
                    sspAmount = subtotal,
                    isUsd = isUsdMode,
                    usdRateSsp = activeRate,
                    currencyCode = strings.currencyCode,
                    primaryStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily)
                )
            }
            if (appliedDiscount > 0.0 || isUsdMode) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(strings.discount)
                    DualCurrencyAmountText(
                        sspAmount = appliedDiscount,
                        isUsd = isUsdMode,
                        usdRateSsp = activeRate,
                        currencyCode = strings.currencyCode,
                        primaryStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily),
                        negativePrefix = appliedDiscount > 0.0
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(strings.total, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                DualCurrencyAmountText(
                    sspAmount = total,
                    isUsd = isUsdMode,
                    usdRateSsp = activeRate,
                    currencyCode = strings.currencyCode,
                    primaryStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily),
                    primaryColor = if (isUsdMode) SuccessTeal else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag(if (isUsdMode) "pos_total_usd_text" else "pos_total_ssp_text")
                )
            }

            Button(
                onClick = {
                    viewModel.checkoutCart()
                    onCheckoutDone()
                },
                enabled = state.openBusinessDay != null && state.cartItems.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("pos_checkout_btn")
            ) {
                Text(strings.completeSale, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun DualCurrencyAmountText(
    sspAmount: Double,
    isUsd: Boolean,
    usdRateSsp: Double,
    currencyCode: String,
    modifier: Modifier = Modifier,
    primaryStyle: TextStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily),
    primaryColor: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    negativePrefix: Boolean = false
) {
    if (isUsd && usdRateSsp > 0.0) {
        val conv = CurrencyFormatter.convertSspToUsd(sspAmount, usdRateSsp, negativePrefix = negativePrefix)
        val subFontSize = if (primaryStyle.fontSize.isSp) {
            (primaryStyle.fontSize.value * 0.78f).coerceAtLeast(10f).sp
        } else {
            MaterialTheme.typography.labelSmall.fontSize
        }
        val subColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
        Text(
            text = buildAnnotatedString {
                withStyle(
                    SpanStyle(
                        color = primaryColor,
                        fontWeight = fontWeight ?: primaryStyle.fontWeight
                    )
                ) {
                    append(conv.usdText)
                }
                append(" ")
                withStyle(
                    SpanStyle(
                        fontSize = subFontSize,
                        color = subColor,
                        fontWeight = FontWeight.Normal
                    )
                ) {
                    append("(${conv.sspText})")
                }
            },
            style = primaryStyle,
            modifier = modifier
        )
    } else {
        val sign = if (negativePrefix && sspAmount > 0.0) "-" else ""
        Text(
            text = "$sign${String.format(Locale.US, "%.2f", sspAmount)} $currencyCode",
            style = primaryStyle,
            color = primaryColor,
            fontWeight = fontWeight ?: primaryStyle.fontWeight,
            modifier = modifier
        )
    }
}

@Composable
private fun CartLineRow(
    item: CartItem,
    isRtl: Boolean,
    isUsdMode: Boolean,
    usdRateSsp: Double,
    currencyCode: String,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onSetQuantity: (Int) -> Unit,
    onToggleWholesale: () -> Unit
) {
    val strings = LocalStoreStrings.current
    val name = if (isRtl) item.product.nameAr else item.product.nameEn
    var qtyText by remember(item.product.id, item.quantity) { mutableStateOf(item.quantity.toString()) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    DualCurrencyAmountText(
                        sspAmount = item.unitPrice,
                        isUsd = isUsdMode,
                        usdRateSsp = usdRateSsp,
                        currencyCode = currencyCode,
                        primaryStyle = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                        primaryColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(8.dp))
                DualCurrencyAmountText(
                    sspAmount = item.lineTotal,
                    isUsd = isUsdMode,
                    usdRateSsp = usdRateSsp,
                    currencyCode = currencyCode,
                    primaryStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily),
                    fontWeight = FontWeight.Bold
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDecrement, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.Remove, contentDescription = "-")
                    }
                    OutlinedTextField(
                        value = qtyText,
                        onValueChange = { raw ->
                            val digits = raw.filter { it.isDigit() }
                            qtyText = digits
                            val parsed = digits.toIntOrNull()
                            if (parsed != null && parsed >= 1) {
                                onSetQuantity(parsed)
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .width(76.dp)
                            .testTag("cart_qty_input_${item.product.id}")
                    )
                    IconButton(onClick = onIncrement, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.Add, contentDescription = "+")
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        if (item.useWholesalePrice) strings.wholesalePrice else strings.retailPrice,
                        style = MaterialTheme.typography.labelMedium
                    )
                    Switch(
                        checked = item.useWholesalePrice,
                        onCheckedChange = { onToggleWholesale() },
                        modifier = Modifier.testTag("cart_wholesale_switch_${item.product.id}")
                    )
                }
            }
        }
    }
}

object IntentChooserHelper {
    fun create(base: android.content.Intent, title: String): android.content.Intent =
        android.content.Intent.createChooser(base, title)
}
