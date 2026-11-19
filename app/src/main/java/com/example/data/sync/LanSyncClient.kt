package com.example.data.sync

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class PcServerInfo(
    val storeNameEn: String,
    val storeNameAr: String,
    val pcVersion: String,
    val productsCount: Int,
    val invoicesCount: Int
)

data class LanSyncSummary(
    val productsSynced: Int,
    val movementsSynced: Int,
    val invoicesSynced: Int,
    val serverTimeMillis: Long
)

object LanSyncClient {

    fun normalizeServerUrl(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isBlank()) return ""
        val withScheme = if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) trimmed else "http://$trimmed"
        return withScheme
    }

    suspend fun pingPc(rawUrl: String): Result<PcServerInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val baseUrl = normalizeServerUrl(rawUrl)
            require(baseUrl.isNotBlank()) { "PC server address is required" }
            val conn = (URL("$baseUrl/api/health").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 4000
            }
            try {
                val code = conn.responseCode
                if (code !in 200..299) {
                    throw IllegalStateException("HTTP $code from PC server")
                }
                val text = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
                val json = JSONObject(text)
                PcServerInfo(
                    storeNameEn = json.optString("storeNameEn", "Aroma Boutique"),
                    storeNameAr = json.optString("storeNameAr", "دار العطور والمستحضرات"),
                    pcVersion = json.optString("version", "1.0.0"),
                    productsCount = json.optInt("productsCount", 0),
                    invoicesCount = json.optInt("invoicesCount", 0)
                )
            } finally {
                conn.disconnect()
            }
        }
    }

    suspend fun syncWithPc(
        rawUrl: String,
        dao: StoreDao,
        deviceName: String = "Android POS"
    ): Result<LanSyncSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val baseUrl = normalizeServerUrl(rawUrl)
            require(baseUrl.isNotBlank()) { "PC server address is required" }

            val payload = buildSnapshotPayload(dao, deviceName)
            val conn = (URL("$baseUrl/api/sync").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 6000
                readTimeout = 10000
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }
            try {
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(payload.toString())
                    writer.flush()
                }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val respText = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
                if (code !in 200..299) {
                    throw IllegalStateException("PC sync HTTP $code: $respText")
                }
                val respJson = JSONObject(respText)
                applyMergedSnapshot(dao, respJson)
            } finally {
                conn.disconnect()
            }
        }
    }

    private suspend fun buildSnapshotPayload(dao: StoreDao, deviceName: String): JSONObject {
        val products = dao.getAllProductsSnapshot()
        val movements = dao.getMovementsSnapshot()
        val invoices = dao.getInvoicesSnapshot()
        val parties = dao.getPartiesSnapshot()
        val days = dao.getBusinessDaysSnapshot()
        val holidays = dao.getHolidaysSnapshot()
        val logs = dao.getAuditLogsSnapshot()
        val members = dao.getMembersSnapshot()
        val settings = dao.getSettingsOnce()
        val releases = dao.getReleasesSnapshot()

        return JSONObject().apply {
            put("clientDevice", deviceName)
            put("sentAtMillis", System.currentTimeMillis())
            put("products", JSONArray().apply {
                products.forEach { p ->
                    put(JSONObject().apply {
                        put("id", p.id)
                        put("nameAr", p.nameAr)
                        put("nameEn", p.nameEn)
                        put("category", p.category)
                        put("sku", p.sku)
                        put("barcode", p.barcode)
                        put("imageUri", p.imageUri)
                        put("drawableResName", p.drawableResName)
                        put("costPrice", p.costPrice)
                        put("retailPrice", p.retailPrice)
                        put("wholesalePrice", p.wholesalePrice)
                        put("wholesaleMinQty", p.wholesaleMinQty)
                        put("unitAr", p.unitAr)
                        put("unitEn", p.unitEn)
                        put("lowStockThreshold", p.lowStockThreshold)
                        put("isDeleted", p.isDeleted)
                        put("createdAtMillis", p.createdAtMillis)
                        put("updatedAtMillis", p.updatedAtMillis)
                    })
                }
            })
            put("movements", JSONArray().apply {
                movements.forEach { m ->
                    put(JSONObject().apply {
                        put("id", m.id)
                        put("productId", m.productId)
                        put("type", m.type)
                        put("qtyDelta", m.qtyDelta)
                        put("unitCost", m.unitCost)
                        put("reason", m.reason)
                        put("refId", m.refId)
                        put("actorUid", m.actorUid)
                        put("actorName", m.actorName)
                        put("createdAtMillis", m.createdAtMillis)
                    })
                }
            })
            put("invoices", JSONArray().apply {
                invoices.forEach { inv ->
                    put(JSONObject().apply {
                        put("id", inv.id)
                        put("invoiceNumber", inv.invoiceNumber)
                        put("saleType", inv.saleType)
                        put("customerId", inv.customerId)
                        put("customerName", inv.customerName)
                        put("itemsJson", inv.itemsJson)
                        put("itemsSummary", inv.itemsSummary)
                        put("subtotal", inv.subtotal)
                        put("discount", inv.discount)
                        put("total", inv.total)
                        put("costTotal", inv.costTotal)
                        put("paymentMethod", inv.paymentMethod)
                        put("status", inv.status)
                        put("businessDayId", inv.businessDayId)
                        put("cashierUid", inv.cashierUid)
                        put("cashierName", inv.cashierName)
                        put("createdAtMillis", inv.createdAtMillis)
                        put("updatedAtMillis", inv.updatedAtMillis)
                    })
                }
            })
            put("parties", JSONArray().apply {
                parties.forEach { pt ->
                    put(JSONObject().apply {
                        put("id", pt.id)
                        put("partyType", pt.partyType)
                        put("name", pt.name)
                        put("phone", pt.phone)
                        put("balance", pt.balance)
                        put("createdAtMillis", pt.createdAtMillis)
                        put("updatedAtMillis", pt.updatedAtMillis)
                    })
                }
            })
            put("businessDays", JSONArray().apply {
                days.forEach { d ->
                    put(JSONObject().apply {
                        put("id", d.id)
                        put("dateStr", d.dateStr)
                        put("status", d.status)
                        put("openedByUid", d.openedByUid)
                        put("openedByName", d.openedByName)
                        put("closedByUid", d.closedByUid)
                        put("closedByName", d.closedByName)
                        put("openingCash", d.openingCash)
                        put("totalSales", d.totalSales)
                        put("cashSales", d.cashSales)
                        put("transferSales", d.transferSales)
                        put("creditSales", d.creditSales)
                        put("itemsSoldCount", d.itemsSoldCount)
                        put("holidayOverride", d.holidayOverride)
                        put("createdAtMillis", d.createdAtMillis)
                        put("updatedAtMillis", d.updatedAtMillis)
                    })
                }
            })
            put("holidays", JSONArray().apply {
                holidays.forEach { h ->
                    put(JSONObject().apply {
                        put("id", h.id)
                        put("dateStr", h.dateStr)
                        put("titleAr", h.titleAr)
                        put("titleEn", h.titleEn)
                        put("isWeeklyOffDay", h.isWeeklyOffDay)
                        put("dayOfWeek", h.dayOfWeek)
                        put("createdAtMillis", h.createdAtMillis)
                        put("updatedAtMillis", h.updatedAtMillis)
                    })
                }
            })
            put("auditLogs", JSONArray().apply {
                logs.forEach { l ->
                    put(JSONObject().apply {
                        put("id", l.id)
                        put("actorUid", l.actorUid)
                        put("actorName", l.actorName)
                        put("actorRole", l.actorRole)
                        put("action", l.action)
                        put("targetType", l.targetType)
                        put("targetId", l.targetId)
                        put("details", l.details)
                        put("createdAtMillis", l.createdAtMillis)
                    })
                }
            })
            put("members", JSONArray().apply {
                members.forEach { m ->
                    put(JSONObject().apply {
                        put("uid", m.uid)
                        put("username", m.username)
                        put("passwordHash", m.passwordHash)
                        put("displayName", m.displayName)
                        put("role", m.role)
                        put("active", m.active)
                        put("createdAtMillis", m.createdAtMillis)
                        put("updatedAtMillis", m.updatedAtMillis)
                    })
                }
            })
            if (settings != null) {
                put("settings", JSONObject().apply {
                    put("id", settings.id)
                    put("storeNameAr", settings.storeNameAr)
                    put("storeNameEn", settings.storeNameEn)
                    put("primaryColorHex", settings.primaryColorHex)
                    put("secondaryColorHex", settings.secondaryColorHex)
                    put("logoUri", settings.logoUri)
                    put("sessionTimeoutMinutes", settings.sessionTimeoutMinutes)
                    put("notifyLowStock", settings.notifyLowStock)
                    put("notifyDayNotClosed", settings.notifyDayNotClosed)
                    put("notifyNewUser", settings.notifyNewUser)
                    put("notifySyncErrors", settings.notifySyncErrors)
                    put("notifyNewVersion", settings.notifyNewVersion)
                    put("notifyRolesCsv", settings.notifyRolesCsv)
                    put("updatedAtMillis", settings.updatedAtMillis)
                })
            }
            put("releases", JSONArray().apply {
                releases.forEach { r ->
                    put(JSONObject().apply {
                        put("id", r.id)
                        put("platform", r.platform)
                        put("versionCode", r.versionCode)
                        put("versionName", r.versionName)
                        put("downloadUrl", r.downloadUrl)
                        put("releaseNotesAr", r.releaseNotesAr)
                        put("releaseNotesEn", r.releaseNotesEn)
                        put("isMandatory", r.isMandatory)
                        put("createdAtMillis", r.createdAtMillis)
                    })
                }
            })
        }
    }

    private suspend fun applyMergedSnapshot(dao: StoreDao, resp: JSONObject): LanSyncSummary {
        val localProductsMap = dao.getAllProductsSnapshot().associateBy { it.id }
        val productsArr = resp.optJSONArray("products") ?: JSONArray()
        val productsToUpsert = mutableListOf<ProductEntity>()
        for (i in 0 until productsArr.length()) {
            val o = productsArr.getJSONObject(i)
            val id = o.getString("id")
            val remoteUpdated = o.optLong("updatedAtMillis", 0L)
            val local = localProductsMap[id]
            if (local == null || remoteUpdated >= local.updatedAtMillis) {
                productsToUpsert.add(
                    ProductEntity(
                        id = id,
                        nameAr = o.optString("nameAr"),
                        nameEn = o.optString("nameEn"),
                        category = o.optString("category", "perfume"),
                        sku = o.optString("sku"),
                        barcode = o.optString("barcode"),
                        imageUri = o.optString("imageUri"),
                        drawableResName = o.optString("drawableResName").ifBlank { local?.drawableResName ?: "" },
                        costPrice = o.optDouble("costPrice", 0.0),
                        retailPrice = o.optDouble("retailPrice", 0.0),
                        wholesalePrice = o.optDouble("wholesalePrice", 0.0),
                        wholesaleMinQty = o.optInt("wholesaleMinQty", 1),
                        unitAr = o.optString("unitAr", "قطعة"),
                        unitEn = o.optString("unitEn", "Unit"),
                        lowStockThreshold = o.optInt("lowStockThreshold", 5),
                        isDeleted = o.optBoolean("isDeleted", false),
                        createdAtMillis = o.optLong("createdAtMillis", System.currentTimeMillis()),
                        updatedAtMillis = remoteUpdated
                    )
                )
            }
        }
        if (productsToUpsert.isNotEmpty()) dao.upsertProducts(productsToUpsert)

        val movArr = resp.optJSONArray("movements") ?: JSONArray()
        val movementsToInsert = mutableListOf<StockMovementEntity>()
        for (i in 0 until movArr.length()) {
            val o = movArr.getJSONObject(i)
            movementsToInsert.add(
                StockMovementEntity(
                    id = o.getString("id"),
                    productId = o.getString("productId"),
                    type = o.getString("type"),
                    qtyDelta = o.getInt("qtyDelta"),
                    unitCost = o.optDouble("unitCost", 0.0),
                    reason = o.optString("reason"),
                    refId = o.optString("refId"),
                    actorUid = o.optString("actorUid"),
                    actorName = o.optString("actorName"),
                    createdAtMillis = o.optLong("createdAtMillis", System.currentTimeMillis()),
                    syncStatus = "SYNCED"
                )
            )
        }
        if (movementsToInsert.isNotEmpty()) dao.insertMovements(movementsToInsert)

        val localInvMap = dao.getInvoicesSnapshot().associateBy { it.id }
        val invArr = resp.optJSONArray("invoices") ?: JSONArray()
        val invToUpsert = mutableListOf<InvoiceEntity>()
        for (i in 0 until invArr.length()) {
            val o = invArr.getJSONObject(i)
            val id = o.getString("id")
            val remoteUpdated = o.optLong("updatedAtMillis", 0L)
            val local = localInvMap[id]
            if (local == null || remoteUpdated >= local.updatedAtMillis) {
                invToUpsert.add(
                    InvoiceEntity(
                        id = id,
                        invoiceNumber = o.optString("invoiceNumber"),
                        saleType = o.optString("saleType", "retail"),
                        customerId = o.optString("customerId"),
                        customerName = o.optString("customerName"),
                        itemsJson = o.optString("itemsJson", "[]"),
                        itemsSummary = o.optString("itemsSummary"),
                        subtotal = o.optDouble("subtotal", 0.0),
                        discount = o.optDouble("discount", 0.0),
                        total = o.optDouble("total", 0.0),
                        costTotal = o.optDouble("costTotal", 0.0),
                        paymentMethod = o.optString("paymentMethod", "cash"),
                        status = o.optString("status", "completed"),
                        businessDayId = o.optString("businessDayId"),
                        cashierUid = o.optString("cashierUid"),
                        cashierName = o.optString("cashierName"),
                        createdAtMillis = o.optLong("createdAtMillis", System.currentTimeMillis()),
                        updatedAtMillis = remoteUpdated,
                        syncStatus = "SYNCED"
                    )
                )
            }
        }
        if (invToUpsert.isNotEmpty()) dao.upsertInvoices(invToUpsert)

        val localPartyMap = dao.getPartiesSnapshot().associateBy { it.id }
        val partyArr = resp.optJSONArray("parties") ?: JSONArray()
        val partiesToUpsert = mutableListOf<PartyEntity>()
        for (i in 0 until partyArr.length()) {
            val o = partyArr.getJSONObject(i)
            val id = o.getString("id")
            val remoteUpdated = o.optLong("updatedAtMillis", 0L)
            val local = localPartyMap[id]
            if (local == null || remoteUpdated >= local.updatedAtMillis) {
                partiesToUpsert.add(
                    PartyEntity(
                        id = id,
                        partyType = o.optString("partyType", "customer"),
                        name = o.optString("name"),
                        phone = o.optString("phone"),
                        balance = o.optDouble("balance", 0.0),
                        createdAtMillis = o.optLong("createdAtMillis", System.currentTimeMillis()),
                        updatedAtMillis = remoteUpdated,
                        syncStatus = "SYNCED"
                    )
                )
            }
        }
        if (partiesToUpsert.isNotEmpty()) dao.upsertParties(partiesToUpsert)

        val localDayMap = dao.getBusinessDaysSnapshot().associateBy { it.id }
        val dayArr = resp.optJSONArray("businessDays") ?: JSONArray()
        val daysToUpsert = mutableListOf<BusinessDayEntity>()
        for (i in 0 until dayArr.length()) {
            val o = dayArr.getJSONObject(i)
            val id = o.getString("id")
            val remoteUpdated = o.optLong("updatedAtMillis", 0L)
            val local = localDayMap[id]
            if (local == null || remoteUpdated >= local.updatedAtMillis) {
                daysToUpsert.add(
                    BusinessDayEntity(
                        id = id,
                        dateStr = o.optString("dateStr"),
                        status = o.optString("status", "closed"),
                        openedByUid = o.optString("openedByUid"),
                        openedByName = o.optString("openedByName"),
                        closedByUid = o.optString("closedByUid"),
                        closedByName = o.optString("closedByName"),
                        openingCash = o.optDouble("openingCash", 0.0),
                        totalSales = o.optDouble("totalSales", 0.0),
                        cashSales = o.optDouble("cashSales", 0.0),
                        transferSales = o.optDouble("transferSales", 0.0),
                        creditSales = o.optDouble("creditSales", 0.0),
                        itemsSoldCount = o.optInt("itemsSoldCount", 0),
                        holidayOverride = o.optBoolean("holidayOverride", false),
                        createdAtMillis = o.optLong("createdAtMillis", System.currentTimeMillis()),
                        updatedAtMillis = remoteUpdated,
                        syncStatus = "SYNCED"
                    )
                )
            }
        }
        if (daysToUpsert.isNotEmpty()) dao.upsertBusinessDays(daysToUpsert)

        val holArr = resp.optJSONArray("holidays") ?: JSONArray()
        val holidaysToUpsert = mutableListOf<HolidayEntity>()
        for (i in 0 until holArr.length()) {
            val o = holArr.getJSONObject(i)
            holidaysToUpsert.add(
                HolidayEntity(
                    id = o.getString("id"),
                    dateStr = o.optString("dateStr"),
                    titleAr = o.optString("titleAr"),
                    titleEn = o.optString("titleEn"),
                    isWeeklyOffDay = o.optBoolean("isWeeklyOffDay", false),
                    dayOfWeek = o.optInt("dayOfWeek", 0),
                    createdAtMillis = o.optLong("createdAtMillis", System.currentTimeMillis()),
                    updatedAtMillis = o.optLong("updatedAtMillis", System.currentTimeMillis())
                )
            )
        }
        if (holidaysToUpsert.isNotEmpty()) dao.upsertHolidays(holidaysToUpsert)

        val logArr = resp.optJSONArray("auditLogs") ?: JSONArray()
        val logsToInsert = mutableListOf<AuditLogEntity>()
        for (i in 0 until logArr.length()) {
            val o = logArr.getJSONObject(i)
            logsToInsert.add(
                AuditLogEntity(
                    id = o.getString("id"),
                    actorUid = o.optString("actorUid"),
                    actorName = o.optString("actorName"),
                    actorRole = o.optString("actorRole"),
                    action = o.optString("action"),
                    targetType = o.optString("targetType"),
                    targetId = o.optString("targetId"),
                    details = o.optString("details"),
                    createdAtMillis = o.optLong("createdAtMillis", System.currentTimeMillis())
                )
            )
        }
        if (logsToInsert.isNotEmpty()) dao.insertAuditLogs(logsToInsert)

        val localMembersMap = dao.getMembersSnapshot().associateBy { it.uid }
        val memArr = resp.optJSONArray("members") ?: JSONArray()
        val membersToUpsert = mutableListOf<StoreMemberEntity>()
        for (i in 0 until memArr.length()) {
            val o = memArr.getJSONObject(i)
            val uid = o.getString("uid")
            val remoteUpdated = o.optLong("updatedAtMillis", 0L)
            val local = localMembersMap[uid]
            if (local == null || remoteUpdated >= local.updatedAtMillis) {
                membersToUpsert.add(
                    StoreMemberEntity(
                        uid = uid,
                        username = o.optString("username").ifBlank { local?.username ?: "" },
                        passwordHash = o.optString("passwordHash").ifBlank { local?.passwordHash ?: "" },
                        displayName = o.optString("displayName"),
                        role = o.optString("role", "seller"),
                        active = o.optBoolean("active", true),
                        createdAtMillis = o.optLong("createdAtMillis", System.currentTimeMillis()),
                        updatedAtMillis = remoteUpdated
                    )
                )
            }
        }
        if (membersToUpsert.isNotEmpty()) dao.upsertMembers(membersToUpsert)

        val settingsObj = resp.optJSONObject("settings")
        if (settingsObj != null) {
            val localSettings = dao.getSettingsOnce()
            val remoteUpdated = settingsObj.optLong("updatedAtMillis", 0L)
            if (localSettings == null || remoteUpdated >= localSettings.updatedAtMillis) {
                dao.upsertSettings(
                    StoreSettingsEntity(
                        id = "main",
                        storeNameAr = settingsObj.optString("storeNameAr", "دار العطور والمستحضرات"),
                        storeNameEn = settingsObj.optString("storeNameEn", "Aroma Boutique"),
                        primaryColorHex = settingsObj.optString("primaryColorHex", "#1B4D3E"),
                        secondaryColorHex = settingsObj.optString("secondaryColorHex", "#C59B27"),
                        logoUri = settingsObj.optString("logoUri"),
                        sessionTimeoutMinutes = settingsObj.optInt("sessionTimeoutMinutes", 30),
                        notifyLowStock = settingsObj.optBoolean("notifyLowStock", true),
                        notifyDayNotClosed = settingsObj.optBoolean("notifyDayNotClosed", true),
                        notifyNewUser = settingsObj.optBoolean("notifyNewUser", true),
                        notifySyncErrors = settingsObj.optBoolean("notifySyncErrors", true),
                        notifyNewVersion = settingsObj.optBoolean("notifyNewVersion", true),
                        notifyRolesCsv = settingsObj.optString("notifyRolesCsv", "admin,manager"),
                        updatedAtMillis = remoteUpdated
                    )
                )
            }
        }

        val relArr = resp.optJSONArray("releases") ?: JSONArray()
        val releasesToUpsert = mutableListOf<AppReleaseEntity>()
        for (i in 0 until relArr.length()) {
            val o = relArr.getJSONObject(i)
            releasesToUpsert.add(
                AppReleaseEntity(
                    id = o.getString("id"),
                    platform = o.optString("platform", "android"),
                    versionCode = o.optInt("versionCode", 1),
                    versionName = o.optString("versionName", "1.0.0"),
                    downloadUrl = o.optString("downloadUrl"),
                    releaseNotesAr = o.optString("releaseNotesAr"),
                    releaseNotesEn = o.optString("releaseNotesEn"),
                    isMandatory = o.optBoolean("isMandatory", false),
                    createdAtMillis = o.optLong("createdAtMillis", System.currentTimeMillis())
                )
            )
        }
        if (releasesToUpsert.isNotEmpty()) dao.upsertReleases(releasesToUpsert)

        return LanSyncSummary(
            productsSynced = productsToUpsert.size,
            movementsSynced = movementsToInsert.size,
            invoicesSynced = invToUpsert.size,
            serverTimeMillis = resp.optLong("serverTimeMillis", System.currentTimeMillis())
        )
    }
}
