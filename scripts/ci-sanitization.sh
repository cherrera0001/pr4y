#!/usr/bin/env bash
# CI: sanitización obligatoria. Impide merge si hay strings prohibidos o esquema de sync sin tipos.
# Ejecutar desde repo root (ej. en GitHub Actions).

set -e

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

echo "==> Sanitización: strings prohibidos en apps/..."

# Patrones alineados con apps/api/src/lib/sanitize.ts: no deben aparecer en código que procese input
# Buscamos uso peligroso en TS/JS/Kt (no en comentarios de documentación)
FORBIDDEN_PATTERNS=(
  "script:"
  "javascript:"
  "onclick\\s*="
  "onerror\\s*="
  "onload\\s*="
)

FOUND=0
for pat in "${FORBIDDEN_PATTERNS[@]}"; do
  if grep -rE "$pat" --include="*.ts" --include="*.tsx" --include="*.js" --include="*.jsx" --include="*.kt" apps/ 2>/dev/null | grep -v "sanitize\|stripHtml\|CONTROL_AND_DANGEROUS\|SafeText\|// \|typescript\|ignoreBuildErrors"; then
    echo "::error::Encontrado patrón prohibido: $pat"
    FOUND=1
  fi
done

if [ "$FOUND" -eq 1 ]; then
  echo "Elimina usos de script:, javascript:, on*= en código que procese input del usuario."
  exit 1
fi

echo "==> Sanitización: esquema de sync debe tener tipos explícitos..."

SYNC_FILE="apps/api/src/routes/sync.ts"
if [ ! -f "$SYNC_FILE" ]; then
  echo "::error::No existe $SYNC_FILE"
  exit 1
fi

# Requerimos que los esquemas de sync tengan type en las propiedades (pullRecordSchema, pushRecordSchema, etc.)
if ! grep -q "type: 'string'\|type: 'number'\|type: 'boolean'\|type: 'object'\|type: 'array'" "$SYNC_FILE"; then
  echo "::error::El esquema de sincronización en $SYNC_FILE debe definir tipos (type: 'string', etc.) en las propiedades."
  exit 1
fi

if ! grep -q "required:.*recordId\|required:.*encryptedPayloadB64" "$SYNC_FILE"; then
  echo "::error::El esquema de sync debe incluir required con recordId y encryptedPayloadB64."
  exit 1
fi

echo "==> Sanitización OK."
