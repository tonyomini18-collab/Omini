@echo off
setlocal
cd /d "%~dp0"

echo ==================================================
echo   Aroma POS - Windows Installer (.exe) Builder
echo ==================================================

where node >nul 2>nul
if errorlevel 1 (
  echo [ERROR] Node.js is not installed. Please install Node.js 20+ from https://nodejs.org/
  pause
  exit /b 1
)

where npm >nul 2>nul
if errorlevel 1 (
  echo [ERROR] npm was not found in PATH.
  pause
  exit /b 1
)

echo.
echo [1/3] Installing dependencies...
call npm install
if errorlevel 1 (
  echo [ERROR] npm install failed.
  pause
  exit /b 1
)

echo.
echo [2/3] Running desktop verification tests...
call npm test
if errorlevel 1 (
  echo [ERROR] Tests failed. Aborting installer build.
  pause
  exit /b 1
)

echo.
echo [3/3] Packaging Windows NSIS .exe installer...
call npm run build:win
if errorlevel 1 (
  echo [ERROR] Windows installer build failed.
  pause
  exit /b 1
)

echo.
echo ==================================================
echo   SUCCESS! Installer created in:
echo   %~dp0dist\
echo ==================================================
dir /b "%~dp0dist\*.exe"
start "" explorer.exe "%~dp0dist"
pause
