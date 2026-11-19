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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.example.util.ExportManager
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
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
                    Text(
                        "${String.format(Locale.US, "%.2f", cartTotal)} ${strings.currencyCode}",
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily),
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

    state.lastCompletedInvoice?.let { receipt ->
        val storeName = if (isRtl) state.settings.storeNameAr else state.settings.storeNameEn
        AlertDialog(
            onDismissRequest = { viewModel.clearLastReceipt() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.ReceiptLong, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(strings.receiptTitle)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${strings.invoiceNumber}: ${receipt.invoiceNumber}", fontWeight = FontWeight.Bold)
                    Text(receipt.itemsSummary, style = MaterialTheme.typography.bodyMedium)
                    HorizontalDivider()
                    Text(
                        "${strings.total}: ${String.format(Locale.US, "%.2f", receipt.total)} ${strings.currencyCode}",
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        ExportManager.exportInvoicePdf(context, storeName, receipt, strings.currencyCode, isRtl)
                            .onSuccess { intent -> context.startActivity(IntentChooserHelper.create(intent, strings.sharePdfReceipt)) }
                    },
                    modifier = Modifier.testTag("share_receipt_pdf_btn")
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.sharePdfReceipt)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.clearLastReceipt() }) {
                    Text(strings.closeLabel)
                }
            }
        )
    }

    if (showInvoicesDialog) {
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
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
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
                                Text(
                                    "${String.format(Locale.US, "%.2f", inv.total)} ${strings.currencyCode} • ${inv.paymentMethod.uppercase()}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            val storeName = if (isRtl) state.settings.storeNameAr else state.settings.storeNameEn
                                            ExportManager.exportInvoicePdf(context, storeName, inv, strings.currencyCode, isRtl)
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
            .clickable(enabled = item.currentStock > 0, onClick = onClick)
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
    val total = (subtotal - state.discountAmount).coerceAtLeast(0.0)
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
                    onIncrement = { viewModel.updateCartItemQuantity(cartItem.product.id, cartItem.quantity + 1) },
                    onDecrement = { viewModel.updateCartItemQuantity(cartItem.product.id, cartItem.quantity - 1) },
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

            if (state.selectedPaymentMethod == "usd") {
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

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(strings.subtotal)
                Text("${String.format(Locale.US, "%.2f", subtotal)} ${strings.currencyCode}", fontFamily = MonoFontFamily)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(strings.total, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "${String.format(Locale.US, "%.2f", total)} ${strings.currencyCode}",
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (state.selectedPaymentMethod == "usd") {
                val activeRate = (usdRateInput.toDoubleOrNull()?.takeIf { it > 0.0 }) ?: state.usdRateSsp.takeIf { it > 0.0 } ?: 1300.0
                val totalUsd = total / activeRate
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(strings.totalInUsdLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "$${String.format(Locale.US, "%.2f", totalUsd)} USD",
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily),
                        fontWeight = FontWeight.Bold,
                        color = SuccessTeal,
                        modifier = Modifier.testTag("pos_total_usd_text")
                    )
                }
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
private fun CartLineRow(
    item: CartItem,
    isRtl: Boolean,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onToggleWholesale: () -> Unit
) {
    val strings = LocalStoreStrings.current
    val name = if (isRtl) item.product.nameAr else item.product.nameEn

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
                Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${String.format(Locale.US, "%.2f", item.lineTotal)}",
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily),
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
                    Text("${item.quantity}", style = MaterialTheme.typography.titleMedium, fontFamily = MonoFontFamily)
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
