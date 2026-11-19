const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const { PcStoreEngine } = require("../src/core/store-engine");
const { startPcServer } = require("../src/server");

function tmpStoreFile() {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "aroma-pc-test-"));
  return path.join(dir, "store.json");
}

test("authenticates admin, manager, cashier, seller, supports password updates, and enforces RBAC", () => {
  const engine = new PcStoreEngine(tmpStoreFile());
  assert.equal(engine.authenticateLocal("omini", "wrong"), null);
  const admin = engine.authenticateLocal("omini", "admin116");
  assert.ok(admin);
  assert.equal(admin.role, "admin");

  const manager = engine.upsertStaffCredential("admin", {
    username: "mgr1",
    password: "mypassword",
    displayName: "General Manager",
    role: "manager"
  });
  const mgrAuth = engine.authenticateLocal("mgr1", "mypassword");
  assert.ok(mgrAuth);
  assert.equal(mgrAuth.role, "manager");

  // Manager can create Cashier and Seller, and change their passwords, but cannot create/modify Admin
  const cashier = engine.upsertStaffCredential("manager", {
    username: "cash1",
    password: "secret",
    displayName: "Cashier 1",
    role: "cashier"
  });
  const seller = engine.upsertStaffCredential("manager", {
    username: "sell1",
    password: "sellpass",
    displayName: "Seller 1",
    role: "seller"
  });

  assert.ok(engine.authenticateLocal("cash1", "secret"));
  assert.ok(engine.authenticateLocal("sell1", "sellpass"));

  assert.throws(() => {
    engine.upsertStaffCredential("manager", {
      username: "rogueadmin",
      password: "123",
      role: "admin"
    });
  }, /^Error: Not allowed$/);

  assert.throws(() => {
    engine.upsertStaffCredential("manager", {
      uid: "local_admin_omini",
      username: "omini",
      password: "hacked",
      role: "admin"
    });
  }, /^Error: Not allowed$/);

  assert.throws(() => {
    engine.upsertStaffCredential("manager", {
      uid: "local_admin_omini",
      username: "omini",
      displayName: "Disabled Admin",
      role: "seller",
      active: false
    });
  }, /^Error: Not allowed$/);

  assert.throws(() => {
    engine.deleteStaffCredential("manager", "local_admin_omini");
  }, /^Error: Not allowed$/);

  // Cashier & Seller cannot create/edit/delete users
  assert.throws(() => {
    engine.upsertStaffCredential("cashier", { username: "u2", password: "123", role: "seller" });
  }, /^Error: Not allowed$/);
  assert.throws(() => {
    engine.upsertStaffCredential("seller", { username: "u3", password: "123", role: "seller" });
  }, /^Error: Not allowed$/);
  assert.throws(() => {
    engine.deleteStaffCredential("cashier", seller.uid);
  }, /^Error: Not allowed$/);
  assert.throws(() => {
    engine.deleteStaffCredential("seller", cashier.uid);
  }, /^Error: Not allowed$/);

  // Last remaining admin cannot be deleted, demoted, or deactivated
  assert.throws(() => {
    engine.deleteStaffCredential("admin", "local_admin_omini");
  }, /last remaining admin/);
  assert.throws(() => {
    engine.upsertStaffCredential("admin", {
      uid: "local_admin_omini",
      username: "omini",
      displayName: "omini",
      role: "manager",
      active: true
    });
  }, /last remaining admin/);
  assert.throws(() => {
    engine.upsertStaffCredential("admin", {
      uid: "local_admin_omini",
      username: "omini",
      displayName: "omini",
      role: "admin",
      active: false
    });
  }, /last remaining admin/);

  // Only admin can create a second admin; once 2 active admins exist, one can be demoted or deleted, leaving the last one protected
  const secondAdmin = engine.upsertStaffCredential("admin", {
    username: "admin2",
    password: "pass2",
    displayName: "Second Admin",
    role: "admin"
  });
  assert.ok(engine.authenticateLocal("admin2", "pass2"));
  engine.upsertStaffCredential("admin", {
    uid: secondAdmin.uid,
    username: "admin2",
    displayName: "Second Admin",
    role: "manager"
  });
  assert.equal(engine.authenticateLocal("admin2", "pass2").role, "manager");
  // Now local_admin_omini is again the sole active admin and stays protected
  assert.throws(() => {
    engine.deleteStaffCredential("admin", "local_admin_omini");
  }, /last remaining admin/);

  // Manager can delete non-admin users (cashier/seller)
  const tempSeller = engine.upsertStaffCredential("manager", {
    username: "temp_sell",
    password: "tp",
    displayName: "Temp Seller",
    role: "seller"
  });
  assert.ok(engine.authenticateLocal("temp_sell", "tp"));
  const delRes = engine.deleteStaffCredential("manager", tempSeller.uid);
  assert.equal(delRes.deleted, true);
  assert.equal(engine.authenticateLocal("temp_sell", "tp"), null);

  // Password change works and invalidates old password
  engine.upsertStaffCredential("manager", {
    uid: seller.uid,
    username: "sell1",
    password: "newpassword99",
    displayName: "Seller Updated",
    role: "seller"
  });
  assert.equal(engine.authenticateLocal("sell1", "sellpass"), null);
  assert.equal(engine.authenticateLocal("sell1", "newpassword99").displayName, "Seller Updated");

  // Cashier can update products & stock, CANNOT complete sales or delete products
  const updatedProd = engine.saveProduct(cashier, { nameAr: "تست", nameEn: "Test Cream", retailPrice: 100 });
  assert.equal(updatedProd.retailPrice, 100);
  engine.openBusinessDay(admin, { openingCash: 50 });
  assert.throws(() => {
    engine.completeSale(cashier, { items: [{ productId: "seed_oud_royal", qty: 1, wholesale: false }] });
  }, /Cashier cannot complete sales/);
  assert.throws(() => {
    engine.deleteProduct(cashier, updatedProd.id);
  }, /Permission denied/);

  // Seller can complete sales, CANNOT update products or receive stock
  assert.throws(() => {
    engine.saveProduct(seller, { nameAr: "منع", nameEn: "Blocked", retailPrice: 50 });
  }, /Seller cannot update products/);
  assert.throws(() => {
    engine.receiveStock(seller, { productId: "seed_oud_royal", qty: 5, unitCost: 140 });
  }, /Seller cannot receive stock/);

  const saleInv = engine.completeSale(seller, {
    items: [{ productId: "seed_oud_royal", qty: 2, wholesale: false }],
    paymentMethod: "cash"
  });
  assert.equal(saleInv.cashierUid, seller.uid);
});

