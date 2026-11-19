const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_UID = "admin_owner_1";
const MANAGER_UID = "manager_user_2";
const CASHIER_UID = "cashier_user_3";
const SELLER_UID = "seller_user_4";
const OUTSIDER_UID = "outsider_999";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function nowDate() {
  return new Date(Date.now() - 2000);
}

async function seedStoreWithRoles() {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const ts = nowDate();
    const members = [ADMIN_UID, MANAGER_UID, CASHIER_UID, SELLER_UID];
    await db.collection("stores").doc(ADMIN_UID).set({
      ownerUid: ADMIN_UID,
      nameAr: "دار العطور",
      nameEn: "Aroma Boutique",
      memberUids: members,
      createdAt: ts,
      updatedAt: ts,
    });
    await db.collection("stores").doc(ADMIN_UID).collection("members").doc(ADMIN_UID).set({
      uid: ADMIN_UID,
      displayName: "Owner Admin",
      role: "admin",
      active: true,
      memberUids: members,
      createdAt: ts,
      updatedAt: ts,
    });
    await db.collection("stores").doc(ADMIN_UID).collection("members").doc(MANAGER_UID).set({
      uid: MANAGER_UID,
      displayName: "General Manager",
      role: "manager",
      active: true,
      memberUids: members,
      createdAt: ts,
      updatedAt: ts,
    });
    await db.collection("stores").doc(ADMIN_UID).collection("members").doc(CASHIER_UID).set({
      uid: CASHIER_UID,
      displayName: "Store Cashier",
      role: "cashier",
      active: true,
      memberUids: members,
      createdAt: ts,
      updatedAt: ts,
    });
    await db.collection("stores").doc(ADMIN_UID).collection("members").doc(SELLER_UID).set({
      uid: SELLER_UID,
      displayName: "Store Seller",
      role: "seller",
      active: true,
      memberUids: members,
      createdAt: ts,
      updatedAt: ts,
    });
  });
}

function validProductPayload(members) {
  const ts = nowDate();
  return {
    nameAr: "عود ملكي",
    nameEn: "Royal Oud",
    category: "perfume",
    sku: "OUD-101",
    barcode: "6291000010101",
    costPrice: 120.0,
    retailPrice: 250.0,
    wholesalePrice: 190.0,
    wholesaleMinQty: 5,
    unitAr: "زجاجة 100 مل",
    unitEn: "100ml Bottle",
    lowStockThreshold: 10,
    isDeleted: false,
    memberUids: members,
    createdAt: ts,
    updatedAt: ts,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) await testEnv.cleanup();
});

beforeEach(async () => {
  if (testEnv) await testEnv.clearFirestore();
});

test("Unauthenticated user cannot read or write store data", async () => {
  await seedStoreWithRoles();
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("stores").doc(ADMIN_UID).get());
  await assertFails(
    unauth.collection("stores").doc(ADMIN_UID).collection("products").get()
  );
  await assertFails(
    unauth.collection("users").doc(ADMIN_UID).get()
  );
});

test("Outsider cannot read or list another store's products or settings", async () => {
  await seedStoreWithRoles();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx
      .firestore()
      .collection("stores")
      .doc(ADMIN_UID)
      .collection("products")
      .doc("prod_1")
      .set(validProductPayload([ADMIN_UID, MANAGER_UID, CASHIER_UID]));
  });
  const outsider = testEnv.authenticatedContext(OUTSIDER_UID).firestore();
  await assertFails(outsider.collection("stores").doc(ADMIN_UID).get());
  await assertFails(
    outsider.collection("stores").doc(ADMIN_UID).collection("products").doc("prod_1").get()
  );
  await assertFails(
    outsider
      .collection("stores")
      .doc(ADMIN_UID)
      .collection("products")
      .where("category", "==", "perfume")
      .get()
  );
});

test("Query without memberUids filter fails (rules are not filters)", async () => {
  await seedStoreWithRoles();
  const manager = testEnv.authenticatedContext(MANAGER_UID).firestore();
  await assertFails(
    manager.collection("stores").doc(ADMIN_UID).collection("products").get()
  );
});

test("Manager can create/update products and query with memberUids filter", async () => {
  await seedStoreWithRoles();
  const members = [ADMIN_UID, MANAGER_UID, CASHIER_UID];
  const manager = testEnv.authenticatedContext(MANAGER_UID).firestore();
  const productsCol = manager.collection("stores").doc(ADMIN_UID).collection("products");

  await assertSucceeds(productsCol.doc("prod_1").set(validProductPayload(members)));
  await assertSucceeds(
    productsCol.where("memberUids", "array-contains", MANAGER_UID).get()
  );
});

