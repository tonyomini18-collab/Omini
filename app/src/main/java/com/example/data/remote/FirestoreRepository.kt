package com.example.data.remote

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FirestoreRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    fun isCloudAuthenticated(): Boolean = auth.currentUser != null

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in before accessing Firestore.")
    }

    private fun storeDoc(storeId: String) = db.collection("stores").document(storeId)

    suspend fun ensureStoreAndProfileInitialized(
        displayName: String,
        email: String?,
        preferredLanguage: String = "ar"
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val userRef = db.collection("users").document(uid)
        val userSnap = userRef.get().await()
        val existingStoreId = userSnap.getString("activeStoreId")
        val storeId = if (!existingStoreId.isNullOrBlank()) existingStoreId else uid

        if (!userSnap.exists()) {
            val profileMap = mutableMapOf<String, Any>(
                "userId" to uid,
                "displayName" to displayName.ifBlank { "Store Owner" },
                "activeStoreId" to storeId,
                "preferredLanguage" to preferredLanguage,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!email.isNullOrBlank()) profileMap["email"] = email
            userRef.set(profileMap).await()
        }

        val sRef = storeDoc(storeId)
        val sSnap = sRef.get().await()
        if (!sSnap.exists() && storeId == uid) {
            val members = listOf(uid)
            sRef.set(
                mapOf(
                    "ownerUid" to uid,
                    "nameAr" to "دار العطور والمستحضرات",
                    "nameEn" to "Aroma Boutique",
                    "memberUids" to members,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            sRef.collection("members").document(uid).set(
                mapOf(
                    "uid" to uid,
                    "displayName" to displayName.ifBlank { "Owner Admin" },
                    "role" to "admin",
                    "active" to true,
                    "memberUids" to members,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            sRef.collection("settings").document("main").set(
                mapOf(
                    "storeNameAr" to "دار العطور والمستحضرات",
                    "storeNameEn" to "Aroma Boutique",
                    "primaryColorHex" to "#1B4D3E",
                    "secondaryColorHex" to "#C59B27",
                    "sessionTimeoutMinutes" to 30,
                    "notifyLowStock" to true,
                    "notifyDayNotClosed" to true,
                    "notifyNewUser" to true,
                    "notifySyncErrors" to true,
                    "notifyNewVersion" to true,
                    "notifyRoles" to listOf("admin", "manager"),
                    "memberUids" to members,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
        storeId
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "users/${auth.currentUser?.uid}")
    }

    suspend fun getMemberUids(storeId: String): List<String> {
        val uid = requireUserId()
        return try {
            val snap = storeDoc(storeId).get().await()
            val raw = snap.get("memberUids") as? List<*>
            val list = raw?.mapNotNull { it as? String }?.toMutableList() ?: mutableListOf()
            if (storeId !in list) list.add(0, storeId)
            if (uid !in list) list.add(uid)
            list.distinct()
        } catch (e: Exception) {
            listOf(storeId, uid).distinct()
        }
    }

    // Products
    fun observeProducts(storeId: String): Flow<List<FsProduct>> {
        val uid = requireUserId()
        val path = "stores/$storeId/products"
        return storeDoc(storeId).collection("products")
            .whereArrayContains("memberUids", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(FsProduct::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                logFlowError(error, OperationType.LIST, path, uid)
                throw error
            }
    }

    suspend fun upsertProduct(storeId: String, product: FsProduct, memberUids: List<String>): Result<String> = runCatching {
        requireUserId()
        val id = product.id.ifBlank { storeDoc(storeId).collection("products").document().id }
        val docRef = storeDoc(storeId).collection("products").document(id)
        val existing = docRef.get().await()
        val payload = buildMap<String, Any> {
            put("nameAr", product.nameAr)
            put("nameEn", product.nameEn)
            put("category", product.category)
            put("sku", product.sku)
            product.barcode?.takeIf { it.isNotBlank() }?.let { put("barcode", it) }
            product.imageUri?.takeIf { it.isNotBlank() }?.let { put("imageUri", it) }
            put("costPrice", product.costPrice)
            put("retailPrice", product.retailPrice)
            put("wholesalePrice", product.wholesalePrice)
            put("wholesaleMinQty", product.wholesaleMinQty)
            put("unitAr", product.unitAr)
            put("unitEn", product.unitEn)
            put("lowStockThreshold", product.lowStockThreshold)
            put("isDeleted", product.isDeleted)
            put("memberUids", memberUids)
            put("createdAt", existing.getTimestamp("createdAt") ?: FieldValue.serverTimestamp())
            put("updatedAt", FieldValue.serverTimestamp())
        }
        docRef.set(payload, SetOptions.merge()).await()
        id
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "stores/$storeId/products/${product.id}")
    }

    suspend fun deleteProduct(storeId: String, productId: String): Result<Unit> = runCatching {
        requireUserId()
        storeDoc(storeId).collection("products").document(productId).delete().await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.DELETE, "stores/$storeId/products/$productId")
    }

    // Movements (append-only)
    fun observeMovements(storeId: String): Flow<List<FsStockMovement>> {
        val uid = requireUserId()
        val path = "stores/$storeId/movements"
        return storeDoc(storeId).collection("movements")
            .whereArrayContains("memberUids", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(FsStockMovement::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                logFlowError(error, OperationType.LIST, path, uid)
                throw error
            }
    }

    suspend fun createMovement(storeId: String, movement: FsStockMovement, memberUids: List<String>): Result<String> = runCatching {
        val uid = requireUserId()
        val id = movement.id.ifBlank { storeDoc(storeId).collection("movements").document().id }
        val docRef = storeDoc(storeId).collection("movements").document(id)
        val payload = buildMap<String, Any> {
            put("productId", movement.productId)
            put("type", movement.type)
            put("qtyDelta", movement.qtyDelta)
            put("unitCost", movement.unitCost)
            put("reason", movement.reason)
            movement.refId?.takeIf { it.isNotBlank() }?.let { put("refId", it) }
            put("actorUid", uid)
            put("actorName", movement.actorName.ifBlank { "Staff" })
            put("memberUids", memberUids)
            put("createdAt", FieldValue.serverTimestamp())
        }
        docRef.set(payload).await()
        id
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "stores/$storeId/movements/${movement.id}")
    }

    // Invoices
    fun observeInvoices(storeId: String): Flow<List<FsInvoice>> {
        val uid = requireUserId()
        val path = "stores/$storeId/invoices"
        return storeDoc(storeId).collection("invoices")
            .whereArrayContains("memberUids", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(FsInvoice::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                logFlowError(error, OperationType.LIST, path, uid)
                throw error
            }
    }

    suspend fun upsertInvoice(storeId: String, invoice: FsInvoice, memberUids: List<String>): Result<String> = runCatching {
        val uid = requireUserId()
        val id = invoice.id.ifBlank { storeDoc(storeId).collection("invoices").document().id }
        val docRef = storeDoc(storeId).collection("invoices").document(id)
        val existing = docRef.get().await()
        if (existing.exists()) {
            docRef.update(
                mapOf(
                    "status" to invoice.status,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } else {
            val payload = buildMap<String, Any> {
                put("invoiceNumber", invoice.invoiceNumber)
                put("saleType", invoice.saleType)
                invoice.customerId?.takeIf { it.isNotBlank() }?.let { put("customerId", it) }
                invoice.customerName?.takeIf { it.isNotBlank() }?.let { put("customerName", it) }
                put("itemsSummary", invoice.itemsSummary)
                put("subtotal", invoice.subtotal)
                put("discount", invoice.discount)
                put("total", invoice.total)
                put("costTotal", invoice.costTotal)
                put("paymentMethod", invoice.paymentMethod)
                put("status", invoice.status)
                put("businessDayId", invoice.businessDayId)
                put("cashierUid", uid)
                put("cashierName", invoice.cashierName.ifBlank { "Cashier" })
                put("memberUids", memberUids)
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }
            docRef.set(payload).await()
        }
        id
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "stores/$storeId/invoices/${invoice.id}")
    }

    // Customers & Suppliers
    fun observeParties(storeId: String, collection: String): Flow<List<FsParty>> {
        val uid = requireUserId()
        val path = "stores/$storeId/$collection"
        return storeDoc(storeId).collection(collection)
            .whereArrayContains("memberUids", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(FsParty::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                logFlowError(error, OperationType.LIST, path, uid)
                throw error
            }
    }

    suspend fun upsertParty(storeId: String, collection: String, party: FsParty, memberUids: List<String>): Result<String> = runCatching {
        requireUserId()
        val id = party.id.ifBlank { storeDoc(storeId).collection(collection).document().id }
        val docRef = storeDoc(storeId).collection(collection).document(id)
        val existing = docRef.get().await()
        val payload = buildMap<String, Any> {
            put("name", party.name)
            party.phone?.takeIf { it.isNotBlank() }?.let { put("phone", it) }
            put("balance", party.balance)
            put("memberUids", memberUids)
            put("createdAt", existing.getTimestamp("createdAt") ?: FieldValue.serverTimestamp())
            put("updatedAt", FieldValue.serverTimestamp())
        }
        docRef.set(payload, SetOptions.merge()).await()
        id
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "stores/$storeId/$collection/${party.id}")
    }

    // Business Days
    fun observeBusinessDays(storeId: String): Flow<List<FsBusinessDay>> {
        val uid = requireUserId()
        val path = "stores/$storeId/business_days"
        return storeDoc(storeId).collection("business_days")
            .whereArrayContains("memberUids", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(FsBusinessDay::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                logFlowError(error, OperationType.LIST, path, uid)
                throw error
            }
    }

    suspend fun upsertBusinessDay(storeId: String, day: FsBusinessDay, memberUids: List<String>): Result<String> = runCatching {
        val uid = requireUserId()
        val id = day.id.ifBlank { day.dateStr }
        val docRef = storeDoc(storeId).collection("business_days").document(id)
        val existing = docRef.get().await()
        if (existing.exists()) {
            val payload = buildMap<String, Any> {
                put("status", day.status)
                day.closedByUid?.takeIf { it.isNotBlank() }?.let { put("closedByUid", it) }
                day.closedByName?.takeIf { it.isNotBlank() }?.let { put("closedByName", it) }
                put("totalSales", day.totalSales)
                put("cashSales", day.cashSales)
                put("transferSales", day.transferSales)
                put("creditSales", day.creditSales)
                put("itemsSoldCount", day.itemsSoldCount)
                put("memberUids", memberUids)
                put("updatedAt", FieldValue.serverTimestamp())
            }
            docRef.update(payload).await()
        } else {
            val payload = buildMap<String, Any> {
                put("dateStr", day.dateStr)
                put("status", day.status)
                put("openedByUid", uid)
                put("openedByName", day.openedByName.ifBlank { "Staff" })
                day.closedByUid?.takeIf { it.isNotBlank() }?.let { put("closedByUid", it) }
                day.closedByName?.takeIf { it.isNotBlank() }?.let { put("closedByName", it) }
                put("openingCash", day.openingCash)
                put("totalSales", day.totalSales)
                put("cashSales", day.cashSales)
                put("transferSales", day.transferSales)
                put("creditSales", day.creditSales)
                put("itemsSoldCount", day.itemsSoldCount)
                put("holidayOverride", day.holidayOverride)
                put("memberUids", memberUids)
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }
            docRef.set(payload).await()
        }
        id
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "stores/$storeId/business_days/${day.id}")
    }

    // Holidays
    fun observeHolidays(storeId: String): Flow<List<FsHoliday>> {
        val uid = requireUserId()
        val path = "stores/$storeId/holidays"
        return storeDoc(storeId).collection("holidays")
            .whereArrayContains("memberUids", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(FsHoliday::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                logFlowError(error, OperationType.LIST, path, uid)
                throw error
            }
    }

    suspend fun upsertHoliday(storeId: String, holiday: FsHoliday, memberUids: List<String>): Result<String> = runCatching {
        requireUserId()
        val id = holiday.id.ifBlank { storeDoc(storeId).collection("holidays").document().id }
        val docRef = storeDoc(storeId).collection("holidays").document(id)
        val existing = docRef.get().await()
        val payload = buildMap<String, Any> {
            holiday.dateStr?.takeIf { it.isNotBlank() }?.let { put("dateStr", it) }
            put("titleAr", holiday.titleAr)
            put("titleEn", holiday.titleEn)
            put("isWeeklyOffDay", holiday.isWeeklyOffDay)
            put("dayOfWeek", holiday.dayOfWeek)
            put("memberUids", memberUids)
            put("createdAt", existing.getTimestamp("createdAt") ?: FieldValue.serverTimestamp())
            put("updatedAt", FieldValue.serverTimestamp())
        }
        docRef.set(payload, SetOptions.merge()).await()
        id
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "stores/$storeId/holidays/${holiday.id}")
    }

    // Audit Logs
    fun observeAuditLogs(storeId: String): Flow<List<FsAuditLog>> {
        val uid = requireUserId()
        val path = "stores/$storeId/audit_logs"
        return storeDoc(storeId).collection("audit_logs")
            .whereArrayContains("memberUids", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(FsAuditLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                logFlowError(error, OperationType.LIST, path, uid)
                throw error
            }
    }

    suspend fun createAuditLog(storeId: String, log: FsAuditLog, memberUids: List<String>): Result<String> = runCatching {
        val uid = requireUserId()
        val id = log.id.ifBlank { storeDoc(storeId).collection("audit_logs").document().id }
        val docRef = storeDoc(storeId).collection("audit_logs").document(id)
        val payload = mapOf(
            "actorUid" to uid,
            "actorName" to log.actorName.ifBlank { "Staff" },
            "actorRole" to log.actorRole,
            "action" to log.action,
            "targetType" to log.targetType,
            "targetId" to log.targetId,
            "details" to log.details,
            "memberUids" to memberUids,
            "createdAt" to FieldValue.serverTimestamp()
        )
        docRef.set(payload).await()
        id
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "stores/$storeId/audit_logs/${log.id}")
    }

    // Members
    fun observeMembers(storeId: String): Flow<List<FsStoreMember>> {
        val uid = requireUserId()
        val path = "stores/$storeId/members"
        return storeDoc(storeId).collection("members")
            .whereArrayContains("memberUids", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(FsStoreMember::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                logFlowError(error, OperationType.LIST, path, uid)
                throw error
            }
    }

    suspend fun upsertMember(storeId: String, member: FsStoreMember, memberUids: List<String>): Result<Unit> = runCatching {
        requireUserId()
        val updatedMembers = (memberUids + member.uid).distinct()
        val docRef = storeDoc(storeId).collection("members").document(member.uid)
        val existing = docRef.get().await()
        val payload = mapOf(
            "uid" to member.uid,
            "displayName" to member.displayName,
            "role" to member.role,
            "active" to member.active,
            "memberUids" to updatedMembers,
            "createdAt" to (existing.getTimestamp("createdAt") ?: FieldValue.serverTimestamp()),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        docRef.set(payload, SetOptions.merge()).await()
        if (member.uid !in memberUids) {
            storeDoc(storeId).update("memberUids", updatedMembers, "updatedAt", FieldValue.serverTimestamp()).await()
        }
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "stores/$storeId/members/${member.uid}")
    }

    // Settings
    fun observeSettings(storeId: String): Flow<FsStoreSettings?> {
        val uid = requireUserId()
        val path = "stores/$storeId/settings/main"
        return storeDoc(storeId).collection("settings").document("main")
            .snapshots()
            .map { snap ->
                if (snap.exists()) snap.toObject(FsStoreSettings::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                else null
            }
            .catch { error ->
                logFlowError(error, OperationType.GET, path, uid)
                throw error
            }
    }

    suspend fun upsertSettings(storeId: String, settings: FsStoreSettings, memberUids: List<String>): Result<Unit> = runCatching {
        requireUserId()
        val docRef = storeDoc(storeId).collection("settings").document("main")
        val existing = docRef.get().await()
        val payload = buildMap<String, Any> {
            put("storeNameAr", settings.storeNameAr)
            put("storeNameEn", settings.storeNameEn)
            put("primaryColorHex", settings.primaryColorHex)
            put("secondaryColorHex", settings.secondaryColorHex)
            settings.logoUri?.takeIf { it.isNotBlank() }?.let { put("logoUri", it) }
            put("sessionTimeoutMinutes", settings.sessionTimeoutMinutes)
            put("notifyLowStock", settings.notifyLowStock)
            put("notifyDayNotClosed", settings.notifyDayNotClosed)
            put("notifyNewUser", settings.notifyNewUser)
            put("notifySyncErrors", settings.notifySyncErrors)
            put("notifyNewVersion", settings.notifyNewVersion)
            put("notifyRoles", settings.notifyRoles)
            put("memberUids", memberUids)
            put("createdAt", existing.getTimestamp("createdAt") ?: FieldValue.serverTimestamp())
            put("updatedAt", FieldValue.serverTimestamp())
        }
        docRef.set(payload, SetOptions.merge()).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "stores/$storeId/settings/main")
    }

    // App Releases
    fun observeReleases(storeId: String): Flow<List<FsAppRelease>> {
        val uid = requireUserId()
        val path = "stores/$storeId/app_releases"
        return storeDoc(storeId).collection("app_releases")
            .whereArrayContains("memberUids", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(FsAppRelease::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                logFlowError(error, OperationType.LIST, path, uid)
                throw error
            }
    }

    suspend fun upsertRelease(storeId: String, release: FsAppRelease, memberUids: List<String>): Result<String> = runCatching {
        requireUserId()
        val id = release.id.ifBlank { "${release.platform}_v${release.versionCode}" }
        val docRef = storeDoc(storeId).collection("app_releases").document(id)
        val existing = docRef.get().await()
        val payload = mapOf(
            "platform" to release.platform,
            "versionCode" to release.versionCode,
            "versionName" to release.versionName,
            "downloadUrl" to release.downloadUrl,
            "releaseNotesAr" to release.releaseNotesAr,
            "releaseNotesEn" to release.releaseNotesEn,
            "isMandatory" to release.isMandatory,
            "memberUids" to memberUids,
            "createdAt" to (existing.getTimestamp("createdAt") ?: FieldValue.serverTimestamp()),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        docRef.set(payload, SetOptions.merge()).await()
        id
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "stores/$storeId/app_releases/${release.id}")
    }

    private fun logFlowError(error: Throwable, op: OperationType, path: String, uid: String) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val ex = firestoreError ?: (error as? Exception)
        if (ex != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(ex, op, path)
        }
    }
}
