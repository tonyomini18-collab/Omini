const crypto = require("node:crypto");
const fs = require("node:fs");
const path = require("node:path");

function sha256(input) {
  return crypto.createHash("sha256").update(String(input), "utf8").digest("hex");
}

function nowMillis() {
  return Date.now();
}

function genId(prefix) {
  return `${prefix}_${crypto.randomUUID().replace(/-/g, "").slice(0, 16)}`;
}

const DEFAULT_ADMIN_USERNAME = "omini";
// SHA-256 of "admin116"
const DEFAULT_ADMIN_PASSWORD_HASH = "4b9cef74e2b8c8e29f5ef21ca0ce05ddfad983d37e45449eb00bac3f1ff0d70d";

function createSeedState() {
  const ts = nowMillis();
  const products = [
    {
      id: "seed_oud_royal",
      nameAr: "عود ملكي فاخر ١٠٠ مل",
      nameEn: "Royal Oud Eau de Parfum 100ml",
      category: "perfume",
      sku: "PRF-OUD-01",
      barcode: "629100100101",
      imageUri: "",
      drawableResName: "img_perfume_oud",
      costPrice: 140.0,
      retailPrice: 290.0,
      wholesalePrice: 225.0,
      wholesaleMinQty: 5,
      unitAr: "زجاجة",
      unitEn: "Bottle",
      lowStockThreshold: 8,
      isDeleted: false,
      createdAtMillis: ts,
      updatedAtMillis: ts
    },
    {
      id: "seed_white_musk",
      nameAr: "مسك الطهارة الأبيض ٥٠ مل",
      nameEn: "Pure White Musk Elixir 50ml",
      category: "perfume",
      sku: "PRF-MSK-02",
      barcode: "629100100102",
      imageUri: "",
      drawableResName: "img_perfume_oud",
      costPrice: 65.0,
      retailPrice: 145.0,
      wholesalePrice: 110.0,
      wholesaleMinQty: 6,
      unitAr: "زجاجة",
      unitEn: "Bottle",
      lowStockThreshold: 10,
      isDeleted: false,
      createdAtMillis: ts,
      updatedAtMillis: ts
    },
    {
      id: "seed_amber_velvet",
      nameAr: "عطر عنبر مخملي ١٠٠ مل",
      nameEn: "Velvet Amber Mist 100ml",
      category: "perfume",
      sku: "PRF-AMB-03",
      barcode: "629100100103",
      imageUri: "",
      drawableResName: "img_perfume_oud",
      costPrice: 95.0,
      retailPrice: 210.0,
      wholesalePrice: 165.0,
      wholesaleMinQty: 4,
      unitAr: "زجاجة",
      unitEn: "Bottle",
      lowStockThreshold: 6,
      isDeleted: false,
      createdAtMillis: ts,
      updatedAtMillis: ts
    },
    {
      id: "seed_cream_rose",
      nameAr: "كريم الورد الدمشقي الفاخر",
      nameEn: "Damask Rose Rich Face Cream 50g",
      category: "cream",
      sku: "CRM-ROS-01",
      barcode: "629100200201",
      imageUri: "",
      drawableResName: "img_cream_jar",
      costPrice: 55.0,
      retailPrice: 125.0,
      wholesalePrice: 95.0,
      wholesaleMinQty: 6,
      unitAr: "عبوة",
      unitEn: "Jar",
      lowStockThreshold: 8,
      isDeleted: false,
      createdAtMillis: ts,
      updatedAtMillis: ts
    },
    {
      id: "seed_argan_cream",
      nameAr: "كريم ليلي بالأركان واللؤلؤ",
      nameEn: "Argan Pearl Night Cream 75g",
      category: "cream",
      sku: "CRM-ARG-02",
      barcode: "629100200202",
      imageUri: "",
      drawableResName: "img_cream_jar",
      costPrice: 70.0,
      retailPrice: 160.0,
      wholesalePrice: 120.0,
      wholesaleMinQty: 5,
      unitAr: "عبوة",
      unitEn: "Jar",
      lowStockThreshold: 8,
      isDeleted: false,
      createdAtMillis: ts,
      updatedAtMillis: ts
    },
    {
      id: "seed_shea_oud_butter",
      nameAr: "زبدة الشيا بلمسة العود ٢٠٠ جم",
      nameEn: "Oud Shea Body Butter 200g",
      category: "cream",
      sku: "CRM-SHB-03",
      barcode: "629100200203",
      imageUri: "",
      drawableResName: "img_cream_jar",
      costPrice: 42.0,
      retailPrice: 98.0,
      wholesalePrice: 74.0,
      wholesaleMinQty: 8,
      unitAr: "عبوة",
      unitEn: "Jar",
      lowStockThreshold: 10,
      isDeleted: false,
      createdAtMillis: ts,
      updatedAtMillis: ts
    }
  ];

  const stockCounts = {
    seed_oud_royal: 42,
    seed_white_musk: 30,
    seed_amber_velvet: 22,
    seed_cream_rose: 36,
    seed_argan_cream: 4,
    seed_shea_oud_butter: 50
  };

  const movements = products.map((p) => ({
    id: `mov_seed_${p.id}`,
    productId: p.id,
    type: "opening",
    qtyDelta: stockCounts[p.id] || 0,
    unitCost: p.costPrice,
    reason: "Initial catalog seed",
    refId: "",
    actorUid: "local_admin_omini",
    actorName: "omini",
    createdAtMillis: ts
  }));

  const parties = [
    {
      id: "cust_luxury_salon",
      partyType: "customer",
      name: "صالون لمسة حرير / Silk Touch Salon",
      phone: "0501234567",
      balance: 450.0,
      createdAtMillis: ts,
      updatedAtMillis: ts
    },
    {
      id: "cust_almas_gallery",
      partyType: "customer",
      name: "معرض الماس للعطور / Almas Fragrances",
      phone: "0559876543",
      balance: 0.0,
      createdAtMillis: ts,
      updatedAtMillis: ts
    },
    {
      id: "sup_oriental_oils",
      partyType: "supplier",
      name: "مصنع دهن العود الشرقي / Oriental Oils Co.",
      phone: "0542223344",
      balance: 1200.0,
      createdAtMillis: ts,
      updatedAtMillis: ts
    },
    {
      id: "sup_botanical_labs",
      partyType: "supplier",
      name: "مختبرات الأعشاب الطبيعية / Botanical Labs",
      phone: "0561112233",
      balance: 0.0,
      createdAtMillis: ts,
      updatedAtMillis: ts
    }
  ];

  const members = [
    {
      uid: "local_admin_omini",
      displayName: "omini",
      role: "admin",
      active: true,
      createdAtMillis: ts,
      updatedAtMillis: ts
    }
  ];

  const settings = {
    id: "main",
    storeNameAr: "دار العطور والمستحضرات",
    storeNameEn: "Aroma Boutique",
    primaryColorHex: "#1B4D3E",
    secondaryColorHex: "#C59B27",
    logoUri: "",
    sessionTimeoutMinutes: 30,
    notifyLowStock: true,
    notifyDayNotClosed: true,
    notifyNewUser: true,
    notifySyncErrors: true,
    notifyNewVersion: true,
    notifyRolesCsv: "admin,manager",
    updatedAtMillis: ts
  };

  return {
    products,
    movements,
    invoices: [],
    parties,
    businessDays: [],
    holidays: [],
    auditLogs: [],
    members,
    settings,
    releases: [],
    pendingCloudSync: [],
    connectedDevices: [],
    localStaffCredentials: [
      {
        uid: "local_admin_omini",
        username: DEFAULT_ADMIN_USERNAME,
        passwordHash: DEFAULT_ADMIN_PASSWORD_HASH,
        displayName: "omini",
        role: "admin"
      }
    ]
  };
}

