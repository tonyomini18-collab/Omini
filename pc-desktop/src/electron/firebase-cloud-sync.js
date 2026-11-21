"use strict";
/**
 * Two-way cloud sync between the PC app and the same Firebase store the Android app uses.
 *
 *  - PC -> cloud: everything queued in engine.state.pendingCloudSync is uploaded when internet is available.
 *  - cloud -> PC: live listeners pull what phones changed and merge it into the PC data.
 *
 * It signs in with the store owner's email + password. Nothing here can break offline work:
 * every call is wrapped, failures stay in the queue and are retried.
 */
const FIRESTORE_DATABASE_ID = "ai-studio-android-aromapos-5c39add0-9320-45cf-a066-a5c1f668de36";
const FIREBASE_WEB_CONFIG = {
  apiKey: "AIzaSyAilLb2rxXwLpulAMum7sf6Bzcm66nkKBM",
  authDomain: "gen-lang-client-0095559461.firebaseapp.com",
  projectId: "gen-lang-client-0095559461",
  storageBucket: "gen-lang-client-0095559461.firebasestorage.app"
};

const PULL_COLLECTIONS = [
  "products", "movements", "invoices", "customers", "suppliers",
  "business_days", "audit_logs", "holidays", "settings"
];
const PUSH_INTERVAL_MS = 20 * 1000;
const RECONNECT_INTERVAL_MS = 60 * 1000;

function withTimeout(promise, ms, label = "operation") {
  let timer;
  const timeout = new Promise((_, reject) => {
    timer = setTimeout(() => reject(new Error(`${label} timed out`)), ms);
  });
  return Promise.race([promise, timeout]).finally(() => clearTimeout(timer));
}

const clip = (v, n) => String(v == null ? "" : v).slice(0, n);
const nonBlank = (v, fallback, n) => clip(String(v || "").trim() || fallback, n);
const money = (v) => Math.max(0, Number(v) || 0);
const whole = (v, min = 0) => Math.max(min, Math.trunc(Number(v) || 0));
const oneOf = (v, list, fallback) => (list.includes(v) ? v : fallback);

function isNetworkError(e) {
  const code = String((e && e.code) || "");
  const msg = String((e && e.message) || "").toLowerCase();
  return (
    code === "unavailable" || code === "auth/network-request-failed" ||
    msg.includes("offline") || msg.includes("network") || msg.includes("timed out") ||
    msg.includes("failed to fetch") || msg.includes("enotfound") || msg.includes("econn")
  );
}

class CloudSync {
  constructor({ engine, loadConfig, onChange, log = console }) {
    this.engine = engine;
    this.loadConfig = loadConfig;
    this.onChange = onChange || (() => {});
    this.log = log;
    this.mods = null;
    this.app = null;
    this.db = null;
    this.auth = null;
    this.uid = "";
    this.storeId = "";
    this.connected = false;
    this.connecting = false;
    this.pushing = false;
    this.unsubs = [];
    this.timer = null;
    this.incoming = {};
    this.flushTimer = null;
    this.lastPushAt = 0;
    this.lastPullAt = 0;
    this.lastError = "";
    this.lastConnectTry = 0;
  }

  status() {
    const cfg = this.loadConfig() || {};
    return {
      configured: Boolean(cfg.email && cfg.password),
      email: cfg.email || "",
      connected: this.connected,
      pending: (this.engine.state.pendingCloudSync || []).length,
      lastPushAt: this.lastPushAt,
      lastPullAt: this.lastPullAt,
      lastError: this.lastError
    };
  }

  start() {
    if (this.timer) return;
    this.timer = setInterval(() => this.tick().catch(() => {}), PUSH_INTERVAL_MS);
    setTimeout(() => this.tick().catch(() => {}), 3000);
  }

  async stop() {
    if (this.timer) clearInterval(this.timer);
    this.timer = null;
    await this.disconnect();
  }

  async restart() {
    await this.disconnect();
    this.lastConnectTry = 0;
    this.start();
    await this.tick().catch(() => {});
  }

  async disconnect() {
    for (const u of this.unsubs) {
      try { u(); } catch (_) { /* ignore */ }
    }
    this.unsubs = [];
    this.connected = false;
    try { if (this.auth && this.mods) await this.mods.auth.signOut(this.auth); } catch (_) { /* ignore */ }
    try { if (this.app && this.mods) await this.mods.app.deleteApp(this.app); } catch (_) { /* ignore */ }
    this.app = null;
    this.db = null;
    this.auth = null;
  }

