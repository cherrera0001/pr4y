# Captura logcat para depurar el fallo de "Continuar con Google".
# Uso:
#   1. Conecta el móvil por USB y abre la app en la pantalla de login.
#   2. Ejecuta: .\capture-logcat-login.ps1
#   3. Cuando diga "Reproduce ahora...", pulsa "Continuar con Google" en el móvil.
#   4. Tras 20 segundos se guarda el log y se detiene.
#
# Salida: apps/mobile-android/logcat-login-YYYYMMDD-HHmmss.txt

$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) { $adb = "adb" }

$outFile = Join-Path $PSScriptRoot ("logcat-login-{0:yyyyMMdd-HHmmss}.txt" -f (Get-Date))

Write-Host "=== Logcat: login con Google ===" -ForegroundColor Cyan
& $adb devices
Write-Host ""
& $adb logcat -c 2>$null
Write-Host "Reproduce ahora: pulsa 'Continuar con Google' en el dispositivo. Capturando 20 s..." -ForegroundColor Yellow

$filter = "PR4Y_APP:V", "PR4Y_ERROR:V", "PR4Y_NETWORK:V", "PR4Y_CRYPTO:V", "PR4Y_DEBUG:V", "*:E"
$job = Start-Job -ScriptBlock {
    param($adbPath, $outPath, $tags)
    & $adbPath logcat -v time $tags 2>$null | Out-File -FilePath $outPath -Encoding utf8
} -ArgumentList $adb, $outFile, $filter
Start-Sleep -Seconds 20
Stop-Job $job -ErrorAction SilentlyContinue
Remove-Job $job -Force -ErrorAction SilentlyContinue

Write-Host "Log guardado: $outFile" -ForegroundColor Green
Write-Host "Busca PR4Y_ERROR, Exception, 'Google Auth' o 'GetCredentialException'." -ForegroundColor Gray