class PcStoreEngine {
  constructor(dataFilePath) {
    this.dataFilePath = dataFilePath;
    this.state = this.loadOrInit();
  }

  loadOrInit() {
    try {
      if (this.dataFilePath && fs.existsSync(this.dataFilePath)) {
        const raw = fs.readFileSync(this.dataFilePath, "utf8");
        const parsed = JSON.parse(raw);
        const base = createSeedState();
        return { ...base, ...parsed };
      }
    } catch (_) {
      // fallback to fresh seed state
    }
    const seed = createSeedState();
    this.persist(seed);
    return seed;
  }

  persist(targetState = this.state) {
    if (!this.dataFilePath) return;
    const dir = path.dirname(this.dataFilePath);
    fs.mkdirSync(dir, { recursive: true });
    const tmp = `${this.dataFilePath}.tmp`;
    fs.writeFileSync(tmp, JSON.stringify(targetState, null, 2), "utf8");
    fs.renameSync(tmp, this.dataFilePath);
  }

  authenticateLocal(username, password) {
    const cleanUser = String(username || "").trim().toLowerCase();
    const passHash = sha256(String(password || ""));
    const match = (this.state.localStaffCredentials || []).find(
      (c) => String(c.username || "").toLowerCase() === cleanUser && c.passwordHash === passHash
    );
    if (!match) return null;
    return {
      uid: match.uid,
      displayName: match.displayName,
      role: match.role
    };
  }