test("LAN snapshot member sync enables immediate PC login for Android-created users", () => {
  const { sha256 } = require("../src/core/store-engine");
  const engine = new PcStoreEngine(tmpStoreFile());
  engine.mergeSnapshot({
    members: [
      {
        uid: "staff_android_1",
        username: "sara",
        passwordHash: sha256("sara2026"),
        displayName: "Sara Seller",
        role: "seller",
        active: true,
        createdAtMillis: Date.now(),
        updatedAtMillis: Date.now()
      }
    ]
  });
  const loggedIn = engine.authenticateLocal("sara", "sara2026");
  assert.ok(loggedIn);
  assert.equal(loggedIn.role, "seller");
  assert.equal(loggedIn.displayName, "Sara Seller");
});

test("offline POS sale, return, and movement-ledger stock calculation", () => {
  const engine = new PcStoreEngine(tmpStoreFile());
  const admin = engine.authenticateLocal("omini", "admin116");
  engine.openBusinessDay(admin, { openingCash: 100 });

  const initialStock = engine.getStockMap().seed_oud_royal;
  assert.equal(initialStock, 42);

  const inv = engine.completeSale(admin, {
    items: [{ productId: "seed_oud_royal", qty: 5, wholesale: true }],
    discount: 25,
    paymentMethod: "cash"
  });
  assert.equal(inv.saleType, "wholesale");
  assert.equal(inv.total, 5 * 225 - 25);
  assert.equal(engine.getStockMap().seed_oud_royal, 37);

  engine.returnInvoice(admin, inv.id);
  assert.equal(engine.getStockMap().seed_oud_royal, 42);
});

