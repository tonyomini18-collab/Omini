package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StoreDao {
    // Products
    @Query("SELECT * FROM products WHERE isDeleted = 0 ORDER BY nameEn ASC")
    fun observeProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE isDeleted = 0")
    suspend fun getProductsSnapshot(): List<ProductEntity>

    @Query("SELECT * FROM products")
    suspend fun getAllProductsSnapshot(): List<ProductEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProduct(product: ProductEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProducts(products: List<ProductEntity>)

    // Stock Movements (Stock calculated strictly from movements)
    @Query("SELECT * FROM stock_movements ORDER BY createdAtMillis DESC")
    fun observeMovements(): Flow<List<StockMovementEntity>>

    @Query("SELECT productId, COALESCE(SUM(qtyDelta), 0) AS currentStock FROM stock_movements GROUP BY productId")
    fun observeProductStocks(): Flow<List<ProductStockRow>>

    @Query("SELECT COALESCE(SUM(qtyDelta), 0) FROM stock_movements WHERE productId = :productId")
    suspend fun getProductStock(productId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovement(movement: StockMovementEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovements(movements: List<StockMovementEntity>)

    @Query("SELECT * FROM stock_movements ORDER BY createdAtMillis DESC")
    suspend fun getMovementsSnapshot(): List<StockMovementEntity>

    // Invoices
    @Query("SELECT * FROM invoices ORDER BY createdAtMillis DESC")
    fun observeInvoices(): Flow<List<InvoiceEntity>>

    @Query("SELECT * FROM invoices WHERE id = :id LIMIT 1")
    suspend fun getInvoiceById(id: String): InvoiceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInvoice(invoice: InvoiceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInvoices(invoices: List<InvoiceEntity>)

    @Query("SELECT * FROM invoices ORDER BY createdAtMillis DESC")
    suspend fun getInvoicesSnapshot(): List<InvoiceEntity>

    // Customers & Suppliers
    @Query("SELECT * FROM parties WHERE partyType = :partyType ORDER BY name ASC")
    fun observeParties(partyType: String): Flow<List<PartyEntity>>

    @Query("SELECT * FROM parties ORDER BY name ASC")
    fun observeAllParties(): Flow<List<PartyEntity>>

    @Query("SELECT * FROM parties WHERE id = :id LIMIT 1")
    suspend fun getPartyById(id: String): PartyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertParty(party: PartyEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertParties(parties: List<PartyEntity>)

    @Query("SELECT * FROM parties")
    suspend fun getPartiesSnapshot(): List<PartyEntity>

    @Query("DELETE FROM parties WHERE id = :id")
    suspend fun deletePartyById(id: String)

    // Business Days
    @Query("SELECT * FROM business_days ORDER BY dateStr DESC")
    fun observeBusinessDays(): Flow<List<BusinessDayEntity>>

    @Query("SELECT * FROM business_days WHERE status = 'open' ORDER BY createdAtMillis DESC LIMIT 1")
    fun observeOpenBusinessDay(): Flow<BusinessDayEntity?>

    @Query("SELECT * FROM business_days WHERE id = :id LIMIT 1")
    suspend fun getBusinessDayById(id: String): BusinessDayEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBusinessDay(day: BusinessDayEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBusinessDays(days: List<BusinessDayEntity>)

    @Query("SELECT * FROM business_days")
    suspend fun getBusinessDaysSnapshot(): List<BusinessDayEntity>

    // Holidays
    @Query("SELECT * FROM holidays ORDER BY isWeeklyOffDay DESC, dateStr ASC")
    fun observeHolidays(): Flow<List<HolidayEntity>>

    @Query("SELECT * FROM holidays")
    suspend fun getHolidaysSnapshot(): List<HolidayEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHoliday(holiday: HolidayEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHolidays(holidays: List<HolidayEntity>)

    @Query("DELETE FROM holidays WHERE id = :id")
    suspend fun deleteHolidayById(id: String)

    // Audit Logs
    @Query("SELECT * FROM audit_logs ORDER BY createdAtMillis DESC")
    fun observeAuditLogs(): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_logs ORDER BY createdAtMillis DESC LIMIT 200")
    suspend fun getAuditLogsSnapshot(): List<AuditLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(log: AuditLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLogs(logs: List<AuditLogEntity>)

    // Store Members
    @Query("SELECT * FROM store_members ORDER BY role ASC, displayName ASC")
    fun observeMembers(): Flow<List<StoreMemberEntity>>

    @Query("SELECT * FROM store_members")
    suspend fun getMembersSnapshot(): List<StoreMemberEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMember(member: StoreMemberEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMembers(members: List<StoreMemberEntity>)

    @Query("DELETE FROM store_members WHERE uid = :uid")
    suspend fun deleteMemberByUid(uid: String)

    // Store Settings
    @Query("SELECT * FROM store_settings WHERE id = 'main' LIMIT 1")
    fun observeSettings(): Flow<StoreSettingsEntity?>

    @Query("SELECT * FROM store_settings WHERE id = 'main' LIMIT 1")
    suspend fun getSettingsOnce(): StoreSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSettings(settings: StoreSettingsEntity)

    // App Releases
    @Query("SELECT * FROM app_releases ORDER BY versionCode DESC")
    fun observeReleases(): Flow<List<AppReleaseEntity>>

    @Query("SELECT * FROM app_releases ORDER BY versionCode DESC")
    suspend fun getReleasesSnapshot(): List<AppReleaseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRelease(release: AppReleaseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReleases(releases: List<AppReleaseEntity>)

    // Pending Sync Queue
    @Query("SELECT * FROM pending_sync_ops ORDER BY createdAtMillis ASC")
    suspend fun getPendingSyncOps(): List<PendingSyncOpEntity>

    @Query("SELECT COUNT(*) FROM pending_sync_ops")
    fun observePendingSyncCount(): Flow<Int>

    @Query("SELECT * FROM pending_sync_ops ORDER BY createdAtMillis DESC")
    fun observePendingSyncOps(): Flow<List<PendingSyncOpEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueueSyncOp(op: PendingSyncOpEntity)

    @Query("DELETE FROM pending_sync_ops WHERE opId = :opId")
    suspend fun deleteSyncOp(opId: String)

    @Query("UPDATE pending_sync_ops SET retryCount = retryCount + 1, lastError = :error WHERE opId = :opId")
    suspend fun markSyncOpError(opId: String, error: String)
}
