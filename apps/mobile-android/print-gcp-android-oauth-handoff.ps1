# Genera el texto listo para pegar para quien tenga que añadir el cliente Android en GCP.
# Uso: .\print-gcp-android-oauth-handoff.ps1
# Requiere: Gradle (.\gradlew) en apps/mobile-android.
# Salida: SHA-1 por variante, enlace a GCP y texto para copiar/pegar.

$ErrorActionPreference = "SilentlyContinue"
$scriptDir = $PSScriptRoot
Set-Location $scriptDir

$out = & .\gradlew :app:signingReport --no-daemon -q 2>$null
$lines = $out -split "`n"
$variant = ""
$sha1 = ""
$results = @()

for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -match "Variant:\s+(.+)") { $variant = $Matches[1].Trim() }
    if ($lines[$i] -match "SHA1:\s+(.+)") {
        $sha1 = $Matches[1].Trim()
        if ($variant -and $sha1) {
            $results += [PSCustomObject]@{ Variant = $variant; SHA1 = $sha1 }
        }
        $sha1 = ""
    }
}

# Package de la app (prod)
$packageName = "com.pr4y.app"
$gcpUrl = "https://console.cloud.google.com/apis/credentials"
$projectHint = "Proyecto donde está el Web Client ID (ej. 583962207001-...apps.googleusercontent.com)"

Write-Host ""
Write-Host "=== Handoff: cliente Android en GCP (login con Google) ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "1. Abre: $gcpUrl" -ForegroundColor Yellow
Write-Host "   Selecciona el proyecto: $projectHint" -ForegroundColor Gray
Write-Host ""
Write-Host "2. Crear credenciales -> ID de cliente de OAuth -> Tipo: Android" -ForegroundColor Yellow
Write-Host ""
Write-Host "3. Rellena:" -ForegroundColor Yellow
Write-Host "   Nombre del paquete: $packageName" -ForegroundColor White
if ($results.Count -gt 0) {
    Write-Host "   Huella SHA-1 (usa la del build que tienes instalado):" -ForegroundColor Gray
    foreach ($r in $results) {
        Write-Host "     [$($r.Variant)] $($r.SHA1)" -ForegroundColor White
    }
} else {
    Write-Host "   Huella SHA-1: (ejecuta .\gradlew :app:signingReport y copia la línea SHA1 del variant que usas)" -ForegroundColor Gray
}
Write-Host ""
Write-Host "4. Guardar. Esperar unos minutos y probar de nuevo en el móvil." -ForegroundColor Yellow
Write-Host ""
Write-Host "--- Texto para copiar/pegar (envío a quien tenga GCP) ---" -ForegroundColor Cyan
Write-Host ""
Write-Host "Cliente OAuth 2.0 tipo Android en el proyecto de pr4y:"
Write-Host "  - Nombre del paquete: $packageName"
if ($results.Count -gt 0) {
    foreach ($r in $results) { Write-Host "  - SHA-1 ($($r.Variant)): $($r.SHA1)" }
} else {
    Write-Host "  - SHA-1: (obtener con .\gradlew :app:signingReport)"
}
Write-Host "  - Enlace: $gcpUrl"
Write-Host ""
Write-Host "--- Fin handoff ---" -ForegroundColor Cyan
