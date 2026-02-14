# Stress Test: Sincronización y Conflictos de Versión

Objetivo: confirmar que la lógica **Pull-before-Push** y el manejo automático de conflictos (`serverVersion + 1`) son robustos cuando hay múltiples cambios en poco tiempo.

## Rol: PR4Y QA & Reliability Engineer

Actúa para “romper” la lógica de sincronización y asegurar que sea ultra-robusta.

---

## 1. Modificación temporal en el cliente (Android)

En `SyncRepository.kt` se añade un retraso artificial **justo antes** del push:

```kotlin
// STRESS TEST: retraso artificial para provocar colisión de versiones (quitar antes de producción)
delay(2000)
```

- **Ubicación**: antes del bucle `while (outbox.isNotEmpty() && pushRound < maxPushRounds)`.
- **Propósito**: dar ventana de 2 segundos para que otro “dispositivo” (script curl) suba una versión más nueva y así forzar un conflicto cuando este cliente haga push.
- **Importante**: quitar este `delay(2000)` antes de producción.

---

## 2. Generación de conflictos

### Opción A: Script PowerShell (Windows)

```powershell
cd scripts
.\stress-test-sync-conflict.ps1 -BaseUrl "http://localhost:4000" -Email "tu@email.com" -Password "tupassword"
```

O con API en Railway:

```powershell
.\stress-test-sync-conflict.ps1 -BaseUrl "https://tu-app.railway.app" -Email "..." -Password "..." -DelaySeconds 1
```

### Opción B: Script Bash (Linux/macOS)

```bash
chmod +x scripts/stress-test-sync-conflict.sh
./scripts/stress-test-sync-conflict.sh http://localhost:4000 user@example.com password 1
```

### Flujo manual equivalente (curl)

1. **Login**
   ```bash
   export TOKEN=$(curl -s -X POST "$BASE/v1/auth/login" -H "Content-Type: application/json" \
     -d '{"email":"...","password":"..."}' | jq -r '.accessToken')
   ```
2. **Pull**
   ```bash
   curl -s -H "Authorization: Bearer $TOKEN" "$BASE/v1/sync/pull?limit=10"
   ```
3. **Push el primer registro con `version` incrementado** (mismo `recordId`, `encryptedPayloadB64`, `clientUpdatedAt`; solo cambiar `version` a `version_actual + 1`).

Así el servidor queda con una versión más nueva; cuando el dispositivo haga push con la versión antigua, el servidor responderá **200** con ese registro en `rejected` y `serverVersion` (no 409; el contrato actual es 200 + body).

### Comando curl específico para forzar conflicto (script listo)

Desde `scripts/` (o con `TOKEN` ya exportado):

```bash
# 1. Obtén token (o usa el de tu app / login)
TOKEN=$(curl -s -X POST http://localhost:4000/v1/auth/login -H "Content-Type: application/json" \
  -d '{"email":"tu@email.com","password":"tupassword"}' | jq -r '.accessToken')

# 2. Opción A: script que hace pull y luego push con version+1 (recomendado)
./scripts/stress-test-sync-conflict.sh http://localhost:4000 tu@email.com tupassword 1

# Opción B: push directo con un recordId concreto (debe existir en el servidor)
export TOKEN="TU_JWT_AQUÍ"
./scripts/stress-test-push-conflict-curl.sh http://localhost:4000 "<recordId del pull>"
```

Ejemplo de body de push para simular “otro dispositivo” (mismo `recordId`, versión mayor):

```bash
curl -X POST http://localhost:4000/v1/sync/push \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "records": [{
      "recordId": "record_test_123",
      "type": "prayer_request",
      "version": 5,
      "encryptedPayloadB64": "SGVsbG8gd29ybGQ=",
      "clientUpdatedAt": "'$(date -u +%Y-%m-%dT%H:%M:%SZ)'",
      "serverUpdatedAt": "'$(date -u +%Y-%m-%dT%H:%M:%SZ)'",
      "deleted": false
    }]
  }'
```

