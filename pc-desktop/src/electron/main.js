const fs = require("node:fs");
const path = require("node:path");
const { app, BrowserWindow, ipcMain, dialog, Menu, safeStorage } = require("electron");
const { startPcServer } = require("../server");
const { CloudSync, FIREBASE_WEB_CONFIG } = require("./firebase-cloud-sync");

let mainWindow = null;
let pcServerHandle = null;
let hasOpenPosActivity = false;
let cloud = null;
let cloudWindow = null;


// ---------- cloud sync (PC <-> phones through the internet) ----------
function cloudConfigPath() {
  return path.join(app.getPath("userData"), "cloud-config.json");
}

function readCloudConfig() {
  try {
    const raw = JSON.parse(fs.readFileSync(cloudConfigPath(), "utf8"));
    let password = raw.password || "";
    if (raw.encrypted && password) {
      password = safeStorage.decryptString(Buffer.from(password, "base64"));
    }
    return { email: raw.email || "", password, apiKey: raw.apiKey || "", storeId: raw.storeId || "" };
  } catch (_) {
    return null;
  }
}

function writeCloudConfig(cfg) {
  const out = { email: cfg.email, apiKey: cfg.apiKey || "", storeId: cfg.storeId || "" };
  if (safeStorage.isEncryptionAvailable()) {
    out.encrypted = true;
    out.password = safeStorage.encryptString(cfg.password).toString("base64");
  } else {
    out.encrypted = false;
    out.password = cfg.password;
  }
  fs.mkdirSync(path.dirname(cloudConfigPath()), { recursive: true });
  fs.writeFileSync(cloudConfigPath(), JSON.stringify(out), "utf8");
}

function openCloudSetup() {
  if (cloudWindow && !cloudWindow.isDestroyed()) {
    cloudWindow.focus();
    return;
  }
  cloudWindow = new BrowserWindow({
    width: 520,
    height: 600,
    title: "Cloud Sync",
    parent: mainWindow || undefined,
    autoHideMenuBar: true,
    webPreferences: {
      preload: path.join(__dirname, "cloud-preload.js"),
      contextIsolation: true,
      nodeIntegration: false
    }
  });
  cloudWindow.loadFile(path.join(__dirname, "cloud-setup.html"));
}

function setupCloudSync() {
  if (!pcServerHandle || !pcServerHandle.engine) return;
  cloud = new CloudSync({
    engine: pcServerHandle.engine,
    loadConfig: readCloudConfig,
    onChange: () => {
      if (mainWindow && !mainWindow.isDestroyed()) mainWindow.webContents.send("pc-data-changed");
    }
  });
  cloud.start();

  ipcMain.handle("cloud:status", () => cloud.status());
  ipcMain.handle("cloud:save", async (_, cfg) => {
    const email = String((cfg && cfg.email) || "").trim();
    const prev = readCloudConfig() || {};
    const password = String((cfg && cfg.password) || "") || prev.password || "";
    if (!email || !password) return { ok: false, error: "Email and password are required" };
    writeCloudConfig({ email, password, apiKey: String((cfg && cfg.apiKey) || "").trim(), storeId: prev.storeId || "" });
    cloud.restart().catch(() => {});
    return { ok: true };
  });
  ipcMain.handle("cloud:sync-now", () => cloud.syncNow());
  ipcMain.handle("cloud:clear", async () => {
    try { fs.unlinkSync(cloudConfigPath()); } catch (_) { /* nothing saved */ }
    await cloud.disconnect();
    return { ok: true };
  });
  ipcMain.handle("cloud:reset-password", async (_, email) => {
    try {
      const cfg = readCloudConfig() || {};
      const key = cfg.apiKey || FIREBASE_WEB_CONFIG.apiKey;
      const res = await fetch(`https://identitytoolkit.googleapis.com/v1/accounts:sendOobCode?key=${key}`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ requestType: "PASSWORD_RESET", email: String(email || "").trim() })
      });
      if (!res.ok) {
        const body = await res.json().catch(() => ({}));
        return { ok: false, error: (body.error && body.error.message) || `HTTP ${res.status}` };
      }
      return { ok: true };
    } catch (e) {
      return { ok: false, error: e.message || String(e) };
    }
  });

  Menu.setApplicationMenu(
    Menu.buildFromTemplate([
      { role: "fileMenu" },
      { role: "editMenu" },
      { role: "viewMenu" },
      { role: "windowMenu" },
      { label: "Cloud Sync", submenu: [{ label: "Cloud Sync setup…", click: openCloudSetup }] }
    ])
  );
}

