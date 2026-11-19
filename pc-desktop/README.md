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

## Build & Download the Windows Installer (`.exe`) & App Files Folder

The project uses `electron-builder` (NSIS) configured in `package.json` with custom app/installer icons (`assets/icon.ico` & `assets/icon.png`) and produces:
- `Aroma-POS-Setup-1.0.0.exe` (Windows NSIS Installer)
- `win-unpacked/` (Portable runnable Windows app files folder)
- `/Aroma-POS-Download/` (Self-contained workspace folder with app files and one-click Windows scripts)

### Option A — Build & Download Automatically in GitHub (Recommended)
1. Push this repository to GitHub (using AI Studio's Push to GitHub option).
2. The **Build Windows EXE Installer** workflow runs automatically on push (or manually via **Actions -> Run workflow**).
3. Download from the workflow run's **Artifacts**:
   - **`Aroma-POS-Windows-Installer`**: direct `Aroma-POS-Setup-1.0.0.exe` installer.
   - **`Aroma-POS-App-Files-Folder`**: complete folder containing the `.exe` installer, `Aroma-POS-Portable-App/` unpacked files, and `Aroma-POS-Windows-App-Files.zip`.
   *(Running the workflow manually with `create_release = true` or pushing a `v*` tag also publishes a GitHub Release with both files attached.)*

### Option B — Use the Pre-Packaged `/Aroma-POS-Download/` Folder on Windows
1. Download/copy the **`Aroma-POS-Download`** folder to a Windows PC (with Node.js 20+).
2. Double-click **`START_OFFLINE_POS.bat`** to launch immediately in offline mode (`http://127.0.0.1:8787`), or double-click **`BUILD_WINDOWS_EXE_INSTALLER.bat`** to compile the `.exe` installer locally.

## Commands (on PC / Windows)
```bash
cd pc-desktop
npm install                   # install Electron, electron-builder, electron-updater, firebase
npm start                     # launch offline PC server & open http://127.0.0.1:8787
npm run electron              # launch native Electron desktop window
npm run prepare:download-folder  # refresh root /Aroma-POS-Download/ bundle folder
npm run build:win             # build Windows NSIS .exe installer into pc-desktop/dist/
npm test                      # run unit & LAN sync integration tests (zero external deps required)
```