  upsertStaffCredential(actorRole, { uid, username, password, displayName, role }) {
    if (actorRole !== "admin" && actorRole !== "manager") {
      throw new Error("Permission denied");
    }
    if (role === "admin" && actorRole !== "admin") {
      throw new Error("Only Admin can create Admin users");
    }
    const cleanUsername = String(username || "").trim();
    if (!cleanUsername || !password) throw new Error("Username and password required");
    const entry = {
      uid: uid || genId("staff"),
      username: cleanUsername,
      passwordHash: sha256(password),
      displayName: displayName || cleanUsername,
      role: role || "cashier"
    };
    this.state.localStaffCredentials = [
      ...this.state.localStaffCredentials.filter((c) => c.username.toLowerCase() !== cleanUsername.toLowerCase()),
      entry
    ];
    this.upsertMember(actorRole, {
      uid: entry.uid,
      displayName: entry.displayName,
      role: entry.role,
      active: true
    });
    this.persist();
    return { uid: entry.uid, displayName: entry.displayName, role: entry.role };
  }

  getStockMap() {
    const map = {};
    for (const m of this.state.movements) {
      map[m.productId] = (map[m.productId] || 0) + Number(m.qtyDelta || 0);
    }
    return map;
  }

  getProductsWithStock(includeDeleted = false) {
    const stocks = this.getStockMap();
    return this.state.products
      .filter((p) => includeDeleted || !p.isDeleted)
      .map((p) => ({ ...p, currentStock: stocks[p.id] || 0 }));
  }

  getOpenBusinessDay() {
    return this.state.businessDays.find((d) => d.status === "open") || null;
  }

  isTodayHoliday() {
    const d = new Date();
    const yyyy = d.getFullYear();
    const mm = String(d.getMonth() + 1).padStart(2, "0");
    const dd = String(d.getDate()).padStart(2, "0");
    const todayStr = `${yyyy}-${mm}-${dd}`;
    const jsDay = d.getDay(); // 0=Sun..6=Sat
    const isoDow = jsDay === 0 ? 7 : jsDay; // 1=Mon..7=Sun
    return (
      this.state.holidays.find(
        (h) => (h.isWeeklyOffDay && Number(h.dayOfWeek) === isoDow) || (!h.isWeeklyOffDay && h.dateStr === todayStr)
      ) || null
    );
  }

  openBusinessDay(actor, { openingCash = 0, holidayOverride = false }) {
    if (this.getOpenBusinessDay()) throw new Error("Business day already open");
    const holiday = this.isTodayHoliday();
    if (holiday && !holidayOverride) {
      throw new Error(`Holiday configured: ${holiday.titleEn}`);
    }
    if (holidayOverride && actor.role !== "admin" && actor.role !== "manager") {
      throw new Error("Manager or Admin required to override holiday");
    }
    const ts = nowMillis();
    const d = new Date(ts);
    const dateStr = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
    const day = {
      id: `day_${dateStr}_${ts % 10000}`,
      dateStr,
      status: "open",
      openedByUid: actor.uid,
      openedByName: actor.displayName,
      closedByUid: "",
      closedByName: "",
      openingCash: Math.max(0, Number(openingCash || 0)),
      totalSales: 0,
      cashSales: 0,
      transferSales: 0,
      creditSales: 0,
      itemsSoldCount: 0,
      holidayOverride: Boolean(holidayOverride),
      createdAtMillis: ts,
      updatedAtMillis: ts
    };
    this.state.businessDays.unshift(day);
    this.enqueueCloudOp("business_days", day.id, day);
    if (holidayOverride) {
      this.addAuditLog(actor, "HOLIDAY_OVERRIDE", "business_day", day.id, `Opened on holiday: ${holiday.titleEn}`);
    }
    this.persist();
    return day;
  }

