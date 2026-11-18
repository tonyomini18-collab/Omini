package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.PartyEntity
import com.example.data.local.ProductEntity
import com.example.i18n.AppLanguage
import com.example.ui.components.ProductThumbnail
import com.example.ui.components.StatusBadge
import com.example.ui.theme.DangerCrimson
import com.example.ui.theme.LocalStoreStrings
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.SuccessTeal
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.ProductWithStock
import com.example.ui.viewmodel.StoreUiState
import com.example.ui.viewmodel.StoreViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun InventoryScreen(
    state: StoreUiState,
    viewModel: StoreViewModel
) {
    val strings = LocalStoreStrings.current
    val isRtl = state.language == AppLanguage.AR
    val role = state.effectiveRole

    var selectedTab by remember { mutableIntStateOf(0) }
    var editingProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var showProductDialog by remember { mutableStateOf(false) }
    var deletingProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var adjustingProduct by remember { mutableStateOf<ProductWithStock?>(null) }

    Scaffold(
        floatingActionButton = {
            if (selectedTab == 0 && role.canManageCatalog()) {
                FloatingActionButton(
                    onClick = {
                        editingProduct = null
                        showProductDialog = true
                    },
                    modifier = Modifier.testTag("add_product_fab")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = strings.addProduct)
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text(strings.tabCatalog, maxLines = 1) })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text(strings.tabReceive, maxLines = 1) })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text(strings.tabMovements, maxLines = 1) })
                Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text(strings.tabParties, maxLines = 1) })
            }

            if (selectedTab == 0 && role.canManageCatalog() && !state.isOnline) {
                Surface(color = WarningAmber.copy(alpha = 0.16f), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        strings.onlineRequiredCatalog,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            when (selectedTab) {
                0 -> CatalogTab(
                    state = state,
                    isRtl = isRtl,
                    onEdit = { p ->
                        editingProduct = p
                        showProductDialog = true
                    },
                    onDelete = { p -> deletingProduct = p },
                    onAdjust = { pw -> adjustingProduct = pw }
                )
                1 -> ReceiveStockTab(state = state, viewModel = viewModel, isRtl = isRtl)
                2 -> MovementsTab(state = state, isRtl = isRtl)
                3 -> PartiesTab(state = state, viewModel = viewModel)
            }
        }
    }

    if (showProductDialog) {
        ProductEditorDialog(
            initial = editingProduct,
            isOnline = state.isOnline,
            onDismiss = { showProductDialog = false },
            onSave = { product, initialStock ->
                viewModel.saveProduct(product, initialStock)
                showProductDialog = false
            }
        )
    }

    deletingProduct?.let { prod ->
        AlertDialog(
            onDismissRequest = { deletingProduct = null },
            title = { Text(strings.confirmDeleteTitle) },
            text = { Text(strings.confirmDeleteMsg) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteProduct(prod)
                        deletingProduct = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerCrimson)
                ) { Text(strings.deleteProduct) }
            },
            dismissButton = {
                TextButton(onClick = { deletingProduct = null }) { Text(strings.cancelLabel) }
            }
        )
    }

    adjustingProduct?.let { pw ->
        var deltaText by remember { mutableStateOf("") }
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adjustingProduct = null },
            title = { Text("${strings.manualAdjustment}: ${if (isRtl) pw.product.nameAr else pw.product.nameEn}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${strings.stockLabel}: ${pw.currentStock}")
                    OutlinedTextField(
                        value = deltaText,
                        onValueChange = { deltaText = it },
                        label = { Text(strings.adjustmentDeltaHint) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("adjust_delta_input")
                    )
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text(strings.reasonRequired) },
                        modifier = Modifier.fillMaxWidth().testTag("adjust_reason_input")
                    )
                }
            },
            confirmButton = {
                val delta = deltaText.toIntOrNull() ?: 0
                Button(
                    onClick = {
                        viewModel.adjustStock(pw.product, delta, reason)
                        adjustingProduct = null
                    },
                    enabled = delta != 0 && reason.isNotBlank(),
                    modifier = Modifier.testTag("apply_adjustment_btn")
                ) { Text(strings.applyAdjustment) }
            },
            dismissButton = {
                TextButton(onClick = { adjustingProduct = null }) { Text(strings.cancelLabel) }
            }
        )
    }
}

