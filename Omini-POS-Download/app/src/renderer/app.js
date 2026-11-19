(function () {
  let lang = localStorage.getItem("aroma_lang") || "ar";
  let dark = localStorage.getItem("aroma_dark") === "1";
  let currentUser = null;
  let state = null;
  let lanUrls = [];
  let pcVersion = "1.0.0";
  let activeTab = "pos";
  let cart = [];
  let searchQuery = "";
  let categoryFilter = "all";
  let periodFilter = "month";
  let editingMember = null;
  let editingProduct = null;
  let adminNotice = "";
  let adminNoticeError = false;

  const IDB_NAME = "aroma_pos_pc_idb";
  function saveSnapshotToIndexedDb(snapshot) {
    try {
      const req = indexedDB.open(IDB_NAME, 1);
      req.onupgradeneeded = () => {
        if (!req.result.objectStoreNames.contains("snapshots")) {
          req.result.createObjectStore("snapshots");
        }
      };
      req.onsuccess = () => {
        const tx = req.result.transaction("snapshots", "readwrite");
        tx.objectStore("snapshots").put(snapshot, "latest");
      };
    } catch (_) {}
  }

  function t() {
    return window.AROMA_I18N[lang] || window.AROMA_I18N.ar;
  }

  function applyThemeAndDirection() {
    document.documentElement.lang = lang;
    document.documentElement.dir = lang === "ar" ? "rtl" : "ltr";
    document.body.classList.toggle("dark", dark);
    if (state && state.settings) {
      document.documentElement.style.setProperty("--primary", state.settings.primaryColorHex || "#1B4D3E");
      document.documentElement.style.setProperty("--secondary", state.settings.secondaryColorHex || "#C59B27");
    }
    const s = t();
    document.getElementById("loginTitle").textContent = s.loginTitle;
    document.getElementById("loginSubtitle").textContent = s.loginSubtitle;
    document.getElementById("userLabel").textContent = s.username;
    document.getElementById("passLabel").textContent = s.password;
    document.getElementById("loginSubmitBtn").textContent = s.loginBtn;
    document.getElementById("loginLangBtn").textContent = s.langToggle;
    document.getElementById("appLangBtn").textContent = s.langToggle;
    document.getElementById("signOutBtn").textContent = s.signOut;
  }

  function updateConnectivityBadge() {
    const badge = document.getElementById("networkBadge");
    if (!badge) return;
    const online = navigator.onLine;
    badge.className = `badge ${online ? "badge-online" : "badge-offline"}`;
    badge.textContent = online ? t().online : t().offline;
  }

  async function apiAction(type, extra = {}) {
    const res = await fetch("/api/action", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ type, actor: currentUser, ...extra })
    });
    const data = await res.json();
    if (!res.ok) throw new Error(data.error || "Action failed");
    state = data.state;
    saveSnapshotToIndexedDb(state);
    renderApp();
    return data.result;
  }

  async function refreshState() {
    if (!currentUser) return;
    const res = await fetch(`/api/state?role=${encodeURIComponent(currentUser.role)}`);
    const data = await res.json();
    state = data.state;
    lanUrls = data.lanUrls || [];
    pcVersion = data.version || "1.0.0";
    saveSnapshotToIndexedDb(state);
    renderApp();
  }

  function renderNav() {
    const s = t();
    const role = currentUser.role;
    const tabs = [];
    if (role !== "cashier") {
      tabs.push(["pos", s.navPos]);
    }
    tabs.push(["inventory", s.navInventory]);
    tabs.push(["day", s.navDay]);
    tabs.push(["dashboard", s.navDashboard]);
    if (role === "admin" || role === "manager") {
      tabs.push(["reports", s.navReports]);
      tabs.push(["admin", s.navAdmin]);
    }
    const nav = document.getElementById("sidebarNav");
    nav.innerHTML = tabs
      .map(
        ([id, label]) =>
          `<button class="nav-item ${activeTab === id ? "active" : ""}" data-tab="${id}">${label}</button>`
      )
      .join("");
    nav.querySelectorAll("button[data-tab]").forEach((btn) => {
      btn.onclick = () => {
        activeTab = btn.getAttribute("data-tab");
        renderApp();
      };
    });
  }

  function formatMoney(v) {
    return `${Number(v || 0).toFixed(2)} ${t().currency}`;
  }

  function notifyElectronPosActivity() {
    if (window.aromaElectron && state) {
      window.aromaElectron.setPosActivity(Boolean(state.openBusinessDay) || cart.length > 0);
    }
  }

  function renderPos(root) {
    const s = t();
    if (currentUser.role === "cashier") {
      root.innerHTML = `<div class="card" style="color:var(--danger)"><strong>${s.cashierNoSale}</strong></div>`;
      return;
    }
    const q = searchQuery.toLowerCase();
    const filtered = (state.products || []).filter((p) => {
      const catOk = categoryFilter === "all" || p.category === categoryFilter;
      const textOk =
        !q ||
        p.nameAr.toLowerCase().includes(q) ||
        p.nameEn.toLowerCase().includes(q) ||
        p.sku.toLowerCase().includes(q) ||
        (p.barcode || "").toLowerCase().includes(q);
      return catOk && textOk;
    });
    const subtotal = cart.reduce((acc, c) => acc + c.qty * (c.wholesale ? c.product.wholesalePrice : c.product.retailPrice), 0);
    const customers = (state.parties || []).filter((p) => p.partyType === "customer");

    root.innerHTML = `
      ${!state.openBusinessDay ? `<div class="card" style="border-color:var(--danger);margin-bottom:14px;color:var(--danger)"><strong>${s.dayClosedBanner}</strong></div>` : ""}
      <div class="grid-2">
        <div class="stack">
          <div class="card row">
            <input id="posSearch" value="${searchQuery.replace(/"/g, "&quot;")}" placeholder="${s.searchHint}" />
            <select id="posCat" style="max-width:160px">
              <option value="all" ${categoryFilter === "all" ? "selected" : ""}>All</option>
              <option value="perfume" ${categoryFilter === "perfume" ? "selected" : ""}>Perfumes</option>
              <option value="cream" ${categoryFilter === "cream" ? "selected" : ""}>Creams</option>
            </select>
          </div>
          <div class="product-grid">
            ${filtered
              .map(
                (p) => `
              <div class="card stack" style="cursor:pointer" data-add="${p.id}">
                <strong>${lang === "ar" ? p.nameAr : p.nameEn}</strong>
                <span class="mono" style="font-size:12px;color:var(--muted)">${p.sku} • ${p.barcode || "-"}</span>
                <div class="between">
                  <span>${s.retail}: <strong>${formatMoney(p.retailPrice)}</strong></span>
                  <span>${s.stock}: <strong>${p.currentStock}</strong></span>
                </div>
                <span style="font-size:12px;color:var(--secondary)">${s.wholesale}: ${formatMoney(p.wholesalePrice)} (${p.wholesaleMinQty}+)</span>
              </div>`
              )
              .join("")}
          </div>
        </div>
        <div class="stack">
          <div class="card stack">
            <h3 style="margin:0">${s.cart}</h3>
            ${
              cart.length === 0
                ? `<p style="color:var(--muted)">${s.emptyCart}</p>`
                : cart
                    .map(
                      (c, idx) => `
                <div class="between" style="border-bottom:1px solid var(--border);padding-bottom:8px">
                  <div>
                    <strong>${lang === "ar" ? c.product.nameAr : c.product.nameEn}</strong>
                    <div style="font-size:12px">
                      <button class="btn" data-tier="${idx}">${c.wholesale ? s.wholesale : s.retail}</button>
                      ${formatMoney(c.wholesale ? c.product.wholesalePrice : c.product.retailPrice)}
                    </div>
                  </div>
                  <div class="row">
                    <button class="btn" data-dec="${idx}">-</button>
                    <strong class="mono">${c.qty}</strong>
                    <button class="btn" data-inc="${idx}">+</button>
                  </div>
                </div>`
                    )
                    .join("")
            }
            <div class="between"><span>${s.subtotal}</span><strong class="mono">${formatMoney(subtotal)}</strong></div>
            <label>${s.discount}<input id="posDiscount" type="number" min="0" value="0" /></label>
            <label>${s.paymentMethod}
              <select id="posPay">
                <option value="cash">${s.cash}</option>
                <option value="transfer">${s.transfer}</option>
                <option value="credit">${s.credit}</option>
              </select>
            </label>
            <label>${s.customer}
              <select id="posCustomer">
                <option value="">${s.walkIn}</option>
                ${customers.map((c) => `<option value="${c.id}">${c.name}</option>`).join("")}
              </select>
            </label>
            <button id="checkoutBtn" class="btn btn-primary" ${!state.openBusinessDay || cart.length === 0 ? "disabled" : ""}>${s.completeSale}</button>
          </div>
          <div class="card stack">
            <h4 style="margin:0">${s.recentInvoices}</h4>
            ${(state.invoices || [])
              .slice(0, 8)
              .map(
                (inv) => `
              <div class="between" style="font-size:13px">
                <div><strong class="mono">${inv.invoiceNumber}</strong> — ${formatMoney(inv.total)} (${inv.status})</div>
                ${inv.status === "completed" ? `<button class="btn" data-ret="${inv.id}">${s.returnBtn}</button>` : ""}
              </div>`
              )
              .join("")}
          </div>
        </div>
      </div>
    `;

    const searchInput = document.getElementById("posSearch");
    searchInput.oninput = (e) => {
      searchQuery = e.target.value;
      renderPos(root);
      document.getElementById("posSearch").focus();
    };
    searchInput.onkeydown = (e) => {
      if (e.key === "Enter" && searchQuery.trim()) {
        const hit = (state.products || []).find(
          (p) => p.barcode === searchQuery.trim() || p.sku.toLowerCase() === searchQuery.trim().toLowerCase()
        );
        if (hit) {
          addProductToCart(hit);
          searchQuery = "";
          renderPos(root);
        }
      }
    };
    document.getElementById("posCat").onchange = (e) => {
      categoryFilter = e.target.value;
      renderPos(root);
    };
    root.querySelectorAll("[data-add]").forEach((el) => {
      el.onclick = () => {
        const prod = state.products.find((p) => p.id === el.getAttribute("data-add"));
        if (prod) {
          addProductToCart(prod);
          renderPos(root);
        }
      };
    });
    root.querySelectorAll("[data-tier]").forEach((el) => {
      el.onclick = () => {
        const i = Number(el.getAttribute("data-tier"));
        cart[i].wholesale = !cart[i].wholesale;
        renderPos(root);
      };
    });
    root.querySelectorAll("[data-inc]").forEach((el) => {
      el.onclick = () => {
        const i = Number(el.getAttribute("data-inc"));
        if (cart[i].qty < cart[i].product.currentStock) cart[i].qty++;
        if (cart[i].qty >= cart[i].product.wholesaleMinQty) cart[i].wholesale = true;
        renderPos(root);
      };
    });
    root.querySelectorAll("[data-dec]").forEach((el) => {
      el.onclick = () => {
        const i = Number(el.getAttribute("data-dec"));
        cart[i].qty--;
        if (cart[i].qty <= 0) cart.splice(i, 1);
        renderPos(root);
      };
    });
    const checkoutBtn = document.getElementById("checkoutBtn");
    if (checkoutBtn) {
      checkoutBtn.onclick = async () => {
        try {
          await apiAction("COMPLETE_SALE", {
            sale: {
              items: cart.map((c) => ({ productId: c.product.id, qty: c.qty, wholesale: c.wholesale })),
              discount: Number(document.getElementById("posDiscount").value || 0),
              paymentMethod: document.getElementById("posPay").value,
              customerId: document.getElementById("posCustomer").value
            }
          });
          cart = [];
          notifyElectronPosActivity();
          window.print();
        } catch (err) {
          alert(err.message);
        }
      };
    }
    root.querySelectorAll("[data-ret]").forEach((el) => {
      el.onclick = () => apiAction("RETURN_INVOICE", { invoiceId: el.getAttribute("data-ret") });
    });
    notifyElectronPosActivity();
  }

  function addProductToCart(prod) {
    if (prod.currentStock <= 0) return;
    const found = cart.find((c) => c.product.id === prod.id);
    if (found) {
      if (found.qty < prod.currentStock) found.qty++;
      if (found.qty >= prod.wholesaleMinQty) found.wholesale = true;
    } else {
      cart.push({ product: prod, qty: 1, wholesale: 1 >= prod.wholesaleMinQty });
    }
  }

  function renderInventory(root) {
    const s = t();
    const canEditProducts = currentUser.role === "admin" || currentUser.role === "manager" || currentUser.role === "cashier";
    const canDelete = currentUser.role === "admin" || currentUser.role === "manager";
    const suppliers = (state.parties || []).filter((p) => p.partyType === "supplier");
    const ep = editingProduct;
    root.innerHTML = `
      <div class="stack">
        ${!canEditProducts ? `<div class="card" style="border-color:var(--secondary);color:var(--secondary)"><strong>${s.sellerNoEdit}</strong></div>` : ""}
        ${
          canEditProducts
            ? `
          <form id="prodForm" class="card stack">
            <div class="between">
              <h3 style="margin:0">${ep ? `${s.edit}: ${lang === "ar" ? ep.nameAr : ep.nameEn}` : s.addProduct}</h3>
              ${ep ? `<button type="button" id="cancelEditProd" class="btn">${s.cancel}</button>` : ""}
            </div>
            <div class="row">
              <input id="pNameAr" placeholder="Name (AR)" value="${ep ? ep.nameAr.replace(/"/g, "&quot;") : ""}" required />
              <input id="pNameEn" placeholder="Name (EN)" value="${ep ? ep.nameEn.replace(/"/g, "&quot;") : ""}" required />
              <select id="pCat" style="max-width:140px">
                <option value="perfume" ${!ep || ep.category === "perfume" ? "selected" : ""}>Perfume</option>
                <option value="cream" ${ep && ep.category === "cream" ? "selected" : ""}>Cream</option>
                <option value="other" ${ep && ep.category === "other" ? "selected" : ""}>Other</option>
              </select>
            </div>
            <div class="row">
              <input id="pSku" placeholder="SKU" value="${ep ? ep.sku.replace(/"/g, "&quot;") : ""}" required />
              <input id="pBarcode" placeholder="Barcode" value="${ep ? (ep.barcode || "").replace(/"/g, "&quot;") : ""}" />
              <input id="pCost" type="number" step="0.01" placeholder="${s.costPrice}" value="${ep ? ep.costPrice : ""}" required />
              <input id="pRetail" type="number" step="0.01" placeholder="${s.retailPrice}" value="${ep ? ep.retailPrice : ""}" required />
              <input id="pWholesale" type="number" step="0.01" placeholder="${s.wholesalePrice}" value="${ep ? ep.wholesalePrice : ""}" required />
              <input id="pMinQty" type="number" value="${ep ? ep.wholesaleMinQty : 5}" placeholder="${s.minWholesaleQty}" />
              ${ep ? "" : `<input id="pInitStock" type="number" value="10" placeholder="Initial Stock" />`}
              <button class="btn btn-primary" type="submit">${s.save}</button>
            </div>
          </form>
          <form id="recvForm" class="card row">
            <strong style="min-width:120px">${s.receiveStock}</strong>
            <select id="rProd">${state.products.map((p) => `<option value="${p.id}">${lang === "ar" ? p.nameAr : p.nameEn} (${p.currentStock})</option>`).join("")}</select>
            <select id="rSup"><option value="">-</option>${suppliers.map((sup) => `<option value="${sup.id}">${sup.name}</option>`).join("")}</select>
            <input id="rQty" type="number" min="1" value="5" style="max-width:100px" />
            <input id="rCost" type="number" step="0.01" placeholder="${s.costPrice}" style="max-width:120px" />
            <button class="btn btn-primary" type="submit">${s.save}</button>
          </form>
          <form id="adjForm" class="card row">
            <strong style="min-width:120px">${s.adjustStock}</strong>
            <select id="aProd">${state.products.map((p) => `<option value="${p.id}">${lang === "ar" ? p.nameAr : p.nameEn}</option>`).join("")}</select>
            <input id="aDelta" type="number" placeholder="+/-" style="max-width:100px" required />
            <input id="aReason" placeholder="${s.reasonRequired}" required />
            <button class="btn btn-primary" type="submit">${s.save}</button>
          </form>`
            : ""
        }
        <div class="card">
          <table>
            <thead><tr><th>SKU</th><th>Product</th><th>${s.retail}</th><th>${s.wholesale}</th><th>${s.stock}</th><th></th></tr></thead>
            <tbody>
              ${state.products
                .map(
                  (p) => `
                <tr>
                  <td class="mono">${p.sku}</td>
                  <td>${lang === "ar" ? p.nameAr : p.nameEn}</td>
                  <td class="mono">${formatMoney(p.retailPrice)}</td>
                  <td class="mono">${formatMoney(p.wholesalePrice)}</td>
                  <td class="mono"><strong>${p.currentStock}</strong></td>
                  <td class="row">
                    ${canEditProducts ? `<button class="btn" data-edit-prod="${p.id}">${s.edit}</button>` : ""}
                    ${canDelete ? `<button class="btn btn-danger" data-del="${p.id}">${s.deleteBtn}</button>` : ""}
                  </td>
                </tr>`
                )
                .join("")}
            </tbody>
          </table>
        </div>
      </div>
    `;
    const cancelEditProd = document.getElementById("cancelEditProd");
    if (cancelEditProd) {
      cancelEditProd.onclick = () => {
        editingProduct = null;
        renderInventory(root);
      };
    }
    const prodForm = document.getElementById("prodForm");
    if (prodForm) {
      prodForm.onsubmit = async (e) => {
        e.preventDefault();
        const initEl = document.getElementById("pInitStock");
        const prodPayload = {
          ...(ep ? { id: ep.id } : {}),
          nameAr: document.getElementById("pNameAr").value,
          nameEn: document.getElementById("pNameEn").value,
          category: document.getElementById("pCat").value,
          sku: document.getElementById("pSku").value,
          barcode: document.getElementById("pBarcode").value,
          costPrice: Number(document.getElementById("pCost").value),
          retailPrice: Number(document.getElementById("pRetail").value),
          wholesalePrice: Number(document.getElementById("pWholesale").value),
          wholesaleMinQty: Number(document.getElementById("pMinQty").value || 5)
        };
        editingProduct = null;
        await apiAction("SAVE_PRODUCT", {
          product: prodPayload,
          initialStock: initEl ? Number(initEl.value || 0) : 0
        });
      };
    }
    const recvForm = document.getElementById("recvForm");
    if (recvForm) {
      recvForm.onsubmit = async (e) => {
        e.preventDefault();
        const pid = document.getElementById("rProd").value;
        const prod = state.products.find((p) => p.id === pid);
        await apiAction("RECEIVE_STOCK", {
          receipt: {
            productId: pid,
            supplierId: document.getElementById("rSup").value,
            qty: Number(document.getElementById("rQty").value),
            unitCost: Number(document.getElementById("rCost").value || (prod ? prod.costPrice : 0))
          }
        });
      };
    }
    const adjForm = document.getElementById("adjForm");
    if (adjForm) {
      adjForm.onsubmit = async (e) => {
        e.preventDefault();
        await apiAction("ADJUST_STOCK", {
          adjustment: {
            productId: document.getElementById("aProd").value,
            qtyDelta: Number(document.getElementById("aDelta").value),
            reason: document.getElementById("aReason").value
          }
        });
      };
    }
    root.querySelectorAll("[data-edit-prod]").forEach((btn) => {
      btn.onclick = () => {
        editingProduct = state.products.find((p) => p.id === btn.getAttribute("data-edit-prod")) || null;
        renderInventory(root);
      };
    });
    root.querySelectorAll("[data-del]").forEach((btn) => {
      btn.onclick = () => apiAction("DELETE_PRODUCT", { productId: btn.getAttribute("data-del") });
    });
  }

  function renderDay(root) {
    const s = t();
    const open = state.openBusinessDay;
    const canOverride = currentUser.role === "admin" || currentUser.role === "manager";
    root.innerHTML = `
      <div class="stack">
        <div class="card stack">
          <h3 style="margin:0">${s.navDay}</h3>
          ${
            open
              ? `<p>${open.dateStr} — <strong>${formatMoney(open.totalSales)}</strong> (${open.itemsSoldCount})</p>
                 <button id="closeDayBtn" class="btn btn-danger">${s.closeDay}</button>`
              : `<div class="row">
                   <input id="openCashInput" type="number" value="0" placeholder="${s.openingCash}" style="max-width:220px" />
                   <button id="openDayBtn" class="btn btn-primary">${s.openDay}</button>
                   ${canOverride ? `<button id="overrideDayBtn" class="btn">${s.openDay} (Override)</button>` : ""}
                 </div>`
          }
        </div>
        ${
          canOverride
            ? `<form id="holForm" class="card row">
                 <strong>${s.holidays}</strong>
                 <input id="hDate" placeholder="YYYY-MM-DD" style="max-width:150px" />
                 <input id="hTitle" placeholder="Title" />
                 <button class="btn btn-primary" type="submit">${s.addHoliday}</button>
               </form>`
            : ""
        }
        <div class="card stack">
          ${(state.businessDays || [])
            .slice(0, 10)
            .map((d) => `<div class="between"><span class="mono">${d.dateStr} (${d.status})</span><strong>${formatMoney(d.totalSales)}</strong></div>`)
            .join("")}
        </div>
      </div>
    `;
    const closeBtn = document.getElementById("closeDayBtn");
    if (closeBtn) closeBtn.onclick = () => apiAction("CLOSE_DAY");
    const openBtn = document.getElementById("openDayBtn");
    if (openBtn) {
      openBtn.onclick = () =>
        apiAction("OPEN_DAY", { day: { openingCash: Number(document.getElementById("openCashInput").value || 0) } }).catch((e) =>
          alert(e.message)
        );
    }
    const ovBtn = document.getElementById("overrideDayBtn");
    if (ovBtn) {
      ovBtn.onclick = () =>
        apiAction("OPEN_DAY", { day: { openingCash: Number(document.getElementById("openCashInput").value || 0), holidayOverride: true } });
    }
    const holForm = document.getElementById("holForm");
    if (holForm) {
      holForm.onsubmit = (e) => {
        e.preventDefault();
        apiAction("ADD_HOLIDAY", {
          holiday: { dateStr: document.getElementById("hDate").value, titleAr: document.getElementById("hTitle").value, titleEn: document.getElementById("hTitle").value }
        });
      };
    }
  }

  function renderDashboard(root) {
    const s = t();
    const completed = (state.invoices || []).filter((i) => i.status === "completed");
    const totalSales = completed.reduce((acc, i) => acc + i.total, 0);
    const totalCost = completed.reduce((acc, i) => acc + (i.costTotal || 0), 0);
    const lowStock = (state.products || []).filter((p) => p.currentStock <= p.lowStockThreshold);
    const showProfit = currentUser.role === "admin" || currentUser.role === "manager";
    root.innerHTML = `
      <div class="stack">
        <div class="kpi-grid">
          <div class="card"><div>${s.periodSales}</div><h2 class="mono">${formatMoney(totalSales)}</h2></div>
          ${showProfit ? `<div class="card"><div>${s.netProfit}</div><h2 class="mono">${formatMoney(totalSales - totalCost)}</h2></div>` : ""}
          <div class="card"><div>${s.lowStock}</div><h2 class="mono">${lowStock.length}</h2></div>
        </div>
        <div class="card stack">
          <h3 style="margin:0">${s.lowStock}</h3>
          ${lowStock.map((p) => `<div class="between"><span>${lang === "ar" ? p.nameAr : p.nameEn}</span><strong class="mono">${p.currentStock} / ${p.lowStockThreshold}</strong></div>`).join("") || `<span style="color:var(--muted)">-</span>`}
        </div>
      </div>
    `;
  }

  function renderReports(root) {
    const s = t();
    const completed = (state.invoices || []).filter((i) => i.status === "completed");
    root.innerHTML = `
      <div class="stack">
        <div class="card row no-print">
          <button id="csvBtn" class="btn btn-primary">${s.exportCsv}</button>
          <button id="printBtn" class="btn">${s.printReport}</button>
        </div>
        <div class="card">
          <h3>${s.navReports}</h3>
          <table>
            <thead><tr><th>Invoice</th><th>Type</th><th>Payment</th><th>Total</th></tr></thead>
            <tbody>
              ${completed
                .slice(0, 50)
                .map((i) => `<tr><td class="mono">${i.invoiceNumber}</td><td>${i.saleType}</td><td>${i.paymentMethod}</td><td class="mono">${formatMoney(i.total)}</td></tr>`)
                .join("")}
            </tbody>
          </table>
        </div>
      </div>
    `;
    document.getElementById("printBtn").onclick = () => window.print();
    document.getElementById("csvBtn").onclick = () => {
      const rows = [["Invoice", "Type", "Payment", "Subtotal", "Discount", "Total", "Profit"]];
      completed.forEach((i) => rows.push([i.invoiceNumber, i.saleType, i.paymentMethod, i.subtotal, i.discount, i.total, (i.total - i.costTotal).toFixed(2)]));
      const csv = "\uFEFF" + rows.map((r) => r.join(",")).join("\n");
      const blob = new Blob([csv], { type: "text/csv;charset=utf-8;" });
      const a = document.createElement("a");
      a.href = URL.createObjectURL(blob);
      a.download = "aroma_pos_report.csv";
      a.click();
    };
  }

  function renderAdmin(root) {
    const s = t();
    const st = state.settings;
    if (editingMember && editingMember.role === "admin" && currentUser.role !== "admin") {
      editingMember = null;
    }
    const em = editingMember;
    const members = state.members || [];
    const activeAdmins = members.filter((x) => x.role === "admin" && x.active !== false).length;
    const totalAdmins = members.filter((x) => x.role === "admin").length;
    const isEditingLastActiveAdmin = Boolean(
      em && em.role === "admin" && em.active !== false && activeAdmins <= 1
    );
    const roleName = (r) =>
      r === "admin" ? s.roleAdmin : r === "manager" ? s.roleManager : r === "cashier" ? s.roleCashier : s.roleSeller;
    root.innerHTML = `
      <div class="stack">
        ${adminNotice ? `<div class="card" style="border-color:${adminNoticeError ? "var(--danger)" : "var(--primary)"};color:${adminNoticeError ? "var(--danger)" : "inherit"}"><strong>${adminNotice}</strong></div>` : ""}
        <div class="card stack" style="border-color:var(--primary)">
          <h3 style="margin:0">${s.pcLanHeading}</h3>
          <p style="margin:0;color:var(--muted)">${s.pcLanDesc}</p>
          <div class="row" style="flex-wrap:wrap">
            ${(lanUrls.length ? lanUrls : ["http://127.0.0.1:8787"]).map((u) => `<span class="badge badge-online mono">${u}</span>`).join("")}
          </div>
          <div><strong>${s.connectedDevices}:</strong> ${(state.connectedDevices || []).map((d) => d.name).join(", ") || "-"}</div>
        </div>
        <form id="staffForm" class="card stack">
          <div class="between">
            <strong>${em ? `${s.editUser}: ${em.username || em.displayName}` : s.staffUsers}</strong>
            ${em ? `<button type="button" id="cancelEditMemberBtn" class="btn">${s.cancel}</button>` : ""}
          </div>
          <div class="row">
            <input id="stUser" placeholder="${s.username}" value="${em ? (em.username || em.displayName).replace(/"/g, "&quot;") : ""}" required />
            <input id="stName" placeholder="${s.displayName}" value="${em ? (em.displayName || "").replace(/"/g, "&quot;") : ""}" required />
            <input id="stPass" type="password" placeholder="${em ? s.newPassHint : s.password}" ${em ? "" : "required"} />
            <select id="stRole" style="max-width:210px" ${isEditingLastActiveAdmin ? "disabled" : ""}>
              <option value="seller" ${!em || em.role === "seller" ? "selected" : ""}>${s.roleSeller}</option>
              <option value="cashier" ${em && em.role === "cashier" ? "selected" : ""}>${s.roleCashier}</option>
              <option value="manager" ${em && em.role === "manager" ? "selected" : ""}>${s.roleManager}</option>
              ${currentUser.role === "admin" ? `<option value="admin" ${em && em.role === "admin" ? "selected" : ""}>${s.roleAdmin}</option>` : ""}
            </select>
            <label class="row" style="max-width:120px;align-items:center">
              <input id="stActive" type="checkbox" ${!em || em.active !== false ? "checked" : ""} ${isEditingLastActiveAdmin ? "disabled" : ""} style="width:auto" />
              <span>${s.activeUser}</span>
            </label>
            <button class="btn btn-primary" type="submit">${s.save}</button>
          </div>
        </form>
        <div class="card">
          <table>
            <thead><tr><th>${s.displayName}</th><th>${s.username}</th><th>Role</th><th>Status</th><th></th></tr></thead>
            <tbody>
              ${members
                .map((m) => {
                  const canManageThis = currentUser.role === "admin" || m.role !== "admin";
                  const isLastProtectedAdmin = m.role === "admin" && ((m.active !== false && activeAdmins <= 1) || totalAdmins <= 1);
                  return `<tr>
                    <td>${m.displayName}</td>
                    <td class="mono">${m.username || m.uid}</td>
                    <td>${roleName(m.role)}</td>
                    <td>${m.active === false ? "✕" : "✓"}</td>
                    <td class="row">
                      ${canManageThis ? `<button class="btn" data-edit-member="${m.uid}">${s.editUser}</button>` : ""}
                      ${canManageThis && !isLastProtectedAdmin ? `<button class="btn btn-danger" data-del-member="${m.uid}">${s.deleteBtn}</button>` : ""}
                    </td>
                  </tr>`;
                })
                .join("")}
            </tbody>
          </table>
        </div>
        ${
          currentUser.role === "admin"
            ? `<form id="themeForm" class="card row">
                 <strong>${s.storeTheme}</strong>
                 <input id="setAr" value="${st.storeNameAr}" />
                 <input id="setEn" value="${st.storeNameEn}" />
                 <input id="setPri" type="color" value="${st.primaryColorHex}" style="max-width:64px;padding:2px" />
                 <input id="setSec" type="color" value="${st.secondaryColorHex}" style="max-width:64px;padding:2px" />
                 <button class="btn btn-primary" type="submit">${s.save}</button>
               </form>
               <form id="relForm" class="card row">
                 <strong>${s.publishUpdate}</strong>
                 <input id="relCode" type="number" value="2" style="max-width:80px" />
                 <input id="relVer" value="1.1.0" style="max-width:110px" />
                 <input id="relUrl" placeholder="https://.../app.apk" required />
                 <button class="btn btn-primary" type="submit">${s.publishUpdate}</button>
               </form>`
            : ""
        }
      </div>
    `;
    const cancelMemberBtn = document.getElementById("cancelEditMemberBtn");
    if (cancelMemberBtn) {
      cancelMemberBtn.onclick = () => {
        editingMember = null;
        renderAdmin(root);
      };
    }
    root.querySelectorAll("[data-edit-member]").forEach((btn) => {
      btn.onclick = () => {
        editingMember = (state.members || []).find((m) => m.uid === btn.getAttribute("data-edit-member")) || null;
        adminNotice = "";
        adminNoticeError = false;
        renderAdmin(root);
      };
    });
    root.querySelectorAll("[data-del-member]").forEach((btn) => {
      btn.onclick = async () => {
        const uid = btn.getAttribute("data-del-member");
        if (!confirm(s.confirmDeleteUser)) return;
        try {
          await apiAction("DELETE_STAFF", { uid });
          if (editingMember && editingMember.uid === uid) editingMember = null;
          adminNotice = s.deletedMsg;
          adminNoticeError = false;
          renderApp();
        } catch (err) {
          adminNotice = err.message === "Not allowed" ? s.notAllowed : err.message;
          adminNoticeError = true;
          renderApp();
        }
      };
    });
    document.getElementById("staffForm").onsubmit = async (e) => {
      e.preventDefault();
      try {
        await apiAction("UPSERT_STAFF", {
          staff: {
            uid: editingMember ? editingMember.uid : "",
            username: document.getElementById("stUser").value,
            displayName: document.getElementById("stName").value,
            password: document.getElementById("stPass").value,
            role: document.getElementById("stRole").value,
            active: document.getElementById("stActive").checked
          }
        });
        editingMember = null;
        adminNotice = s.savedMsg;
        adminNoticeError = false;
        renderApp();
      } catch (err) {
        adminNotice = err.message === "Not allowed" ? s.notAllowed : err.message;
        adminNoticeError = true;
        renderApp();
      }
    };
    const themeForm = document.getElementById("themeForm");
    if (themeForm) {
      themeForm.onsubmit = (e) => {
        e.preventDefault();
        apiAction("SAVE_SETTINGS", {
          settings: {
            storeNameAr: document.getElementById("setAr").value,
            storeNameEn: document.getElementById("setEn").value,
            primaryColorHex: document.getElementById("setPri").value,
            secondaryColorHex: document.getElementById("setSec").value
          }
        });
      };
    }
    const relForm = document.getElementById("relForm");
    if (relForm) {
      relForm.onsubmit = (e) => {
        e.preventDefault();
        apiAction("PUBLISH_RELEASE", {
          release: {
            platform: "android",
            versionCode: Number(document.getElementById("relCode").value),
            versionName: document.getElementById("relVer").value,
            downloadUrl: document.getElementById("relUrl").value,
            releaseNotesAr: "تحديث متاح",
            releaseNotesEn: "Update available",
            isMandatory: false
          }
        });
      };
    }
  }

  function renderApp() {
    applyThemeAndDirection();
    updateConnectivityBadge();
    if (!currentUser || !state) return;
    document.getElementById("storeHeaderName").textContent =
      lang === "ar" ? state.settings.storeNameAr : state.settings.storeNameEn;
    document.getElementById("userRoleBadge").textContent = `${currentUser.displayName} • ${currentUser.role}`;
    document.getElementById("lanIpChip").textContent = lanUrls[0] || `http://127.0.0.1:8787`;
    renderNav();
    const main = document.getElementById("mainContent");
    if (activeTab === "pos") renderPos(main);
    else if (activeTab === "inventory") renderInventory(main);
    else if (activeTab === "day") renderDay(main);
    else if (activeTab === "dashboard") renderDashboard(main);
    else if (activeTab === "reports") renderReports(main);
    else if (activeTab === "admin") renderAdmin(main);
  }

  document.getElementById("loginForm").onsubmit = async (e) => {
    e.preventDefault();
    const errEl = document.getElementById("loginError");
    errEl.classList.add("hidden");
    const res = await fetch("/api/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        username: document.getElementById("loginUsername").value,
        password: document.getElementById("loginPassword").value
      })
    });
    const data = await res.json();
    if (!res.ok) {
      errEl.textContent = t().invalidLogin;
      errEl.classList.remove("hidden");
      return;
    }
    currentUser = data.user;
    state = data.state;
    activeTab = currentUser.role === "cashier" ? "inventory" : "pos";
    editingMember = null;
    editingProduct = null;
    adminNotice = "";
    document.getElementById("loginView").classList.add("hidden");
    document.getElementById("appView").classList.remove("hidden");
    await refreshState();
  };

  function toggleLang() {
    lang = lang === "ar" ? "en" : "ar";
    localStorage.setItem("aroma_lang", lang);
    renderApp();
  }
  function toggleDark() {
    dark = !dark;
    localStorage.setItem("aroma_dark", dark ? "1" : "0");
    renderApp();
  }

  document.getElementById("loginLangBtn").onclick = toggleLang;
  document.getElementById("appLangBtn").onclick = toggleLang;
  document.getElementById("loginThemeBtn").onclick = toggleDark;
  document.getElementById("appThemeBtn").onclick = toggleDark;
  document.getElementById("signOutBtn").onclick = () => {
    currentUser = null;
    cart = [];
    document.getElementById("appView").classList.add("hidden");
    document.getElementById("loginView").classList.remove("hidden");
  };

  window.addEventListener("online", updateConnectivityBadge);
  window.addEventListener("offline", updateConnectivityBadge);
  if (window.aromaElectron) {
    window.aromaElectron.onUpdateReady((info) => {
      if (info.canRestartNow && confirm(`PC Update v${info.version} ready. Restart & install?`)) {
        window.aromaElectron.installPendingUpdate();
      }
    });
  }

  applyThemeAndDirection();
  updateConnectivityBadge();
})();