  closeBusinessDay(actor) {
    const openDay = this.getOpenBusinessDay();
    if (!openDay) throw new Error("No open business day");
    const ts = nowMillis();
    openDay.status = "closed";
    openDay.closedByUid = actor.uid;
    openDay.closedByName = actor.displayName;
    openDay.updatedAtMillis = ts;
    this.enqueueCloudOp("business_days", openDay.id, openDay);
    this.addAuditLog(actor, "CLOSE_DAY", "business_day", openDay.id, `Total Sales: ${openDay.totalSales}`);
    this.persist();
    return openDay;
  }

  saveProduct(actor, input, initialStock = 0) {
    if (actor.role !== "admin" && actor.role !== "manager") {
      throw new Error("Only Admin or General Manager can manage products");
    }
    const ts = nowMillis();
    const isNew = !input.id;
    const id = input.id || genId("prod");
    const existing = this.state.products.find((p) => p.id === id);
    const entity = {
      id,
      nameAr: input.nameAr || input.nameEn || "منتج",
      nameEn: input.nameEn || input.nameAr || "Product",
      category: input.category || "perfume",
      sku: input.sku || id.toUpperCase(),
      barcode: input.barcode || "",
      imageUri: input.imageUri || "",
      drawableResName: input.drawableResName || (input.category === "cream" ? "img_cream_jar" : "img_perfume_oud"),
      costPrice: Number(input.costPrice || 0),
      retailPrice: Number(input.retailPrice || 0),
      wholesalePrice: Number(input.wholesalePrice || 0),
      wholesaleMinQty: Math.max(1, Number(input.wholesaleMinQty || 1)),
      unitAr: input.unitAr || "قطعة",
      unitEn: input.unitEn || "Unit",
      lowStockThreshold: Math.max(0, Number(input.lowStockThreshold ?? 5)),
      isDeleted: Boolean(input.isDeleted),
      createdAtMillis: existing ? existing.createdAtMillis : ts,
      updatedAtMillis: ts
    };
    this.state.products = [...this.state.products.filter((p) => p.id !== id), entity];
    this.enqueueCloudOp("products", id, entity);

    if (isNew && Number(initialStock) > 0) {
      const mov = {
        id: genId("mov"),
        productId: id,
        type: "opening",
        qtyDelta: Number(initialStock),
        unitCost: entity.costPrice,
        reason: "Initial stock",
        refId: "",
        actorUid: actor.uid,
        actorName: actor.displayName,
        createdAtMillis: ts
      };
      this.state.movements.unshift(mov);
      this.enqueueCloudOp("movements", mov.id, mov);
    }

    this.addAuditLog(
      actor,
      isNew ? "CREATE_PRODUCT" : "UPDATE_PRODUCT",
      "product",
      id,
      `${entity.nameEn} | Retail: ${entity.retailPrice}, Wholesale: ${entity.wholesalePrice}`
    );
    this.persist();
    return entity;
  }

  deleteProduct(actor, productId) {
    if (actor.role !== "admin" && actor.role !== "manager") {
      throw new Error("Permission denied");
    }
    const p = this.state.products.find((x) => x.id === productId);
    if (!p) return null;
    p.isDeleted = true;
    p.updatedAtMillis = nowMillis();
    this.enqueueCloudOp("products", p.id, p);
    this.addAuditLog(actor, "DELETE_PRODUCT", "product", p.id, p.nameEn);
    this.persist();
    return p;
  }

