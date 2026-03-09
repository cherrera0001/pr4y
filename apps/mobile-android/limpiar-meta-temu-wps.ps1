# Limpieza completa: Facebook, Instagram, Facebook System, Temu, WPS Office
# Ejecutar con el móvil conectado por USB (depuración USB activada).
# Uso: powershell -NoProfile -ExecutionPolicy Bypass -File limpiar-meta-temu-wps.ps1

$ErrorActionPreference = "Stop"
$ADB = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"

if (-not (Test-Path $ADB)) {
    Write-Error "No se encuentra adb en: $ADB"
    exit 1
}

# Paquetes a desinstalar (apps de usuario)
$USER_APPS = @(
    "com.facebook.katana",           # Facebook
    "com.instagram.android",       # Instagram
    "com.facebook.stella",         # Stella (Meta)
    "com.einnovation.temu",        # Temu
    "cn.wps.xiaomi.abroad.lite"    # WPS Office
)

# Paquetes de sistema Meta (solo se pueden deshabilitar sin root)
$SYSTEM_META = @(
    "com.facebook.appmanager",
    "com.facebook.system",
    "com.facebook.services"
)

$ALL_PACKAGES = $USER_APPS + $SYSTEM_META
$STORAGE = "/sdcard"   # en muchos dispositivos = /storage/emulated/0

function Invoke-Adb {
    param([string]$Cmd, [switch]$IgnoreError)
    $out = & $ADB shell $Cmd 2>&1
    if (-not $IgnoreError -and $LASTEXITCODE -ne 0) { Write-Warning "ADB: $Cmd -> exit $LASTEXITCODE" }
    $out
}

Write-Host "=== Comprobando dispositivo ===" -ForegroundColor Cyan
$dev = & $ADB devices -l
if (-not ($dev -match "device product:")) {
    Write-Error "No hay dispositivo Android conectado. Conecta el móvil y activa depuración USB."
    exit 1
}
Write-Host "Dispositivo detectado." -ForegroundColor Green

Write-Host "`n=== 1. Borrar datos y caché de todos los paquetes ===" -ForegroundColor Cyan
foreach ($pkg in $ALL_PACKAGES) {
    Write-Host "  Limpiando datos: $pkg"
    Invoke-Adb "pm clear $pkg"
}

Write-Host "`n=== 2. Desinstalar apps de usuario ===" -ForegroundColor Cyan
foreach ($pkg in $USER_APPS) {
    Write-Host "  Desinstalando: $pkg"
    Invoke-Adb "pm uninstall --user 0 $pkg"
}

Write-Host "`n=== 3. Deshabilitar componentes de sistema Meta (sin root) ===" -ForegroundColor Cyan
foreach ($pkg in $SYSTEM_META) {
    Write-Host "  Deshabilitando: $pkg"
    $r = Invoke-Adb "pm disable-user --user 0 $pkg" -IgnoreError
    if ($r -match "Cannot disable system packages") {
        Write-Host "    (Xiaomi no permite deshabilitar este paquete por ADB; sus datos ya se borraron. Puedes intentar en Ajustes > Apps.)" -ForegroundColor Yellow
    }
}

Write-Host "`n=== 4. Eliminar carpetas residuales en almacenamiento interno ===" -ForegroundColor Cyan
foreach ($pkg in $ALL_PACKAGES) {
    $dataPath = "$STORAGE/Android/data/$pkg"
    $obbPath  = "$STORAGE/Android/obb/$pkg"
    Write-Host "  Eliminando (si existen): $dataPath"
    Invoke-Adb "rm -rf $dataPath"
    Write-Host "  Eliminando (si existen): $obbPath"
    Invoke-Adb "rm -rf $obbPath"
}
# Algunos dispositivos usan /storage/emulated/0
Invoke-Adb "rm -rf /storage/emulated/0/Android/data/com.facebook.katana"
Invoke-Adb "rm -rf /storage/emulated/0/Android/data/com.instagram.android"
Invoke-Adb "rm -rf /storage/emulated/0/Android/data/com.facebook.stella"
Invoke-Adb "rm -rf /storage/emulated/0/Android/data/com.einnovation.temu"
Invoke-Adb "rm -rf /storage/emulated/0/Android/data/cn.wps.xiaomi.abroad.lite"
foreach ($p in $SYSTEM_META) {
    Invoke-Adb "rm -rf /storage/emulated/0/Android/data/$p"
    Invoke-Adb "rm -rf /storage/emulated/0/Android/obb/$p"
}

Write-Host "`n=== 5. Limpiar caché de la app 'Archivos' / Media Store (opcional) ===" -ForegroundColor Cyan
Invoke-Adb "pm trim-caches 999999999999"

Write-Host "`n=== Limpieza completada ===" -ForegroundColor Green
Write-Host "Hecho:"
Write-Host "  - Desinstaladas: Facebook, Instagram, Stella, Temu, WPS Office"
Write-Host "  - Sistema Meta: datos borrados (pm clear). Si Xiaomi lo permite, deshabilitados; si no, quedan en sistema pero sin datos."
Write-Host "  - Carpetas en Android/data y Android/obb eliminadas si existían."
Write-Host "Si no pudiste deshabilitar los paquetes Meta de sistema, en el móvil: Ajustes > Aplicaciones > Facebook App Manager / Facebook System > Deshabilitar (si la opción aparece)."