@Composable
private fun CatalogTab(
    state: StoreUiState,
    isRtl: Boolean,
    onEdit: (ProductEntity) -> Unit,
    onDelete: (ProductEntity) -> Unit,
    onAdjust: (ProductWithStock) -> Unit
) {
    val strings = LocalStoreStrings.current
    val role = state.effectiveRole
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(state.products, key = { it.product.id }) { pw ->
            val p = pw.product
            val title = if (isRtl) p.nameAr else p.nameEn
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ProductThumbnail(imageUri = p.imageUri, category = p.category, contentDescription = title)
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${p.sku} • ${p.barcode.ifBlank { "-" }}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "${strings.retailPrice}: ${p.retailPrice}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "${strings.wholesalePrice}: ${p.wholesalePrice}",
                                style = MaterialTheme.typography.labelMedium
                            )
                            if (role.canViewProfitAndReports()) {
                                Text(
                                    "${strings.costPrice}: ${p.costPrice}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        val badgeColor = when {
                            pw.currentStock <= 0 -> DangerCrimson
                            pw.isLowStock -> WarningAmber
                            else -> SuccessTeal
                        }
                        StatusBadge(
                            label = "${strings.stockLabel}: ${pw.currentStock}",
                            containerColor = badgeColor.copy(alpha = 0.15f),
                            contentColor = badgeColor
                        )
                        if (role.canManageCatalog()) {
                            Row {
                                IconButton(onClick = { onAdjust(pw) }) {
                                    Icon(Icons.Filled.Tune, contentDescription = strings.manualAdjustment)
                                }
                                IconButton(onClick = { onEdit(p) }, enabled = state.isOnline) {
                                    Icon(Icons.Filled.Edit, contentDescription = strings.editProduct)
                                }
                                IconButton(onClick = { onDelete(p) }, enabled = state.isOnline) {
                                    Icon(Icons.Filled.Delete, contentDescription = strings.deleteProduct, tint = DangerCrimson)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReceiveStockTab(
    state: StoreUiState,
    viewModel: StoreViewModel,
    isRtl: Boolean
) {
    val strings = LocalStoreStrings.current
    var selectedProduct by remember { mutableStateOf(state.products.firstOrNull()?.product) }
    var selectedSupplier by remember { mutableStateOf(state.suppliers.firstOrNull()) }
    var qtyText by remember { mutableStateOf("10") }
    var costText by remember { mutableStateOf(selectedProduct?.costPrice?.toString() ?: "0") }
    var prodExpanded by remember { mutableStateOf(false) }
    var supExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(strings.receiveStockTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        ExposedDropdownMenuBox(expanded = prodExpanded, onExpandedChange = { prodExpanded = !prodExpanded }) {
            val prodLabel = selectedProduct?.let { if (isRtl) it.nameAr else it.nameEn } ?: "-"
            OutlinedTextField(
                value = prodLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text(strings.tabCatalog) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(prodExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = prodExpanded, onDismissRequest = { prodExpanded = false }) {
                state.products.forEach { pw ->
                    DropdownMenuItem(
                        text = { Text("${if (isRtl) pw.product.nameAr else pw.product.nameEn} (${pw.currentStock})") },
                        onClick = {
                            selectedProduct = pw.product
                            costText = pw.product.costPrice.toString()
                            prodExpanded = false
                        }
                    )
                }
            }
        }

        ExposedDropdownMenuBox(expanded = supExpanded, onExpandedChange = { supExpanded = !supExpanded }) {
            OutlinedTextField(
                value = selectedSupplier?.name ?: "-",
                onValueChange = {},
                readOnly = true,
                label = { Text(strings.supplierLabel) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(supExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = supExpanded, onDismissRequest = { supExpanded = false }) {
                state.suppliers.forEach { sup ->
                    DropdownMenuItem(
                        text = { Text(sup.name) },
                        onClick = {
                            selectedSupplier = sup
                            supExpanded = false
                        }
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = qtyText,
                onValueChange = { qtyText = it },
                label = { Text(strings.quantityLabel) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("receive_qty_input")
            )
            OutlinedTextField(
                value = costText,
                onValueChange = { costText = it },
                label = { Text(strings.unitCostLabel) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("receive_cost_input")
            )
        }

        Button(
            onClick = {
                val p = selectedProduct ?: return@Button
                val qty = qtyText.toIntOrNull() ?: 0
                val cost = costText.toDoubleOrNull() ?: p.costPrice
                viewModel.receiveStock(p, selectedSupplier?.id, qty, cost)
            },
            enabled = selectedProduct != null && (qtyText.toIntOrNull() ?: 0) > 0,
            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("record_receipt_btn")
        ) {
            Icon(Icons.Filled.Inventory2, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(strings.recordReceipt)
        }
    }
}

@Composable
private fun MovementsTab(state: StoreUiState, isRtl: Boolean) {
    val fmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }
    val productMap = remember(state.products) { state.products.associate { it.product.id to it.product } }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(state.movements.take(100), key = { it.id }) { m ->
            val p = productMap[m.productId]
            val pName = p?.let { if (isRtl) it.nameAr else it.nameEn } ?: m.productId
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(pName, style = MaterialTheme.typography.titleMedium)
                        Text("${m.type.uppercase()} • ${m.reason}", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${m.actorName} • ${fmt.format(Date(m.createdAtMillis))}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val color = if (m.qtyDelta >= 0) SuccessTeal else DangerCrimson
                    Text(
                        text = if (m.qtyDelta >= 0) "+${m.qtyDelta}" else "${m.qtyDelta}",
                        style = MaterialTheme.typography.titleLarge.copy(fontFamily = MonoFontFamily),
                        color = color,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun PartiesTab(state: StoreUiState, viewModel: StoreViewModel) {
    val strings = LocalStoreStrings.current
    var showAddType by remember { mutableStateOf<String?>(null) }
    var paymentTarget by remember { mutableStateOf<PartyEntity?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(strings.customersTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = { showAddType = "customer" }) { Text(strings.addCustomer) }
            }
        }
        items(state.customers, key = { it.id }) { c ->
            PartyRowCard(c, strings.currencyCode, strings.recordPayment) { paymentTarget = c }
        }
        item {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(strings.suppliersTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = { showAddType = "supplier" }) { Text(strings.addSupplier) }
            }
        }
        items(state.suppliers, key = { it.id }) { s ->
            PartyRowCard(s, strings.currencyCode, strings.recordPayment) { paymentTarget = s }
        }
    }

    showAddType?.let { partyType ->
        var name by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddType = null },
            title = { Text(if (partyType == "customer") strings.addCustomer else strings.addSupplier) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(strings.memberNameLabel) }, singleLine = true)
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text(strings.phoneLabel) }, singleLine = true)
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.saveParty(partyType, name, phone); showAddType = null }, enabled = name.isNotBlank()) {
                    Text(strings.saveLabel)
                }
            },
            dismissButton = { TextButton(onClick = { showAddType = null }) { Text(strings.cancelLabel) } }
        )
    }

    paymentTarget?.let { party ->
        var amountStr by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { paymentTarget = null },
            title = { Text("${strings.recordPayment}: ${party.name}") },
            text = {
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text(strings.paymentAmount) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
            },
            confirmButton = {
                val amt = amountStr.toDoubleOrNull() ?: 0.0
                Button(onClick = { viewModel.recordPartyPayment(party, amt); paymentTarget = null }, enabled = amt > 0.0) {
                    Text(strings.saveLabel)
                }
            },
            dismissButton = { TextButton(onClick = { paymentTarget = null }) { Text(strings.cancelLabel) } }
        )
    }
}

@Composable
private fun PartyRowCard(
    party: PartyEntity,
    currency: String,
    actionLabel: String,
    onPayClick: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(party.name, style = MaterialTheme.typography.titleMedium)
                if (party.phone.isNotBlank()) {
                    Text(party.phone, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${String.format(Locale.US, "%.2f", party.balance)} $currency",
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily),
                    fontWeight = FontWeight.Bold
                )
                if (party.balance > 0.0) {
                    TextButton(onClick = onPayClick) {
                        Icon(Icons.Filled.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(actionLabel)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductEditorDialog(
    initial: ProductEntity?,
    isOnline: Boolean,
    onDismiss: () -> Unit,
    onSave: (ProductEntity, Int) -> Unit
) {
    val strings = LocalStoreStrings.current
    var nameAr by remember { mutableStateOf(initial?.nameAr ?: "") }
    var nameEn by remember { mutableStateOf(initial?.nameEn ?: "") }
    var category by remember { mutableStateOf(initial?.category ?: "perfume") }
    var sku by remember { mutableStateOf(initial?.sku ?: "") }
    var barcode by remember { mutableStateOf(initial?.barcode ?: "") }
    var imageUri by remember { mutableStateOf(initial?.imageUri ?: "") }
    var costStr by remember { mutableStateOf(initial?.costPrice?.toString() ?: "") }
    var retailStr by remember { mutableStateOf(initial?.retailPrice?.toString() ?: "") }
    var wholesaleStr by remember { mutableStateOf(initial?.wholesalePrice?.toString() ?: "") }
    var minQtyStr by remember { mutableStateOf((initial?.wholesaleMinQty ?: 5).toString()) }
    var unitAr by remember { mutableStateOf(initial?.unitAr ?: "زجاجة") }
    var unitEn by remember { mutableStateOf(initial?.unitEn ?: "Bottle") }
    var thresholdStr by remember { mutableStateOf((initial?.lowStockThreshold ?: 5).toString()) }
    var initialStockStr by remember { mutableStateOf("0") }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) imageUri = uri.toString()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) strings.addProduct else strings.editProduct) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(strings.pickImage)
                }
                OutlinedTextField(value = nameAr, onValueChange = { nameAr = it }, label = { Text(strings.productNameAr) }, singleLine = true)
                OutlinedTextField(value = nameEn, onValueChange = { nameEn = it }, label = { Text(strings.productNameEn) }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("perfume" to strings.catPerfume, "cream" to strings.catCream, "other" to strings.catOther).forEach { (k, lbl) ->
                        FilterChip(selected = category == k, onClick = { category = k }, label = { Text(lbl) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = sku, onValueChange = { sku = it }, label = { Text(strings.skuLabel) }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = barcode, onValueChange = { barcode = it }, label = { Text(strings.barcodeLabel) }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = costStr, onValueChange = { costStr = it }, label = { Text(strings.costPrice) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = retailStr, onValueChange = { retailStr = it }, label = { Text(strings.retailPrice) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = wholesaleStr, onValueChange = { wholesaleStr = it }, label = { Text(strings.wholesalePrice) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = minQtyStr, onValueChange = { minQtyStr = it }, label = { Text(strings.minWholesaleQty) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = unitAr, onValueChange = { unitAr = it }, label = { Text(strings.unitArLabel) }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = unitEn, onValueChange = { unitEn = it }, label = { Text(strings.unitEnLabel) }, singleLine = true, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(value = thresholdStr, onValueChange = { thresholdStr = it }, label = { Text(strings.lowThresholdLabel) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                if (initial == null) {
                    OutlinedTextField(value = initialStockStr, onValueChange = { initialStockStr = it }, label = { Text(strings.quantityLabel) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val entity = ProductEntity(
                        id = initial?.id ?: "",
                        nameAr = nameAr.ifBlank { nameEn },
                        nameEn = nameEn.ifBlank { nameAr },
                        category = category,
                        sku = sku.ifBlank { "SKU-${System.currentTimeMillis() % 10000}" },
                        barcode = barcode,
                        imageUri = imageUri,
                        costPrice = costStr.toDoubleOrNull() ?: 0.0,
                        retailPrice = retailStr.toDoubleOrNull() ?: 0.0,
                        wholesalePrice = wholesaleStr.toDoubleOrNull() ?: 0.0,
                        wholesaleMinQty = (minQtyStr.toIntOrNull() ?: 5).coerceAtLeast(1),
                        unitAr = unitAr.ifBlank { "قطعة" },
                        unitEn = unitEn.ifBlank { "Unit" },
                        lowStockThreshold = (thresholdStr.toIntOrNull() ?: 5).coerceAtLeast(0),
                        createdAtMillis = initial?.createdAtMillis ?: System.currentTimeMillis()
                    )
                    onSave(entity, initialStockStr.toIntOrNull() ?: 0)
                },
                enabled = isOnline && (nameAr.isNotBlank() || nameEn.isNotBlank())
            ) {
                Text(strings.saveLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancelLabel) } }
    )
}