  completeSale(actor, { items, discount = 0, paymentMethod = "cash", customerId = "" }) {
    const openDay = this.getOpenBusinessDay();
    if (!openDay) throw new Error("Open business day first");
    if (!Array.isArray(items) || items.length === 0) throw new Error("Cart is empty");

    const ts = nowMillis();
    const stocks = this.getStockMap();
    const resolvedItems = items.map((line) => {
      const prod = this.state.products.find((p) => p.id === line.productId && !p.isDeleted);
      if (!prod) throw new Error(`Product not found: ${line.productId}`);
      const avail = stocks[prod.id] || 0;
      const qty = Math.max(1, Number(line.qty || 1));
      if (qty > avail) throw new Error(`Insufficient stock for ${prod.nameEn}`);
      const wholesale = Boolean(line.wholesale);
      const unitPrice = wholesale ? prod.wholesalePrice : prod.retailPrice;
      return {
        productId: prod.id,
        nameAr: prod.nameAr,
        nameEn: prod.nameEn,
        qty,
        unitPrice,
        unitCost: prod.costPrice,
        wholesale
      };
    });

    const subtotal = resolvedItems.reduce((s, i) => s + i.qty * i.unitPrice, 0);
    const clampedDiscount = Math.min(Math.max(0, Number(discount || 0)), subtotal);
    const total = Math.max(0, subtotal - clampedDiscount);
    const costTotal = resolvedItems.reduce((s, i) => s + i.qty * i.unitCost, 0);
    const hasW = resolvedItems.some((i) => i.wholesale);
    const hasR = resolvedItems.some((i) => !i.wholesale);
    const saleType = hasW && hasR ? "mixed" : hasW ? "wholesale" : "retail";
    const customer = this.state.parties.find((p) => p.id === customerId && p.partyType === "customer");
    const invId = genId("inv");
    const d = new Date(ts);
    const invoiceNumber = `INV-PC-${String(d.getFullYear()).slice(-2)}${String(d.getMonth() + 1).padStart(2, "0")}${String(d.getDate()).padStart(2, "0")}-${String(ts).slice(-5)}`;
    const itemsSummary = resolvedItems
      .map((i) => `${i.nameEn} x${i.qty} (${i.wholesale ? "W" : "R"}) = ${(i.qty * i.unitPrice).toFixed(2)}`)
      .join("\n");

    const invoice = {
      id: invId,
      invoiceNumber,
      saleType,
      customerId: customer ? customer.id : "",
      customerName: customer ? customer.name : "",
      itemsJson: JSON.stringify(resolvedItems),
      itemsSummary,
      subtotal,
      discount: clampedDiscount,
      total,
      costTotal,
      paymentMethod,
      status: "completed",
      businessDayId: openDay.id,
      cashierUid: actor.uid,
      cashierName: actor.displayName,
      createdAtMillis: ts,
      updatedAtMillis: ts
    };
    this.state.invoices.unshift(invoice);
    this.enqueueCloudOp("invoices", invId, invoice);

    for (const line of resolvedItems) {
      const mov = {
        id: genId("mov"),
        productId: line.productId,
        type: "sale",
        qtyDelta: -line.qty,
        unitCost: line.unitCost,
        reason: `Sale ${invoiceNumber}`,
        refId: invId,
        actorUid: actor.uid,
        actorName: actor.displayName,
        createdAtMillis: ts
      };
      this.state.movements.unshift(mov);
      this.enqueueCloudOp("movements", mov.id, mov);
    }

    if (paymentMethod === "credit" && customer) {
      customer.balance += total;
      customer.updatedAtMillis = ts;
      this.enqueueCloudOp("customers", customer.id, customer);
    }

    const soldQty = resolvedItems.reduce((s, i) => s + i.qty, 0);
    openDay.totalSales += total;
    if (paymentMethod === "cash") openDay.cashSales += total;
    if (paymentMethod === "transfer") openDay.transferSales += total;
    if (paymentMethod === "credit") openDay.creditSales += total;
    openDay.itemsSoldCount += soldQty;
    openDay.updatedAtMillis = ts;
    this.enqueueCloudOp("business_days", openDay.id, openDay);

    this.persist();
    return invoice;
  }

