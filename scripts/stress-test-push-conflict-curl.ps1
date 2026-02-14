# PR4Y - Stress Test: curl directo para forzar conflicto de versión (200 + rejected con serverVersion)
# La API devuelve 200 con body { accepted: [], rejected: [{ reason: "version conflict", serverVersion }] }; no 409.
# Uso: $env:TOKEN = "tu_jwt"; .\stress-test-push-conflict-curl.ps1 [-BaseUrl "http://localhost:4000"] [-RecordId "record_test_123"]

param(
    [string]$BaseUrl = "http://localhost:4000",
    [string]$RecordId = "record_test_123"
)

$base = $BaseUrl.TrimEnd('/')
$now = [DateTime]::UtcNow.ToString("yyyy-MM-ddTHH:mm:ssZ")

if (-not $env:TOKEN) {
    Write-Host "Obtén un JWT con:"
    Write-Host "  Invoke-RestMethod -Uri $base/v1/auth/login -Method Post -Body '{\"email\":\"...\",\"password\":\"...\"}' -ContentType 'application/json'"
    Write-Host "Luego: `$env:TOKEN = '<token>'; .\stress-test-push-conflict-curl.ps1 -BaseUrl $base -RecordId <recordId>"
    exit 1
}

$body = @{
    records = @(
        @{
            recordId = $RecordId
            type = "prayer_request"
            version = 5
            encryptedPayloadB64 = "SGVsbG8gd29ybGQ="
            clientUpdatedAt = $now
            serverUpdatedAt = $now  # requerido por el schema de la API
            deleted = $false
        }
    )
} | ConvertTo-Json -Depth 5

Write-Host "Push con version=5 para recordId=$RecordId (si el servidor ya tiene version>=5, responderá rejected con serverVersion)."
$headers = @{ Authorization = "Bearer $env:TOKEN" }
$resp = Invoke-RestMethod -Uri "$base/v1/sync/push" -Method Post -Headers $headers -Body $body -ContentType "application/json"
$resp | ConvertTo-Json -Depth 5

Write-Host ""
Write-Host "Si ves rejected[].reason == 'version conflict' y rejected[].serverVersion, el cliente Android debe:"
Write-Host "  1. Leer serverVersion del rechazo"
Write-Host "  2. Actualizar outbox a version = serverVersion + 1"
Write-Host "  3. Reintentar el push en la misma ejecución (maxPushRounds)."
