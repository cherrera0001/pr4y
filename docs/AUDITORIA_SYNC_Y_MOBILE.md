# Auditoría: Sync (Backend) y PR4Y Mobile (Android)

## Fase 1 — Database Architect: schema.prisma y endpoint de sync

### 1. Resolución de conflictos (Last-Write-Wins y relojes)

- **Comportamiento actual:** El servidor **no** usa estrictamente "Last-Write-Wins por `updated_at`". Usa **versionado**: en `push` se rechaza un registro si `rec.version <= existing.version` (ver `apps/api/src/services/sync.ts`). El campo `clientUpdatedAt` se guarda pero **no** se usa para decidir quién gana.
- **Riesgo con relojes desincronizados:**
  - **Pérdida de datos:** Si dos dispositivos editan el mismo registro y cada uno sube con `version` mayor que el del otro, el que llega después puede ser rechazado por "version conflict". El orden de llegada al servidor y el valor de `version` determinan el resultado.
  - Si los clientes no obtienen la `version` más reciente antes de editar (p. ej. no hacen pull antes de push), un dispositivo puede sobrescribir con una versión antigua que tenga un número de versión menor y ser rechazado, percibiendo "pérdida" de sus cambios.
- **Recomendación:** Mantener política por `version` y documentar que el cliente debe hacer pull (o al menos conocer la `version` actual del registro) antes de editar, o implementar merge/CRDT si se necesita colaboración real. Opcional: usar `clientUpdatedAt` como desempate cuando `version` sea igual (no implementado hoy).

### 2. Índices de sync (tabla Record, equivalente a “Prayers”)

El modelo se llama **Record** (no Prayers); es la tabla genérica de sync.

- **Índices actuales en `schema.prisma`:**
  - `@@index([userId])`
  - `@@index([userId, clientUpdatedAt])`
- **Uso en pull:** `findMany({ where: { userId }, orderBy: { clientUpdatedAt: 'asc' }, cursor, take })`. El índice compuesto `[userId, clientUpdatedAt]` es el adecuado para esta query; el pull está bien indexado y no será lento con miles de registros por usuario.

### 3. Privacidad de metadatos

- **Record:**  
  - `type`: String en texto plano (p. ej. `"prayer_request"`, `"journal_entry"`). Es metadato de tipo de registro, no contenido sensible.  
  - `encryptedPayloadB64`: payload cifrado; correcto.  
- No hay columnas de título o etiquetas en texto plano en Record; todo el contenido sensible va dentro del payload cifrado.  
- **Recomendación:** Si en el futuro se exige ocultar también el tipo de registro al servidor, `type` podría ser cifrado (Bytes/String cifrado); por ahora es aceptable dejarlo en claro para filtrado y operación del sync.

---

## Fase 2 — PR4Y Mobile Core Engineer: Android

### 1. E2EE local (búnker local)

- **Estado actual:**
  - **Pedidos (Request):** Se guardan en Room en **texto plano** (`RequestEntity`: `title`, `body`). Al enviar al servidor, el payload se cifra y se pone en outbox como `encryptedPayloadB64` (correcto para sync). El almacenamiento local de pedidos **no** es E2EE.
  - **Diario (Journal):** En `NewJournalScreen` se inserta `JournalEntity` con `content` en **texto plano** y **no** se cifra antes de `journalDao.insert()`. No se escribe nada en outbox para journal, por lo que las entradas nuevas **no se suben** al sync.
- **Conclusión:** No se cumple “las oraciones se cifren antes de insertarse en la base de datos Room” ni “no queremos texto plano en el almacenamiento local”. Hay riesgo de exposición en el dispositivo.

### 2. Keystore (Master Key / DEK)

- **Estado actual:** `DekManager` mantiene la DEK solo **en memoria** (`private var dek: SecretKey?`). La DEK se obtiene del servidor (unwrap con KEK derivada de passphrase) y no se persiste en Android Keystore.
- **Riesgo:** La llave no está anclada al hardware; si alguien obtiene acceso a la memoria del proceso podría extraerla. No se cumple el objetivo de “llave nunca extraíble del hardware” con Keystore.

### 3. Sincronización invisible (SyncWorker)

- **Estado actual:** No existe un **SyncWorker** con WorkManager. El sync se dispara solo de forma manual desde HomeScreen (“Sincronizar”) y SettingsScreen (“Sincronizar ahora”).
- **Recomendación:** Implementar un `SyncWorker` con WorkManager y restricciones: `setRequiredNetworkType(CONNECTED)`, y opcionalmente `setRequiresBatteryNotLow(true)` o similares, para que el sync sea eficiente y solo se ejecute con red (y batería suficiente si se desea).

### 4. Resolución de conflictos (LWW en cliente)

- El servidor espera `clientUpdatedAt` en el push; el cliente ya lo envía en `OutboxEntity` y en `PushRecordDto`. La resolución real en servidor es por `version`, no por timestamp; el cliente está alineado con lo que el backend espera.

### 5. UI y “paz mental” (Compose)

- **Hilos:** En `NewJournalScreen` se usa `withContext(Dispatchers.IO)` para el insert (correcto). En `NewEditScreen` el cifrado y el insert se hacen en `Dispatchers.IO`. Para operaciones criptográficas pesadas, se puede usar `Dispatchers.Default` y mantener `Dispatchers.IO` para DB.
- **Observabilidad:** `JournalDao.getAll()` devuelve `Flow<List<JournalEntity>>` y `JournalScreen` usa `collectAsState`; cuando el sync (manual o futuro SyncWorker) inserte nuevas entradas en Room, el Flow emitirá y la UI se actualizará. La observabilidad con Flow está bien utilizada.

### 6. Outbox y formato para el servidor

- **Pedidos:** Se construye el JSON, se cifra con `LocalCrypto.encrypt(payload, dek)` y se guarda en `OutboxEntity.encryptedPayloadB64`; el push envía ese mismo valor. Correcto.
- **Diario:** No se añade ninguna fila al outbox al crear/editar una entrada de diario, por lo que las entradas de journal **nunca se envían** al servidor.

---

## Resumen de acciones recomendadas (y estado)

| Área | Acción | Estado |
|------|--------|--------|
| Backend | Documentar política de conflictos (version) y metadato `type` en schema. | Hecho: comentarios en `schema.prisma`. |
| Android E2EE local Journal | Cifrar antes de insertar en Room; guardar solo cifrado; outbox para sync. | Hecho: `JournalEntity.encryptedPayloadB64`, cifrado en `NewJournalScreen`, outbox al crear; pull guarda cifrado en Room; descifrado en `JournalScreen` con `Dispatchers.Default`. |
| Android Keystore | Valorar guardar/recuperar DEK (o KEK) con Android Keystore. | Pendiente: DEK sigue solo en memoria. |
| Android Sync | SyncWorker con WorkManager y restricciones de red/batería. | Hecho: `SyncWorker`, `SyncScheduler` con `NetworkType.CONNECTED` y `requiresBatteryNotLow`; sync periódico al abrir app (si logueado); "Sincronizar ahora" encola one-time. |
| Android Journal sync | Outbox con payload cifrado y `clientUpdatedAt`/version al crear journal. | Hecho: ver E2EE local. |
| UI (Dispatchers) | Cifrado en `Dispatchers.Default`, DB en `Dispatchers.IO`. | Hecho en `NewJournalScreen` y descifrado en `JournalScreen`. |
