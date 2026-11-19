package com.example.ui.viewmodel

import com.example.data.local.AppReleaseEntity
import com.example.data.local.AuditLogEntity
import com.example.data.local.BusinessDayEntity
import com.example.data.local.HolidayEntity
import com.example.data.local.InvoiceEntity
import com.example.data.local.PartyEntity
import com.example.data.local.ProductEntity
import com.example.data.local.StockMovementEntity
import com.example.data.local.StoreMemberEntity
import com.example.data.local.StoreSettingsEntity
import com.example.i18n.AppLanguage

enum class UserRole(val key: String) {
    ADMIN("admin"),
    MANAGER("manager"),
    CASHIER("cashier"),
    SELLER("seller");

    fun canSell(): Boolean = this == ADMIN || this == MANAGER || this == SELLER
    fun canUpdateProducts(): Boolean = this == ADMIN || this == MANAGER || this == CASHIER
    fun canManageCatalog(): Boolean = canUpdateProducts()
    fun canDeleteCatalog(): Boolean = this == ADMIN || this == MANAGER
    fun canAdjustStock(): Boolean = this == ADMIN || this == MANAGER || this == CASHIER
    fun canViewProfitAndReports(): Boolean = this == ADMIN || this == MANAGER
    fun canOverrideHoliday(): Boolean = this == ADMIN || this == MANAGER
    fun canManageUsers(): Boolean = this == ADMIN || this == MANAGER
    fun canAccessSystemAdmin(): Boolean = this == ADMIN

    companion object {
        fun fromKey(key: String?): UserRole = when (key?.lowercase()) {
            "admin" -> ADMIN
            "manager" -> MANAGER
            "cashier" -> CASHIER
            "seller" -> SELLER
            else -> SELLER
        }
    }
}

enum class MainTab {
    POS,
    INVENTORY,
    DAY_CONTROL,
    DASHBOARD,
    REPORTS,
    ADMIN
}

enum class PeriodFilter {
    DAY, WEEK, MONTH, YEAR
}

data class ProductWithStock(
    val product: ProductEntity,
    val currentStock: Int
) {
    val isLowStock: Boolean get() = currentStock <= product.lowStockThreshold
}

data class CartItem(
    val product: ProductEntity,
    val currentStock: Int,
    val quantity: Int,
    val useWholesalePrice: Boolean
) {
    val unitPrice: Double
        get() = if (useWholesalePrice) product.wholesalePrice else product.retailPrice
    val lineTotal: Double get() = unitPrice * quantity
    val lineCost: Double get() = product.costPrice * quantity
}

data class ProductMetric(
    val productId: String,
    val nameAr: String,
    val nameEn: String,
    val unitsSold: Int,
    val revenue: Double,
    val stockQty: Int
)

data class UserPerformanceRow(
    val userId: String,
    val userName: String,
    val invoiceCount: Int,
    val totalSales: Double
)

data class DashboardAnalytics(
    val todaySales: Double = 0.0,
    val monthSales: Double = 0.0,
    val periodSales: Double = 0.0,
    val periodPurchases: Double = 0.0,
    val periodProfit: Double = 0.0,
    val stockTotalValueCost: Double = 0.0,
    val stockTotalValueRetail: Double = 0.0,
    val bestSelling: List<ProductMetric> = emptyList(),
    val worstSelling: List<ProductMetric> = emptyList(),
    val highestStock: List<ProductWithStock> = emptyList(),
    val lowStockAlerts: List<ProductWithStock> = emptyList(),
    val userPerformance: List<UserPerformanceRow> = emptyList()
)

data class InAppNotification(
    val id: String,
    val type: String,
    val titleAr: String,
    val titleEn: String,
    val bodyAr: String,
    val bodyEn: String
)

data class StoreUiState(
    val isLoading: Boolean = true,
    val userId: String = "",
    val userName: String = "",
    val storeId: String = "",
    val actualRole: UserRole = UserRole.ADMIN,
    val previewRole: UserRole? = null,
    val language: AppLanguage = AppLanguage.AR,
    val darkMode: Boolean = false,
    val isOnline: Boolean = true,
    val isSyncing: Boolean = false,
    val pendingSyncCount: Int = 0,
    val lastSyncError: String? = null,
    val activeTab: MainTab = MainTab.POS,
    val periodFilter: PeriodFilter = PeriodFilter.MONTH,
    val products: List<ProductWithStock> = emptyList(),
    val movements: List<StockMovementEntity> = emptyList(),
    val invoices: List<InvoiceEntity> = emptyList(),
    val customers: List<PartyEntity> = emptyList(),
    val suppliers: List<PartyEntity> = emptyList(),
    val businessDays: List<BusinessDayEntity> = emptyList(),
    val openBusinessDay: BusinessDayEntity? = null,
    val lastClosedDay: BusinessDayEntity? = null,
    val holidays: List<HolidayEntity> = emptyList(),
    val auditLogs: List<AuditLogEntity> = emptyList(),
    val members: List<StoreMemberEntity> = emptyList(),
    val settings: StoreSettingsEntity = StoreSettingsEntity(),
    val releases: List<AppReleaseEntity> = emptyList(),
    val availableUpdate: AppReleaseEntity? = null,
    val updateDismissedForVersion: Int? = null,
    val cartItems: List<CartItem> = emptyList(),
    val discountAmount: Double = 0.0,
    val selectedPaymentMethod: String = "cash",
    val selectedCustomerId: String? = null,
    val lastCompletedInvoice: InvoiceEntity? = null,
    val analytics: DashboardAnalytics = DashboardAnalytics(),
    val notifications: List<InAppNotification> = emptyList(),
    val sessionLocked: Boolean = false,
    val pcServerUrl: String = "",
    val pcConnectionStatus: String? = null,
    val isPcSyncing: Boolean = false,
    val lastPcSyncMillis: Long = 0L,
    val statusMessage: String? = null
) {
    val effectiveRole: UserRole get() = previewRole ?: actualRole
}