test("LAN sync server connects with Android snapshot and merges prices, sales, and updates offline", async () => {
  const handle = await startPcServer({ dataFilePath: tmpStoreFile(), port: 0, host: "127.0.0.1" });
  try {
    const admin = handle.engine.authenticateLocal("omini", "admin116");
    // PC updates retail price of seed_oud_royal and publishes Android v2 release
    const oud = handle.engine.state.products.find((p) => p.id === "seed_oud_royal");
    handle.engine.saveProduct(admin, { ...oud, retailPrice: 315.0 });
    handle.engine.publishRelease(admin, {
      platform: "android",
      versionCode: 2,
      versionName: "1.1.0",
      downloadUrl: "https://example.com/aroma-v1.1.0.apk",
      releaseNotesEn: "Synced from PC"
    });

    // Check /api/health
    const healthRes = await fetch(`http://127.0.0.1:${handle.port}/api/health`);
    assert.equal(healthRes.status, 200);
    const health = await healthRes.json();
    assert.equal(health.ok, true);
    assert.equal(health.productsCount, 6);

    // Simulated Android offline client sends a sale movement + invoice via POST /api/sync
    const now = Date.now();
    const syncRes = await fetch(`http://127.0.0.1:${handle.port}/api/sync`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        clientDevice: "Android (omini)",
        products: [],
        movements: [
          {
            id: "mov_android_offline_1",
            productId: "seed_white_musk",
            type: "sale",
            qtyDelta: -3,
            unitCost: 65,
            reason: "Sale INV-AND-001",
            refId: "inv_android_1",
            actorUid: "local_admin_omini",
            actorName: "omini",
            createdAtMillis: now
          }
        ],
        invoices: [
          {
            id: "inv_android_1",
            invoiceNumber: "INV-AND-001",
            saleType: "retail",
            customerId: "",
            customerName: "",
            itemsJson: "[]",
            itemsSummary: "Pure White Musk x3",
            subtotal: 435,
            discount: 0,
            total: 435,
            costTotal: 195,
            paymentMethod: "cash",
            status: "completed",
            businessDayId: "day_1",
            cashierUid: "local_admin_omini",
            cashierName: "omini",
            createdAtMillis: now,
            updatedAtMillis: now
          }
        ]
      })
    });
    assert.equal(syncRes.status, 200);
    const merged = await syncRes.json();

    // PC received Android's offline sale and recalculated stock (30 - 3 = 27)
    assert.equal(handle.engine.getStockMap().seed_white_musk, 27);
    assert.ok(handle.engine.state.invoices.some((i) => i.id === "inv_android_1"));

    // Android received PC's updated price (315.0) and v2 release
    const mergedOud = merged.products.find((p) => p.id === "seed_oud_royal");
    assert.equal(mergedOud.retailPrice, 315.0);
    assert.ok(merged.releases.some((r) => r.versionCode === 2));
  } finally {
    await handle.close();
  }
});

test("USD payment option records usd method, usdAmount, usdRateSsp, and keeps SSP day totals", () => {
  const engine = new PcStoreEngine(tmpStoreFile());
  const admin = engine.authenticateLocal("omini", "admin116");
  engine.openBusinessDay(admin, { openingCash: 0 });

  const inv = engine.completeSale(admin, {
    items: [{ productId: "seed_white_musk", qty: 2, wholesale: false }],
    discount: 0,
    paymentMethod: "usd",
    usdRateSsp: 1450
  });
  assert.equal(inv.paymentMethod, "usd");
  assert.equal(inv.total, 290);
  assert.equal(inv.usdRateSsp, 1450);
  assert.equal(inv.usdAmount, Number((290 / 1450).toFixed(2)));
  assert.ok(inv.itemsSummary.includes("USD"));
  assert.equal(engine.getOpenBusinessDay().totalSales, 290);
});
