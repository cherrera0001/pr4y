#!/usr/bin/env bash
# PR4Y - Escaneo local de secretos antes de push (gitleaks vía Docker)
# Uso: desde la raíz del repo: ./scripts/ci/scan-secrets.sh
# Requiere: Docker. Alternativa sin Docker: instala gitleaks y ejecuta gitleaks detect --source . --no-git

set -e
REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$REPO_ROOT"

if ! command -v docker &>/dev/null; then
  echo "Docker no encontrado. Instala Docker o ejecuta gitleaks directamente: gitleaks detect --source . --no-git"
  exit 1
fi

echo "[scan-secrets] Ejecutando gitleaks (Docker)..."
docker run --rm -v "$REPO_ROOT:/path:ro" zricethezav/gitleaks:latest detect --source /path --no-git --verbose

echo "[scan-secrets] OK - No se detectaron secretos expuestos."
exit 0
