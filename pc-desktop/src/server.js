const fs = require("node:fs");
const http = require("node:http");
const os = require("node:os");
const path = require("node:path");
const { PcStoreEngine } = require("./core/store-engine");

const DEFAULT_PORT = Number(process.env.AROMA_PC_PORT || 8787);
const RENDERER_DIR = path.join(__dirname, "renderer");
const PKG_VERSION = require("../package.json").version || "1.0.0";

function getLanAddresses(port) {
  const ifaces = os.networkInterfaces();
  const urls = [];
  for (const name of Object.keys(ifaces)) {
    for (const net of ifaces[name] || []) {
      if (net.family === "IPv4" && !net.internal) {
        urls.push(`http://${net.address}:${port}`);
      }
    }
  }
  return urls;
}

function readJsonBody(req) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let size = 0;
    req.on("data", (chunk) => {
      size += chunk.length;
      if (size > 15 * 1024 * 1024) {
        reject(new Error("Payload too large"));
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });
    req.on("end", () => {
      try {
        const str = Buffer.concat(chunks).toString("utf8").trim();
        resolve(str ? JSON.parse(str) : {});
      } catch (e) {
        reject(e);
      }
    });
    req.on("error", reject);
  });
}

function sendJson(res, status, data) {
  const body = JSON.stringify(data);
  res.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Headers": "Content-Type",
    "Access-Control-Allow-Methods": "GET,POST,OPTIONS"
  });
  res.end(body);
}

function sanitizeStateForRole(engine, role) {
  const isCashier = role === "cashier";
  const products = engine.getProductsWithStock(false).map((p) =>
    isCashier ? { ...p, costPrice: 0 } : p
  );
  const invoices = engine.state.invoices.map((inv) =>
    isCashier ? { ...inv, costTotal: 0 } : inv
  );
  return {
    products,
    movements: engine.state.movements.slice(0, 300),
    invoices: invoices.slice(0, 300),
    parties: engine.state.parties,
    businessDays: engine.state.businessDays,
    openBusinessDay: engine.getOpenBusinessDay(),
    holidays: engine.state.holidays,
    auditLogs: isCashier ? [] : engine.state.auditLogs.slice(0, 200),
    members: engine.state.members,
    settings: engine.state.settings,
    releases: engine.state.releases,
    pendingCloudCount: (engine.state.pendingCloudSync || []).length,
    connectedDevices: engine.state.connectedDevices || []
  };
}

