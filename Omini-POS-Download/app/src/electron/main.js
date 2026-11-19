const fs = require("node:fs");
const path = require("node:path");
const { app, BrowserWindow, ipcMain } = require("electron");
const { startPcServer } = require("../server");

let mainWindow = null;
let pcServerHandle = null;
let hasOpenPosActivity = false;

function setupAutoUpdater() {
  let autoUpdater;
  try {
    ({ autoUpdater } = require("electron-updater"));
  } catch (_) {
    return;
  }

  autoUpdater.autoDownload = true;
  autoUpdater.autoInstallOnAppQuit = true;

  autoUpdater.on("update-downloaded", (info) => {
    if (mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.webContents.send("pc-update-ready", {
        version: info.version,
        canRestartNow: !hasOpenPosActivity
      });
    }
  });

  const checkUpdates = () => {
    if (!hasOpenPosActivity) {
      autoUpdater.checkForUpdatesAndNotify().catch(() => {});
    }
  };

  checkUpdates();
  setInterval(checkUpdates, 4 * 60 * 60 * 1000);

  ipcMain.handle("pc-install-update", () => {
    if (!hasOpenPosActivity) {
      autoUpdater.quitAndInstall();
      return true;
    }
    return false;
  });
}

ipcMain.on("pos-activity-state", (_, active) => {
  hasOpenPosActivity = Boolean(active);
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
}

app.whenReady().then(createWindow);

app.on("window-all-closed", async () => {
  if (pcServerHandle) await pcServerHandle.close();
  if (process.platform !== "darwin") app.quit();
});
