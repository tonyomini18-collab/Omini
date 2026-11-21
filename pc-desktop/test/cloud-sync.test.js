const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const { PcStoreEngine } = require("../src/core/store-engine");
const { CloudSync } = require("../src/electron/firebase-cloud-sync");

function tmpStoreFile() {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "aroma-cloud-test-"));
  return path.join(dir, "store.json");
}

// Minimal in-memory Firestore that applies the same key checks as firestore.rules.
function fakeFirestore() {
  const docs = new Map();
  const SV = { __serverTimestamp: true };
  const ts = (millis = Date.now()) => ({ toMillis: () => millis });
  const resolve = (data) => {
    const out = {};
    for (const [k, v] of Object.entries(data)) out[k] = v === SV ? ts() : v;
    return out;
  };
  const required = {
    products: ["nameAr", "nameEn", "category", "sku", "costPrice", "retailPrice", "wholesalePrice", "wholesaleMinQty", "unitAr", "unitEn", "lowStockThreshold", "isDeleted", "memberUids", "createdAt", "updatedAt"],
    movements: ["productId", "type", "qtyDelta", "unitCost", "reason", "actorUid", "actorName", "memberUids", "createdAt"],
    invoices: ["invoiceNumber", "saleType", "itemsSummary", "subtotal", "discount", "total", "costTotal", "paymentMethod", "status", "businessDayId", "cashierUid", "cashierName", "memberUids", "createdAt", "updatedAt"],
    customers: ["name", "balance", "memberUids", "createdAt", "updatedAt"],
    business_days: ["dateStr", "status", "openedByUid", "openedByName", "openingCash", "totalSales", "cashSales", "transferSales", "creditSales", "itemsSoldCount", "holidayOverride", "memberUids", "createdAt", "updatedAt"],
    audit_logs: ["actorUid", "actorName", "actorRole", "action", "targetType", "targetId", "details", "memberUids", "createdAt"],
    holidays: ["titleAr", "titleEn", "isWeeklyOffDay", "dayOfWeek", "memberUids", "createdAt", "updatedAt"]
  };
  const fs_ = {
    serverTimestamp: () => SV,
    doc: (_db, ...segs) => ({ path: segs.join("/"), coll: segs[2] }),
    getDoc: async (ref) => {
      const d = docs.get(ref.path);
      return { exists: () => Boolean(d), data: () => d };
    },
    setDoc: async (ref, data, opts) => {
      const merged = opts && opts.merge ? { ...(docs.get(ref.path) || {}), ...resolve(data) } : resolve(data);
      const need = required[ref.coll] || [];
      for (const k of need) assert.ok(k in merged, `${ref.coll} missing ${k}`);
      assert.ok(Array.isArray(merged.memberUids) ? merged.memberUids.includes("store1") : true);
      docs.set(ref.path, merged);
    },
    updateDoc: async (ref, data) => {
      assert.ok(docs.has(ref.path), "update of missing doc");
      docs.set(ref.path, { ...docs.get(ref.path), ...resolve(data) });
    }
  };
  return { docs, fs: fs_, ts };
}

function makeSync(engine, fake) {
  const cs = new CloudSync({ engine, loadConfig: () => ({ email: "a@b.c", password: "x" }), log: { warn() {} } });
  cs.mods = { fs: fake.fs };
  cs.db = {};
  cs.uid = "store1";
  cs.storeId = "store1";
  cs.connected = true;
  return cs;
}

