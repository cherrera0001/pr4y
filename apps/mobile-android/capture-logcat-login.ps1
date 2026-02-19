# Captura logcat para depurar login y reentrada en la app.
# Uso (login con Google):
#   1. Conecta el móvil por USB y abre la app en la pantalla de login.
#   2. Ejecuta: .\capture-logcat-login.ps1   (o .\capture-logcat-login.ps1 -Seconds 30 para 30 s)
#   3. Cuando diga "Reproduce ahora...", pulsa "Continuar con Google" en el móvil.
#   4. Tras N segundos se guarda el log y se detiene.
#
# Parámetro opcional: -Seconds <número>   Duración de la captura en segundos (por defecto: 20).
#
# Si el dispositivo sale "offline": reconecta USB, acepta depuración en el móvil, ejecuta "adb reconnect".
# Salida: apps/mobile-android/logcat-login-YYYYMMDD-HHmmss.txt

param(
    [int]$Seconds = 20
)

$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) { $adb = "adb" }

$outFile = Join-Path $PSScriptRoot ("logcat-login-{0:yyyyMMdd-HHmmss}.txt" -f (Get-Date))

# --- Estado de dispositivos ADB ---
$devicesRaw = & $adb devices 2>$null
$deviceLines = $devicesRaw | Where-Object { $_ -match "^\S+\s+(.+)$" }
$anyOnline = $false
$statusSummary = @()
foreach ($line in $deviceLines) {
    if ($line -match "^\s*$") { continue }
    if ($line -match "List of devices") { continue }
    $serial = ($line -split "\s+")[0]
    $state = ($line -split "\s+", 2)[1]
    $statusSummary += "$serial = $state"
    if ($state -eq "device") { $anyOnline = $true }
}

Write-Host "=== Logcat: login con Google ===" -ForegroundColor Cyan
$devicesRaw | ForEach-Object { Write-Host $_ }
Write-Host ""

if (-not $anyOnline) {
    Write-Host "ADVERTENCIA: Ningún dispositivo en estado 'device' (hay: $($statusSummary -join '; '))." -ForegroundColor Red
    Write-Host "La captura puede quedar vacía. Pasos recomendados:" -ForegroundColor Yellow
    Write-Host "  1. Reconecta el cable USB." -ForegroundColor Gray
    Write-Host "  2. En el móvil: acepta de nuevo 'Permitir depuración USB' si sale el diálogo." -ForegroundColor Gray
    Write-Host "  3. En esta terminal:  adb reconnect" -ForegroundColor Gray
    Write-Host "  4. Vuelve a ejecutar este script cuando 'adb devices' muestre 'device'." -ForegroundColor Gray
    Write-Host ""
}

# Limpiar logcat con timeout para no bloquear si el dispositivo está offline
$clearJob = Start-Job -ScriptBlock { param($a) & $a logcat -c 2>$null } -ArgumentList $adb
$null = Wait-Job $clearJob -Timeout 5
Stop-Job $clearJob -ErrorAction SilentlyContinue
Remove-Job $clearJob -Force -ErrorAction SilentlyContinue

Write-Host "Reproduce ahora (login o reabrir app y entrar). Capturando $Seconds s..." -ForegroundColor Yellow

$filter = "PR4Y_APP:V", "PR4Y_ERROR:V", "PR4Y_NETWORK:V", "PR4Y_CRYPTO:V", "PR4Y_DEBUG:V", "*:E"
$job = Start-Job -ScriptBlock {
    param($adbPath, $outPath, $tags)
    & $adbPath logcat -v time $tags 2>&1 | Out-File -FilePath $outPath -Encoding utf8
} -ArgumentList $adb, $outFile, $filter
Start-Sleep -Seconds $Seconds
Stop-Job $job -ErrorAction SilentlyContinue
Remove-Job $job -Force -ErrorAction SilentlyContinue

# --- Informe siempre útil: contenido real o diagnóstico claro ---
$content = $null
if (Test-Path $outFile) { $content = Get-Content -Path $outFile -Raw -ErrorAction SilentlyContinue }
$isEmpty = [string]::IsNullOrWhiteSpace($content)

if (-not (Test-Path $outFile) -or $isEmpty) {
    $header = @"
=== Logcat login - Sin datos (dispositivo no en línea) ===
Generado: $(Get-Date -Format "yyyy-MM-dd HH:mm:ss")
Estado ADB al iniciar: $($statusSummary -join '; ')

Para obtener un log real:
  1. Reconecta el USB y en el móvil acepta "Permitir depuración USB".
  2. En PC:  adb reconnect
  3. Comprueba:  adb devices   (debe aparecer "device", no "offline").
  4. Vuelve a ejecutar: .\capture-logcat-login.ps1 -Seconds 20

"@
    Set-Content -Path $outFile -Value $header -Encoding utf8 -NoNewline
}

Write-Host "Log guardado: $outFile" -ForegroundColor Green
if ($isEmpty -or -not $anyOnline) {
    Write-Host "El archivo no contiene logcat (dispositivo offline). Sigue los pasos indicados en el propio archivo." -ForegroundColor Yellow
} else {
    Write-Host "Busca PR4Y_ERROR, Exception, 'Google Auth' o 'GetCredentialException'." -ForegroundColor Gray
}
