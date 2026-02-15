#!/usr/bin/env bash
# DNS Cloudflare: pr4y.cl raíz → Vercel (A 76.76.21.21), api → Railway (CNAME).
# Requiere: CLOUDFLARE_API_TOKEN, ZONE_ID (zona pr4y.cl).
# Uso: export CLOUDFLARE_API_TOKEN="..."; export ZONE_ID="..."; ./scripts/cloudflare-dns-vercel.sh

set -e
BASE="https://api.cloudflare.com/client/v4"
ZONE_ID="${ZONE_ID:?Definir ZONE_ID (zona pr4y.cl en Cloudflare)}"
TOKEN="${CLOUDFLARE_API_TOKEN:?Definir CLOUDFLARE_API_TOKEN}"

echo "=== 1. Listar registros DNS (buscar CNAME raíz → Railway) ==="
LIST=$(curl -s -X GET "$BASE/zones/$ZONE_ID/dns_records" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json")
echo "$LIST" | jq '.result[] | {name, type, content}' 2>/dev/null || echo "$LIST"

# Obtener ID del CNAME de la raíz que apunta a Railway (nombre pr4y.cl o @)
ROOT_CNAME_ID=$(echo "$LIST" | jq -r '.result[] | select((.name == "pr4y.cl" or .name == "@") and .type == "CNAME" and (.content | test("railway"))) | .id' | head -1)
if [ -z "$ROOT_CNAME_ID" ] || [ "$ROOT_CNAME_ID" = "null" ]; then
  echo "No se encontró CNAME de raíz apuntando a Railway. ¿Ya lo eliminaste? Si quieres eliminar manualmente, usa el ID del listado anterior:"
  echo "  curl -s -X DELETE \"$BASE/zones/$ZONE_ID/dns_records/RECORD_ID\" -H \"Authorization: Bearer \$CLOUDFLARE_API_TOKEN\""
else
  echo "=== 2. Eliminar CNAME raíz (id: $ROOT_CNAME_ID) ==="
  curl -s -X DELETE "$BASE/zones/$ZONE_ID/dns_records/$ROOT_CNAME_ID" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" | jq '.success, .errors' 2>/dev/null || true
fi

echo "=== 3. Crear registro A raíz (@) → 76.76.21.21 (Vercel) ==="
curl -s -X POST "$BASE/zones/$ZONE_ID/dns_records" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  --data '{"type":"A","name":"@","content":"76.76.21.21","ttl":1,"proxied":false}' | jq '.success, .result.type, .result.name, .result.content, .errors' 2>/dev/null || true

echo "=== 4. Comprobar que api.pr4y.cl sigue en Railway ==="
curl -s -X GET "$BASE/zones/$ZONE_ID/dns_records?name=api.pr4y.cl" \
  -H "Authorization: Bearer $TOKEN" | jq '.result[] | {name, type, content}' 2>/dev/null || true
echo "Si no aparece api CNAME, créalo en el dashboard: api → CNAME → rrjx0w83.up.railway.app"
