# Redirige a la captura de logcat para login (script en apps/mobile-android)
$scriptDir = Join-Path $PSScriptRoot "apps\mobile-android"
Set-Location $scriptDir
& (Join-Path $scriptDir "capture-logcat-login.ps1")
