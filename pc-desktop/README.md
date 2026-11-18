# Aroma POS — PC Desktop Client (Offline-First + Android Sync)

Offline-first Windows / Desktop POS & Inventory companion for the Aroma POS Android application.

## Features
- **Works 100% Offline**: Local persistent store (`store-data.json` + IndexedDB mirror), pre-seeded perfume & cream catalog, movement-ledger stock, POS cart, returns, business day open/close, holidays, reports (CSV/Print PDF), and Admin customization.
- **Admin Login**: Default credentials `omini` / `admin116`.
- **Direct Wi-Fi LAN Sync with Android App (No Internet Required)**:
  - Runs an embedded LAN sync server on port `8787` (`GET /api/health`, `POST /api/sync`).
  - Displays the PC's local IP address (e.g. `http://192.168.1.x:8787`) under **Admin & Sync**.
  - On the Android app, open **Admin & System -> Updates & PC Sync**, enter the PC address, and tap **Sync & Pull Updates from PC** (or tap the PC icon in the top bar) to exchange sales, stock movements, price updates, customers/suppliers, and APK releases.
- **Cloud Sync & Windows `.exe` Auto-Updater**:
  - Includes Firebase Firestore sync connector (`src/electron/firebase-cloud-sync.js`) pointing to the shared named database.
  - Includes `electron-builder` NSIS configuration and `electron-updater` (never interrupts an open business day or active cart).

## Build the Windows Installer (`.exe`)

The project uses `electron-builder` (NSIS) configured in `package.json` with custom app/installer icons (`build/icon.ico` & `build/icon.png`) and produces `pc-desktop/dist/Aroma-POS-Setup-1.0.0.exe` (preserving store data on upgrades/uninstall).

### Option A — Build locally on a Windows PC (One-Click)
1. Install [Node.js 20+](https://nodejs.org/) on Windows.
2. Double-click **`BUILD_WINDOWS_INSTALLER.bat`** (or run `./BUILD_WINDOWS_INSTALLER.ps1` in PowerShell).
3. The script installs dependencies, runs `npm test`, and outputs `pc-desktop\dist\Aroma-POS-Setup-1.0.0.exe`.

### Option B — Build automatically via GitHub Actions (No Windows PC needed for build)
1. Push this repository to GitHub (using AI Studio's Push to GitHub option).
2. Open the repository's **Actions** tab and run **Build Windows EXE Installer** (`.github/workflows/build-windows-exe.yml`).
3. Download the **`Aroma-POS-Windows-Installer`** artifact containing `Aroma-POS-Setup-1.0.0.exe`.

## Commands (on PC / Windows)
```bash
cd pc-desktop
npm install          # install Electron, electron-builder, electron-updater, firebase
npm start            # launch offline PC server & open http://127.0.0.1:8787
npm run electron     # launch native Electron desktop window
npm run build:win    # build Windows NSIS .exe installer into pc-desktop/dist/
npm test             # run unit & LAN sync integration tests (zero external deps required)
```

