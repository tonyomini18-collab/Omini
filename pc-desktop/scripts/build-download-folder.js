const fs = require("node:fs");
const path = require("node:path");

const pcRoot = path.resolve(__dirname, "..");
const repoRoot = path.resolve(pcRoot, "..");
const outDir = path.join(repoRoot, "Omini-POS-Download");
const appOutDir = path.join(outDir, "app");

function resetDir(dir) {
  fs.rmSync(dir, { recursive: true, force: true });
  fs.mkdirSync(dir, { recursive: true });
}

function copyRecursive(src, dest) {
  const stat = fs.statSync(src);
  if (stat.isDirectory()) {
    fs.mkdirSync(dest, { recursive: true });
    for (const entry of fs.readdirSync(src)) {
      copyRecursive(path.join(src, entry), path.join(dest, entry));
    }
  } else {
    fs.mkdirSync(path.dirname(dest), { recursive: true });
    fs.copyFileSync(src, dest);
  }
}

function buildDownloadFolder() {
  resetDir(outDir);
  fs.mkdirSync(appOutDir, { recursive: true });

  copyRecursive(path.join(pcRoot, "src"), path.join(appOutDir, "src"));
  copyRecursive(path.join(pcRoot, "assets"), path.join(appOutDir, "assets"));
  fs.copyFileSync(path.join(pcRoot, "package.json"), path.join(appOutDir, "package.json"));

  const startBat = `@echo off
setlocal
cd /d "%~dp0app"

where node >nul 2>nul
if errorlevel 1 (
  echo [ERROR] Node.js 20+ is required to run directly from source.
  echo Alternatively, use Omini-POS-Setup-1.0.0.exe built via GitHub Actions.
  pause
  exit /b 1
)

echo Starting Omini POS Offline Server on http://127.0.0.1:8787 ...
start "" http://127.0.0.1:8787
node src\\server.js
pause
`;

  const buildExeBat = `@echo off
setlocal
cd /d "%~dp0app"

where node >nul 2>nul
if errorlevel 1 (
  echo [ERROR] Node.js 20+ is required. Install from https://nodejs.org/
  pause
  exit /b 1
)

echo [1/2] Installing Electron and electron-builder...
call npm install
if errorlevel 1 (
  echo [ERROR] npm install failed.
  pause
  exit /b 1
)

echo [2/2] Building Windows NSIS .exe installer...
call npm run build:win
if errorlevel 1 (
  echo [ERROR] build:win failed.
  pause
  exit /b 1
)

echo.
echo Installer built inside: %~dp0app\\dist\\
start "" explorer.exe "%~dp0app\\dist"
pause
`;

  const readmeTxt = `Omini POS - Windows App Download Folder
========================================

1) GitHub Automated Windows .exe Installer (Recommended):
   - Push this repository to GitHub.
   - Open the GitHub repository -> "Actions" tab -> "Build Windows EXE Installer".
   - Download either:
     * Omini-POS-Windows-Installer   -> Omini-POS-Setup-1.0.0.exe (NSIS Installer)
     * Omini-POS-App-Files-Folder    -> Full Windows app files folder + installer + portable .exe

2) Run Offline Directly on Windows (with Node.js 20+):
   - Double-click: START_OFFLINE_POS.bat
   - Opens Omini POS offline on http://127.0.0.1:8787 (with LAN sync for Android on port 8787).

3) Build the Windows .exe Installer Locally on Windows:
   - Double-click: BUILD_WINDOWS_EXE_INSTALLER.bat
   - Outputs: app\\dist\\Omini-POS-Setup-1.0.0.exe and app\\dist\\win-unpacked\\

Default Admin Credentials:
  Username: omini
  Password: admin116
`;

  fs.writeFileSync(path.join(outDir, "START_OFFLINE_POS.bat"), startBat, "utf8");
  fs.writeFileSync(path.join(outDir, "BUILD_WINDOWS_EXE_INSTALLER.bat"), buildExeBat, "utf8");
  fs.writeFileSync(path.join(outDir, "HOW_TO_USE_AND_BUILD_EXE.txt"), readmeTxt, "utf8");

  console.log(`Prepared downloadable app folder at: ${outDir}`);
}

buildDownloadFolder();
