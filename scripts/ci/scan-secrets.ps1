# PR4Y - Escaneo local de secretos antes de push (gitleaks vía Docker)
# Uso: desde la raíz del repo: .\scripts\ci\scan-secrets.ps1
# Requiere: Docker. Alternativa sin Docker: instala gitleaks y ejecuta gitleaks detect --source . --no-git

$ErrorActionPreference = "Stop"
$repoRoot = (Get-Item $PSScriptRoot).Parent.Parent.FullName
Set-Location $repoRoot

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Host "Docker no encontrado. Instala Docker o ejecuta gitleaks directamente: gitleaks detect --source . --no-git"
    exit 1
}

Write-Host "[scan-secrets] Ejecutando gitleaks (Docker)..."
docker run --rm -v "${repoRoot}:/path:ro" zricethezav/gitleaks:latest detect --source /path --no-git --verbose

Write-Host "[scan-secrets] OK - No se detectaron secretos expuestos."
exit 0
