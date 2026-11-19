@echo off
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
node src\server.js
pause