test("Cashier can create/update products & adjustments, CANNOT delete products or sell", async () => {
  await seedStoreWithRoles();
  const members = [ADMIN_UID, MANAGER_UID, CASHIER_UID, SELLER_UID];
  const cashier = testEnv.authenticatedContext(CASHIER_UID).firestore();
  const productsCol = cashier.collection("stores").doc(ADMIN_UID).collection("products");

  await assertSucceeds(productsCol.doc("prod_c1").set(validProductPayload(members)));
  await assertSucceeds(productsCol.doc("prod_c1").update({ retailPrice: 275.0, updatedAt: nowDate() }));
  await assertFails(productsCol.doc("prod_c1").delete());

  const ts = nowDate();
  await assertSucceeds(
    cashier.collection("stores").doc(ADMIN_UID).collection("movements").doc("mov_adj_1").set({
      productId: "prod_c1",
      type: "adjustment",
      qtyDelta: -5,
      unitCost: 120.0,
      reason: "Damaged",
      actorUid: CASHIER_UID,
      actorName: "Store Cashier",
      memberUids: members,
      createdAt: ts,
    })
  );
  await assertFails(
    cashier.collection("stores").doc(ADMIN_UID).collection("movements").doc("mov_sale_c").set({
      productId: "prod_c1",
      type: "sale",
      qtyDelta: -1,
      unitCost: 120.0,
      reason: "Unauthorized sale",
      refId: "inv_c",
      actorUid: CASHIER_UID,
      actorName: "Store Cashier",
      memberUids: members,
      createdAt: ts,
    })
  );
});

test("Seller can create sale movement & invoice, CANNOT update products or create adjustments", async () => {
  await seedStoreWithRoles();
  const members = [ADMIN_UID, MANAGER_UID, CASHIER_UID, SELLER_UID];
  const seller = testEnv.authenticatedContext(SELLER_UID).firestore();
  const ts = nowDate();

  await assertFails(
    seller.collection("stores").doc(ADMIN_UID).collection("products").doc("prod_s1").set(validProductPayload(members))
  );

  await assertSucceeds(
    seller.collection("stores").doc(ADMIN_UID).collection("movements").doc("mov_sale_1").set({
      productId: "prod_1",
      type: "sale",
      qtyDelta: -2,
      unitCost: 120.0,
      reason: "POS Sale",
      refId: "inv_1",
      actorUid: SELLER_UID,
      actorName: "Store Seller",
      memberUids: members,
      createdAt: ts,
    })
  );

  await assertFails(
    seller.collection("stores").doc(ADMIN_UID).collection("movements").doc("mov_adj_1").set({
      productId: "prod_1",
      type: "adjustment",
      qtyDelta: -5,
      unitCost: 120.0,
      reason: "Damaged",
      actorUid: SELLER_UID,
      actorName: "Store Seller",
      memberUids: members,
      createdAt: ts,
    })
  );

  // Movements are append-only (cannot update or delete)
  await assertFails(
    seller.collection("stores").doc(ADMIN_UID).collection("movements").doc("mov_sale_1").delete()
  );
});

test("Admin ONLY can modify store settings and publish app releases (Manager blocked)", async () => {
  await seedStoreWithRoles();
  const members = [ADMIN_UID, MANAGER_UID, CASHIER_UID];
  const ts = nowDate();

  const settingsPayload = {
    storeNameAr: "دار العطور الفاخرة",
    storeNameEn: "Aroma Luxury",
    primaryColorHex: "#1B4D3E",
    secondaryColorHex: "#C59B27",
    sessionTimeoutMinutes: 30,
    notifyLowStock: true,
    notifyDayNotClosed: true,
    notifyNewUser: true,
    notifySyncErrors: true,
    notifyNewVersion: true,
    notifyRoles: ["admin", "manager"],
    memberUids: members,
    createdAt: ts,
    updatedAt: ts,
  };

  const releasePayload = {
    platform: "android",
    versionCode: 2,
    versionName: "1.1.0",
    downloadUrl: "https://example.com/aroma-pos-1.1.0.apk",
    releaseNotesAr: "تحديث جديد",
    releaseNotesEn: "New update",
    isMandatory: false,
    memberUids: members,
    createdAt: ts,
    updatedAt: ts,
  };

  const manager = testEnv.authenticatedContext(MANAGER_UID).firestore();
  await assertFails(
    manager.collection("stores").doc(ADMIN_UID).collection("settings").doc("main").set(settingsPayload)
  );
  await assertFails(
    manager.collection("stores").doc(ADMIN_UID).collection("app_releases").doc("rel_2").set(releasePayload)
  );

  const admin = testEnv.authenticatedContext(ADMIN_UID).firestore();
  await assertSucceeds(
    admin.collection("stores").doc(ADMIN_UID).collection("settings").doc("main").set(settingsPayload)
  );
  await assertSucceeds(
    admin.collection("stores").doc(ADMIN_UID).collection("app_releases").doc("rel_2").set(releasePayload)
  );
});

test("Shadow update with ghost field is rejected", async () => {
  await seedStoreWithRoles();
  const members = [ADMIN_UID, MANAGER_UID, CASHIER_UID];
  const admin = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const payload = validProductPayload(members);
  payload.ghostField = true;
  await assertFails(
    admin.collection("stores").doc(ADMIN_UID).collection("products").doc("prod_ghost").set(payload)
  );
});

test("Cashier cannot promote self to admin in members collection", async () => {
  await seedStoreWithRoles();
  const members = [ADMIN_UID, MANAGER_UID, CASHIER_UID];
  const cashier = testEnv.authenticatedContext(CASHIER_UID).firestore();
  await assertFails(
    cashier.collection("stores").doc(ADMIN_UID).collection("members").doc(CASHIER_UID).update({
      role: "admin",
      updatedAt: nowDate(),
    })
  );
});