  returnInvoice(actor, invoiceId) {
    const inv = this.state.invoices.find((i) => i.id === invoiceId);
    if (!inv || inv.status === "returned") return null;
    const ts = nowMillis();
    inv.status = "returned";
    inv.updatedAtMillis = ts;
    this.enqueueCloudOp("invoices", inv.id, inv);

    const saleMoves = this.state.movements.filter((m) => m.refId === inv.id && m.type === "sale");
    for (const sm of saleMoves) {
      const retMov = {
        id: genId("mov"),
        productId: sm.productId,
        type: "return",
        qtyDelta: Math.abs(sm.qtyDelta),
        unitCost: sm.unitCost,
        reason: `Return ${inv.invoiceNumber}`,
        refId: inv.id,
        actorUid: actor.uid,
        actorName: actor.displayName,
        createdAtMillis: ts
      };
      this.state.movements.unshift(retMov);
      this.enqueueCloudOp("movements", retMov.id, retMov);
    }

    if (inv.paymentMethod === "credit" && inv.customerId) {
      const cust = this.state.parties.find((p) => p.id === inv.customerId);
      if (cust) {
        cust.balance = Math.max(0, cust.balance - inv.total);
        cust.updatedAtMillis = ts;
        this.enqueueCloudOp("customers", cust.id, cust);
      }
    }

    this.addAuditLog(actor, "RETURN_INVOICE", "invoice", inv.id, `Returned ${inv.invoiceNumber}`);
    this.persist();
    return inv;
  }

  receiveStock(actor, { productId, supplierId = "", qty, unitCost }) {
    const prod = this.state.products.find((p) => p.id === productId);
    if (!prod) throw new Error("Product not found");
    const q = Number(qty || 0);
    if (q <= 0) throw new Error("Quantity must be positive");
    const cost = Number(unitCost ?? prod.costPrice);
    const ts = nowMillis();
    const supplier = this.state.parties.find((p) => p.id === supplierId && p.partyType === "supplier");
    const mov = {
      id: genId("mov"),
      productId,
      type: "receive",
      qtyDelta: q,
      unitCost: cost,
      reason: supplier ? `Receive from ${supplier.name}` : "Stock receipt",
      refId: supplierId || "",
      actorUid: actor.uid,
      actorName: actor.displayName,
      createdAtMillis: ts
    };
    this.state.movements.unshift(mov);
    this.enqueueCloudOp("movements", mov.id, mov);

    if (supplier) {
      supplier.balance += q * cost;
      supplier.updatedAtMillis = ts;
      this.enqueueCloudOp("suppliers", supplier.id, supplier);
    }
    this.persist();
    return mov;
  }

  adjustStock(actor, { productId, qtyDelta, reason }) {
    if (actor.role !== "admin" && actor.role !== "manager") {
      throw new Error("Admin or General Manager required");
    }
    const delta = Number(qtyDelta || 0);
    if (!delta || !String(reason || "").trim()) throw new Error("Delta and reason required");
    const prod = this.state.products.find((p) => p.id === productId);
    if (!prod) throw new Error("Product not found");
    const ts = nowMillis();
    const mov = {
      id: genId("mov"),
      productId,
      type: "adjustment",
      qtyDelta: delta,
      unitCost: prod.costPrice,
      reason: String(reason).trim(),
      refId: "",
      actorUid: actor.uid,
      actorName: actor.displayName,
      createdAtMillis: ts
    };
    this.state.movements.unshift(mov);
    this.enqueueCloudOp("movements", mov.id, mov);
    this.addAuditLog(actor, "STOCK_ADJUSTMENT", "product", productId, `${prod.nameEn}: ${delta} (${reason})`);
    this.persist();
    return mov;
  }

  saveParty({ partyType, name, phone }) {
    const ts = nowMillis();
    const entity = {
      id: genId(partyType === "supplier" ? "supp" : "cust"),
      partyType: partyType === "supplier" ? "supplier" : "customer",
      name: String(name || "").trim(),
      phone: String(phone || "").trim(),
      balance: 0,
      createdAtMillis: ts,
      updatedAtMillis: ts
    };
    if (!entity.name) throw new Error("Name required");
    this.state.parties.push(entity);
    this.enqueueCloudOp(entity.partyType === "supplier" ? "suppliers" : "customers", entity.id, entity);
    this.persist();
    return entity;
  }

