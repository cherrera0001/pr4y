#!/usr/bin/env bash
# PR4Y - Escaneo de vulnerabilidades en dependencias e imagen (Trivy vía Docker)
# Uso: ./scripts/ci/scan-trivy.sh [ruta]
# Sin argumentos: escanea el directorio actual (repo). Con argumentos: escanea esa ruta (ej. apps/api).

set -e
SCAN_PATH="${1:-.}"
REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$REPO_ROOT"

if ! command -v docker &>/dev/null; then
  echo "Docker no encontrado. Instala Docker para usar Trivy."
  exit 1
fi

echo "[trivy] Escaneando $SCAN_PATH (fs + dependencias)..."
docker run --rm -v "$REPO_ROOT:/app:ro" aquasec/trivy:latest fs --severity HIGH,CRITICAL /app/$SCAN_PATH

echo "[trivy] OK."
exit 0
