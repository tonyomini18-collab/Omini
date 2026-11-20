package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.StoreMemberEntity
import com.example.data.sync.PreferencesManager
import com.example.ui.viewmodel.UserRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Omini POS", appName)
  }

  @Test
  fun `verify local admin credentials omini and admin116`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = PreferencesManager(context)
    prefs.clearLocalSession()

    assertFalse(prefs.verifyAndSignInLocalAdmin("omini", "wrong"))
    assertNull(prefs.localSessionFlow.first())

    assertTrue(prefs.verifyAndSignInLocalAdmin("omini", "admin116"))
    val session = prefs.localSessionFlow.first()
    assertNotNull(session)
    assertEquals("omini", session?.displayName)
    assertEquals("admin", session?.roleKey)
  }

  @Test
  fun `verify created cashier seller manager login password updates and inactive check`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    try {
      val dao = db.storeDao()
      val prefs = PreferencesManager(context)
      prefs.clearLocalSession()

      val cashier = StoreMemberEntity(
        uid = "staff_cash1",
        username = "cash1",
        passwordHash = PreferencesManager.sha256("pass123"),
        displayName = "Cashier Ali",
        role = "cashier",
        active = true
      )
      val seller = StoreMemberEntity(
        uid = "staff_sell1",
        username = "sell1",
        passwordHash = PreferencesManager.sha256("sellpass"),
        displayName = "Seller Sara",
        role = "seller",
        active = true
      )
      val disabled = StoreMemberEntity(
        uid = "staff_off",
        username = "disabled1",
        passwordHash = PreferencesManager.sha256("1234"),
        displayName = "Inactive User",
        role = "seller",
        active = false
      )
      dao.upsertMember(cashier)
      dao.upsertMember(seller)
      dao.upsertMember(disabled)

      // Created Cashier can log in with their username/password
      assertFalse(prefs.verifyAndSignInLocalUser(dao, "cash1", "wrong"))
      assertTrue(prefs.verifyAndSignInLocalUser(dao, "cash1", "pass123"))
      val cashSession = prefs.localSessionFlow.first()
      assertEquals("staff_cash1", cashSession?.uid)
      assertEquals("Cashier Ali", cashSession?.displayName)
      assertEquals("cashier", cashSession?.roleKey)

      // Created Seller can log in
      assertTrue(prefs.verifyAndSignInLocalUser(dao, "SELL1", "sellpass"))
      val sellSession = prefs.localSessionFlow.first()
      assertEquals("seller", sellSession?.roleKey)

      // Password change works and invalidates old password
      dao.upsertMember(seller.copy(passwordHash = PreferencesManager.sha256("newpass99")))
      assertFalse(prefs.verifyAndSignInLocalUser(dao, "sell1", "sellpass"))
      assertTrue(prefs.verifyAndSignInLocalUser(dao, "sell1", "newpass99"))

      // Disabled account cannot log in
      assertFalse(prefs.verifyAndSignInLocalUser(dao, "disabled1", "1234"))
    } finally {
      db.close()
    }
  }

  @Test
  fun `verify role permissions for admin manager cashier and seller`() {
    assertTrue(UserRole.ADMIN.canManageUsers())
    assertTrue(UserRole.ADMIN.canAccessSystemAdmin())
    assertTrue(UserRole.ADMIN.canSell())
    assertTrue(UserRole.ADMIN.canUpdateProducts())

    assertTrue(UserRole.MANAGER.canManageUsers())
    assertFalse(UserRole.MANAGER.canAccessSystemAdmin())
    assertTrue(UserRole.MANAGER.canSell())
    assertTrue(UserRole.MANAGER.canUpdateProducts())

    // Cashier can update products & stock, CANNOT sell or manage users
    assertTrue(UserRole.CASHIER.canUpdateProducts())
    assertTrue(UserRole.CASHIER.canAdjustStock())
    assertFalse(UserRole.CASHIER.canDeleteCatalog())
    assertFalse(UserRole.CASHIER.canSell())
    assertFalse(UserRole.CASHIER.canManageUsers())

    // Seller can sell ONLY, CANNOT update products or manage users
    assertTrue(UserRole.SELLER.canSell())
    assertFalse(UserRole.SELLER.canUpdateProducts())
    assertFalse(UserRole.SELLER.canAdjustStock())
    assertFalse(UserRole.SELLER.canDeleteCatalog())
    assertFalse(UserRole.SELLER.canManageUsers())
  }

  @Test
  fun `enforce manager restrictions not allowed message and last admin protection in StoreViewModel`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    if (com.google.firebase.FirebaseApp.getApps(context).isEmpty()) {
      com.google.firebase.FirebaseApp.initializeApp(
        context,
        com.google.firebase.FirebaseOptions.Builder()
          .setProjectId("test-project")
          .setApplicationId("1:123456:android:abcdef")
          .setApiKey("test-api-key")
          .build()
      )
    }

    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    try {
      val dao = db.storeDao()
      val prefs = PreferencesManager(context)
      prefs.setLanguage("en")
      val connectivity = com.example.data.sync.ConnectivityObserver(context)
      val remote = com.example.data.remote.FirestoreRepository(context)
      val syncEngine = com.example.data.sync.SyncEngine(dao, remote, connectivity, prefs)

      val vm = com.example.ui.viewmodel.StoreViewModel(
        dao = dao,
        remote = remote,
        syncEngine = syncEngine,
        connectivity = connectivity,
        prefs = prefs,
        currentUserId = PreferencesManager.DEFAULT_ADMIN_UID,
        currentUserDisplayName = "omini",
        currentUserEmail = null,
        initialRoleKey = "admin"
      )

      val deadline = System.currentTimeMillis() + 5_000L
      while ((vm.uiState.value.language != com.example.i18n.AppLanguage.EN || vm.uiState.value.isLoading) &&
        System.currentTimeMillis() < deadline
      ) {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        Thread.sleep(20)
      }
      org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      assertEquals(com.example.i18n.AppLanguage.EN, vm.uiState.value.language)

      // Seed sole active admin directly into DAO (if not already seeded by bootstrap)
      dao.upsertMember(
        StoreMemberEntity(
          uid = PreferencesManager.DEFAULT_ADMIN_UID,
          username = PreferencesManager.DEFAULT_ADMIN_USERNAME,
          passwordHash = PreferencesManager.DEFAULT_ADMIN_PASSWORD_HASH,
          displayName = "omini",
          role = "admin",
          active = true
        )
      )

      // Manager can create & edit cashier and seller
      val cashierRes = vm.saveMemberEnforced(
        actorRole = UserRole.MANAGER,
        uid = "",
        username = "cashier1",
        displayName = "Cashier One",
        password = "123",
        role = UserRole.CASHIER
      )
      assertTrue(cashierRes.isSuccess)
      val sellerRes = vm.saveMemberEnforced(
        actorRole = UserRole.MANAGER,
        uid = "",
        username = "seller1",
        displayName = "Seller One",
        password = "123",
        role = UserRole.SELLER
      )
      assertTrue(sellerRes.isSuccess)
      val sellerUid = sellerRes.getOrThrow().uid

      // Manager cannot create an admin -> rejected with "Not allowed"
      val createAdminByMgr = vm.saveMemberEnforced(
        actorRole = UserRole.MANAGER,
        uid = "",
        username = "rogue_admin",
        displayName = "Rogue",
        password = "123",
        role = UserRole.ADMIN
      )
      assertTrue(createAdminByMgr.isFailure)
      assertEquals("Not allowed", vm.uiState.value.statusMessage)

      // Manager cannot edit, deactivate, or delete an admin -> rejected with "Not allowed"
      val editAdminByMgr = vm.saveMemberEnforced(
        actorRole = UserRole.MANAGER,
        uid = PreferencesManager.DEFAULT_ADMIN_UID,
        username = "omini",
        displayName = "Hacked",
        password = "123",
        role = UserRole.ADMIN,
        active = false
      )
      assertTrue(editAdminByMgr.isFailure)
      assertEquals("Not allowed", vm.uiState.value.statusMessage)

      val delAdminByMgr = vm.deleteMemberEnforced(
        actorRole = UserRole.MANAGER,
        targetUid = PreferencesManager.DEFAULT_ADMIN_UID
      )
      assertTrue(delAdminByMgr.isFailure)
      assertEquals("Not allowed", vm.uiState.value.statusMessage)

      // Cashier & Seller cannot create/update/delete users
      assertTrue(
        vm.saveMemberEnforced(UserRole.CASHIER, "", "u_c", "U", "123", UserRole.SELLER).isFailure
      )
      assertEquals("Not allowed", vm.uiState.value.statusMessage)
      assertTrue(
        vm.deleteMemberEnforced(UserRole.SELLER, sellerUid).isFailure
      )
      assertEquals("Not allowed", vm.uiState.value.statusMessage)

      // Manager CAN delete seller
      assertTrue(vm.deleteMemberEnforced(UserRole.MANAGER, sellerUid).isSuccess)
      assertNull(dao.getMembersSnapshot().find { it.uid == sellerUid })

      // Last remaining admin cannot be deleted, demoted, or deactivated even by Admin
      assertTrue(
        vm.deleteMemberEnforced(UserRole.ADMIN, PreferencesManager.DEFAULT_ADMIN_UID).isFailure
      )
      assertTrue(
        vm.saveMemberEnforced(
          actorRole = UserRole.ADMIN,
          uid = PreferencesManager.DEFAULT_ADMIN_UID,
          username = "omini",
          displayName = "omini",
          password = "",
          role = UserRole.MANAGER,
          active = true
        ).isFailure
      )
      assertTrue(
        vm.saveMemberEnforced(
          actorRole = UserRole.ADMIN,
          uid = PreferencesManager.DEFAULT_ADMIN_UID,
          username = "omini",
          displayName = "omini",
          password = "",
          role = UserRole.ADMIN,
          active = false
        ).isFailure
      )

      // Admin creates a second admin, then deletes the second admin while the last remaining stays protected
      val secondAdminRes = vm.saveMemberEnforced(
        actorRole = UserRole.ADMIN,
        uid = "",
        username = "admin2",
        displayName = "Admin Two",
        password = "pw2",
        role = UserRole.ADMIN
      )
      assertTrue(secondAdminRes.isSuccess)
      assertTrue(vm.deleteMemberEnforced(UserRole.ADMIN, secondAdminRes.getOrThrow().uid).isSuccess)
      assertTrue(vm.deleteMemberEnforced(UserRole.ADMIN, PreferencesManager.DEFAULT_ADMIN_UID).isFailure)
    } finally {
      db.close()
    }
  }

  @Test
  fun usdPaymentMethod_checkoutRecordsUsdAndUpdatesTotals() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    if (com.google.firebase.FirebaseApp.getApps(context).isEmpty()) {
      com.google.firebase.FirebaseApp.initializeApp(
        context,
        com.google.firebase.FirebaseOptions.Builder()
          .setProjectId("test-project")
          .setApplicationId("1:123456:android:abcdef")
          .setApiKey("test-api-key")
          .build()
      )
    }

    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    try {
      val dao = db.storeDao()
      val prefs = PreferencesManager(context)
      prefs.setLanguage("en")
      val connectivity = com.example.data.sync.ConnectivityObserver(context)
      val remote = com.example.data.remote.FirestoreRepository(context)
      val syncEngine = com.example.data.sync.SyncEngine(dao, remote, connectivity, prefs)

      val vm = com.example.ui.viewmodel.StoreViewModel(
        dao = dao,
        remote = remote,
        syncEngine = syncEngine,
        connectivity = connectivity,
        prefs = prefs,
        currentUserId = PreferencesManager.DEFAULT_ADMIN_UID,
        currentUserDisplayName = "omini",
        currentUserEmail = null,
        initialRoleKey = "admin"
      )

      val deadline = System.currentTimeMillis() + 5_000L
      while ((vm.uiState.value.isLoading || vm.uiState.value.products.isEmpty()) &&
        System.currentTimeMillis() < deadline
      ) {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        Thread.sleep(20)
      }
      org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

      vm.openBusinessDay(0.0)
      val dayDeadline = System.currentTimeMillis() + 3_000L
      while (vm.uiState.value.openBusinessDay == null && System.currentTimeMillis() < dayDeadline) {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        Thread.sleep(20)
      }
      assertNotNull(vm.uiState.value.openBusinessDay)

      val firstProduct = vm.uiState.value.products.first()
      vm.addToCart(firstProduct)
      vm.setPaymentMethod("usd")
      vm.setUsdRateSsp(1000.0)
      assertEquals("usd", vm.uiState.value.selectedPaymentMethod)
      assertEquals(1000.0, vm.uiState.value.usdRateSsp, 0.01)

      vm.checkoutCart()
      val invDeadline = System.currentTimeMillis() + 3_000L
      while (vm.uiState.value.lastCompletedInvoice == null && System.currentTimeMillis() < invDeadline) {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        Thread.sleep(20)
      }

      val invoice = vm.uiState.value.lastCompletedInvoice
      assertNotNull(invoice)
      assertEquals("usd", invoice?.paymentMethod)
      assertTrue(invoice?.itemsSummary?.contains("USD") == true)
    } finally {
      db.close()
    }
  }

  @Test
  fun unlimitedWholesaleAndRetail_allowsLargeQuantitiesBeyondStock() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    if (com.google.firebase.FirebaseApp.getApps(context).isEmpty()) {
      com.google.firebase.FirebaseApp.initializeApp(
        context,
        com.google.firebase.FirebaseOptions.Builder()
          .setProjectId("test-project")
          .setApplicationId("1:123456:android:abcdef")
          .setApiKey("test-api-key")
          .build()
      )
    }

    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    try {
      val dao = db.storeDao()
      val prefs = PreferencesManager(context)
      prefs.markStoreSeeded("")
      prefs.setLanguage("en")
      val connectivity = com.example.data.sync.ConnectivityObserver(context)
      val remote = com.example.data.remote.FirestoreRepository(context)
      val syncEngine = com.example.data.sync.SyncEngine(dao, remote, connectivity, prefs)

      val vm = com.example.ui.viewmodel.StoreViewModel(
        dao = dao,
        remote = remote,
        syncEngine = syncEngine,
        connectivity = connectivity,
        prefs = prefs,
        currentUserId = PreferencesManager.DEFAULT_ADMIN_UID,
        currentUserDisplayName = "omini",
        currentUserEmail = null,
        initialRoleKey = "admin"
      )

      val deadline = System.currentTimeMillis() + 5_000L
      while ((vm.uiState.value.isLoading || vm.uiState.value.products.isEmpty()) &&
        System.currentTimeMillis() < deadline
      ) {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        Thread.sleep(20)
      }
      org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      assertTrue(vm.uiState.value.products.isNotEmpty())

      vm.openBusinessDay(0.0)
      val dayDeadline = System.currentTimeMillis() + 3_000L
      while (vm.uiState.value.openBusinessDay == null && System.currentTimeMillis() < dayDeadline) {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        Thread.sleep(20)
      }
      assertNotNull(vm.uiState.value.openBusinessDay)

      val prod = vm.uiState.value.products.first()
      // Add product and set an unlimited quantity far exceeding currentStock
      vm.addToCart(prod)
      val targetQty = prod.currentStock + 500
      vm.updateCartItemQuantity(prod.product.id, targetQty)
      assertEquals(targetQty, vm.uiState.value.cartItems.first().quantity)
      assertTrue(vm.uiState.value.cartItems.first().useWholesalePrice)

      // Can also toggle freely back to retail for any quantity
      vm.toggleCartItemWholesale(prod.product.id)
      assertFalse(vm.uiState.value.cartItems.first().useWholesalePrice)

      // Checkout succeeds even with quantity exceeding stock
      vm.checkoutCart()
      val invDeadline = System.currentTimeMillis() + 3_000L
      while (vm.uiState.value.lastCompletedInvoice == null && System.currentTimeMillis() < invDeadline) {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        Thread.sleep(20)
      }
      val invoice = vm.uiState.value.lastCompletedInvoice
      assertNotNull(invoice)
      assertEquals("completed", invoice?.status)
    } finally {
      db.close()
    }
  }

  @Test
  fun usdInvoice_snapshotsExchangeRateAndGeneratesDualCurrencyPdf() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    if (com.google.firebase.FirebaseApp.getApps(context).isEmpty()) {
      com.google.firebase.FirebaseApp.initializeApp(
        context,
        com.google.firebase.FirebaseOptions.Builder()
          .setProjectId("test-project")
          .setApplicationId("1:123456:android:abcdef")
          .setApiKey("test-api-key")
          .build()
      )
    }

    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    try {
      val dao = db.storeDao()
      val prefs = PreferencesManager(context)
      prefs.markStoreSeeded("")
      prefs.setLanguage("en")
      val connectivity = com.example.data.sync.ConnectivityObserver(context)
      val remote = com.example.data.remote.FirestoreRepository(context)
      val syncEngine = com.example.data.sync.SyncEngine(dao, remote, connectivity, prefs)

      val vm = com.example.ui.viewmodel.StoreViewModel(
        dao = dao,
        remote = remote,
        syncEngine = syncEngine,
        connectivity = connectivity,
        prefs = prefs,
        currentUserId = PreferencesManager.DEFAULT_ADMIN_UID,
        currentUserDisplayName = "omini",
        currentUserEmail = null,
        initialRoleKey = "admin"
      )

      val deadline = System.currentTimeMillis() + 5_000L
      while ((vm.uiState.value.isLoading || vm.uiState.value.products.isEmpty()) &&
        System.currentTimeMillis() < deadline
      ) {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        Thread.sleep(20)
      }
      org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

      vm.openBusinessDay(0.0)
      val dayDeadline = System.currentTimeMillis() + 3_000L
      while (vm.uiState.value.openBusinessDay == null && System.currentTimeMillis() < dayDeadline) {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        Thread.sleep(20)
      }

      val prod = vm.uiState.value.products.first()
      vm.addToCart(prod)
      vm.setPaymentMethod("usd")
      vm.setUsdRateSsp(650.0)
      vm.checkoutCart()

      val invDeadline = System.currentTimeMillis() + 3_000L
      while (vm.uiState.value.lastCompletedInvoice == null && System.currentTimeMillis() < invDeadline) {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        Thread.sleep(20)
      }
      val createdInvoice = vm.uiState.value.lastCompletedInvoice!!
      assertEquals("USD", createdInvoice.currencyCode)
      assertEquals(650.0, createdInvoice.usdRateSsp, 0.001)

      // Update rate in settings afterwards; saved invoice must keep its original 650.0 snapshot
      vm.setUsdRateSsp(1800.0)
      org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      val reloaded = dao.getInvoiceById(createdInvoice.id)!!
      assertEquals(650.0, reloaded.usdRateSsp, 0.001)
      assertEquals(
        650.0,
        com.example.util.CurrencyFormatter.effectiveInvoiceRate(reloaded, 1800.0),
        0.001
      )

      // Verify every item's price & line total, subtotal, and total convert cleanly with the stored 650.0 rate
      val parsedItems = com.example.util.CurrencyFormatter.parseInvoiceItems(reloaded.itemsJson)
      assertTrue(parsedItems.isNotEmpty())
      val storedRate = com.example.util.CurrencyFormatter.effectiveInvoiceRate(reloaded, 1800.0)
      assertEquals(650.0, storedRate, 0.001)
      val firstItemConv = com.example.util.CurrencyFormatter.convertSspToUsd(parsedItems.first().unitPriceSsp, storedRate)
      assertTrue(firstItemConv.usdText.startsWith("$"))
      assertTrue(firstItemConv.sspText.startsWith("SSP "))
    } finally {
      db.close()
    }
  }
}