test("offline PC work is uploaded to cloud with rule-valid documents", async () => {
  const engine = new PcStoreEngine(tmpStoreFile());
  const admin = engine.authenticateLocal("omini", "admin116");
  engine.openBusinessDay(admin, { openingCash: 10 });
  const prod = engine.saveProduct(admin, { nameEn: "Oud", nameAr: "عود", category: "perfume", costPrice: 5, retailPrice: 10, wholesalePrice: 8 }, 20);
  engine.completeSale(admin, { items: [{ productId: prod.id, qty: 2 }], paymentMethod: "cash" });
  engine.saveParty({ partyType: "customer", name: "Ali", phone: "123" });
  assert.ok(engine.state.pendingCloudSync.length >= 5);

  const fake = fakeFirestore();
  const cs = makeSync(engine, fake);
  await cs.pushPending();

  assert.equal(engine.state.pendingCloudSync.length, 0, cs.lastError);
  const paths = [...fake.docs.keys()];
  assert.ok(paths.some((p) => p.includes("/products/")));
  assert.ok(paths.some((p) => p.includes("/invoices/")));
  assert.ok(paths.some((p) => p.includes("/movements/")));
  assert.ok(paths.some((p) => p.includes("/business_days/")));
  assert.ok(paths.some((p) => p.includes("/customers/")));
  assert.ok(paths.some((p) => p.includes("/audit_logs/")));
  const inv = [...fake.docs.entries()].find(([p]) => p.includes("/invoices/"))[1];
  assert.equal(inv.cashierUid, "store1");

  // pushing the same ops again must not break (ledger docs are skipped, others updated)
  engine.enqueueCloudOp("invoices", inv && [...fake.docs.keys()].find((p) => p.includes("/invoices/")).split("/").pop(), { ...engine.state.invoices[0], status: "returned" });
  await cs.pushPending();
  assert.equal(engine.state.pendingCloudSync.length, 0, cs.lastError);
});

test("a network failure keeps operations queued", async () => {
  const engine = new PcStoreEngine(tmpStoreFile());
  const admin = engine.authenticateLocal("omini", "admin116");
  engine.saveProduct(admin, { nameEn: "Cream", category: "cream", retailPrice: 3 }, 0);
  const before = engine.state.pendingCloudSync.length;
  const fake = fakeFirestore();
  fake.fs.getDoc = async () => {
    const e = new Error("Failed to get document because the client is offline.");
    e.code = "unavailable";
    throw e;
  };
  const cs = makeSync(engine, fake);
  await cs.pushPending();
  assert.equal(engine.state.pendingCloudSync.length, before);
  assert.equal(cs.connected, false);
});

test("phone changes from the cloud are merged into PC data", () => {
  const engine = new PcStoreEngine(tmpStoreFile());
  const fake = fakeFirestore();
  const cs = makeSync(engine, fake);
  const t = fake.ts(Date.now() + 1000);
  const mk = (name, id, d) => {
    const mapped = cs.mapIn(name, id, d);
    (cs.incoming[mapped.key] ||= []).push(mapped.entity);
  };
  mk("products", "p_phone", { nameAr: "ع", nameEn: "Phone product", category: "perfume", sku: "S1", costPrice: 1, retailPrice: 4, wholesalePrice: 3, wholesaleMinQty: 2, unitAr: "قطعة", unitEn: "Unit", lowStockThreshold: 1, isDeleted: false, createdAt: t, updatedAt: t });
  mk("movements", "m_phone", { productId: "p_phone", type: "opening", qtyDelta: 9, unitCost: 1, reason: "x", actorUid: "u", actorName: "n", createdAt: t });
  mk("invoices", "i_phone", { invoiceNumber: "INV-9", saleType: "retail", itemsSummary: "x", itemsJson: "[]", subtotal: 4, discount: 0, total: 4, costTotal: 1, paymentMethod: "cash", status: "completed", businessDayId: "d", cashierUid: "u", cashierName: "n", createdAt: t, updatedAt: t });
  mk("customers", "c_phone", { name: "Sara", balance: 0, createdAt: t, updatedAt: t });
  let changed = 0;
  cs.onChange = () => { changed += 1; };
  cs.flushIncoming();

  assert.ok(engine.state.products.some((p) => p.id === "p_phone" && p.nameEn === "Phone product"));
  assert.ok(engine.state.movements.some((m) => m.id === "m_phone"));
  assert.ok(engine.state.invoices.some((i) => i.id === "i_phone"));
  assert.ok(engine.state.parties.some((p) => p.id === "c_phone" && p.partyType === "customer"));
  assert.equal(changed, 1);
  const stock = engine.getProductsWithStock(false).find((p) => p.id === "p_phone");
  assert.equal(stock.currentStock, 9);
  // data that came from the cloud must not be queued for upload again
  assert.equal(engine.state.pendingCloudSync.filter((o) => o.documentId === "p_phone").length, 0);
});