  recordPartyPayment(actor, partyId, amount) {
    const party = this.state.parties.find((p) => p.id === partyId);
    const amt = Number(amount || 0);
    if (!party || amt <= 0) throw new Error("Invalid payment");
    party.balance = Math.max(0, party.balance - amt);
    party.updatedAtMillis = nowMillis();
    this.enqueueCloudOp(party.partyType === "supplier" ? "suppliers" : "customers", party.id, party);
    this.addAuditLog(actor, "PARTY_PAYMENT", party.partyType, party.id, `${party.name}: -${amt}`);
    this.persist();
    return party;
  }

  addHoliday(actor, { dateStr = "", titleAr, titleEn, isWeeklyOffDay = false, dayOfWeek = 0 }) {
    if (actor.role !== "admin" && actor.role !== "manager") throw new Error("Permission denied");
    const ts = nowMillis();
    const h = {
      id: genId("hol"),
      dateStr: isWeeklyOffDay ? "" : String(dateStr).trim(),
      titleAr: titleAr || titleEn || "عطلة",
      titleEn: titleEn || titleAr || "Holiday",
      isWeeklyOffDay: Boolean(isWeeklyOffDay),
      dayOfWeek: isWeeklyOffDay ? Number(dayOfWeek) : 0,
      createdAtMillis: ts,
      updatedAtMillis: ts
    };
    this.state.holidays.push(h);
    this.persist();
    return h;
  }

  deleteHoliday(actor, id) {
    if (actor.role !== "admin" && actor.role !== "manager") throw new Error("Permission denied");
    this.state.holidays = this.state.holidays.filter((h) => h.id !== id);
    this.persist();
  }

  upsertMember(actorRole, { uid, displayName, role, active = true }) {
    if (actorRole !== "admin" && actorRole !== "manager") throw new Error("Permission denied");
    const ts = nowMillis();
    const cleanUid = String(uid || "").trim();
    const existing = this.state.members.find((m) => m.uid === cleanUid);
    const entity = {
      uid: cleanUid,
      displayName: String(displayName || "").trim(),
      role,
      active,
      createdAtMillis: existing ? existing.createdAtMillis : ts,
      updatedAtMillis: ts
    };
    this.state.members = [...this.state.members.filter((m) => m.uid !== cleanUid), entity];
    this.persist();
    return entity;
  }

  saveSettings(actor, updated) {
    if (actor.role !== "admin") throw new Error("Admin required");
    this.state.settings = {
      ...this.state.settings,
      ...updated,
      id: "main",
      updatedAtMillis: nowMillis()
    };
    this.addAuditLog(actor, "UPDATE_SETTINGS", "settings", "main", `${this.state.settings.storeNameEn}`);
    this.persist();
    return this.state.settings;
  }

  publishRelease(actor, { platform = "android", versionCode, versionName, downloadUrl, releaseNotesAr = "", releaseNotesEn = "", isMandatory = false }) {
    if (actor.role !== "admin") throw new Error("Admin required");
    const id = `${platform}_v${versionCode}`;
    const rel = {
      id,
      platform,
      versionCode: Number(versionCode),
      versionName: String(versionName).trim(),
      downloadUrl: String(downloadUrl).trim(),
      releaseNotesAr: String(releaseNotesAr).trim(),
      releaseNotesEn: String(releaseNotesEn).trim(),
      isMandatory: Boolean(isMandatory),
      createdAtMillis: nowMillis()
    };
    this.state.releases = [...this.state.releases.filter((r) => r.id !== id), rel].sort((a, b) => b.versionCode - a.versionCode);
    this.addAuditLog(actor, "PUBLISH_RELEASE", "app_release", id, `${platform} v${rel.versionName}`);
    this.persist();
    return rel;
  }