  async tick() {
    const cfg = this.loadConfig();
    if (!cfg || !cfg.email || !cfg.password) return;
    if (!this.connected) {
      if (Date.now() - this.lastConnectTry < RECONNECT_INTERVAL_MS) return;
      this.lastConnectTry = Date.now();
      await this.connect(cfg);
    }
    if (this.connected) await this.pushPending();
  }

  async syncNow() {
    this.lastConnectTry = 0;
    await this.tick();
    return this.status();
  }

  async connect(cfg) {
    if (this.connecting) return;
    this.connecting = true;
    try {
      if (!this.mods) {
        this.mods = {
          app: require("firebase/app"),
          auth: require("firebase/auth"),
          fs: require("firebase/firestore")
        };
      }
      const { app, auth, fs } = this.mods;
      this.app = app.initializeApp({
        ...FIREBASE_WEB_CONFIG,
        apiKey: cfg.apiKey || FIREBASE_WEB_CONFIG.apiKey
      });
      this.auth = auth.getAuth(this.app);
      this.db = fs.initializeFirestore(this.app, { experimentalAutoDetectLongPolling: true }, FIRESTORE_DATABASE_ID);
      const cred = await withTimeout(
        auth.signInWithEmailAndPassword(this.auth, cfg.email, cfg.password),
        30000,
        "sign in"
      );
      this.uid = cred.user.uid;
      this.storeId = cfg.storeId || this.uid;
      this.startListeners();
      this.connected = true;
      this.lastError = "";
    } catch (e) {
      this.lastError = this.describe(e);
      this.log.warn("[cloud] connect failed:", this.lastError);
      await this.disconnect();
    } finally {
      this.connecting = false;
    }
  }

  describe(e) {
    const code = e && e.code ? `${e.code}: ` : "";
    return `${code}${(e && e.message) || e}`.slice(0, 300);
  }

  // ---------- cloud -> PC ----------

  startListeners() {
    const { fs } = this.mods;
    for (const name of PULL_COLLECTIONS) {
      const q = fs.query(
        fs.collection(this.db, "stores", this.storeId, name),
        fs.where("memberUids", "array-contains", this.uid)
      );
      const unsub = fs.onSnapshot(
        q,
        (snap) => this.onSnapshot(name, snap),
        (err) => {
          this.lastError = `${name}: ${this.describe(err)}`;
        }
      );
      this.unsubs.push(unsub);
    }
  }

  onSnapshot(name, snap) {
    try {
      const changes = snap.docChanges().filter((c) => c.type !== "removed");
      if (!changes.length) return;
      for (const c of changes) {
        const mapped = this.mapIn(name, c.doc.id, c.doc.data());
        if (!mapped) continue;
        const { key, entity } = mapped;
        (this.incoming[key] ||= []).push(entity);
      }
      clearTimeout(this.flushTimer);
      this.flushTimer = setTimeout(() => this.flushIncoming(), 500);
    } catch (e) {
      this.lastError = this.describe(e);
    }
  }

  flushIncoming() {
    const batch = this.incoming;
    this.incoming = {};
    if (!Object.keys(batch).length) return;
    try {
      const snapshot = { ...batch };
      if (batch.settings) snapshot.settings = batch.settings[batch.settings.length - 1];
      this.engine.mergeSnapshot(snapshot);
      this.lastPullAt = Date.now();
      this.onChange();
    } catch (e) {
      this.lastError = this.describe(e);
    }
  }

  findLocal(list, id) {
    return (list || []).find((x) => x.id === id) || {};
  }

