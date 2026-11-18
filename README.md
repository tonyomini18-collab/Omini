# Aroma POS (أروما POS) — Perfume & Cream Store Suite

A production-grade **offline-first** Android store management application built with **Kotlin, Jetpack Compose, Material Design 3, Room, CameraX + ML Kit, and Firebase (Auth + Cloud Firestore)** for perfume and cream wholesale & retail businesses.

> **Platform Scope Note**: This AI Studio workspace builds and previews the native **Android (`.apk`)** app directly, and also includes the complete **offline-first PC Desktop client** (`pc-desktop/`) with Electron + `electron-builder` (`.exe` NSIS packaging for Windows) and built-in **Wi-Fi LAN Sync + Firebase Cloud Sync** connecting the PC and Android apps.

---

## Connecting PC Desktop & Android (Offline LAN + Cloud)

1. **Start the PC Desktop App (`pc-desktop/`)**:
   - Run `npm install && npm start` (or `npm run electron` / `npm run build:win` on Windows to generate the `.exe` installer).
   - Sign in with Admin credentials (`omini` / `admin116`).
   - Open **Admin & Sync** to view the PC's local Wi-Fi address (for example `http://192.168.1.10:8787`).
2. **Connect from the Android App**:
   - On Android, sign in as `omini` / `admin116` (or via Google Sign-In).
   - Go to **Admin & System → App Updates**, enter the PC address under **PC Desktop Connection & Offline Sync (LAN)**, tap **Test PC Connection**, and tap **Sync & Pull Updates from PC** (or use the PC icon in the top bar anytime).
   - Products, price updates, sales invoices, stock movements, customers/suppliers, business days, and app updates sync bidirectionally over local Wi-Fi without internet—and also sync via Firebase Firestore when online.

---

## Key Features

- **Bilingual Arabic-First UI (RTL / LTR)**:
  - Defaults to Arabic (`ar`, full RTL layout) with a one-tap toggle to English (`en`, LTR).
  - Centralized translations in `app/src/main/java/com/example/i18n/AppStrings.kt`.
  - Light & Dark modes plus instant Admin-customizable primary/secondary theme colors and store branding.
  - Adaptive navigation: `NavigationBar` on phones and `NavigationRail` on tablets/foldables.
- **Fast Point of Sale (POS)**:
  - Instant search by product name (AR/EN), SKU, or barcode.
  - Live **CameraX + ML Kit** barcode scanner (`BarcodeScannerSheet.kt`) with manual barcode fallback.
  - Per-item Retail / Wholesale price toggle (auto-switches at `wholesaleMinQty`).
  - Cart discounts, Cash / Transfer / Credit payment methods, optional customer selection, and **PDF receipt export & sharing** (`ExportManager.kt`).
  - Invoice history & returns (restores stock via immutable `return` ledger movements and reverses customer credit balance).
- **Movement-Ledger Inventory & Parties**:
  - Current stock is computed dynamically as `SUM(qtyDelta)` across `stock_movements` (`opening`, `receive`, `sale`, `return`, `adjustment`) rather than overwritten.
  - Admin/Manager catalog & pricing edits require online connectivity; cashiers work offline and receive catalog updates on next sync.
  - Stock receiving updates supplier payables; manual stock adjustments (Admin/Manager only) require a reason and write to the audit log.
  - Customer receivables & supplier payables tracking with payment recording.
- **Business Day Control & Holidays**:
  - Sales are gated behind an open business day (`Open Day` / `Close Day`).
  - End-of-day closing summary locks totals (total sales, cash, transfer, credit, items sold).
  - Holiday calendar & recurring weekly off-days block day opening unless overridden by an Admin or General Manager.
- **Auto-Updating Dashboard & Reports**:
  - Period filters (Day, Week, Month, Year), KPI cards (profit hidden from Cashiers), Canvas bar charts (Best-Selling, Worst-Selling, Highest Stock), and Low-Stock alerts with quick restock navigation.
  - Manager/Admin Reports screen with **PDF** and **Excel-compatible UTF-8 BOM CSV** export.
- **Role-Based Access Control (UI + Firestore Rules)**:
  - `admin` (Technical Owner): Full access including store theme/name/logo customization, notification routing rules, session timeout, app release publishing, user management, and immutable audit log.
  - `manager` (General Manager): Full business operations (catalog, prices, stock adjustments, receiving, sales, reports/profit, holiday overrides, non-admin team members). No technical/theme/update controls.
  - `cashier` (Regular User): POS sales/returns and receiving stock only; cost prices, profits, reports, and catalog edits are hidden and blocked.
- **Offline Sync & App Updates**:
  - Offline mutations queue in Room `pending_sync_ops` and auto-flush when connectivity returns, with a live Online/Offline indicator and pending count pill.
  - Launch update check compares `BuildConfig.VERSION_CODE` against Firestore `app_releases` (never interrupting an active POS cart) and launches the APK download URL via `Intent.ACTION_VIEW`.

---

## Firebase & Project Configuration

1. **Firebase Project & Named Database**:
   - `app/google-services.json` contains your Android Firebase client configuration.
   - `app/src/main/res/values/firebase_applet_config.xml` specifies `firestore_database_id` (`ai-studio-android-aromapos-5c39add0-9320-45cf-a066-a5c1f668de36`).
   - `FirestoreRepository` always binds to this named database via `FirebaseFirestore.getInstance(context.getString(R.string.firestore_database_id))`.
2. **Authentication**:
   - Uses **Google Sign-In via Jetpack Credential Manager** (`GetSignInWithGoogleOption`) + Firebase Auth.
   - Ensure Google Sign-In is enabled in Firebase Console -> Authentication -> Sign-in method, and your signing certificate SHA-1 is registered.
   - On first sign-in, `ensureStoreAndProfileInitialized` creates `/users/{uid}`, `/stores/{uid}`, `/stores/{uid}/members/{uid}` (`admin`), and seeds sample perfumes & creams locally and remotely.
3. **Firestore Security Rules**:
   - Rules live in `/firestore.rules` and schema metadata in `/firebase-blueprint.json`.
   - Zero-trust enforcement: unauthenticated reads/writes are denied, every subcollection checks `memberUids` and role (`admin`, `manager`, `cashier`), `audit_logs` are append-only/immutable, and `app_releases`/`settings` writes are restricted to `admin`.

---

## Running & Building

- **Build Debug APK**:
  ```bash
  gradle assembleDebug
  ```
- **Run Unit & Firestore Rules Tests** (start Firebase Firestore & Auth emulators first for rules tests):
  ```bash
  node --test firestore.test.js
  gradle :app:testDebugUnitTest
  ```
- **Publish an Android Update**:
  1. Sign in as **Admin** and navigate to **Admin & System -> App Updates**.
  2. Enter the new `versionCode` (higher than current), `versionName`, HTTPS APK download URL, AR/EN release notes, and optional Mandatory flag, then tap **Publish Update**.
