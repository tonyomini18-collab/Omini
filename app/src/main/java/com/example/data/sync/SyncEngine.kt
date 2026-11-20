package com.example.data.sync

import android.util.Log
import com.example.data.local.AppReleaseEntity
import com.example.data.local.AuditLogEntity
import com.example.data.local.BusinessDayEntity
import com.example.data.local.HolidayEntity
import com.example.data.local.InvoiceEntity
import com.example.data.local.PartyEntity
import com.example.data.local.PendingSyncOpEntity
import com.example.data.local.ProductEntity
import com.example.data.local.StockMovementEntity
import com.example.data.local.StoreDao
import com.example.data.local.StoreMemberEntity
import com.example.data.local.StoreSettingsEntity
import com.example.data.remote.FirestoreRepository
import com.example.data.remote.FsAuditLog
import com.example.data.remote.FsBusinessDay
import com.example.data.remote.FsInvoice
import com.example.data.remote.FsParty
import com.example.data.remote.FsStockMovement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

class SyncEngine(
    private val dao: StoreDao,
    private val remote: FirestoreRepository,
    private val connectivity: ConnectivityObserver,
    private val prefs: PreferencesManager
) {
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncError = MutableStateFlow<String?>(null)
    val lastSyncError: StateFlow<String?> = _lastSyncError.asStateFlow()

    private var listenerJobs: List<Job> = emptyList()
    private var activeStoreId: String = ""

    fun startListeners(scope: CoroutineScope, storeId: String) {
        if (!remote.isCloudAuthenticated()) return
        if (activeStoreId == storeId && listenerJobs.isNotEmpty()) return
        stopListeners()
        activeStoreId = storeId

        listenerJobs = listOf(
            scope.launch {
                runCatching {
                    remote.observeProducts(storeId).collect { remoteList ->
                        val localEntities = remoteList.map { fs ->
                            ProductEntity(
                                id = fs.id,
                                nameAr = fs.nameAr,
                                nameEn = fs.nameEn,
                                category = fs.category,
                                sku = fs.sku,
                                barcode = fs.barcode ?: "",
                                imageUri = fs.imageUri ?: "",
                                drawableResName = defaultDrawableForSku(fs.sku, fs.category),
                                costPrice = fs.costPrice,
                                retailPrice = fs.retailPrice,
                                wholesalePrice = fs.wholesalePrice,
                                wholesaleMinQty = fs.wholesaleMinQty,
                                unitAr = fs.unitAr,
                                unitEn = fs.unitEn,
                                lowStockThreshold = fs.lowStockThreshold,
                                isDeleted = fs.isDeleted,
                                createdAtMillis = fs.createdAt?.toDate()?.time ?: System.currentTimeMillis(),
                                updatedAtMillis = fs.updatedAt?.toDate()?.time ?: System.currentTimeMillis()
                            )
                        }
                        if (localEntities.isNotEmpty()) dao.upsertProducts(localEntities)
                    }
                }.onFailure { _lastSyncError.value = it.localizedMessage }
            },
            scope.launch {
                runCatching {
                    remote.observeMovements(storeId).collect { list ->
                        val entities = list.map { fs ->
                            StockMovementEntity(
                                id = fs.id,
                                productId = fs.productId,
                                type = fs.type,
                                qtyDelta = fs.qtyDelta,
                                unitCost = fs.unitCost,
                                reason = fs.reason,
                                refId = fs.refId ?: "",
                                actorUid = fs.actorUid,
                                actorName = fs.actorName,
                                createdAtMillis = fs.createdAt?.toDate()?.time ?: System.currentTimeMillis(),
                                syncStatus = "SYNCED"
                            )
                        }
                        if (entities.isNotEmpty()) dao.insertMovements(entities)
                    }
                }.onFailure { _lastSyncError.value = it.localizedMessage }
            },
            scope.launch {
                runCatching {
                    remote.observeInvoices(storeId).collect { list ->
                        val entities = list.map { fs ->
                            InvoiceEntity(
                                id = fs.id,
                                invoiceNumber = fs.invoiceNumber,
                                saleType = fs.saleType,
                                customerId = fs.customerId ?: "",
                                customerName = fs.customerName ?: "",
                                itemsJson = fs.itemsJson ?: fs.itemsSummary,
                                itemsSummary = fs.itemsSummary,
                                subtotal = fs.subtotal,
                                discount = fs.discount,
                                total = fs.total,
                                costTotal = fs.costTotal,
                                paymentMethod = fs.paymentMethod,
                                status = fs.status,
                                businessDayId = fs.businessDayId,
                                cashierUid = fs.cashierUid,
                                cashierName = fs.cashierName,
                                createdAtMillis = fs.createdAt?.toDate()?.time ?: System.currentTimeMillis(),
                                updatedAtMillis = fs.updatedAt?.toDate()?.time ?: System.currentTimeMillis(),
                                syncStatus = "SYNCED",
                                currencyCode = fs.currencyCode ?: if (fs.paymentMethod.equals("usd", ignoreCase = true)) "USD" else "SSP",
                                usdRateSsp = fs.usdRateSsp ?: 0.0,
                                usdTotal = fs.usdTotal ?: 0.0
                            )
                        }
                        if (entities.isNotEmpty()) dao.upsertInvoices(entities)
                    }
                }.onFailure { _lastSyncError.value = it.localizedMessage }
            },
            scope.launch {
                runCatching {
                    remote.observeParties(storeId, "customers").collect { list ->
                        val entities = list.map { fs ->
                            PartyEntity(
                                id = fs.id,
                                partyType = "customer",
                                name = fs.name,
                                phone = fs.phone ?: "",
                                balance = fs.balance,
                                createdAtMillis = fs.createdAt?.toDate()?.time ?: System.currentTimeMillis(),
                                updatedAtMillis = fs.updatedAt?.toDate()?.time ?: System.currentTimeMillis(),
                                syncStatus = "SYNCED"
                            )
                        }
                        if (entities.isNotEmpty()) dao.upsertParties(entities)
                    }
                }
            },
            scope.launch {
                runCatching {
                    remote.observeParties(storeId, "suppliers").collect { list ->
                        val entities = list.map { fs ->
                            PartyEntity(
                                id = fs.id,
                                partyType = "supplier",
                                name = fs.name,
                                phone = fs.phone ?: "",
                                balance = fs.balance,
                                createdAtMillis = fs.createdAt?.toDate()?.time ?: System.currentTimeMillis(),
                                updatedAtMillis = fs.updatedAt?.toDate()?.time ?: System.currentTimeMillis(),
                                syncStatus = "SYNCED"
                            )
                        }
                        if (entities.isNotEmpty()) dao.upsertParties(entities)
                    }
                }
            },
            scope.launch {
                runCatching {
                    remote.observeBusinessDays(storeId).collect { list ->
                        val entities = list.map { fs ->
                            BusinessDayEntity(
                                id = fs.id,
                                dateStr = fs.dateStr,
                                status = fs.status,
                                openedByUid = fs.openedByUid,
                                openedByName = fs.openedByName,
                                closedByUid = fs.closedByUid ?: "",
                                closedByName = fs.closedByName ?: "",
                                openingCash = fs.openingCash,
                                totalSales = fs.totalSales,
                                cashSales = fs.cashSales,
                                transferSales = fs.transferSales,
                                creditSales = fs.creditSales,
                                itemsSoldCount = fs.itemsSoldCount,
                                holidayOverride = fs.holidayOverride,
                                createdAtMillis = fs.createdAt?.toDate()?.time ?: System.currentTimeMillis(),
                                updatedAtMillis = fs.updatedAt?.toDate()?.time ?: System.currentTimeMillis(),
                                syncStatus = "SYNCED"
                            )
                        }
                        if (entities.isNotEmpty()) dao.upsertBusinessDays(entities)
                    }
                }
            },
            scope.launch {
                runCatching {
                    remote.observeHolidays(storeId).collect { list ->
                        val entities = list.map { fs ->
                            HolidayEntity(
                                id = fs.id,
                                dateStr = fs.dateStr ?: "",
                                titleAr = fs.titleAr,
                                titleEn = fs.titleEn,
                                isWeeklyOffDay = fs.isWeeklyOffDay,
                                dayOfWeek = fs.dayOfWeek,
                                createdAtMillis = fs.createdAt?.toDate()?.time ?: System.currentTimeMillis(),
                                updatedAtMillis = fs.updatedAt?.toDate()?.time ?: System.currentTimeMillis()
                            )
                        }
                        if (entities.isNotEmpty()) dao.upsertHolidays(entities)
                    }
                }
            },
            scope.launch {
                runCatching {
                    remote.observeAuditLogs(storeId).collect { list ->
                        val entities = list.map { fs ->
                            AuditLogEntity(
                                id = fs.id,
                                actorUid = fs.actorUid,
                                actorName = fs.actorName,
                                actorRole = fs.actorRole,
                                action = fs.action,
                                targetType = fs.targetType,
                                targetId = fs.targetId,
                                details = fs.details,
                                createdAtMillis = fs.createdAt?.toDate()?.time ?: System.currentTimeMillis()
                            )
                        }
                        if (entities.isNotEmpty()) dao.insertAuditLogs(entities)
                    }
                }
            },
            scope.launch {
                runCatching {
                    remote.observeMembers(storeId).collect { list ->
                        val entities = list.map { fs ->
                            StoreMemberEntity(
                                uid = fs.uid,
                                displayName = fs.displayName,
                                role = fs.role,
                                active = fs.active,
                                createdAtMillis = fs.createdAt?.toDate()?.time ?: System.currentTimeMillis(),
                                updatedAtMillis = fs.updatedAt?.toDate()?.time ?: System.currentTimeMillis()
                            )
                        }
                        if (entities.isNotEmpty()) dao.upsertMembers(entities)
                    }
                }
            },
            scope.launch {
                runCatching {
                    remote.observeSettings(storeId).collect { fs ->
                        if (fs != null) {
                            dao.upsertSettings(
                                StoreSettingsEntity(
                                    id = "main",
                                    storeNameAr = fs.storeNameAr,
                                    storeNameEn = fs.storeNameEn,
                                    primaryColorHex = fs.primaryColorHex,
                                    secondaryColorHex = fs.secondaryColorHex,
                                    logoUri = fs.logoUri ?: "",
                                    sessionTimeoutMinutes = fs.sessionTimeoutMinutes,
                                    notifyLowStock = fs.notifyLowStock,
                                    notifyDayNotClosed = fs.notifyDayNotClosed,
                                    notifyNewUser = fs.notifyNewUser,
                                    notifySyncErrors = fs.notifySyncErrors,
                                    notifyNewVersion = fs.notifyNewVersion,
                                    notifyRolesCsv = fs.notifyRoles.joinToString(","),
                                    updatedAtMillis = fs.updatedAt?.toDate()?.time ?: System.currentTimeMillis(),
                                    usdRateSsp = fs.usdRateSsp?.takeIf { it > 0.0 } ?: 1300.0
                                )
                            )
                            fs.usdRateSsp?.takeIf { it > 0.0 }?.let { prefs.setUsdRateSsp(it) }
                        }
                    }
                }
            },
            scope.launch {
                runCatching {
                    remote.observeReleases(storeId).collect { list ->
                        val entities = list.map { fs ->
                            AppReleaseEntity(
                                id = fs.id,
                                platform = fs.platform,
                                versionCode = fs.versionCode,
                                versionName = fs.versionName,
                                downloadUrl = fs.downloadUrl,
                                releaseNotesAr = fs.releaseNotesAr,
                                releaseNotesEn = fs.releaseNotesEn,
                                isMandatory = fs.isMandatory,
                                createdAtMillis = fs.createdAt?.toDate()?.time ?: System.currentTimeMillis()
                            )
                        }
                        if (entities.isNotEmpty()) dao.upsertReleases(entities)
                    }
                }
            }
        )
    }

    fun stopListeners() {
        listenerJobs.forEach { it.cancel() }
        listenerJobs = emptyList()
        activeStoreId = ""
    }

    suspend fun enqueueOperation(collectionName: String, documentId: String, operation: String, payload: JSONObject) {
        dao.enqueueSyncOp(
            PendingSyncOpEntity(
                opId = UUID.randomUUID().toString(),
                collectionName = collectionName,
                documentId = documentId,
                operation = operation,
                payloadJson = payload.toString(),
                createdAtMillis = System.currentTimeMillis()
            )
        )
    }

    suspend fun flushPendingOps(storeId: String): Int {
        if (!connectivity.isOnline.value || !remote.isCloudAuthenticated()) return 0
        if (_isSyncing.value) return 0
        _isSyncing.value = true
        _lastSyncError.value = null
        var syncedCount = 0
        try {
            val memberUids = remote.getMemberUids(storeId)
            val ops = dao.getPendingSyncOps()
            for (op in ops) {
                val res = executeSyncOp(storeId, op, memberUids)
                if (res.isSuccess) {
                    dao.deleteSyncOp(op.opId)
                    syncedCount++
                } else {
                    val err = res.exceptionOrNull()?.localizedMessage ?: "Sync error"
                    Log.w("SyncEngine", "Sync failed for ${op.collectionName}/${op.documentId}: $err")
                    dao.markSyncOpError(op.opId, err)
                    _lastSyncError.value = err
                }
            }
        } finally {
            _isSyncing.value = false
        }
        return syncedCount
    }

    private suspend fun executeSyncOp(storeId: String, op: PendingSyncOpEntity, memberUids: List<String>): Result<Unit> {
        val json = JSONObject(op.payloadJson)
        return when (op.collectionName) {
            "products" -> {
                remote.upsertProduct(
                    storeId,
                    com.example.data.remote.FsProduct(
                        id = op.documentId,
                        nameAr = json.getString("nameAr"),
                        nameEn = json.getString("nameEn"),
                        category = json.optString("category", "perfume"),
                        sku = json.getString("sku"),
                        barcode = json.optString("barcode").takeIf { it.isNotBlank() },
                        imageUri = json.optString("imageUri").takeIf { it.isNotBlank() },
                        costPrice = json.optDouble("costPrice", 0.0),
                        retailPrice = json.optDouble("retailPrice", 0.0),
                        wholesalePrice = json.optDouble("wholesalePrice", 0.0),
                        wholesaleMinQty = json.optInt("wholesaleMinQty", 1),
                        unitAr = json.optString("unitAr", "قطعة"),
                        unitEn = json.optString("unitEn", "Unit"),
                        lowStockThreshold = json.optInt("lowStockThreshold", 5),
                        isDeleted = json.optBoolean("isDeleted", false)
                    ),
                    memberUids
                ).map { }
            }
            "movements" -> {
                remote.createMovement(
                    storeId,
                    FsStockMovement(
                        id = op.documentId,
                        productId = json.getString("productId"),
                        type = json.getString("type"),
                        qtyDelta = json.getInt("qtyDelta"),
                        unitCost = json.getDouble("unitCost"),
                        reason = json.getString("reason"),
                        refId = json.optString("refId").takeIf { it.isNotBlank() },
                        actorUid = json.getString("actorUid"),
                        actorName = json.getString("actorName")
                    ),
                    memberUids
                ).map { }
            }
            "invoices" -> {
                remote.upsertInvoice(
                    storeId,
                    FsInvoice(
                        id = op.documentId,
                        invoiceNumber = json.getString("invoiceNumber"),
                        saleType = json.getString("saleType"),
                        customerId = json.optString("customerId").takeIf { it.isNotBlank() },
                        customerName = json.optString("customerName").takeIf { it.isNotBlank() },
                        itemsSummary = json.getString("itemsSummary"),
                        subtotal = json.getDouble("subtotal"),
                        discount = json.getDouble("discount"),
                        total = json.getDouble("total"),
                        costTotal = json.getDouble("costTotal"),
                        paymentMethod = json.getString("paymentMethod"),
                        status = json.getString("status"),
                        businessDayId = json.getString("businessDayId"),
                        cashierUid = json.getString("cashierUid"),
                        cashierName = json.getString("cashierName"),
                        itemsJson = json.optString("itemsJson").takeIf { it.isNotBlank() },
                        currencyCode = json.optString("currencyCode").takeIf { it.isNotBlank() },
                        usdRateSsp = if (json.has("usdRateSsp")) json.optDouble("usdRateSsp", 0.0) else null,
                        usdTotal = if (json.has("usdTotal")) json.optDouble("usdTotal", 0.0) else null
                    ),
                    memberUids
                ).map { }
            }
            "customers", "suppliers" -> {
                remote.upsertParty(
                    storeId,
                    op.collectionName,
                    FsParty(
                        id = op.documentId,
                        name = json.getString("name"),
                        phone = json.optString("phone").takeIf { it.isNotBlank() },
                        balance = json.getDouble("balance")
                    ),
                    memberUids
                ).map { }
            }
            "business_days" -> {
                remote.upsertBusinessDay(
                    storeId,
                    FsBusinessDay(
                        id = op.documentId,
                        dateStr = json.getString("dateStr"),
                        status = json.getString("status"),
                        openedByUid = json.getString("openedByUid"),
                        openedByName = json.getString("openedByName"),
                        closedByUid = json.optString("closedByUid").takeIf { it.isNotBlank() },
                        closedByName = json.optString("closedByName").takeIf { it.isNotBlank() },
                        openingCash = json.getDouble("openingCash"),
                        totalSales = json.getDouble("totalSales"),
                        cashSales = json.getDouble("cashSales"),
                        transferSales = json.getDouble("transferSales"),
                        creditSales = json.getDouble("creditSales"),
                        itemsSoldCount = json.getInt("itemsSoldCount"),
                        holidayOverride = json.getBoolean("holidayOverride")
                    ),
                    memberUids
                ).map { }
            }
            "audit_logs" -> {
                remote.createAuditLog(
                    storeId,
                    FsAuditLog(
                        id = op.documentId,
                        actorUid = json.getString("actorUid"),
                        actorName = json.getString("actorName"),
                        actorRole = json.getString("actorRole"),
                        action = json.getString("action"),
                        targetType = json.getString("targetType"),
                        targetId = json.getString("targetId"),
                        details = json.getString("details")
                    ),
                    memberUids
                ).map { }
            }
            else -> Result.success(Unit)
        }
    }

    suspend fun seedSampleDataIfEmpty(storeId: String, actorUid: String, actorName: String): Boolean {
        val alreadySeeded = prefs.seededStoreIdFlow.first() == storeId
        val localProducts = dao.getProductsSnapshot()
        if (alreadySeeded || localProducts.isNotEmpty()) return false

        val canCloudSync = connectivity.isOnline.value && remote.isCloudAuthenticated()
        val memberUids = if (canCloudSync) remote.getMemberUids(storeId) else listOf(storeId)
        val samples = listOf(
            Triple(
                ProductEntity(
                    id = "seed_oud_royal",
                    nameAr = "عود ملكي فاخر ١٠٠ مل",
                    nameEn = "Royal Oud Eau de Parfum 100ml",
                    category = "perfume",
                    sku = "PRF-OUD-01",
                    barcode = "629100100101",
                    drawableResName = "img_perfume_oud",
                    costPrice = 140.0,
                    retailPrice = 290.0,
                    wholesalePrice = 225.0,
                    wholesaleMinQty = 5,
                    unitAr = "زجاجة",
                    unitEn = "Bottle",
                    lowStockThreshold = 8
                ),
                42,
                Unit
            ),
            Triple(
                ProductEntity(
                    id = "seed_white_musk",
                    nameAr = "مسك الطهارة الأبيض ٥٠ مل",
                    nameEn = "Pure White Musk Elixir 50ml",
                    category = "perfume",
                    sku = "PRF-MSK-02",
                    barcode = "629100100102",
                    drawableResName = "img_perfume_oud",
                    costPrice = 65.0,
                    retailPrice = 145.0,
                    wholesalePrice = 110.0,
                    wholesaleMinQty = 6,
                    unitAr = "زجاجة",
                    unitEn = "Bottle",
                    lowStockThreshold = 10
                ),
                30,
                Unit
            ),
            Triple(
                ProductEntity(
                    id = "seed_amber_velvet",
                    nameAr = "عطر عنبر مخملي ١٠٠ مل",
                    nameEn = "Velvet Amber Mist 100ml",
                    category = "perfume",
                    sku = "PRF-AMB-03",
                    barcode = "629100100103",
                    drawableResName = "img_perfume_oud",
                    costPrice = 95.0,
                    retailPrice = 210.0,
                    wholesalePrice = 165.0,
                    wholesaleMinQty = 4,
                    unitAr = "زجاجة",
                    unitEn = "Bottle",
                    lowStockThreshold = 6
                ),
                5, // deliberately low stock to trigger alert demo!
                Unit
            ),
            Triple(
                ProductEntity(
                    id = "seed_saffron_cream",
                    nameAr = "كريم الحرير بالزعفران ٥٠ جم",
                    nameEn = "Saffron Silk Face Cream 50g",
                    category = "cream",
                    sku = "CRM-SAF-01",
                    barcode = "629100200201",
                    drawableResName = "img_cream_jar",
                    costPrice = 55.0,
                    retailPrice = 130.0,
                    wholesalePrice = 95.0,
                    wholesaleMinQty = 6,
                    unitAr = "عبوة",
                    unitEn = "Jar",
                    lowStockThreshold = 8
                ),
                36,
                Unit
            ),
            Triple(
                ProductEntity(
                    id = "seed_argan_cream",
                    nameAr = "كريم ليلي بالأركان واللؤلؤ",
                    nameEn = "Argan Pearl Night Cream 75g",
                    category = "cream",
                    sku = "CRM-ARG-02",
                    barcode = "629100200202",
                    drawableResName = "img_cream_jar",
                    costPrice = 70.0,
                    retailPrice = 160.0,
                    wholesalePrice = 120.0,
                    wholesaleMinQty = 5,
                    unitAr = "عبوة",
                    unitEn = "Jar",
                    lowStockThreshold = 8
                ),
                4, // low stock alert!
                Unit
            ),
            Triple(
                ProductEntity(
                    id = "seed_shea_oud_butter",
                    nameAr = "زبدة الشيا بلمسة العود ٢٠٠ جم",
                    nameEn = "Oud Shea Body Butter 200g",
                    category = "cream",
                    sku = "CRM-SHB-03",
                    barcode = "629100200203",
                    drawableResName = "img_cream_jar",
                    costPrice = 42.0,
                    retailPrice = 98.0,
                    wholesalePrice = 74.0,
                    wholesaleMinQty = 8,
                    unitAr = "عبوة",
                    unitEn = "Jar",
                    lowStockThreshold = 10
                ),
                50,
                Unit
            )
        )

        val defaultCustomers = listOf(
            PartyEntity(id = "cust_luxury_salon", partyType = "customer", name = "صالون لمسة حرير / Silk Touch Salon", phone = "0501234567", balance = 450.0),
            PartyEntity(id = "cust_almas_gallery", partyType = "customer", name = "معرض الماس للعطور / Almas Fragrances", phone = "0559876543", balance = 0.0)
        )
        val defaultSuppliers = listOf(
            PartyEntity(id = "sup_oriental_oils", partyType = "supplier", name = "مصنع دهن العود الشرقي / Oriental Oils Co.", phone = "0542223344", balance = 1200.0),
            PartyEntity(id = "sup_botanical_labs", partyType = "supplier", name = "مختبرات الأعشاب الطبيعية / Botanical Labs", phone = "0561112233", balance = 0.0)
        )

        dao.upsertProducts(samples.map { it.first })
        dao.upsertParties(defaultCustomers + defaultSuppliers)
        if (dao.getSettingsOnce() == null) {
            dao.upsertSettings(StoreSettingsEntity())
        }

        val movements = samples.map { (p, qty, _) ->
            StockMovementEntity(
                id = "mov_seed_${p.id}",
                productId = p.id,
                type = "opening",
                qtyDelta = qty,
                unitCost = p.costPrice,
                reason = "Initial catalog seed",
                actorUid = actorUid,
                actorName = actorName,
                syncStatus = if (canCloudSync) "SYNCED" else "PENDING"
            )
        }
        dao.insertMovements(movements)

        if (canCloudSync) {
            samples.forEach { (p, _, _) ->
                remote.upsertProduct(
                    storeId,
                    com.example.data.remote.FsProduct(
                        id = p.id,
                        nameAr = p.nameAr,
                        nameEn = p.nameEn,
                        category = p.category,
                        sku = p.sku,
                        barcode = p.barcode,
                        costPrice = p.costPrice,
                        retailPrice = p.retailPrice,
                        wholesalePrice = p.wholesalePrice,
                        wholesaleMinQty = p.wholesaleMinQty,
                        unitAr = p.unitAr,
                        unitEn = p.unitEn,
                        lowStockThreshold = p.lowStockThreshold,
                        isDeleted = false
                    ),
                    memberUids
                )
            }
            movements.forEach { m ->
                remote.createMovement(
                    storeId,
                    FsStockMovement(
                        id = m.id,
                        productId = m.productId,
                        type = m.type,
                        qtyDelta = m.qtyDelta,
                        unitCost = m.unitCost,
                        reason = m.reason,
                        actorUid = actorUid,
                        actorName = actorName
                    ),
                    memberUids
                )
            }
            defaultCustomers.forEach { c ->
                remote.upsertParty(storeId, "customers", FsParty(id = c.id, name = c.name, phone = c.phone, balance = c.balance), memberUids)
            }
            defaultSuppliers.forEach { s ->
                remote.upsertParty(storeId, "suppliers", FsParty(id = s.id, name = s.name, phone = s.phone, balance = s.balance), memberUids)
            }
        }
        prefs.markStoreSeeded(storeId)
        return true
    }

    private fun defaultDrawableForSku(sku: String, category: String): String {
        return when {
            category == "cream" || sku.startsWith("CRM") -> "img_cream_jar"
            category == "perfume" || sku.startsWith("PRF") -> "img_perfume_oud"
            else -> "img_perfume_oud"
        }
    }
}