  mapIn(name, id, d) {
    const st = this.engine.state;
    const ms = (v) => (v && typeof v.toMillis === "function" ? v.toMillis() : Date.now());
    const created = ms(d.createdAt);
    const updated = d.updatedAt ? ms(d.updatedAt) : created;
    switch (name) {
      case "products": {
        const ex = this.findLocal(st.products, id);
        return {
          key: "products",
          entity: {
            ...ex, id,
            nameAr: d.nameAr, nameEn: d.nameEn, category: d.category, sku: d.sku,
            barcode: d.barcode || "", imageUri: d.imageUri || "",
            drawableResName: ex.drawableResName || (d.category === "cream" ? "img_cream_jar" : "img_perfume_oud"),
            costPrice: d.costPrice, retailPrice: d.retailPrice, wholesalePrice: d.wholesalePrice,
            wholesaleMinQty: d.wholesaleMinQty, unitAr: d.unitAr, unitEn: d.unitEn,
            lowStockThreshold: d.lowStockThreshold, isDeleted: Boolean(d.isDeleted),
            createdAtMillis: created, updatedAtMillis: updated
          }
        };
      }
      case "movements":
        return {
          key: "movements",
          entity: {
            id, productId: d.productId, type: d.type, qtyDelta: d.qtyDelta, unitCost: d.unitCost,
            reason: d.reason || "", refId: d.refId || "", actorUid: d.actorUid, actorName: d.actorName,
            createdAtMillis: created
          }
        };
      case "invoices": {
        const ex = this.findLocal(st.invoices, id);
        return {
          key: "invoices",
          entity: {
            ...ex, id,
            invoiceNumber: d.invoiceNumber, saleType: d.saleType,
            customerId: d.customerId || "", customerName: d.customerName || "",
            itemsJson: d.itemsJson || ex.itemsJson || d.itemsSummary, itemsSummary: d.itemsSummary,
            subtotal: d.subtotal, discount: d.discount, total: d.total, costTotal: d.costTotal,
            paymentMethod: d.paymentMethod, status: d.status, businessDayId: d.businessDayId,
            cashierUid: d.cashierUid, cashierName: d.cashierName,
            currencyCode: d.currencyCode || (d.paymentMethod === "usd" ? "USD" : "SSP"),
            usdRateSsp: d.usdRateSsp == null ? 0 : d.usdRateSsp,
            usdTotal: d.usdTotal == null ? 0 : d.usdTotal,
            createdAtMillis: created, updatedAtMillis: updated
          }
        };
      }
      case "customers":
      case "suppliers": {
        const ex = this.findLocal(st.parties, id);
        return {
          key: "parties",
          entity: {
            ...ex, id,
            partyType: name === "suppliers" ? "supplier" : "customer",
            name: d.name, phone: d.phone || "", balance: d.balance,
            createdAtMillis: created, updatedAtMillis: updated
          }
        };
      }
      case "business_days": {
        const ex = this.findLocal(st.businessDays, id);
        return {
          key: "businessDays",
          entity: {
            ...ex, id,
            dateStr: d.dateStr, status: d.status,
            openedByUid: d.openedByUid, openedByName: d.openedByName,
            closedByUid: d.closedByUid || "", closedByName: d.closedByName || "",
            openingCash: d.openingCash, totalSales: d.totalSales, cashSales: d.cashSales,
            transferSales: d.transferSales, creditSales: d.creditSales, itemsSoldCount: d.itemsSoldCount,
            holidayOverride: Boolean(d.holidayOverride),
            createdAtMillis: created, updatedAtMillis: updated
          }
        };
      }
      case "audit_logs":
        return {
          key: "auditLogs",
          entity: {
            id, actorUid: d.actorUid, actorName: d.actorName, actorRole: d.actorRole,
            action: d.action, targetType: d.targetType, targetId: d.targetId, details: d.details || "",
            createdAtMillis: created
          }
        };
      case "holidays":
        return {
          key: "holidays",
          entity: {
            id, dateStr: d.dateStr || "", titleAr: d.titleAr, titleEn: d.titleEn,
            isWeeklyOffDay: Boolean(d.isWeeklyOffDay), dayOfWeek: d.dayOfWeek || 0,
            createdAtMillis: created, updatedAtMillis: updated
          }
        };
      case "settings": {
        if (id !== "main") return null;
        const s = {
          id: "main",
          storeNameAr: d.storeNameAr, storeNameEn: d.storeNameEn,
          primaryColorHex: d.primaryColorHex, secondaryColorHex: d.secondaryColorHex,
          sessionTimeoutMinutes: d.sessionTimeoutMinutes,
          updatedAtMillis: updated
        };
        if (d.logoUri) s.logoUri = d.logoUri;
        if (d.usdRateSsp != null) s.usdRateSsp = d.usdRateSsp;
        return { key: "settings", entity: s };
      }
      default:
        return null;
    }
  }

