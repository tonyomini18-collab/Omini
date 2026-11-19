package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.local.AppReleaseEntity
import com.example.data.local.AuditLogEntity
import com.example.data.local.BusinessDayEntity
import com.example.data.local.HolidayEntity
import com.example.data.local.InvoiceEntity
import com.example.data.local.PartyEntity
import com.example.data.local.ProductEntity
import com.example.data.local.StockMovementEntity
import com.example.data.local.StoreDao
import com.example.data.local.StoreMemberEntity
import com.example.data.local.StoreSettingsEntity
import com.example.data.remote.FirestoreRepository
import com.example.data.remote.FsAppRelease
import com.example.data.remote.FsHoliday
import com.example.data.remote.FsProduct
import com.example.data.remote.FsStockMovement
import com.example.data.remote.FsStoreMember
import com.example.data.remote.FsStoreSettings
import com.example.data.sync.ConnectivityObserver
import com.example.data.sync.LanSyncClient
import com.example.data.sync.PreferencesManager
import com.example.data.sync.SyncEngine
import com.example.i18n.AppLanguage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

class StoreViewModel(
    private val dao: StoreDao,
    private val remote: FirestoreRepository,
    private val syncEngine: SyncEngine,
    private val connectivity: ConnectivityObserver,
    private val prefs: PreferencesManager,
    private val currentUserId: String,
    private val currentUserDisplayName: String,
    private val currentUserEmail: String?,
    private val initialRoleKey: String = "admin"
) : ViewModel() {

    private val initialRole = UserRole.fromKey(initialRoleKey)

    private val _uiState = MutableStateFlow(
        StoreUiState(
            userId = currentUserId,
            userName = currentUserDisplayName.ifBlank { currentUserEmail ?: "Staff" },
            actualRole = initialRole,
            activeTab = if (initialRole.canSell()) MainTab.POS else MainTab.INVENTORY
        )
    )
    val uiState: StateFlow<StoreUiState> = _uiState.asStateFlow()

    private var lastActivityMillis: Long = System.currentTimeMillis()

    init {
        observeLocalAndConnectivity()
        bootstrapStoreSession()
        startSessionTimeoutWatcher()
    }

    private fun bootstrapStoreSession() {
        viewModelScope.launch {
            val savedStoreId = prefs.activeStoreIdFlow.first()
            val langCode = prefs.languageFlow.first()
            val canCloud = connectivity.isOnline.value && remote.isCloudAuthenticated()
            val storeId = if (canCloud) {
                remote.ensureStoreAndProfileInitialized(
                    displayName = _uiState.value.userName,
                    email = currentUserEmail,
                    preferredLanguage = langCode
                ).getOrElse {
                    Log.w("StoreVM", "Offline/fallback storeId initialization", it)
                    savedStoreId.ifBlank { PreferencesManager.DEFAULT_ADMIN_UID }
                }
            } else {
                savedStoreId.ifBlank { PreferencesManager.DEFAULT_ADMIN_UID }
            }

            prefs.setActiveStoreId(storeId)
            _uiState.update { it.copy(storeId = storeId, isLoading = false) }

            // Ensure default local admin record exists in store_members for password editing & sync
            val currentMembers = dao.getMembersSnapshot()
            if (currentMembers.none { it.uid == PreferencesManager.DEFAULT_ADMIN_UID }) {
                dao.upsertMember(
                    StoreMemberEntity(
                        uid = PreferencesManager.DEFAULT_ADMIN_UID,
                        username = PreferencesManager.DEFAULT_ADMIN_USERNAME,
                        passwordHash = PreferencesManager.DEFAULT_ADMIN_PASSWORD_HASH,
                        displayName = PreferencesManager.DEFAULT_ADMIN_USERNAME,
                        role = UserRole.ADMIN.key,
                        active = true
                    )
                )
            }

            if (canCloud) {
                syncEngine.startListeners(viewModelScope, storeId)
                syncEngine.flushPendingOps(storeId)
            }

            syncEngine.seedSampleDataIfEmpty(
                storeId = storeId,
                actorUid = currentUserId,
                actorName = _uiState.value.userName
            )
        }
    }

    private fun observeLocalAndConnectivity() {
        viewModelScope.launch {
            prefs.languageFlow.collect { code ->
                _uiState.update { it.copy(language = AppLanguage.fromCode(code)) }
            }
        }
        viewModelScope.launch {
            prefs.darkModeFlow.collect { dark ->
                _uiState.update { it.copy(darkMode = dark) }
            }
        }
        viewModelScope.launch {
            prefs.pcServerUrlFlow.collect { url ->
                _uiState.update { it.copy(pcServerUrl = url) }
            }
        }
        viewModelScope.launch {
            prefs.lastPcSyncMillisFlow.collect { ts ->
                _uiState.update { it.copy(lastPcSyncMillis = ts) }
            }
        }
        viewModelScope.launch {
            prefs.usdRateSspFlow.collect { rate ->
                if (rate > 0.0) {
                    _uiState.update { it.copy(usdRateSsp = rate) }
                }
            }
        }
        viewModelScope.launch {
            connectivity.isOnline.collect { online ->
                _uiState.update { it.copy(isOnline = online) }
                val storeId = _uiState.value.storeId
                if (online && storeId.isNotBlank() && remote.isCloudAuthenticated()) {
                    syncEngine.startListeners(viewModelScope, storeId)
                    syncEngine.flushPendingOps(storeId)
                }
            }
        }
        viewModelScope.launch {
            combine(
                syncEngine.isSyncing,
                dao.observePendingSyncCount(),
                syncEngine.lastSyncError
            ) { syncing, count, err -> Triple(syncing, count, err) }
                .collect { (syncing, count, err) ->
                    _uiState.update {
                        it.copy(isSyncing = syncing, pendingSyncCount = count, lastSyncError = err)
                    }
                }
        }
        viewModelScope.launch {
            combine(
                dao.observeProducts(),
                dao.observeProductStocks(),
                dao.observeMovements(),
                dao.observeInvoices(),
                dao.observeAllParties()
            ) { products, stockRows, movements, invoices, parties ->
                val stockMap = stockRows.associate { it.productId to it.currentStock }
                val productsWithStock = products.map { p ->
                    ProductWithStock(p, stockMap[p.id] ?: 0)
                }
                val customers = parties.filter { it.partyType == "customer" }
                val suppliers = parties.filter { it.partyType == "supplier" }
                DataBundle1(productsWithStock, movements, invoices, customers, suppliers)
            }.collect { b ->
                _uiState.update { state ->
                    val updatedCart = state.cartItems.mapNotNull { cart ->
                        val match = b.products.find { it.product.id == cart.product.id } ?: return@mapNotNull null
                        cart.copy(product = match.product, currentStock = match.currentStock)
                    }
                    val analytics = computeAnalytics(b.products, b.movements, b.invoices, state.periodFilter)
                    val nextState = state.copy(
                        products = b.products,
                        movements = b.movements,
                        invoices = b.invoices,
                        customers = b.customers,
                        suppliers = b.suppliers,
                        cartItems = updatedCart,
                        analytics = analytics
                    )
                    nextState.copy(notifications = buildNotifications(nextState))
                }
            }
        }
        viewModelScope.launch {
            combine(
                dao.observeBusinessDays(),
                dao.observeOpenBusinessDay(),
                dao.observeHolidays(),
                dao.observeAuditLogs(),
                dao.observeMembers()
            ) { days, openDay, holidays, logs, members ->
                DataBundle2(days, openDay, holidays, logs, members)
            }.collect { b ->
                _uiState.update { state ->
                    val myMember = b.members.find { it.uid == currentUserId }
                    val actualRole = when {
                        currentUserId == PreferencesManager.DEFAULT_ADMIN_UID -> UserRole.ADMIN
                        remote.isCloudAuthenticated() && state.storeId == currentUserId -> UserRole.ADMIN
                        myMember != null -> UserRole.fromKey(myMember.role)
                        else -> state.actualRole
                    }
                    val effective = state.previewRole ?: actualRole
                    val safeTab = when {
                        state.activeTab == MainTab.POS && !effective.canSell() -> MainTab.INVENTORY
                        state.activeTab == MainTab.REPORTS && !effective.canViewProfitAndReports() -> if (effective.canSell()) MainTab.POS else MainTab.INVENTORY
                        state.activeTab == MainTab.ADMIN && !effective.canManageUsers() && !effective.canAccessSystemAdmin() -> if (effective.canSell()) MainTab.POS else MainTab.INVENTORY
                        else -> state.activeTab
                    }
                    val lastClosed = b.days.firstOrNull { it.status == "closed" }
                    val next = state.copy(
                        businessDays = b.days,
                        openBusinessDay = b.openDay,
                        lastClosedDay = lastClosed,
                        holidays = b.holidays,
                        auditLogs = b.logs,
                        members = b.members,
                        actualRole = actualRole,
                        activeTab = safeTab
                    )
                    next.copy(notifications = buildNotifications(next))
                }
            }
        }
        viewModelScope.launch {
            combine(
                dao.observeSettings(),
                dao.observeReleases()
            ) { settings, releases -> settings to releases }
                .collect { (settings, releases) ->
                    _uiState.update { state ->
                        val effectiveSettings = settings ?: StoreSettingsEntity()
                        val latestAndroid = releases
                            .filter { it.platform == "android" }
                            .maxByOrNull { it.versionCode }
                        val updateCandidate = if (
                            latestAndroid != null &&
                            latestAndroid.versionCode > BuildConfig.VERSION_CODE &&
                            (latestAndroid.isMandatory || state.updateDismissedForVersion != latestAndroid.versionCode)
                        ) latestAndroid else null

                        val next = state.copy(
                            settings = effectiveSettings,
                            releases = releases,
                            availableUpdate = updateCandidate
                        )
                        next.copy(notifications = buildNotifications(next))
                    }
                }
        }
    }

    private fun startSessionTimeoutWatcher() {
        viewModelScope.launch {
            while (true) {
                delay(30_000L)
                val timeoutMinutes = _uiState.value.settings.sessionTimeoutMinutes.coerceAtLeast(5)
                val elapsed = System.currentTimeMillis() - lastActivityMillis
                if (elapsed > timeoutMinutes * 60_000L && !_uiState.value.sessionLocked) {
                    _uiState.update { it.copy(sessionLocked = true) }
                }
            }
        }
    }

    fun touchActivity() {
        lastActivityMillis = System.currentTimeMillis()
    }

    fun unlockSession() {
        lastActivityMillis = System.currentTimeMillis()
        _uiState.update { it.copy(sessionLocked = false) }
    }

    fun selectTab(tab: MainTab) {
        touchActivity()
        val role = _uiState.value.effectiveRole
        val allowed = when (tab) {
            MainTab.POS -> role.canSell()
            MainTab.REPORTS -> role.canViewProfitAndReports()
            MainTab.ADMIN -> role.canManageUsers() || role.canAccessSystemAdmin()
            else -> true
        }
        if (allowed) {
            _uiState.update { it.copy(activeTab = tab) }
        }
    }

    fun setPeriodFilter(period: PeriodFilter) {
        touchActivity()
        _uiState.update { state ->
            state.copy(
                periodFilter = period,
                analytics = computeAnalytics(state.products, state.movements, state.invoices, period)
            )
        }
    }

    fun toggleLanguage() {
        touchActivity()
        viewModelScope.launch {
            val next = if (_uiState.value.language == AppLanguage.AR) "en" else "ar"
            prefs.setLanguage(next)
        }
    }

    fun toggleDarkMode() {
        touchActivity()
        viewModelScope.launch {
            prefs.setDarkMode(!_uiState.value.darkMode)
        }
    }

    fun setPreviewRole(role: UserRole?) {
        if (_uiState.value.actualRole != UserRole.ADMIN) return
        _uiState.update { state ->
            val next = state.copy(previewRole = role)
            val effective = next.effectiveRole
            val defaultTab = if (effective.canSell()) MainTab.POS else MainTab.INVENTORY
            val safeTab = when {
                next.activeTab == MainTab.POS && !effective.canSell() -> MainTab.INVENTORY
                next.activeTab == MainTab.REPORTS && !effective.canViewProfitAndReports() -> defaultTab
                next.activeTab == MainTab.ADMIN && !effective.canManageUsers() && !effective.canAccessSystemAdmin() -> defaultTab
                else -> next.activeTab
            }
            next.copy(activeTab = safeTab, notifications = buildNotifications(next))
        }
    }

    fun triggerManualSync() {
        touchActivity()
        val state = _uiState.value
        val storeId = state.storeId
        viewModelScope.launch {
            if (storeId.isNotBlank() && state.isOnline && remote.isCloudAuthenticated()) {
                syncEngine.flushPendingOps(storeId)
            }
            if (state.pcServerUrl.isNotBlank()) {
                syncWithPcInternal(state.pcServerUrl)
            }
        }
    }

    fun savePcServerUrl(url: String) {
        touchActivity()
        viewModelScope.launch {
            val normalized = LanSyncClient.normalizeServerUrl(url)
            prefs.setPcServerUrl(normalized)
        }
    }

    fun testPcConnection(url: String) {
        touchActivity()
        viewModelScope.launch {
            val normalized = LanSyncClient.normalizeServerUrl(url)
            prefs.setPcServerUrl(normalized)
            _uiState.update { it.copy(isPcSyncing = true, pcConnectionStatus = null) }
            val res = LanSyncClient.pingPc(normalized)
            res.onSuccess { info ->
                val msg = if (_uiState.value.language == AppLanguage.AR) {
                    "متصل: ${info.storeNameAr} (v${info.pcVersion}) • منتجات: ${info.productsCount}"
                } else {
                    "Connected: ${info.storeNameEn} (v${info.pcVersion}) • Products: ${info.productsCount}"
                }
                _uiState.update { it.copy(isPcSyncing = false, pcConnectionStatus = msg, statusMessage = msg) }
            }.onFailure { err ->
                val msg = err.localizedMessage ?: "Connection failed"
                _uiState.update { it.copy(isPcSyncing = false, pcConnectionStatus = msg, statusMessage = msg) }
            }
        }
    }

    fun syncWithPc(urlOverride: String? = null) {
        touchActivity()
        val raw = urlOverride ?: _uiState.value.pcServerUrl
        viewModelScope.launch {
            syncWithPcInternal(raw)
        }
    }

    private suspend fun syncWithPcInternal(rawUrl: String) {
        val normalized = LanSyncClient.normalizeServerUrl(rawUrl)
        if (normalized.isBlank()) return
        prefs.setPcServerUrl(normalized)
        _uiState.update { it.copy(isPcSyncing = true) }
        val res = LanSyncClient.syncWithPc(
            rawUrl = normalized,
            dao = dao,
            deviceName = "Android (${_uiState.value.userName})"
        )
        res.onSuccess { summary ->
            val now = System.currentTimeMillis()
            prefs.setLastPcSyncMillis(now)
            val msg = if (_uiState.value.language == AppLanguage.AR) {
                "تمت المزامنة مع الكمبيوتر • منتجات: ${summary.productsSynced}، فواتير: ${summary.invoicesSynced}"
            } else {
                "Synced with PC • Products: ${summary.productsSynced}, Invoices: ${summary.invoicesSynced}"
            }
            recordAuditLog("PC_LAN_SYNC", "pc_server", normalized, msg)
            _uiState.update {
                it.copy(
                    isPcSyncing = false,
                    pcConnectionStatus = msg,
                    statusMessage = msg
                )
            }
        }.onFailure { err ->
            val msg = err.localizedMessage ?: "PC sync failed"
            _uiState.update {
                it.copy(
                    isPcSyncing = false,
                    pcConnectionStatus = msg,
                    statusMessage = msg
                )
            }
        }
    }

    fun dismissUpdateDialog(versionCode: Int) {
        _uiState.update { it.copy(updateDismissedForVersion = versionCode, availableUpdate = null) }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    // --- POS & CART ---
    fun addToCartByBarcode(code: String): Boolean {
        touchActivity()
        if (!_uiState.value.effectiveRole.canSell()) {
            _uiState.update {
                it.copy(statusMessage = if (it.language == AppLanguage.AR) "صلاحية الكاشير لتحديث المنتجات ولا تشمل البيع" else "Cashier role cannot perform sales")
            }
            return false
        }
        val trimmed = code.trim()
        val match = _uiState.value.products.firstOrNull {
            it.product.barcode.equals(trimmed, ignoreCase = true) ||
                it.product.sku.equals(trimmed, ignoreCase = true)
        }
        return if (match != null) {
            addToCart(match)
            true
        } else {
            _uiState.update {
                it.copy(statusMessage = if (it.language == AppLanguage.AR) "لم يتم العثور على باركود: $trimmed" else "Barcode not found: $trimmed")
            }
            false
        }
    }

    fun addToCart(item: ProductWithStock) {
        touchActivity()
        if (!_uiState.value.effectiveRole.canSell()) {
            _uiState.update {
                it.copy(statusMessage = if (it.language == AppLanguage.AR) "صلاحية الكاشير لتحديث المنتجات ولا تشمل البيع" else "Cashier role cannot perform sales")
            }
            return
        }
        if (item.currentStock <= 0) {
            _uiState.update {
                it.copy(statusMessage = if (it.language == AppLanguage.AR) "المنتج غير متوفر في المخزون" else "Product out of stock")
            }
            return
        }
        _uiState.update { state ->
            val current = state.cartItems.toMutableList()
            val idx = current.indexOfFirst { it.product.id == item.product.id }
            if (idx >= 0) {
                val existing = current[idx]
                val nextQty = (existing.quantity + 1).coerceAtMost(item.currentStock)
                val autoWholesale = existing.useWholesalePrice || nextQty >= item.product.wholesaleMinQty
                current[idx] = existing.copy(quantity = nextQty, useWholesalePrice = autoWholesale)
            } else {
                val autoWholesale = 1 >= item.product.wholesaleMinQty
                current.add(CartItem(item.product, item.currentStock, 1, autoWholesale))
            }
            state.copy(cartItems = current)
        }
    }

    fun updateCartItemQuantity(productId: String, newQty: Int) {
        touchActivity()
        _uiState.update { state ->
            val updated = state.cartItems.mapNotNull { item ->
                if (item.product.id != productId) return@mapNotNull item
                if (newQty <= 0) return@mapNotNull null
                val clamped = newQty.coerceAtMost(item.currentStock.coerceAtLeast(1))
                val autoWholesale = if (clamped >= item.product.wholesaleMinQty) true else item.useWholesalePrice
                item.copy(quantity = clamped, useWholesalePrice = autoWholesale)
            }
            state.copy(cartItems = updated)
        }
    }

    fun toggleCartItemWholesale(productId: String) {
        touchActivity()
        _uiState.update { state ->
            val updated = state.cartItems.map { item ->
                if (item.product.id == productId) item.copy(useWholesalePrice = !item.useWholesalePrice)
                else item
            }
            state.copy(cartItems = updated)
        }
    }

    fun setDiscount(amount: Double) {
        touchActivity()
        _uiState.update { it.copy(discountAmount = amount.coerceAtLeast(0.0)) }
    }

    fun setPaymentMethod(method: String) {
        touchActivity()
        val normalized = method.lowercase().trim()
        if (normalized in setOf("cash", "transfer", "credit", "usd")) {
            _uiState.update { it.copy(selectedPaymentMethod = normalized) }
        }
    }

    fun setUsdRateSsp(rate: Double) {
        touchActivity()
        if (rate <= 0.0) return
        _uiState.update { it.copy(usdRateSsp = rate) }
        viewModelScope.launch {
            prefs.setUsdRateSsp(rate)
        }
    }

    fun setSelectedCustomer(customerId: String?) {
        touchActivity()
        _uiState.update { it.copy(selectedCustomerId = customerId) }
    }

    fun clearLastReceipt() {
        _uiState.update { it.copy(lastCompletedInvoice = null) }
    }

    fun checkoutCart() {
        touchActivity()
        val state = _uiState.value
        if (!state.effectiveRole.canSell()) {
            _uiState.update {
                it.copy(statusMessage = if (it.language == AppLanguage.AR) "صلاحية الكاشير لتحديث المنتجات ولا تشمل البيع" else "Cashier role cannot perform sales")
            }
            return
        }
        val openDay = state.openBusinessDay ?: run {
            _uiState.update {
                it.copy(statusMessage = if (it.language == AppLanguage.AR) "يجب فتح اليوم التجاري أولاً" else "Open business day first")
            }
            return
        }
        if (state.cartItems.isEmpty()) return

        viewModelScope.launch {
            val subtotal = state.cartItems.sumOf { it.lineTotal }
            val discount = state.discountAmount.coerceIn(0.0, subtotal)
            val total = (subtotal - discount).coerceAtLeast(0.0)
            val costTotal = state.cartItems.sumOf { it.lineCost }
            val hasWholesale = state.cartItems.any { it.useWholesalePrice }
            val hasRetail = state.cartItems.any { !it.useWholesalePrice }
            val saleType = when {
                hasWholesale && hasRetail -> "mixed"
                hasWholesale -> "wholesale"
                else -> "retail"
            }

            val itemsJsonArr = JSONArray()
            val summaryLines = state.cartItems.map { item ->
                itemsJsonArr.put(
                    JSONObject().apply {
                        put("productId", item.product.id)
                        put("nameAr", item.product.nameAr)
                        put("nameEn", item.product.nameEn)
                        put("qty", item.quantity)
                        put("unitPrice", item.unitPrice)
                        put("unitCost", item.product.costPrice)
                        put("wholesale", item.useWholesalePrice)
                    }
                )
                val label = if (state.language == AppLanguage.AR) item.product.nameAr else item.product.nameEn
                val tier = if (item.useWholesalePrice) "W" else "R"
                "$label x${item.quantity} ($tier) = ${String.format(Locale.US, "%.2f", item.lineTotal)}"
            }.toMutableList()
            if (state.selectedPaymentMethod == "usd") {
                val rate = state.usdRateSsp.takeIf { it > 0.0 } ?: 1300.0
                val usdAmount = total / rate
                val usdLine = if (state.language == AppLanguage.AR) {
                    "الدفع بالدولار: $${String.format(Locale.US, "%.2f", usdAmount)} USD (1 USD = ${String.format(Locale.US, "%.2f", rate)} SSP)"
                } else {
                    "Paid in USD: $${String.format(Locale.US, "%.2f", usdAmount)} USD (1 USD = ${String.format(Locale.US, "%.2f", rate)} SSP)"
                }
                summaryLines.add(usdLine)
            }

            val customer = state.customers.find { it.id == state.selectedCustomerId }
            val invoiceId = "inv_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            val invoiceNo = "INV-${SimpleDateFormat("yyMMdd-HHmmss", Locale.US).format(Date())}"
            val now = System.currentTimeMillis()
            val invoice = InvoiceEntity(
                id = invoiceId,
                invoiceNumber = invoiceNo,
                saleType = saleType,
                customerId = customer?.id ?: "",
                customerName = customer?.name ?: "",
                itemsJson = itemsJsonArr.toString(),
                itemsSummary = summaryLines.joinToString("\n"),
                subtotal = subtotal,
                discount = discount,
                total = total,
                costTotal = costTotal,
                paymentMethod = state.selectedPaymentMethod,
                status = "completed",
                businessDayId = openDay.id,
                cashierUid = currentUserId,
                cashierName = state.userName,
                createdAtMillis = now,
                updatedAtMillis = now,
                syncStatus = "PENDING"
            )
            dao.upsertInvoice(invoice)
            syncEngine.enqueueOperation(
                "invoices",
                invoiceId,
                "UPSERT",
                JSONObject().apply {
                    put("invoiceNumber", invoice.invoiceNumber)
                    put("saleType", invoice.saleType)
                    put("customerId", invoice.customerId)
                    put("customerName", invoice.customerName)
                    put("itemsSummary", invoice.itemsSummary)
                    put("subtotal", invoice.subtotal)
                    put("discount", invoice.discount)
                    put("total", invoice.total)
                    put("costTotal", invoice.costTotal)
                    put("paymentMethod", invoice.paymentMethod)
                    put("status", invoice.status)
                    put("businessDayId", invoice.businessDayId)
                    put("cashierUid", invoice.cashierUid)
                    put("cashierName", invoice.cashierName)
                }
            )

            // Record immutable stock movements per item
            state.cartItems.forEach { cartItem ->
                val movId = "mov_${UUID.randomUUID().toString().replace("-", "").take(16)}"
                val movement = StockMovementEntity(
                    id = movId,
                    productId = cartItem.product.id,
                    type = "sale",
                    qtyDelta = -cartItem.quantity,
                    unitCost = cartItem.product.costPrice,
                    reason = "Sale $invoiceNo",
                    refId = invoiceId,
                    actorUid = currentUserId,
                    actorName = state.userName,
                    createdAtMillis = now,
                    syncStatus = "PENDING"
                )
                dao.insertMovement(movement)
                syncEngine.enqueueOperation(
                    "movements",
                    movId,
                    "UPSERT",
                    JSONObject().apply {
                        put("productId", movement.productId)
                        put("type", movement.type)
                        put("qtyDelta", movement.qtyDelta)
                        put("unitCost", movement.unitCost)
                        put("reason", movement.reason)
                        put("refId", movement.refId)
                        put("actorUid", movement.actorUid)
                        put("actorName", movement.actorName)
                    }
                )
            }

            // Update customer credit balance if Credit payment
            if (state.selectedPaymentMethod == "credit" && customer != null) {
                val updatedCust = customer.copy(balance = customer.balance + total, updatedAtMillis = now, syncStatus = "PENDING")
                dao.upsertParty(updatedCust)
                enqueuePartyOp("customers", updatedCust)
            }

            // Update open business day totals
            val itemsCount = state.cartItems.sumOf { it.quantity }
            val updatedDay = openDay.copy(
                totalSales = openDay.totalSales + total,
                cashSales = openDay.cashSales + if (state.selectedPaymentMethod == "cash") total else 0.0,
                transferSales = openDay.transferSales + if (state.selectedPaymentMethod == "transfer") total else 0.0,
                creditSales = openDay.creditSales + if (state.selectedPaymentMethod == "credit") total else 0.0,
                itemsSoldCount = openDay.itemsSoldCount + itemsCount,
                updatedAtMillis = now,
                syncStatus = "PENDING"
            )
            dao.upsertBusinessDay(updatedDay)
            enqueueDayOp(updatedDay)

            _uiState.update {
                it.copy(
                    cartItems = emptyList(),
                    discountAmount = 0.0,
                    selectedCustomerId = null,
                    lastCompletedInvoice = invoice
                )
            }
            if (state.isOnline) syncEngine.flushPendingOps(state.storeId)
        }
    }

    fun returnInvoice(invoice: InvoiceEntity) {
        touchActivity()
        val state = _uiState.value
        if (!state.effectiveRole.canSell()) return
        if (invoice.status == "returned") return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val updated = invoice.copy(status = "returned", updatedAtMillis = now, syncStatus = "PENDING")
            dao.upsertInvoice(updated)
            syncEngine.enqueueOperation(
                "invoices",
                updated.id,
                "UPSERT",
                JSONObject().apply {
                    put("invoiceNumber", updated.invoiceNumber)
                    put("saleType", updated.saleType)
                    put("customerId", updated.customerId)
                    put("customerName", updated.customerName)
                    put("itemsSummary", updated.itemsSummary)
                    put("subtotal", updated.subtotal)
                    put("discount", updated.discount)
                    put("total", updated.total)
                    put("costTotal", updated.costTotal)
                    put("paymentMethod", updated.paymentMethod)
                    put("status", "returned")
                    put("businessDayId", updated.businessDayId)
                    put("cashierUid", updated.cashierUid)
                    put("cashierName", updated.cashierName)
                }
            )

            // Restore stock via RETURN movements from original SALE movements
            val saleMovements = state.movements.filter { it.refId == invoice.id && it.type == "sale" }
            saleMovements.forEach { sm ->
                val movId = "mov_${UUID.randomUUID().toString().replace("-", "").take(16)}"
                val retMov = StockMovementEntity(
                    id = movId,
                    productId = sm.productId,
                    type = "return",
                    qtyDelta = kotlin.math.abs(sm.qtyDelta),
                    unitCost = sm.unitCost,
                    reason = "Return ${invoice.invoiceNumber}",
                    refId = invoice.id,
                    actorUid = currentUserId,
                    actorName = state.userName,
                    createdAtMillis = now,
                    syncStatus = "PENDING"
                )
                dao.insertMovement(retMov)
                syncEngine.enqueueOperation(
                    "movements",
                    movId,
                    "UPSERT",
                    JSONObject().apply {
                        put("productId", retMov.productId)
                        put("type", retMov.type)
                        put("qtyDelta", retMov.qtyDelta)
                        put("unitCost", retMov.unitCost)
                        put("reason", retMov.reason)
                        put("refId", retMov.refId)
                        put("actorUid", retMov.actorUid)
                        put("actorName", retMov.actorName)
                    }
                )
            }

            if (invoice.paymentMethod == "credit" && invoice.customerId.isNotBlank()) {
                val cust = dao.getPartyById(invoice.customerId)
                if (cust != null) {
                    val adj = cust.copy(balance = (cust.balance - invoice.total).coerceAtLeast(0.0), updatedAtMillis = now)
                    dao.upsertParty(adj)
                    enqueuePartyOp("customers", adj)
                }
            }

            recordAuditLog("RETURN_INVOICE", "invoice", invoice.id, "Returned ${invoice.invoiceNumber} (${invoice.total})")
            if (state.isOnline) syncEngine.flushPendingOps(state.storeId)
        }
    }

    // --- PRODUCTS & INVENTORY ---
    fun saveProduct(product: ProductEntity, initialStockIfNew: Int = 0) {
        touchActivity()
        val state = _uiState.value
        if (!state.effectiveRole.canUpdateProducts()) {
            _uiState.update {
                it.copy(statusMessage = if (it.language == AppLanguage.AR) "صلاحية البائع لا تشمل تعديل المنتجات" else "Sellers cannot update products")
            }
            return
        }
        viewModelScope.launch {
            val isNew = product.id.isBlank()
            val id = product.id.ifBlank { "prod_${UUID.randomUUID().toString().replace("-", "").take(16)}" }
            val entity = product.copy(id = id, updatedAtMillis = System.currentTimeMillis())
            dao.upsertProduct(entity)

            if (state.isOnline && remote.isCloudAuthenticated()) {
                val members = remote.getMemberUids(state.storeId)
                remote.upsertProduct(
                    state.storeId,
                    FsProduct(
                        id = entity.id,
                        nameAr = entity.nameAr,
                        nameEn = entity.nameEn,
                        category = entity.category,
                        sku = entity.sku,
                        barcode = entity.barcode.takeIf { it.isNotBlank() },
                        imageUri = entity.imageUri.takeIf { it.isNotBlank() },
                        costPrice = entity.costPrice,
                        retailPrice = entity.retailPrice,
                        wholesalePrice = entity.wholesalePrice,
                        wholesaleMinQty = entity.wholesaleMinQty,
                        unitAr = entity.unitAr,
                        unitEn = entity.unitEn,
                        lowStockThreshold = entity.lowStockThreshold,
                        isDeleted = entity.isDeleted
                    ),
                    members
                )
            } else {
                syncEngine.enqueueOperation(
                    "products",
                    entity.id,
                    "UPSERT",
                    JSONObject().apply {
                        put("nameAr", entity.nameAr)
                        put("nameEn", entity.nameEn)
                        put("category", entity.category)
                        put("sku", entity.sku)
                        put("barcode", entity.barcode)
                        put("imageUri", entity.imageUri)
                        put("costPrice", entity.costPrice)
                        put("retailPrice", entity.retailPrice)
                        put("wholesalePrice", entity.wholesalePrice)
                        put("wholesaleMinQty", entity.wholesaleMinQty)
                        put("unitAr", entity.unitAr)
                        put("unitEn", entity.unitEn)
                        put("lowStockThreshold", entity.lowStockThreshold)
                        put("isDeleted", entity.isDeleted)
                    }
                )
            }

            if (isNew && initialStockIfNew > 0) {
                val movId = "mov_${UUID.randomUUID().toString().replace("-", "").take(16)}"
                val mov = StockMovementEntity(
                    id = movId,
                    productId = id,
                    type = "opening",
                    qtyDelta = initialStockIfNew,
                    unitCost = entity.costPrice,
                    reason = "Initial stock",
                    actorUid = currentUserId,
                    actorName = state.userName,
                    syncStatus = "PENDING"
                )
                dao.insertMovement(mov)
                syncEngine.enqueueOperation(
                    "movements",
                    movId,
                    "UPSERT",
                    JSONObject().apply {
                        put("productId", mov.productId)
                        put("type", mov.type)
                        put("qtyDelta", mov.qtyDelta)
                        put("unitCost", mov.unitCost)
                        put("reason", mov.reason)
                        put("refId", "")
                        put("actorUid", mov.actorUid)
                        put("actorName", mov.actorName)
                    }
                )
                if (state.isOnline && remote.isCloudAuthenticated()) {
                    syncEngine.flushPendingOps(state.storeId)
                }
            }
            recordAuditLog(
                if (isNew) "CREATE_PRODUCT" else "UPDATE_PRODUCT",
                "product",
                id,
                "${entity.nameEn} | Retail: ${entity.retailPrice}, Wholesale: ${entity.wholesalePrice}"
            )
        }
    }

    fun deleteProduct(product: ProductEntity) {
        touchActivity()
        val state = _uiState.value
        if (!state.effectiveRole.canDeleteCatalog()) return
        viewModelScope.launch {
            val softDeleted = product.copy(isDeleted = true, updatedAtMillis = System.currentTimeMillis())
            dao.upsertProduct(softDeleted)
            if (state.isOnline && remote.isCloudAuthenticated()) {
                remote.deleteProduct(state.storeId, product.id)
            }
            recordAuditLog("DELETE_PRODUCT", "product", product.id, product.nameEn)
        }
    }

    fun receiveStock(product: ProductEntity, supplierId: String?, qty: Int, unitCost: Double) {
        touchActivity()
        if (qty <= 0) return
        val state = _uiState.value
        if (!state.effectiveRole.canUpdateProducts()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val movId = "mov_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            val supplier = state.suppliers.find { it.id == supplierId }
            val reason = if (supplier != null) "Receive from ${supplier.name}" else "Stock receipt"
            val mov = StockMovementEntity(
                id = movId,
                productId = product.id,
                type = "receive",
                qtyDelta = qty,
                unitCost = unitCost,
                reason = reason,
                refId = supplierId ?: "",
                actorUid = currentUserId,
                actorName = state.userName,
                createdAtMillis = now,
                syncStatus = "PENDING"
            )
            dao.insertMovement(mov)
            syncEngine.enqueueOperation(
                "movements",
                movId,
                "UPSERT",
                JSONObject().apply {
                    put("productId", mov.productId)
                    put("type", mov.type)
                    put("qtyDelta", mov.qtyDelta)
                    put("unitCost", mov.unitCost)
                    put("reason", mov.reason)
                    put("refId", mov.refId)
                    put("actorUid", mov.actorUid)
                    put("actorName", mov.actorName)
                }
            )
            if (supplier != null) {
                val updatedSup = supplier.copy(balance = supplier.balance + (qty * unitCost), updatedAtMillis = now)
                dao.upsertParty(updatedSup)
                enqueuePartyOp("suppliers", updatedSup)
            }
            if (state.isOnline) syncEngine.flushPendingOps(state.storeId)
        }
    }

    fun adjustStock(product: ProductEntity, qtyDelta: Int, reason: String) {
        touchActivity()
        val state = _uiState.value
        if (!state.effectiveRole.canAdjustStock() || qtyDelta == 0 || reason.isBlank()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val movId = "mov_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            val mov = StockMovementEntity(
                id = movId,
                productId = product.id,
                type = "adjustment",
                qtyDelta = qtyDelta,
                unitCost = product.costPrice,
                reason = reason.trim(),
                actorUid = currentUserId,
                actorName = state.userName,
                createdAtMillis = now,
                syncStatus = "PENDING"
            )
            dao.insertMovement(mov)
            syncEngine.enqueueOperation(
                "movements",
                movId,
                "UPSERT",
                JSONObject().apply {
                    put("productId", mov.productId)
                    put("type", mov.type)
                    put("qtyDelta", mov.qtyDelta)
                    put("unitCost", mov.unitCost)
                    put("reason", mov.reason)
                    put("refId", "")
                    put("actorUid", mov.actorUid)
                    put("actorName", mov.actorName)
                }
            )
            recordAuditLog("STOCK_ADJUSTMENT", "product", product.id, "${product.nameEn}: $qtyDelta ($reason)")
            if (state.isOnline) syncEngine.flushPendingOps(state.storeId)
        }
    }

    fun saveParty(partyType: String, name: String, phone: String) {
        touchActivity()
        if (name.isBlank()) return
        val state = _uiState.value
        viewModelScope.launch {
            val id = "${partyType.take(4)}_${UUID.randomUUID().toString().replace("-", "").take(14)}"
            val entity = PartyEntity(id = id, partyType = partyType, name = name.trim(), phone = phone.trim(), balance = 0.0)
            dao.upsertParty(entity)
            val col = if (partyType == "customer") "customers" else "suppliers"
            enqueuePartyOp(col, entity)
            if (state.isOnline) syncEngine.flushPendingOps(state.storeId)
        }
    }

    fun recordPartyPayment(party: PartyEntity, amount: Double) {
        touchActivity()
        if (amount <= 0.0) return
        val state = _uiState.value
        viewModelScope.launch {
            val updated = party.copy(balance = (party.balance - amount).coerceAtLeast(0.0), updatedAtMillis = System.currentTimeMillis())
            dao.upsertParty(updated)
            val col = if (party.partyType == "customer") "customers" else "suppliers"
            enqueuePartyOp(col, updated)
            recordAuditLog("PARTY_PAYMENT", party.partyType, party.id, "${party.name}: -$amount")
            if (state.isOnline) syncEngine.flushPendingOps(state.storeId)
        }
    }

    // --- BUSINESS DAY & HOLIDAYS ---
    fun isTodayHoliday(): HolidayEntity? {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val cal = Calendar.getInstance()
        val dow = when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            else -> 7
        }
        return _uiState.value.holidays.firstOrNull { h ->
            (h.isWeeklyOffDay && h.dayOfWeek == dow) || (!h.isWeeklyOffDay && h.dateStr == todayStr)
        }
    }

    fun openBusinessDay(openingCash: Double, holidayOverride: Boolean = false) {
        touchActivity()
        val state = _uiState.value
        if (state.openBusinessDay != null) return
        val todayHoliday = isTodayHoliday()
        if (todayHoliday != null && !holidayOverride) return
        if (holidayOverride && !state.effectiveRole.canOverrideHoliday()) return

        viewModelScope.launch {
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val dayId = "day_${todayStr}_${System.currentTimeMillis() % 10000}"
            val day = BusinessDayEntity(
                id = dayId,
                dateStr = todayStr,
                status = "open",
                openedByUid = currentUserId,
                openedByName = state.userName,
                openingCash = openingCash.coerceAtLeast(0.0),
                holidayOverride = holidayOverride,
                syncStatus = "PENDING"
            )
            dao.upsertBusinessDay(day)
            enqueueDayOp(day)
            if (holidayOverride) {
                recordAuditLog("HOLIDAY_OVERRIDE", "business_day", dayId, "Opened on holiday: ${todayHoliday?.titleEn}")
            }
            if (state.isOnline) syncEngine.flushPendingOps(state.storeId)
        }
    }

    fun closeBusinessDay() {
        touchActivity()
        val state = _uiState.value
        val openDay = state.openBusinessDay ?: return
        viewModelScope.launch {
            val closed = openDay.copy(
                status = "closed",
                closedByUid = currentUserId,
                closedByName = state.userName,
                updatedAtMillis = System.currentTimeMillis(),
                syncStatus = "PENDING"
            )
            dao.upsertBusinessDay(closed)
            enqueueDayOp(closed)
            recordAuditLog("CLOSE_DAY", "business_day", closed.id, "Total Sales: ${closed.totalSales}, Items: ${closed.itemsSoldCount}")
            if (state.isOnline) syncEngine.flushPendingOps(state.storeId)
        }
    }

    fun addHoliday(dateStr: String, titleAr: String, titleEn: String, isWeekly: Boolean, dayOfWeek: Int) {
        touchActivity()
        val state = _uiState.value
        if (!state.effectiveRole.canManageCatalog()) return
        viewModelScope.launch {
            val id = "hol_${UUID.randomUUID().toString().replace("-", "").take(14)}"
            val entity = HolidayEntity(
                id = id,
                dateStr = if (isWeekly) "" else dateStr.trim(),
                titleAr = titleAr.ifBlank { titleEn },
                titleEn = titleEn.ifBlank { titleAr },
                isWeeklyOffDay = isWeekly,
                dayOfWeek = if (isWeekly) dayOfWeek else 0
            )
            dao.upsertHoliday(entity)
            if (state.isOnline && remote.isCloudAuthenticated()) {
                val members = remote.getMemberUids(state.storeId)
                remote.upsertHoliday(
                    state.storeId,
                    FsHoliday(
                        id = id,
                        dateStr = entity.dateStr.takeIf { it.isNotBlank() },
                        titleAr = entity.titleAr,
                        titleEn = entity.titleEn,
                        isWeeklyOffDay = entity.isWeeklyOffDay,
                        dayOfWeek = entity.dayOfWeek
                    ),
                    members
                )
            }
        }
    }

    fun deleteHoliday(id: String) {
        touchActivity()
        if (!_uiState.value.effectiveRole.canManageCatalog()) return
        viewModelScope.launch { dao.deleteHolidayById(id) }
    }

    // --- ADMIN & TEAM ---
    private fun notAllowedMessage(lang: AppLanguage): String =
        if (lang == AppLanguage.AR) "غير مسموح" else "Not allowed"

    private fun lastAdminProtectedMessage(lang: AppLanguage): String =
        if (lang == AppLanguage.AR) "لا يمكن حذف أو تخفيض آخر مسؤول نشط" else "Cannot delete, deactivate, or demote the last remaining admin"

    fun addOrUpdateMember(
        uid: String,
        username: String,
        displayName: String,
        password: String,
        role: UserRole,
        active: Boolean = true
    ) {
        touchActivity()
        viewModelScope.launch {
            saveMemberEnforced(
                actorRole = _uiState.value.effectiveRole,
                uid = uid,
                username = username,
                displayName = displayName,
                password = password,
                role = role,
                active = active
            )
        }
    }

    internal suspend fun saveMemberEnforced(
        actorRole: UserRole = _uiState.value.effectiveRole,
        uid: String,
        username: String,
        displayName: String,
        password: String,
        role: UserRole,
        active: Boolean = true
    ): Result<StoreMemberEntity> {
        val state = _uiState.value
        if (!actorRole.canManageUsers()) {
            _uiState.update { it.copy(statusMessage = notAllowedMessage(it.language)) }
            return Result.failure(SecurityException("Not allowed"))
        }

        val cleanUsername = username.trim()
        val cleanName = displayName.trim().ifBlank { cleanUsername }
        if (cleanUsername.isBlank()) {
            _uiState.update {
                it.copy(statusMessage = if (it.language == AppLanguage.AR) "يرجى إدخال اسم المستخدم" else "Username is required")
            }
            return Result.failure(IllegalArgumentException("Username is required"))
        }

        val allMembers = dao.getMembersSnapshot()
        val byUid = uid.trim().takeIf { it.isNotBlank() }?.let { targetUid ->
            allMembers.find { it.uid == targetUid }
        }
        val byUsername = allMembers.find { it.username.equals(cleanUsername, ignoreCase = true) }

        if (byUid != null && byUsername != null && byUid.uid != byUsername.uid) {
            _uiState.update {
                it.copy(statusMessage = if (it.language == AppLanguage.AR) "اسم المستخدم مستخدم بالفعل" else "Username is already in use")
            }
            return Result.failure(IllegalArgumentException("Username is already in use"))
        }

        val existing = byUid ?: byUsername
        val existingRole = existing?.let { UserRole.fromKey(it.role) }

        // Only an Admin can create, edit, deactivate, or demote an Admin account.
        if ((role == UserRole.ADMIN || existingRole == UserRole.ADMIN) && actorRole != UserRole.ADMIN) {
            _uiState.update { it.copy(statusMessage = notAllowedMessage(it.language)) }
            return Result.failure(SecurityException("Not allowed"))
        }

        // Block demoting or deactivating the last remaining active Admin.
        if (existing != null && existingRole == UserRole.ADMIN && (role != UserRole.ADMIN || !active)) {
            val remainingActiveAdmins = allMembers.count {
                UserRole.fromKey(it.role) == UserRole.ADMIN && it.active && it.uid != existing.uid
            }
            if (remainingActiveAdmins == 0) {
                _uiState.update { it.copy(statusMessage = lastAdminProtectedMessage(it.language)) }
                return Result.failure(IllegalStateException("Cannot demote or deactivate the last remaining admin"))
            }
        }

        val passwordHash = if (password.isNotEmpty()) {
            PreferencesManager.sha256(password)
        } else {
            existing?.passwordHash.orEmpty()
        }
        if (passwordHash.isBlank()) {
            _uiState.update {
                it.copy(statusMessage = if (it.language == AppLanguage.AR) "يرجى تعيين كلمة مرور للمستخدم الجديد" else "Password is required for a new user")
            }
            return Result.failure(IllegalArgumentException("Password is required for a new user"))
        }

        val resolvedUid = existing?.uid ?: uid.trim().ifBlank {
            "staff_${cleanUsername.lowercase().replace(Regex("[^a-z0-9]"), "").ifBlank { "user" }}_${UUID.randomUUID().toString().take(6)}"
        }
        val now = System.currentTimeMillis()
        val entity = StoreMemberEntity(
            uid = resolvedUid,
            username = cleanUsername,
            passwordHash = passwordHash,
            displayName = cleanName,
            role = role.key,
            active = active,
            createdAtMillis = existing?.createdAtMillis ?: now,
            updatedAtMillis = now
        )
        dao.upsertMember(entity)
        if (state.isOnline && remote.isCloudAuthenticated()) {
            val members = remote.getMemberUids(state.storeId)
            remote.upsertMember(
                state.storeId,
                FsStoreMember(uid = resolvedUid, displayName = entity.displayName, role = role.key, active = active),
                members
            )
        }
        recordAuditLog("UPSERT_MEMBER", "member", resolvedUid, "${entity.username} (${entity.displayName}) -> ${role.key}")
        _uiState.update {
            it.copy(statusMessage = if (it.language == AppLanguage.AR) "تم حفظ المستخدم وكلمة المرور بنجاح" else "User and password saved successfully")
        }
        return Result.success(entity)
    }

    fun deleteMember(targetUid: String) {
        touchActivity()
        viewModelScope.launch {
            deleteMemberEnforced(actorRole = _uiState.value.effectiveRole, targetUid = targetUid)
        }
    }

    internal suspend fun deleteMemberEnforced(
        actorRole: UserRole = _uiState.value.effectiveRole,
        targetUid: String
    ): Result<Unit> {
        val state = _uiState.value
        if (!actorRole.canManageUsers()) {
            _uiState.update { it.copy(statusMessage = notAllowedMessage(it.language)) }
            return Result.failure(SecurityException("Not allowed"))
        }

        val cleanUid = targetUid.trim()
        val allMembers = dao.getMembersSnapshot()
        val target = allMembers.find { it.uid == cleanUid }
        if (target == null) {
            _uiState.update {
                it.copy(statusMessage = if (it.language == AppLanguage.AR) "المستخدم غير موجود" else "User not found")
            }
            return Result.failure(NoSuchElementException("User not found"))
        }

        val targetRole = UserRole.fromKey(target.role)
        if (targetRole == UserRole.ADMIN && actorRole != UserRole.ADMIN) {
            _uiState.update { it.copy(statusMessage = notAllowedMessage(it.language)) }
            return Result.failure(SecurityException("Not allowed"))
        }

        if (targetRole == UserRole.ADMIN) {
            val remainingActiveAdmins = allMembers.count {
                UserRole.fromKey(it.role) == UserRole.ADMIN && it.active && it.uid != target.uid
            }
            val remainingTotalAdmins = allMembers.count {
                UserRole.fromKey(it.role) == UserRole.ADMIN && it.uid != target.uid
            }
            if (remainingActiveAdmins == 0 || remainingTotalAdmins == 0) {
                _uiState.update { it.copy(statusMessage = lastAdminProtectedMessage(it.language)) }
                return Result.failure(IllegalStateException("Cannot delete the last remaining admin"))
            }
        }

        dao.deleteMemberByUid(target.uid)
        if (state.isOnline && remote.isCloudAuthenticated()) {
            val members = remote.getMemberUids(state.storeId)
            remote.deleteMember(state.storeId, target.uid, members)
        }
        recordAuditLog("DELETE_MEMBER", "member", target.uid, "${target.username} (${target.role})")
        _uiState.update {
            it.copy(statusMessage = if (it.language == AppLanguage.AR) "تم حذف المستخدم بنجاح" else "User deleted successfully")
        }
        return Result.success(Unit)
    }

    fun saveSettings(updated: StoreSettingsEntity) {
        touchActivity()
        val state = _uiState.value
        if (state.effectiveRole != UserRole.ADMIN) return
        viewModelScope.launch {
            dao.upsertSettings(updated)
            if (state.isOnline && remote.isCloudAuthenticated()) {
                val members = remote.getMemberUids(state.storeId)
                remote.upsertSettings(
                    state.storeId,
                    FsStoreSettings(
                        id = "main",
                        storeNameAr = updated.storeNameAr,
                        storeNameEn = updated.storeNameEn,
                        primaryColorHex = updated.primaryColorHex,
                        secondaryColorHex = updated.secondaryColorHex,
                        logoUri = updated.logoUri.takeIf { it.isNotBlank() },
                        sessionTimeoutMinutes = updated.sessionTimeoutMinutes,
                        notifyLowStock = updated.notifyLowStock,
                        notifyDayNotClosed = updated.notifyDayNotClosed,
                        notifyNewUser = updated.notifyNewUser,
                        notifySyncErrors = updated.notifySyncErrors,
                        notifyNewVersion = updated.notifyNewVersion,
                        notifyRoles = updated.notifyRolesCsv.split(",").filter { it.isNotBlank() }
                    ),
                    members
                )
            }
            recordAuditLog("UPDATE_SETTINGS", "settings", "main", "${updated.storeNameEn} (${updated.primaryColorHex})")
        }
    }

    fun publishAppRelease(
        versionCode: Int,
        versionName: String,
        downloadUrl: String,
        notesAr: String,
        notesEn: String,
        isMandatory: Boolean
    ) {
        touchActivity()
        val state = _uiState.value
        if (state.effectiveRole != UserRole.ADMIN) return
        if (!state.isOnline) {
            _uiState.update {
                it.copy(statusMessage = if (it.language == AppLanguage.AR) "نشر التحديثات يتطلب اتصالاً بالإنترنت" else "Publishing updates requires internet connection")
            }
            return
        }
        viewModelScope.launch {
            val id = "android_v$versionCode"
            val entity = AppReleaseEntity(
                id = id,
                platform = "android",
                versionCode = versionCode,
                versionName = versionName.trim(),
                downloadUrl = downloadUrl.trim(),
                releaseNotesAr = notesAr.trim(),
                releaseNotesEn = notesEn.trim(),
                isMandatory = isMandatory
            )
            dao.upsertRelease(entity)
            if (remote.isCloudAuthenticated()) {
                val members = remote.getMemberUids(state.storeId)
                remote.upsertRelease(
                    state.storeId,
                    FsAppRelease(
                        id = id,
                        platform = "android",
                        versionCode = versionCode,
                        versionName = entity.versionName,
                        downloadUrl = entity.downloadUrl,
                        releaseNotesAr = entity.releaseNotesAr,
                        releaseNotesEn = entity.releaseNotesEn,
                        isMandatory = isMandatory
                    ),
                    members
                )
            }
            recordAuditLog("PUBLISH_RELEASE", "app_release", id, "v${entity.versionName} ($versionCode)")
        }
    }

    private suspend fun enqueuePartyOp(collection: String, party: PartyEntity) {
        syncEngine.enqueueOperation(
            collection,
            party.id,
            "UPSERT",
            JSONObject().apply {
                put("name", party.name)
                put("phone", party.phone)
                put("balance", party.balance)
            }
        )
    }

    private suspend fun enqueueDayOp(day: BusinessDayEntity) {
        syncEngine.enqueueOperation(
            "business_days",
            day.id,
            "UPSERT",
            JSONObject().apply {
                put("dateStr", day.dateStr)
                put("status", day.status)
                put("openedByUid", day.openedByUid)
                put("openedByName", day.openedByName)
                put("closedByUid", day.closedByUid)
                put("closedByName", day.closedByName)
                put("openingCash", day.openingCash)
                put("totalSales", day.totalSales)
                put("cashSales", day.cashSales)
                put("transferSales", day.transferSales)
                put("creditSales", day.creditSales)
                put("itemsSoldCount", day.itemsSoldCount)
                put("holidayOverride", day.holidayOverride)
            }
        )
    }

    private suspend fun recordAuditLog(action: String, targetType: String, targetId: String, details: String) {
        val state = _uiState.value
        val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val log = AuditLogEntity(
            id = logId,
            actorUid = currentUserId,
            actorName = state.userName,
            actorRole = state.actualRole.key,
            action = action,
            targetType = targetType,
            targetId = targetId,
            details = details
        )
        dao.insertAuditLog(log)
        syncEngine.enqueueOperation(
            "audit_logs",
            logId,
            "UPSERT",
            JSONObject().apply {
                put("actorUid", log.actorUid)
                put("actorName", log.actorName)
                put("actorRole", log.actorRole)
                put("action", log.action)
                put("targetType", log.targetType)
                put("targetId", log.targetId)
                put("details", log.details)
            }
        )
    }

    private fun computeAnalytics(
        products: List<ProductWithStock>,
        movements: List<StockMovementEntity>,
        invoices: List<InvoiceEntity>,
        period: PeriodFilter
    ): DashboardAnalytics {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfDay = cal.timeInMillis

        val monthCal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfMonth = monthCal.timeInMillis

        val periodStart = when (period) {
            PeriodFilter.DAY -> startOfDay
            PeriodFilter.WEEK -> now - 7L * 86_400_000L
            PeriodFilter.MONTH -> startOfMonth
            PeriodFilter.YEAR -> now - 365L * 86_400_000L
        }

        val completedInvoices = invoices.filter { it.status == "completed" }
        val todaySales = completedInvoices.filter { it.createdAtMillis >= startOfDay }.sumOf { it.total }
        val monthSales = completedInvoices.filter { it.createdAtMillis >= startOfMonth }.sumOf { it.total }
        val periodInvoices = completedInvoices.filter { it.createdAtMillis >= periodStart }
        val periodSales = periodInvoices.sumOf { it.total }
        val periodCostOfGoods = periodInvoices.sumOf { it.costTotal }
        val periodProfit = periodSales - periodCostOfGoods

        val periodPurchases = movements
            .filter { it.type == "receive" && it.createdAtMillis >= periodStart }
            .sumOf { it.qtyDelta * it.unitCost }

        val salesByProduct = mutableMapOf<String, Pair<Int, Double>>()
        periodInvoices.forEach { inv ->
            runCatching {
                val arr = JSONArray(inv.itemsJson)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val pid = o.getString("productId")
                    val q = o.getInt("qty")
                    val rev = q * o.getDouble("unitPrice")
                    val prev = salesByProduct[pid] ?: (0 to 0.0)
                    salesByProduct[pid] = (prev.first + q) to (prev.second + rev)
                }
            }
        }

        val productMetrics = products.map { pw ->
            val sold = salesByProduct[pw.product.id] ?: (0 to 0.0)
            ProductMetric(
                productId = pw.product.id,
                nameAr = pw.product.nameAr,
                nameEn = pw.product.nameEn,
                unitsSold = sold.first,
                revenue = sold.second,
                stockQty = pw.currentStock
            )
        }

        val bestSelling = productMetrics.sortedByDescending { it.unitsSold }.take(5)
        val worstSelling = productMetrics.sortedBy { it.unitsSold }.take(5)
        val highestStock = products.sortedByDescending { it.currentStock }.take(5)
        val lowStockAlerts = products.filter { it.isLowStock }.sortedBy { it.currentStock }

        val userPerf = periodInvoices.groupBy { it.cashierUid to it.cashierName }.map { (k, list) ->
            UserPerformanceRow(k.first, k.second, list.size, list.sumOf { it.total })
        }.sortedByDescending { it.totalSales }

        val stockValueCost = products.sumOf { it.currentStock.coerceAtLeast(0) * it.product.costPrice }
        val stockValueRetail = products.sumOf { it.currentStock.coerceAtLeast(0) * it.product.retailPrice }

        return DashboardAnalytics(
            todaySales = todaySales,
            monthSales = monthSales,
            periodSales = periodSales,
            periodPurchases = periodPurchases,
            periodProfit = periodProfit,
            stockTotalValueCost = stockValueCost,
            stockTotalValueRetail = stockValueRetail,
            bestSelling = bestSelling,
            worstSelling = worstSelling,
            highestStock = highestStock,
            lowStockAlerts = lowStockAlerts,
            userPerformance = userPerf
        )
    }

    private fun buildNotifications(state: StoreUiState): List<InAppNotification> {
        val allowedRoles = state.settings.notifyRolesCsv.split(",").map { it.trim() }
        if (state.effectiveRole.key !in allowedRoles) return emptyList()

        val list = mutableListOf<InAppNotification>()
        if (state.settings.notifyLowStock && state.analytics.lowStockAlerts.isNotEmpty()) {
            val count = state.analytics.lowStockAlerts.size
            list.add(
                InAppNotification(
                    id = "notif_low_stock",
                    type = "low_stock",
                    titleAr = "تنبيه نقص المخزون",
                    titleEn = "Low Stock Alert",
                    bodyAr = "يوجد $count منتجات وصلت إلى الحد الأدنى للمخزون.",
                    bodyEn = "$count products are at or below low-stock threshold."
                )
            )
        }
        if (state.settings.notifyDayNotClosed && state.openBusinessDay != null) {
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            if (state.openBusinessDay.dateStr != todayStr) {
                list.add(
                    InAppNotification(
                        id = "notif_day_open",
                        type = "day_not_closed",
                        titleAr = "يوم تجاري سابق لم يُغلق",
                        titleEn = "Previous Business Day Unclosed",
                        bodyAr = "يوم ${state.openBusinessDay.dateStr} ما زال مفتوحاً.",
                        bodyEn = "Business day ${state.openBusinessDay.dateStr} is still open."
                    )
                )
            }
        }
        if (state.settings.notifySyncErrors && !state.lastSyncError.isNullOrBlank()) {
            list.add(
                InAppNotification(
                    id = "notif_sync_err",
                    type = "sync_error",
                    titleAr = "خطأ في المزامنة",
                    titleEn = "Sync Error",
                    bodyAr = state.lastSyncError,
                    bodyEn = state.lastSyncError
                )
            )
        }
        if (state.settings.notifyNewVersion && state.availableUpdate != null) {
            list.add(
                InAppNotification(
                    id = "notif_new_ver",
                    type = "new_version",
                    titleAr = "يتوفر إصدار جديد ${state.availableUpdate.versionName}",
                    titleEn = "New Version ${state.availableUpdate.versionName}",
                    bodyAr = state.availableUpdate.releaseNotesAr,
                    bodyEn = state.availableUpdate.releaseNotesEn
                )
            )
        }
        return list
    }

    override fun onCleared() {
        super.onCleared()
        syncEngine.stopListeners()
        connectivity.unregister()
    }

    private data class DataBundle1(
        val products: List<ProductWithStock>,
        val movements: List<StockMovementEntity>,
        val invoices: List<InvoiceEntity>,
        val customers: List<PartyEntity>,
        val suppliers: List<PartyEntity>
    )

    private data class DataBundle2(
        val days: List<BusinessDayEntity>,
        val openDay: BusinessDayEntity?,
        val holidays: List<HolidayEntity>,
        val logs: List<AuditLogEntity>,
        val members: List<StoreMemberEntity>
    )
}
