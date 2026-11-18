$ErrorActionPreference = "Stop"
Set-Location -Path $PSScriptRoot

Write-Host "==================================================" -ForegroundColor Cyan
Write-Host "  Aroma POS - Windows Installer (.exe) Builder" -ForegroundColor Cyan
Write-Host "==================================================" -ForegroundColor Cyan

if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
    throw "Node.js is not installed. Please install Node.js 20+ from https://nodejs.org/"
}
if (-not (Get-Command npm -ErrorAction SilentlyContinue)) {
    throw "npm was not found in PATH."
}

Write-Host "`n[1/3] Installing dependencies..." -ForegroundColor Yellow
& npm install
if ($LASTEXITCODE -ne 0) { throw "npm install failed with exit code $LASTEXITCODE" }

Write-Host "`n[2/3] Running desktop verification tests..." -ForegroundColor Yellow
& npm test
if ($LASTEXITCODE -ne 0) { throw "npm test failed with exit code $LASTEXITCODE" }

Write-Host "`n[3/3] Packaging Windows NSIS .exe installer..." -ForegroundColor Yellow
& npm run build:win
if ($LASTEXITCODE -ne 0) { throw "npm run build:win failed with exit code $LASTEXITCODE" }

$distDir = Join-Path $PSScriptRoot "dist"
$installers = Get-ChildItem -Path $distDir -Filter "*.exe" -ErrorAction SilentlyContinue
Write-Host "`nInstaller output:" -ForegroundColor Green
$installers | ForEach-Object { Write-Host " - $($_.FullName)" -ForegroundColor Green }