  // ---------- PC -> cloud ----------

  async memberUids() {
    const { fs } = this.mods;
    try {
      const snap = await withTimeout(fs.getDoc(fs.doc(this.db, "stores", this.storeId)), 15000, "store");
      const raw = snap.exists() && Array.isArray(snap.data().memberUids) ? snap.data().memberUids : [];
      const list = raw.filter((x) => typeof x === "string");
      if (!list.includes(this.storeId)) list.unshift(this.storeId);
      if (!list.includes(this.uid)) list.push(this.uid);
      return Array.from(new Set(list));
    } catch (_) {
      return Array.from(new Set([this.storeId, this.uid]));
    }
  }

  async pushPending() {
    if (this.pushing || !this.connected) return;
    this.pushing = true;
    try {
      const ops = [...(this.engine.state.pendingCloudSync || [])];
      if (!ops.length) return;
      const members = await this.memberUids();
      let done = 0;
      for (const op of ops) {
        try {
          await withTimeout(this.pushOp(op, members), 20000, op.collectionName);
          this.engine.state.pendingCloudSync = this.engine.state.pendingCloudSync.filter((x) => x.opId !== op.opId);
          done += 1;
        } catch (e) {
          this.lastError = `${op.collectionName}: ${this.describe(e)}`;
          if (isNetworkError(e)) {
            this.connected = false;
            break;
          }
        }
      }
      if (done > 0) {
        this.lastPushAt = Date.now();
        this.engine.persist();
        if (this.connected) this.lastError = "";
      }
    } finally {
      this.pushing = false;
    }
  }

