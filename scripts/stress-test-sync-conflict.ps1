# PR4Y - Stress Test: Generar conflicto de versión para validar Pull-before-Push y resolución automática
# Uso: .\stress-test-sync-conflict.ps1 -BaseUrl "http://localhost:4000" -Email "tu@email.com" -Password "tupassword"
# Opcional: -DelaySeconds 1  (esperar N segundos tras login antes de pull+push, para coordinar con el sync del dispositivo)

param(
    [Parameter(Mandatory=$true)]
    [string]$BaseUrl,
    [Parameter(Mandatory=$true)]
    [string]$Email,
    [Parameter(Mandatory=$true)]
    [string]$Password,
    [int]$DelaySeconds = 0
)

$ErrorActionPreference = "Stop"
$base = $BaseUrl.TrimEnd('/')

# 1) Login
Write-Host "[1/4] Login..." -ForegroundColor Cyan
$loginBody = @{ email = $Email; password = $Password } | ConvertTo-Json
$loginResp = Invoke-RestMethod -Uri "$base/v1/auth/login" -Method Post -Body $loginBody -ContentType "application/json"
$token = $loginResp.accessToken
Write-Host "    OK. Token obtenido." -ForegroundColor Green

if ($DelaySeconds -gt 0) {
    Write-Host "[*] Esperando $DelaySeconds s para coordinar con el sync del dispositivo..." -ForegroundColor Yellow
    Start-Sleep -Seconds $DelaySeconds
}

# 2) Pull para obtener registros
Write-Host "[2/4] Pull..." -ForegroundColor Cyan
$headers = @{ Authorization = "Bearer $token" }
$pullResp = Invoke-RestMethod -Uri "$base/v1/sync/pull?limit=10" -Method Get -Headers $headers
$records = $pullResp.records
if (-not $records -or $records.Count -eq 0) {
    Write-Host "    No hay registros. Crea al menos una entrada (diario o pedido) en la app y sincroniza una vez, luego vuelve a ejecutar este script." -ForegroundColor Red
    exit 1
}
$rec = $records[0]
Write-Host "    OK. Primer registro: $($rec.recordId) tipo=$($rec.type) version=$($rec.version)" -ForegroundColor Green

# 3) Push el mismo registro con version+1 para provocar conflicto en el cliente
$newVersion = [int]$rec.version + 1
Write-Host "[3/4] Push mismo registro con version=$newVersion (simula Device B)..." -ForegroundColor Cyan
$pushRecords = @(
    @{
        recordId = $rec.recordId
        type = $rec.type
        version = $newVersion
        encryptedPayloadB64 = $rec.encryptedPayloadB64
        clientUpdatedAt = $rec.clientUpdatedAt
        deleted = $false
    }
)
$pushBody = @{ records = $pushRecords } | ConvertTo-Json -Depth 10
$pushResp = Invoke-RestMethod -Uri "$base/v1/sync/push" -Method Post -Headers $headers -Body $pushBody -ContentType "application/json"

if ($pushResp.accepted -contains $rec.recordId) {
    Write-Host "    OK. Servidor tiene ahora version=$newVersion para $($rec.recordId)" -ForegroundColor Green
} else {
    Write-Host "    Rechazado: $($pushResp.rejected | ConvertTo-Json -Compress)" -ForegroundColor Yellow
}

# 4) Resumen
Write-Host "[4/4] Resumen" -ForegroundColor Cyan
Write-Host "    Cuando el dispositivo haga push con version anterior, recibira rejected con serverVersion=$newVersion."
Write-Host "    El cliente debe actualizar a serverVersion+1 y reintentar; last_sync_status debe quedar SUCCESS."
Write-Host ""
Write-Host "Pasos en el dispositivo Android:" -ForegroundColor White
Write-Host "  1. Tener este mismo usuario logueado y al menos una entrada sincronizada."
Write-Host "  2. Editar esa entrada (o otra que corresponda al recordId anterior)."
Write-Host "  3. Iniciar sync() en la app (o esperar SyncWorker)."
Write-Host "  4. Con el delay(2000) en SyncRepository, el pull ya habra pasado; ejecuta este script justo despues de tocar Sincronizar (en la ventana de 2s)."
Write-Host "  5. Verificar en UI que last_sync_status termina en ok (Protegido y Sincronizado)."
Write-Host ""
Write-Host "Stress test listo. Recuerda quitar delay(2000) de SyncRepository.kt antes de produccion." -ForegroundColor Yellow
