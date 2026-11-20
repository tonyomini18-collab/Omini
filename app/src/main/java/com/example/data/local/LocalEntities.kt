package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val id: String,
    val nameAr: String,
    val nameEn: String,
    val category: String, // "perfume", "cream", "other"
    val sku: String,
    val barcode: String = "",
    val imageUri: String = "",
    val drawableResName: String = "",
    val costPrice: Double,
    val retailPrice: Double,
    val wholesalePrice: Double,
    val wholesaleMinQty: Int,
    val unitAr: String,
    val unitEn: String,
    val lowStockThreshold: Int,
    val isDeleted: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "stock_movements")
data class StockMovementEntity(
    @PrimaryKey val id: String,
    val productId: String,
    val type: String, // "opening", "receive", "sale", "return", "adjustment"
    val qtyDelta: Int,
    val unitCost: Double,
    val reason: String,
    val refId: String = "",
    val actorUid: String,
    val actorName: String,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val syncStatus: String = "SYNCED" // "PENDING", "SYNCED", "ERROR"
)

data class ProductStockRow(
    val productId: String,
    val currentStock: Int
)

@Entity(tableName = "invoices")
data class InvoiceEntity(
    @PrimaryKey val id: String,
    val invoiceNumber: String,
    val saleType: String, // "retail", "wholesale", "mixed"
    val customerId: String = "",
    val customerName: String = "",
    val itemsJson: String,
    val itemsSummary: String,
    val subtotal: Double,
    val discount: Double,
    val total: Double,
    val costTotal: Double,
    val paymentMethod: String, // "cash", "transfer", "credit", "usd"
    val status: String, // "completed", "returned"
    val businessDayId: String,
    val cashierUid: String,
    val cashierName: String,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val syncStatus: String = "SYNCED",
    val currencyCode: String = "SSP",
    val usdRateSsp: Double = 0.0,
    val usdTotal: Double = 0.0
)

@Entity(tableName = "parties")
data class PartyEntity(
    @PrimaryKey val id: String,
    val partyType: String, // "customer", "supplier"
    val name: String,
    val phone: String = "",
    val balance: Double = 0.0,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val syncStatus: String = "SYNCED"
)

@Entity(tableName = "business_days")
data class BusinessDayEntity(
    @PrimaryKey val id: String,
    val dateStr: String,
    val status: String, // "open", "closed"
    val openedByUid: String,
    val openedByName: String,
    val closedByUid: String = "",
    val closedByName: String = "",
    val openingCash: Double = 0.0,
    val totalSales: Double = 0.0,
    val cashSales: Double = 0.0,
    val transferSales: Double = 0.0,
    val creditSales: Double = 0.0,
    val itemsSoldCount: Int = 0,
    val holidayOverride: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val syncStatus: String = "SYNCED"
)

@Entity(tableName = "holidays")
data class HolidayEntity(
    @PrimaryKey val id: String,
    val dateStr: String = "",
    val titleAr: String,
    val titleEn: String,
    val isWeeklyOffDay: Boolean = false,
    val dayOfWeek: Int = 0, // 1=Mon .. 7=Sun (java.time.DayOfWeek), 0 if specific date
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "audit_logs")
data class AuditLogEntity(
    @PrimaryKey val id: String,
    val actorUid: String,
    val actorName: String,
    val actorRole: String,
    val action: String,
    val targetType: String,
    val targetId: String,
    val details: String,
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "store_members")
data class StoreMemberEntity(
    @PrimaryKey val uid: String,
    val username: String = "",
    val passwordHash: String = "",
    val displayName: String,
    val role: String, // "admin", "manager", "cashier", "seller"
    val active: Boolean = true,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "store_settings")
data class StoreSettingsEntity(
    @PrimaryKey val id: String = "main",
    val storeNameAr: String = "دار العطور والمستحضرات",
    val storeNameEn: String = "Aroma Boutique",
    val primaryColorHex: String = "#1B4D3E",
    val secondaryColorHex: String = "#C59B27",
    val logoUri: String = "",
    val sessionTimeoutMinutes: Int = 30,
    val notifyLowStock: Boolean = true,
    val notifyDayNotClosed: Boolean = true,
    val notifyNewUser: Boolean = true,
    val notifySyncErrors: Boolean = true,
    val notifyNewVersion: Boolean = true,
    val notifyRolesCsv: String = "admin,manager",
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val usdRateSsp: Double = 1300.0
)

@Entity(tableName = "app_releases")
data class AppReleaseEntity(
    @PrimaryKey val id: String,
    val platform: String, // "android", "windows"
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val releaseNotesAr: String,
    val releaseNotesEn: String,
    val isMandatory: Boolean,
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "pending_sync_ops")
data class PendingSyncOpEntity(
    @PrimaryKey val opId: String,
    val collectionName: String,
    val documentId: String,
    val operation: String, // "UPSERT", "DELETE"
    val payloadJson: String,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val lastError: String = ""
)
