package com.example.data.remote

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class FsUserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String? = null,
    val activeStoreId: String = "",
    val preferredLanguage: String = "ar",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FsStore(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val nameAr: String = "",
    val nameEn: String = "",
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FsStoreMember(
    @DocumentId val id: String = "",
    val uid: String = "",
    val displayName: String = "",
    val role: String = "cashier",
    val active: Boolean = true,
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FsProduct(
    @DocumentId val id: String = "",
    val nameAr: String = "",
    val nameEn: String = "",
    val category: String = "perfume",
    val sku: String = "",
    val barcode: String? = null,
    val imageUri: String? = null,
    val costPrice: Double = 0.0,
    val retailPrice: Double = 0.0,
    val wholesalePrice: Double = 0.0,
    val wholesaleMinQty: Int = 5,
    val unitAr: String = "",
    val unitEn: String = "",
    val lowStockThreshold: Int = 5,
    val isDeleted: Boolean = false,
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FsStockMovement(
    @DocumentId val id: String = "",
    val productId: String = "",
    val type: String = "sale",
    val qtyDelta: Int = 0,
    val unitCost: Double = 0.0,
    val reason: String = "",
    val refId: String? = null,
    val actorUid: String = "",
    val actorName: String = "",
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FsInvoice(
    @DocumentId val id: String = "",
    val invoiceNumber: String = "",
    val saleType: String = "retail",
    val customerId: String? = null,
    val customerName: String? = null,
    val itemsSummary: String = "",
    val subtotal: Double = 0.0,
    val discount: Double = 0.0,
    val total: Double = 0.0,
    val costTotal: Double = 0.0,
    val paymentMethod: String = "cash",
    val status: String = "completed",
    val businessDayId: String = "",
    val cashierUid: String = "",
    val cashierName: String = "",
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FsParty(
    @DocumentId val id: String = "",
    val name: String = "",
    val phone: String? = null,
    val balance: Double = 0.0,
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FsBusinessDay(
    @DocumentId val id: String = "",
    val dateStr: String = "",
    val status: String = "open",
    val openedByUid: String = "",
    val openedByName: String = "",
    val closedByUid: String? = null,
    val closedByName: String? = null,
    val openingCash: Double = 0.0,
    val totalSales: Double = 0.0,
    val cashSales: Double = 0.0,
    val transferSales: Double = 0.0,
    val creditSales: Double = 0.0,
    val itemsSoldCount: Int = 0,
    val holidayOverride: Boolean = false,
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FsHoliday(
    @DocumentId val id: String = "",
    val dateStr: String? = null,
    val titleAr: String = "",
    val titleEn: String = "",
    val isWeeklyOffDay: Boolean = false,
    val dayOfWeek: Int = 0,
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FsAuditLog(
    @DocumentId val id: String = "",
    val actorUid: String = "",
    val actorName: String = "",
    val actorRole: String = "admin",
    val action: String = "",
    val targetType: String = "",
    val targetId: String = "",
    val details: String = "",
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FsStoreSettings(
    @DocumentId val id: String = "",
    val storeNameAr: String = "دار العطور والمستحضرات",
    val storeNameEn: String = "Aroma Boutique",
    val primaryColorHex: String = "#1B4D3E",
    val secondaryColorHex: String = "#C59B27",
    val logoUri: String? = null,
    val sessionTimeoutMinutes: Int = 30,
    val notifyLowStock: Boolean = true,
    val notifyDayNotClosed: Boolean = true,
    val notifyNewUser: Boolean = true,
    val notifySyncErrors: Boolean = true,
    val notifyNewVersion: Boolean = true,
    val notifyRoles: List<String> = listOf("admin", "manager"),
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FsAppRelease(
    @DocumentId val id: String = "",
    val platform: String = "android",
    val versionCode: Int = 1,
    val versionName: String = "1.0",
    val downloadUrl: String = "",
    val releaseNotesAr: String = "",
    val releaseNotesEn: String = "",
    val isMandatory: Boolean = false,
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