Si el servidor ya tiene ese `recordId` con `version >= 5`, la respuesta será **200** con `rejected: [{ "recordId": "...", "reason": "version conflict", "serverVersion": N }]`. El cliente Android debe leer `serverVersion`, actualizar a `serverVersion + 1` y reintentar.

---

## 3. Escenario de prueba

1. **Dispositivo A (Android)**  
   - Usuario logueado, al menos una entrada (diario o pedido) ya sincronizada.  
   - Editar esa entrada (para que entre en outbox con la versión actual).

2. **Dispositivo B (script)**  
   - Mismo usuario.  
   - Ejecutar el script que hace pull y luego push del mismo registro con `version + 1`.  
   - Hacerlo **dentro de la ventana de 2 s** después de que en el dispositivo se haya iniciado sync (tras el pull y antes de que el cliente envíe el push).

3. **Dispositivo A**  
   - Tras el `delay(2000)`, hace push.  
   - El servidor ya tiene una versión mayor → rechaza el registro y devuelve `rejected` con `reason: "version conflict"` y `serverVersion`.

4. **Validación**  
   - El cliente actualiza el outbox a `serverVersion + 1` y reintenta (hasta `maxPushRounds`).  
   - Tras la resolución, `last_sync_status` debe quedar en **SUCCESS** (ok) y la UI mostrar “Protegido y Sincronizado” (o equivalente).

---

## 4. Validación de resultados

| Comprobación | Cómo validar |
|--------------|---------------|
| Servidor devuelve conflicto de versión | Respuesta **200** de `POST /v1/sync/push` con `rejected[].reason === "version conflict"` y `rejected[].serverVersion` presente. (El API actual no usa 409; los conflictos van en el body 200.) |
| Cliente recibe `serverVersion` y actualiza | En `SyncRepository` se actualiza outbox con `version = serverVersion + 1` y se vuelve a intentar en la misma ejecución. |
| Resolución sin intervención del usuario | Tras uno o dos intentos, el push es aceptado y el outbox se vacía para ese registro. |
| `last_sync_status` en SUCCESS | Tras el sync completo, `getLastSyncStatus()` devuelve `lastOk == true` y la UI refleja estado correcto. |

### Validación en logs de Android Studio

1. **Filtro Logcat**: tag o texto `sync`, `SyncRepository`, `push`, `rejected`, `version conflict`.
2. **Qué observar**:
   - Tras el push, si hay conflicto, la respuesta del servidor incluye `rejected` con `reason: "version conflict"` y `serverVersion`.
   - En `SyncRepository.kt`, el bucle que procesa `pushBody.rejected` debe actualizar el outbox con `version = r.serverVersion + 1` e insertar de nuevo en `outboxDao`.
   - En la siguiente iteración de `while (outbox.isNotEmpty() && pushRound < maxPushRounds)` el cliente envía el mismo registro con la nueva versión; el servidor lo acepta y devuelve el `recordId` en `accepted`.
   - Tras eso, `outboxDao.deleteByRecordId(recordId)` limpia el outbox y `persistLastSyncStatus("ok", null)` deja el estado en SUCCESS.
3. **Resultado esperado**: Sin errores visibles para el usuario; indicador "Protegido y Sincronizado" (o equivalente) al finalizar el sync.

---

## 5. Resumen de robustez

| Escenario | Respuesta del sistema | Impacto en el usuario |
|-----------|------------------------|------------------------|
| Conflicto de versión | Cliente detecta rechazo, actualiza a `serverVersion + 1` y reintenta. | Invisible: los datos se guardan sin errores. |
| Sin conexión | SyncWorker detecta falta de red y espera a que el sistema notifique conectividad. | Indicador tipo “Sincronización pausada”. |
| Escritura sin llave | JournalDraftStore (SharedPreferences). | No se pierde el borrador. |

---

## 6. Cierre del stress test

- Verificar que todas las comprobaciones de la sección 4 pasen.  
- **Quitar `delay(2000)`** de `SyncRepository.kt` antes de producción.  
- Opcional: revisar README.md y DEPLOY.md para dejar el proyecto listo para Railway y versión 1.0.
