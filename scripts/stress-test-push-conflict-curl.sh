#!/usr/bin/env bash
# PR4Y - Stress Test: curl directo para forzar conflicto de versión (200 + rejected con serverVersion)
# La API devuelve 200 con body { accepted: [], rejected: [{ reason: "version conflict", serverVersion }] }; no 409.
# Uso: export TOKEN="tu_jwt"; ./stress-test-push-conflict-curl.sh [BASE_URL] [RECORD_ID]
# Para obtener RECORD_ID: haz pull primero o usa scripts/stress-test-sync-conflict.sh que hace login+pull+push.

set -e
BASE="${1:-http://localhost:4000}"
RECORD_ID="${2:-record_test_123}"
NOW=$(date -u +%Y-%m-%dT%H:%M:%SZ)

if [[ -z "$TOKEN" ]]; then
  echo "Obtén un JWT con: curl -s -X POST $BASE/v1/auth/login -H 'Content-Type: application/json' -d '{\"email\":\"...\",\"password\":\"...\"}' | jq -r '.accessToken'"
  echo "Luego: export TOKEN=\"<token>\"; $0 $BASE <recordId>"
  exit 1
fi

echo "Push con version=5 para recordId=$RECORD_ID (si el servidor ya tiene version>=5, responderá rejected con serverVersion)."
curl -s -X POST "${BASE}/v1/sync/push" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"records\": [{
      \"recordId\": \"$RECORD_ID\",
      \"type\": \"prayer_request\",
      \"version\": 5,
      \"encryptedPayloadB64\": \"SGVsbG8gd29ybGQ=\",
      \"clientUpdatedAt\": \"$NOW\",
      \"serverUpdatedAt\": \"$NOW\",
      \"deleted\": false
    }]
  }" | jq .

echo ""
echo "Si ves rejected[].reason == \"version conflict\" y rejected[].serverVersion, el cliente Android debe:"
echo "  1. Leer serverVersion del rechazo"
echo "  2. Actualizar outbox a version = serverVersion + 1"
echo "  3. Reintentar el push en la misma ejecución (maxPushRounds)."
