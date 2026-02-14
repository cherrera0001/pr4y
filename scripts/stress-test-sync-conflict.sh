#!/usr/bin/env bash
# PR4Y - Stress Test: Generar conflicto de versión para validar Pull-before-Push y resolución automática
# Uso: ./stress-test-sync-conflict.sh BASE_URL EMAIL PASSWORD [DELAY_SECONDS]
# Ejemplo: ./stress-test-sync-conflict.sh http://localhost:4000 user@example.com secret 1

set -e
BASE_URL="${1:-}"
EMAIL="${2:-}"
PASSWORD="${3:-}"
DELAY_SECONDS="${4:-0}"

if [[ -z "$BASE_URL" || -z "$EMAIL" || -z "$PASSWORD" ]]; then
  echo "Uso: $0 BASE_URL EMAIL PASSWORD [DELAY_SECONDS]"
  exit 1
fi

BASE="${BASE_URL%/}"

# 1) Login
echo "[1/4] Login..."
LOGIN_JSON=$(cat <<EOF
{"email":"$EMAIL","password":"$PASSWORD"}
EOF
)
LOGIN_RESP=$(curl -s -X POST "$BASE/v1/auth/login" -H "Content-Type: application/json" -d "$LOGIN_JSON")
TOKEN=$(echo "$LOGIN_RESP" | jq -r '.accessToken')
if [[ "$TOKEN" == "null" || -z "$TOKEN" ]]; then
  echo "    Error: no se obtuvo accessToken. Respuesta: $LOGIN_RESP"
  exit 1
fi
echo "    OK. Token obtenido."

if [[ -n "$DELAY_SECONDS" && "$DELAY_SECONDS" -gt 0 ]]; then
  echo "[*] Esperando ${DELAY_SECONDS}s para coordinar con el sync del dispositivo..."
  sleep "$DELAY_SECONDS"
fi

# 2) Pull
echo "[2/4] Pull..."
PULL_RESP=$(curl -s -H "Authorization: Bearer $TOKEN" "$BASE/v1/sync/pull?limit=10")
RECORDS=$(echo "$PULL_RESP" | jq -c '.records')
COUNT=$(echo "$RECORDS" | jq 'length')
if [[ "$COUNT" -eq 0 ]]; then
  echo "    No hay registros. Crea al menos una entrada en la app, sincroniza una vez y vuelve a ejecutar."
  exit 1
fi
REC=$(echo "$RECORDS" | jq -c '.[0]')
RECORD_ID=$(echo "$REC" | jq -r '.recordId')
TYPE=$(echo "$REC" | jq -r '.type')
VERSION=$(echo "$REC" | jq -r '.version')
echo "    OK. Primer registro: $RECORD_ID tipo=$TYPE version=$VERSION"

# 3) Push mismo registro con version+1
NEW_VERSION=$((VERSION + 1))
echo "[3/4] Push mismo registro con version=$NEW_VERSION (simula Device B)..."
PAYLOAD_B64=$(echo "$REC" | jq -r '.encryptedPayloadB64')
CLIENT_UPDATED=$(echo "$REC" | jq -r '.clientUpdatedAt')
PUSH_JSON=$(jq -n \
  --arg id "$RECORD_ID" \
  --arg t "$TYPE" \
  --argjson v $NEW_VERSION \
  --arg p "$PAYLOAD_B64" \
  --arg c "$CLIENT_UPDATED" \
  '{ records: [ { recordId: $id, type: $t, version: $v, encryptedPayloadB64: $p, clientUpdatedAt: $c, deleted: false } ] }')
PUSH_RESP=$(curl -s -X POST "$BASE/v1/sync/push" -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d "$PUSH_JSON")
ACCEPTED=$(echo "$PUSH_RESP" | jq -r '.accepted[]' 2>/dev/null || true)
if [[ "$ACCEPTED" == "$RECORD_ID" ]]; then
  echo "    OK. Servidor tiene ahora version=$NEW_VERSION para $RECORD_ID"
else
  echo "    Rechazado: $(echo "$PUSH_RESP" | jq -c '.rejected')"
fi

# 4) Resumen
echo "[4/4] Resumen"
echo "    Cuando el dispositivo haga push con version anterior, recibirá rejected con serverVersion=$NEW_VERSION."
echo "    El cliente debe actualizar a serverVersion+1 y reintentar; last_sync_status debe quedar SUCCESS."
echo ""
echo "Pasos en el dispositivo Android:"
echo "  1. Mismo usuario logueado y al menos una entrada sincronizada."
echo "  2. Editar esa entrada."
echo "  3. Iniciar sync() (o esperar SyncWorker). Con delay(2000), ejecuta este script justo después de tocar Sincronizar."
echo "  4. Verificar en UI que last_sync_status termina en ok."
echo ""
echo "Recuerda quitar delay(2000) de SyncRepository.kt antes de producción."