  addAuditLog(actor, action, targetType, targetId, details) {
    const log = {
      id: genId("log"),
      actorUid: actor.uid,
      actorName: actor.displayName,
      actorRole: actor.role,
      action,
      targetType,
      targetId,
      details,
      createdAtMillis: nowMillis()
    };
    this.state.auditLogs.unshift(log);
    this.enqueueCloudOp("audit_logs", log.id, log);
  }

  enqueueCloudOp(collectionName, documentId, payload) {
    this.state.pendingCloudSync.push({
      opId: genId("op"),
      collectionName,
      documentId,
      payload,
      createdAtMillis: nowMillis()
    });
  }

  // Bidirectional LWW + immutable-ledger merge used for BOTH PC<->Android LAN sync and Cloud sync
  mergeSnapshot(incoming = {}) {
    const mergeByKeyLww = (currentList, incomingList, keyField = "id") => {
      const map = new Map();
      for (const item of currentList || []) {
        map.set(item[keyField], item);
      }
      for (const inc of incomingList || []) {
        const k = inc[keyField];
        if (!k) continue;
        const prev = map.get(k);
        if (!prev || Number(inc.updatedAtMillis || 0) > Number(prev.updatedAtMillis || 0)) {
          map.set(k, inc);
        }
      }
      return Array.from(map.values());
    };

    const mergeImmutableUnion = (currentList, incomingList, keyField = "id") => {
      const map = new Map();
      for (const item of currentList || []) map.set(item[keyField], item);
      for (const inc of incomingList || []) {
        if (inc[keyField] && !map.has(inc[keyField])) map.set(inc[keyField], inc);
      }
      return Array.from(map.values()).sort((a, b) => Number(b.createdAtMillis || 0) - Number(a.createdAtMillis || 0));
    };

    this.state.products = mergeByKeyLww(this.state.products, incoming.products, "id");
    this.state.movements = mergeImmutableUnion(this.state.movements, incoming.movements, "id");
    this.state.invoices = mergeByKeyLww(this.state.invoices, incoming.invoices, "id").sort(
      (a, b) => Number(b.createdAtMillis || 0) - Number(a.createdAtMillis || 0)
    );
    this.state.parties = mergeByKeyLww(this.state.parties, incoming.parties, "id");
    this.state.businessDays = mergeByKeyLww(this.state.businessDays, incoming.businessDays, "id").sort(
      (a, b) => String(b.dateStr || "").localeCompare(String(a.dateStr || ""))
    );
    this.state.holidays = mergeByKeyLww(this.state.holidays, incoming.holidays, "id");
    this.state.auditLogs = mergeImmutableUnion(this.state.auditLogs, incoming.auditLogs, "id").slice(0, 500);
    this.state.members = mergeByKeyLww(this.state.members, incoming.members, "uid");
    this.state.releases = mergeImmutableUnion(this.state.releases, incoming.releases, "id").sort(
      (a, b) => Number(b.versionCode || 0) - Number(a.versionCode || 0)
    );

    if (incoming.settings && Number(incoming.settings.updatedAtMillis || 0) > Number(this.state.settings.updatedAtMillis || 0)) {
      this.state.settings = { ...this.state.settings, ...incoming.settings, id: "main" };
    }

    if (incoming.clientDevice) {
      const ts = nowMillis();
      this.state.connectedDevices = [
        ...this.state.connectedDevices.filter((d) => d.name !== incoming.clientDevice),
        { name: incoming.clientDevice, lastSeenMillis: ts }
      ].slice(-20);
    }

    this.persist();
    return this.getFullSyncSnapshot();
  }

  getFullSyncSnapshot() {
    return {
      serverTimeMillis: nowMillis(),
      products: this.state.products,
      movements: this.state.movements,
      invoices: this.state.invoices,
      parties: this.state.parties,
      businessDays: this.state.businessDays,
      holidays: this.state.holidays,
      auditLogs: this.state.auditLogs.slice(0, 200),
      members: this.state.members,
      settings: this.state.settings,
      releases: this.state.releases
    };
  }
}

module.exports = {
  PcStoreEngine,
  createSeedState,
  sha256,
  DEFAULT_ADMIN_USERNAME,
  DEFAULT_ADMIN_PASSWORD_HASH
};