let pendingUpdateVersion = null;
let idlePromptSent = false;

function notifyUpdateReady() {
  if (!pendingUpdateVersion || !mainWindow || mainWindow.isDestroyed()) return;
  const canRestartNow = !hasOpenPosActivity;
  // Ask to restart once, at the first moment the POS is idle (no open day, empty cart).
  if (canRestartNow && idlePromptSent) return;
  if (canRestartNow) idlePromptSent = true;
  mainWindow.webContents.send("pc-update-ready", {
    version: pendingUpdateVersion,
    canRestartNow
  });
}

function setupAutoUpdater() {
  // Updates only work in the installed app, not in `npm run electron` dev runs.
  if (!app.isPackaged) return;

  let autoUpdater;
  try {
    ({ autoUpdater } = require("electron-updater"));
  } catch (_) {
    return;
  }

  autoUpdater.autoDownload = true;
  autoUpdater.autoInstallOnAppQuit = true;

  // Without an "error" listener, a failed check (for example no internet) can crash the app.
  autoUpdater.on("error", (err) => {
    console.warn("[updater]", err && err.message ? err.message : err);
  });

  autoUpdater.on("update-downloaded", (info) => {
    pendingUpdateVersion = info.version;
    idlePromptSent = false;
    notifyUpdateReady();
  });

  // Checking and downloading happen in the background at any time.
  // Restarting to install only happens when the POS is idle.
  const checkUpdates = () => {
    if (pendingUpdateVersion) return;
    autoUpdater.checkForUpdates().catch(() => {});
  };

  setTimeout(checkUpdates, 15 * 1000);
  setInterval(checkUpdates, 2 * 60 * 60 * 1000);

  ipcMain.handle("pc-install-update", () => {
    if (pendingUpdateVersion && !hasOpenPosActivity) {
      // silent install, then reopen the app
      autoUpdater.quitAndInstall(true, true);
      return true;
    }
    return false;
  });
}

ipcMain.on("pos-activity-state", (_, active) => {
  const wasActive = hasOpenPosActivity;
  hasOpenPosActivity = Boolean(active);
  if (wasActive && !hasOpenPosActivity) notifyUpdateReady();
});

ipcMain.handle("export-invoice-pdf", async (_, invoiceNumber) => {
  if (!mainWindow || mainWindow.isDestroyed()) return { ok: false };
  const safeName = String(invoiceNumber || "invoice").replace(/[^a-zA-Z0-9_-]/g, "_");
  const { canceled, filePath } = await dialog.showSaveDialog(mainWindow, {
    title: "Export Invoice PDF",
    defaultPath: `receipt_${safeName}.pdf`,
    filters: [{ name: "PDF Documents", extensions: ["pdf"] }]
  });
  if (canceled || !filePath) return { ok: false, canceled: true };
  const pdfBuffer = await mainWindow.webContents.printToPDF({
    printBackground: true,
    pageSize: "A4"
  });
  fs.writeFileSync(filePath, pdfBuffer);
  return { ok: true, filePath };
});

async function createWindow() {
  const dataPath = path.join(app.getPath("userData"), "aroma-pos-pc-store.json");
  pcServerHandle = await startPcServer({ dataFilePath: dataPath, port: 8787 });

  const iconPath = path.join(__dirname, "../../assets/icon.png");
  mainWindow = new BrowserWindow({
    width: 1320,
    height: 860,
    minWidth: 960,
    minHeight: 640,
    title: "Omini POS Desktop",
    ...(fs.existsSync(iconPath) ? { icon: iconPath } : {}),
    webPreferences: {
      preload: path.join(__dirname, "preload.js"),
      contextIsolation: true,
      nodeIntegration: false
    }
  });

  await mainWindow.loadURL(`http://127.0.0.1:${pcServerHandle.port}/`);
  setupAutoUpdater();
  try {
    setupCloudSync();
  } catch (e) {
    console.warn("[cloud] setup failed:", e && e.message);
  }
}

app.whenReady().then(createWindow);

app.on("window-all-closed", async () => {
  try {
    if (cloud) await cloud.stop();
  } catch (_) { /* ignore */ }
  if (pcServerHandle) await pcServerHandle.close();
  if (process.platform !== "darwin") app.quit();
});
