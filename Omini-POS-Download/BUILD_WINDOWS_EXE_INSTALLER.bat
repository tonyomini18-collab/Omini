@echo off
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
echo Installer built inside: %~dp0app\dist\
start "" explorer.exe "%~dp0app\dist"
pause
