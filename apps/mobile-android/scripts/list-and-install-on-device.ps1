# Lista dispositivos Android conectados e instala la app PR4Y (flavor dev debug) en el que elijas.
# Uso: desde apps/mobile-android ejecutar: .\scripts\list-and-install-on-device.ps1
# O con dispositivo concreto: .\scripts\list-and-install-on-device.ps1 -Serial "ABC123"

param(
    [string]$Serial = ""
)

$ErrorActionPreference = "Continue"
$adb = $env:ANDROID_HOME
if (-not $adb) { $adb = "$env:LOCALAPPDATA\Android\Sdk" }
$adbExe = Join-Path $adb "platform-tools\adb.exe"
if (-not (Test-Path $adbExe)) {
    Write-Host "No se encontró adb. Configura ANDROID_HOME o instala Android SDK (p. ej. Android Studio)." -ForegroundColor Red
    exit 1
}

Write-Host "==> Dispositivos conectados (adb devices -l):" -ForegroundColor Cyan
$devicesOut = & $adbExe devices -l 2>&1
$devicesOut | ForEach-Object { Write-Host $_ }

$lines = $devicesOut | Where-Object { $_ -match "^\w+\s+device\b" }
$serials = @()
foreach ($line in $lines) {
    if ($line -match "^(\S+)\s+device") { $serials += $Matches[1] }
}

if ($serials.Count -eq 0) {
    Write-Host "`nNo hay dispositivos en estado 'device'. Comprueba:" -ForegroundColor Yellow
    Write-Host "  - Cable USB y que el móvil esté desbloqueado."
    Write-Host "  - Opciones desarrollador > Depuración USB activada."
    Write-Host "  - Si aparece el diálogo '¿Permitir depuración USB?' en el móvil, acepta."
    Write-Host "  - En el móvil elige 'Transferencia de archivos' o 'PTP' si lo pide."
    exit 1
}

$targetSerial = $Serial
if (-not $targetSerial -and $serials.Count -gt 1) {
    Write-Host "`nVarios dispositivos. Elige por número (1-$($serials.Count)):" -ForegroundColor Cyan
    for ($i = 0; $i -lt $serials.Count; $i++) {
        Write-Host "  $($i+1)) $($serials[$i])"
    }
    $num = Read-Host "Número"
    $idx = [int]$num - 1
    if ($idx -lt 0 -or $idx -ge $serials.Count) {
        Write-Host "Número no válido." -ForegroundColor Red
        exit 1
    }
    $targetSerial = $serials[$idx]
}
elseif (-not $targetSerial) {
    $targetSerial = $serials[0]
}

Write-Host "`n==> Instalando en dispositivo: $targetSerial" -ForegroundColor Green
$root = Split-Path $PSScriptRoot -Parent
if (-not (Test-Path (Join-Path $root "gradlew.bat"))) {
    Write-Host "No se encontró gradlew.bat en $root" -ForegroundColor Red
    exit 1
}
Set-Location $root

$env:ANDROID_SERIAL = $targetSerial
& .\gradlew.bat installDevDebug --no-daemon
if ($LASTEXITCODE -ne 0) {
    Write-Host "Falló la instalación. Revisa que el dispositivo esté desbloqueado y acepte la instalación." -ForegroundColor Red
    exit $LASTEXITCODE
}
Write-Host "`nApp instalada en $targetSerial. Puedes abrirla en el móvil." -ForegroundColor Green
