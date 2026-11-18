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

test("authenticates admin omini/admin116 and enforces cashier RBAC", () => {
  const engine = new PcStoreEngine(tmpStoreFile());
  assert.equal(engine.authenticateLocal("omini", "wrong"), null);
  const admin = engine.authenticateLocal("omini", "admin116");
  assert.ok(admin);
  assert.equal(admin.role, "admin");

  const cashier = engine.upsertStaffCredential("admin", {
    username: "cash1",
    password: "secret",
    displayName: "Cashier 1",
    role: "cashier"
  });

  assert.throws(() => {
    engine.saveProduct(cashier, { nameAr: "تست", nameEn: "Test", retailPrice: 100 });
  }, /Admin or General Manager/);
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
