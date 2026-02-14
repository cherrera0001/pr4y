#!/usr/bin/env bash
# Pre-flight check: evita subir código de prueba o retrasos artificiales a producción.
# Ejecutar antes de push (p. ej. bash scripts/pre-flight-check.sh).
# En Windows: Git Bash o WSL; o usar .gitattributes para que *.sh tenga LF.

set -e

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

echo "==> Pre-flight check (lint, typecheck, código de stress test)..."

echo "==> pnpm lint"
pnpm lint

echo "==> pnpm --filter @pr4y/api typecheck"
pnpm --filter @pr4y/api typecheck

echo "==> Buscando 'STRESS TEST' en código fuente..."
if grep -r "STRESS TEST" --include="*.kt" --include="*.ts" --include="*.tsx" --include="*.js" --include="*.jsx" apps/ 2>/dev/null | grep -v "\.md:" | grep -v "\.sh" | grep -v "\.ps1"; then
  echo "ERROR: Encontrado marcador STRESS TEST en código. Elimínalo antes de producción."
  exit 1
fi

echo "==> Buscando delay(2000) en código..."
if grep -rE "delay\s*\(\s*2000\s*\)" --include="*.kt" apps/ 2>/dev/null | grep -q .; then
  echo "ERROR: Encontrado delay(2000) en código. Elimínalo antes de producción."
  exit 1
fi

echo "==> Pre-flight OK. Listo para push."