function startPcServer({
  dataFilePath = path.join(os.homedir(), ".aroma-pos-pc", "store-data.json"),
  port = DEFAULT_PORT,
  host = "0.0.0.0",
  onSyncActivity = null
} = {}) {
  const engine = new PcStoreEngine(dataFilePath);

  const server = http.createServer(async (req, res) => {
    const reqUrl = new URL(req.url, `http://127.0.0.1:${port}`);
    if (req.method === "OPTIONS") {
      sendJson(res, 204, {});
      return;
    }

    try {
      if (req.method === "GET" && reqUrl.pathname === "/api/health") {
        sendJson(res, 200, {
          ok: true,
          version: PKG_VERSION,
          storeNameAr: engine.state.settings.storeNameAr,
          storeNameEn: engine.state.settings.storeNameEn,
          productsCount: engine.state.products.filter((p) => !p.isDeleted).length,
          invoicesCount: engine.state.invoices.length,
          lanUrls: getLanAddresses(server.address().port),
          serverTimeMillis: Date.now()
        });
        return;
      }

      if (req.method === "POST" && reqUrl.pathname === "/api/sync") {
        const incoming = await readJsonBody(req);
        const merged = engine.mergeSnapshot(incoming);
        if (typeof onSyncActivity === "function") onSyncActivity(incoming.clientDevice || "Android");
        sendJson(res, 200, merged);
        return;
      }

      if (req.method === "POST" && reqUrl.pathname === "/api/auth/login") {
        const body = await readJsonBody(req);
        const user = engine.authenticateLocal(body.username, body.password);
        if (!user) {
          sendJson(res, 401, { error: "Invalid credentials" });
          return;
        }
        sendJson(res, 200, { user, state: sanitizeStateForRole(engine, user.role) });
        return;
      }

      if (req.method === "GET" && reqUrl.pathname === "/api/state") {
        const role = reqUrl.searchParams.get("role") || "cashier";
        sendJson(res, 200, {
          state: sanitizeStateForRole(engine, role),
          lanUrls: getLanAddresses(server.address().port),
          version: PKG_VERSION
        });
        return;
      }

      if (req.method === "POST" && reqUrl.pathname === "/api/action") {
        const body = await readJsonBody(req);
        const actor = body.actor || { uid: "local_admin_omini", displayName: "omini", role: "admin" };
        let result = null;
        switch (body.type) {
          case "SAVE_PRODUCT":
            result = engine.saveProduct(actor, body.product, body.initialStock || 0);
            break;
          case "DELETE_PRODUCT":
            result = engine.deleteProduct(actor, body.productId);
            break;
          case "COMPLETE_SALE":
            result = engine.completeSale(actor, body.sale);
            break;
          case "RETURN_INVOICE":
            result = engine.returnInvoice(actor, body.invoiceId);
            break;
          case "RECEIVE_STOCK":
            result = engine.receiveStock(actor, body.receipt);
            break;
          case "ADJUST_STOCK":
            result = engine.adjustStock(actor, body.adjustment);
            break;
          case "SAVE_PARTY":
            result = engine.saveParty(body.party);
            break;
          case "PARTY_PAYMENT":
            result = engine.recordPartyPayment(actor, body.partyId, body.amount);
            break;
          case "OPEN_DAY":
            result = engine.openBusinessDay(actor, body.day || {});
            break;
          case "CLOSE_DAY":
            result = engine.closeBusinessDay(actor);
            break;
          case "ADD_HOLIDAY":
            result = engine.addHoliday(actor, body.holiday);
            break;
          case "DELETE_HOLIDAY":
            engine.deleteHoliday(actor, body.id);
            result = { deleted: true };
            break;
          case "UPSERT_STAFF":
            result = engine.upsertStaffCredential(actor.role, body.staff);
            break;
          case "SAVE_SETTINGS":
            result = engine.saveSettings(actor, body.settings);
            break;
          case "PUBLISH_RELEASE":
            result = engine.publishRelease(actor, body.release);
            break;
          default:
            sendJson(res, 400, { error: `Unknown action: ${body.type}` });
            return;
        }
        sendJson(res, 200, { result, state: sanitizeStateForRole(engine, actor.role) });
        return;
      }

      // Serve static renderer UI
      const safePath = reqUrl.pathname === "/" ? "/index.html" : reqUrl.pathname;
      const target = path.normalize(path.join(RENDERER_DIR, safePath));
      if (!target.startsWith(RENDERER_DIR) || !fs.existsSync(target) || fs.statSync(target).isDirectory()) {
        res.writeHead(404);
        res.end("Not Found");
        return;
      }
      const ext = path.extname(target).toLowerCase();
      const mimeMap = {
        ".html": "text/html; charset=utf-8",
        ".js": "application/javascript; charset=utf-8",
        ".css": "text/css; charset=utf-8",
        ".json": "application/json; charset=utf-8"
      };
      res.writeHead(200, { "Content-Type": mimeMap[ext] || "application/octet-stream" });
      fs.createReadStream(target).pipe(res);
    } catch (err) {
      sendJson(res, 400, { error: err.message || "Bad request" });
    }
  });

  return new Promise((resolve) => {
    server.listen(port, host, () => {
      const boundPort = server.address().port;
      resolve({
        server,
        engine,
        port: boundPort,
        lanUrls: getLanAddresses(boundPort),
        close: () => new Promise((r) => server.close(() => r()))
      });
    });
  });
}

if (require.main === module) {
  startPcServer().then(({ port, lanUrls }) => {
    console.log(`Aroma POS PC Server running offline-ready on http://127.0.0.1:${port}`);
    if (lanUrls.length > 0) {
      console.log(`Connect Android App (same Wi-Fi) to: ${lanUrls.join(", ")}`);
    }
  });
}

module.exports = { startPcServer, getLanAddresses };
