package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.remote.FirestoreRepository
import com.example.data.remote.FsAppRelease
import com.example.data.remote.FsProduct
import com.example.data.remote.FsStockMovement
import com.example.data.remote.FsStoreMember
import com.example.data.remote.FsStoreSettings
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class FirestoreRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun adminInitializeStore_createsAndObservesProductAndMovement() = runBlocking {
        val adminUid = signInTestUser("admin_${UUID.randomUUID()}@aromapos.test")
        val repo = FirestoreRepository(firestore, auth)

        val storeResult = withTimeout(TIMEOUT_MS) {
            repo.ensureStoreAndProfileInitialized("Owner Admin", null, "ar")
        }
        assertTrue(storeResult.isSuccess)
        val storeId = storeResult.getOrThrow()
        assertEquals(adminUid, storeId)

        val prodId = "prod_${UUID.randomUUID().toString().replace("-", "")}"
        val upsertResult = withTimeout(TIMEOUT_MS) {
            repo.upsertProduct(
                storeId = storeId,
                product = FsProduct(
                    id = prodId,
                    nameAr = "مسك أبيض",
                    nameEn = "White Musk",
                    category = "perfume",
                    sku = "MSK-01",
                    barcode = "6291000001",
                    costPrice = 45.0,
                    retailPrice = 110.0,
                    wholesalePrice = 85.0,
                    wholesaleMinQty = 6,
                    unitAr = "زجاجة",
                    unitEn = "Bottle",
                    lowStockThreshold = 5
                ),
                memberUids = listOf(adminUid)
            )
        }
        assertTrue(upsertResult.isSuccess)

        val observedProducts = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeProducts(storeId).first { list -> list.any { it.id == prodId } }
        }
        assertTrue(observedProducts.any { it.id == prodId && it.nameEn == "White Musk" })

        val movId = "mov_${UUID.randomUUID().toString().replace("-", "")}"
        val movResult = withTimeout(TIMEOUT_MS) {
            repo.createMovement(
                storeId = storeId,
                movement = FsStockMovement(
                    id = movId,
                    productId = prodId,
                    type = "opening",
                    qtyDelta = 25,
                    unitCost = 45.0,
                    reason = "Initial stock",
                    actorUid = adminUid,
                    actorName = "Owner Admin"
                ),
                memberUids = listOf(adminUid)
            )
        }
        assertTrue(movResult.isSuccess)
    }

    @Test
    fun rbacIsolation_cashierBlockedFromAdminSettingsAndProducts() = runBlocking {
        val adminEmail = "owner_${UUID.randomUUID()}@aromapos.test"
        val cashierEmail = "cashier_${UUID.randomUUID()}@aromapos.test"
        val outsiderEmail = "outsider_${UUID.randomUUID()}@aromapos.test"

        val adminUid = signInTestUser(adminEmail)
        val adminRepo = FirestoreRepository(firestore, auth)
        val storeId = withTimeout(TIMEOUT_MS) {
            adminRepo.ensureStoreAndProfileInitialized("Owner", null, "ar").getOrThrow()
        }

        val cashierUid = signInTestUser(cashierEmail)
        signInTestUser(adminEmail)
        val members = listOf(adminUid, cashierUid)
        withTimeout(TIMEOUT_MS) {
            adminRepo.upsertMember(
                storeId = storeId,
                member = FsStoreMember(uid = cashierUid, displayName = "Cashier", role = "cashier", active = true),
                memberUids = members
            ).getOrThrow()
            adminRepo.upsertRelease(
                storeId = storeId,
                release = FsAppRelease(
                    id = "android_v2",
                    platform = "android",
                    versionCode = 2,
                    versionName = "1.1.0",
                    downloadUrl = "https://example.com/app.apk",
                    releaseNotesAr = "تحديث",
                    releaseNotesEn = "Update",
                    isMandatory = false
                ),
                memberUids = members
            ).getOrThrow()
        }

        // Switch to cashier
        signInTestUser(cashierEmail)
        val cashierRepo = FirestoreRepository(firestore, auth)

        // Cashier CANNOT create product
        val prodRes = withTimeout(TIMEOUT_MS) {
            cashierRepo.upsertProduct(
                storeId = storeId,
                product = FsProduct(
                    id = "prod_forbidden",
                    nameAr = "كريم",
                    nameEn = "Cream",
                    category = "cream",
                    sku = "CRM-1",
                    costPrice = 10.0,
                    retailPrice = 20.0,
                    wholesalePrice = 15.0,
                    wholesaleMinQty = 3,
                    unitAr = "علبة",
                    unitEn = "Jar",
                    lowStockThreshold = 3
                ),
                memberUids = members
            )
        }
        assertTrue(prodRes.isFailure)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            prodRes.exceptionOrNull()?.unwrapFirestoreException()?.code
        )

        // Cashier CANNOT update settings
        val settingsRes = withTimeout(TIMEOUT_MS) {
            cashierRepo.upsertSettings(storeId, FsStoreSettings(storeNameEn = "Hacked"), members)
        }
        assertTrue(settingsRes.isFailure)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            settingsRes.exceptionOrNull()?.unwrapFirestoreException()?.code
        )

        // Cashier CAN create sale movement
        val saleMovRes = withTimeout(TIMEOUT_MS) {
            cashierRepo.createMovement(
                storeId = storeId,
                movement = FsStockMovement(
                    id = "mov_${UUID.randomUUID().toString().replace("-", "")}",
                    productId = "prod_1",
                    type = "sale",
                    qtyDelta = -1,
                    unitCost = 10.0,
                    reason = "POS sale",
                    actorUid = cashierUid,
                    actorName = "Cashier"
                ),
                memberUids = members
            )
        }
        assertTrue(saleMovRes.isSuccess)

        // Outsider CANNOT observe products
        signInTestUser(outsiderEmail)
        val outsiderRepo = FirestoreRepository(firestore, auth)
        val products = withTimeout(FLOW_TIMEOUT_MS) {
            outsiderRepo.observeProducts(storeId).first()
        }
        assertTrue(products.isEmpty())
    }

    @Test
    fun unauthenticatedUser_observeProducts_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = FirestoreRepository(firestore, auth)
        val result = runCatching {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeProducts("some_store_id").first()
            }
        }
        assertTrue(result.isFailure)
    }

    private companion object {
        const val TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3500L
    }
}