  async pushOp(op, members) {
    const { fs } = this.mods;
    const p = op.payload || {};
    const ref = fs.doc(this.db, "stores", this.storeId, op.collectionName, op.documentId);
    const sv = fs.serverTimestamp();
    const existing = await fs.getDoc(ref);
    const createdAt = existing.exists() && existing.data().createdAt ? existing.data().createdAt : sv;

    switch (op.collectionName) {
      case "products": {
        const data = {
          nameAr: nonBlank(p.nameAr, "منتج", 120),
          nameEn: nonBlank(p.nameEn, "Product", 120),
          category: oneOf(p.category, ["perfume", "cream", "other"], "other"),
          sku: nonBlank(p.sku, op.documentId, 64),
          costPrice: money(p.costPrice),
          retailPrice: money(p.retailPrice),
          wholesalePrice: money(p.wholesalePrice),
          wholesaleMinQty: whole(p.wholesaleMinQty, 1),
          unitAr: nonBlank(p.unitAr, "قطعة", 40),
          unitEn: nonBlank(p.unitEn, "Unit", 40),
          lowStockThreshold: whole(p.lowStockThreshold, 0),
          isDeleted: Boolean(p.isDeleted),
          memberUids: members,
          createdAt,
          updatedAt: sv
        };
        if (p.barcode && String(p.barcode).length <= 64) data.barcode = String(p.barcode);
        if (p.imageUri && String(p.imageUri).length <= 500) data.imageUri = String(p.imageUri);
        await fs.setDoc(ref, data, { merge: true });
        return;
      }
      case "movements": {
        if (existing.exists()) return; // immutable ledger: already uploaded
        const qty = whole(p.qtyDelta, -1e9) || 0;
        if (qty === 0) return;
        const data = {
          productId: clip(p.productId, 128),
          type: oneOf(p.type, ["opening", "receive", "sale", "return", "adjustment"], "adjustment"),
          qtyDelta: qty,
          unitCost: money(p.unitCost),
          reason: clip(p.reason, 300),
          actorUid: this.uid,
          actorName: nonBlank(p.actorName, "Staff", 100),
          memberUids: members,
          createdAt: sv
        };
        if (p.refId) data.refId = clip(p.refId, 128);
        await fs.setDoc(ref, data);
        return;
      }
      case "invoices": {
        if (existing.exists()) {
          await fs.updateDoc(ref, {
            status: oneOf(p.status, ["completed", "returned"], "completed"),
            updatedAt: sv
          });
          return;
        }
        const data = {
          invoiceNumber: nonBlank(p.invoiceNumber, op.documentId, 64),
          saleType: oneOf(p.saleType, ["retail", "wholesale", "mixed"], "retail"),
          itemsSummary: nonBlank(p.itemsSummary, "-", 8000),
          subtotal: money(p.subtotal),
          discount: money(p.discount),
          total: money(p.total),
          costTotal: money(p.costTotal),
          paymentMethod: oneOf(p.paymentMethod, ["cash", "transfer", "credit", "usd"], "cash"),
          status: oneOf(p.status, ["completed", "returned"], "completed"),
          businessDayId: nonBlank(p.businessDayId, "day", 64),
          cashierUid: this.uid,
          cashierName: nonBlank(p.cashierName, "Cashier", 100),
          memberUids: members,
          createdAt: sv,
          updatedAt: sv
        };
        if (p.customerId) data.customerId = clip(p.customerId, 128);
        if (p.customerName) data.customerName = clip(p.customerName, 120);
        if (p.itemsJson && String(p.itemsJson).length <= 16000) data.itemsJson = String(p.itemsJson);
        if (p.currencyCode) data.currencyCode = oneOf(p.currencyCode, ["SSP", "USD"], "SSP");
        if (Number(p.usdRateSsp) >= 0) data.usdRateSsp = Number(p.usdRateSsp);
        if (Number(p.usdTotal) >= 0) data.usdTotal = Number(p.usdTotal);
        await fs.setDoc(ref, data);
        return;
      }
      case "customers":
      case "suppliers": {
        const data = {
          name: nonBlank(p.name, "-", 120),
          balance: Number(p.balance) || 0,
          memberUids: members,
          createdAt,
          updatedAt: sv
        };
        if (p.phone && String(p.phone).length <= 40) data.phone = String(p.phone);
        await fs.setDoc(ref, data, { merge: true });
        return;
      }
      case "business_days": {
        const totals = {
          status: oneOf(p.status, ["open", "closed"], "open"),
          totalSales: money(p.totalSales),
          cashSales: money(p.cashSales),
          transferSales: money(p.transferSales),
          creditSales: money(p.creditSales),
          itemsSoldCount: whole(p.itemsSoldCount, 0),
          memberUids: members,
          updatedAt: sv
        };
        if (p.closedByUid) totals.closedByUid = clip(p.closedByUid, 128);
        if (p.closedByName) totals.closedByName = clip(p.closedByName, 100);
        if (existing.exists()) {
          await fs.updateDoc(ref, totals);
          return;
        }
        await fs.setDoc(ref, {
          ...totals,
          dateStr: nonBlank(p.dateStr, "0000-00-00", 20),
          openedByUid: this.uid,
          openedByName: nonBlank(p.openedByName, "Staff", 100),
          openingCash: money(p.openingCash),
          holidayOverride: Boolean(p.holidayOverride),
          createdAt: sv
        });
        return;
      }
      case "holidays": {
        const data = {
          titleAr: nonBlank(p.titleAr, "عطلة", 120),
          titleEn: nonBlank(p.titleEn, "Holiday", 120),
          isWeeklyOffDay: Boolean(p.isWeeklyOffDay),
          dayOfWeek: Math.min(7, whole(p.dayOfWeek, 0)),
          memberUids: members,
          createdAt,
          updatedAt: sv
        };
        if (p.dateStr && String(p.dateStr).length <= 20) data.dateStr = String(p.dateStr);
        await fs.setDoc(ref, data, { merge: true });
        return;
      }
      case "audit_logs": {
        if (existing.exists()) return;
        await fs.setDoc(ref, {
          actorUid: this.uid,
          actorName: nonBlank(p.actorName, "Staff", 100),
          actorRole: oneOf(p.actorRole, ["admin", "manager", "cashier", "seller"], "cashier"),
          action: nonBlank(p.action, "ACTION", 80),
          targetType: nonBlank(p.targetType, "item", 60),
          targetId: nonBlank(p.targetId, "n/a", 128),
          details: clip(p.details, 500),
          memberUids: members,
          createdAt: sv
        });
        return;
      }
      default:
        // Unknown collection: drop it so it cannot block the queue forever.
        return;
    }
  }
}

module.exports = { CloudSync, FIRESTORE_DATABASE_ID, FIREBASE_WEB_CONFIG };
