# Reporte de Gaps Técnicos – Supervisión Backend-First (Pr4y)

**Rol:** Principal Software Engineer / Supervisor de Calidad Técnica  
**Fecha:** 2026-02-28  
**Alcance:** Coherencia con contrato API, identidad de marca, Hilt, telemetría, Pr4y Roulette (Privacy Stripping), Pr4y Ledger (seguridad local y aislamiento).

---

## 1. Resumen ejecutivo

Se detectaron **gaps de coherencia** entre el contrato `docs/api-openapi.yaml` y la implementación real (API y clientes), **una fuga de identidad (UID) en Pr4y Roulette**, **migración a Hilt incompleta**, **ausencia de TelemetryInterceptor en backend** y **desalineación del estado autoritativo (status)** entre API y cliente Android. A continuación se detallan los hallazgos y la lista de archivos que requieren refactorización inmediata para cumplir el estándar Backend-First.

---

## 2. Auditoría de coherencia (contrato vs código)

### 2.1 Contrato OpenAPI desactualizado

| Aspecto | Contrato `docs/api-openapi.yaml` | Realidad en API / cliente |
|--------|----------------------------------|----------------------------|
| **EncryptedRecord** | No incluye `status` | `apps/api/src/routes/sync.ts` y `services/sync.ts` devuelven `status` (PENDING \| IN_PROCESS \| ANSWERED). El cliente **no** lo consume. |
| **Rutas adicionales** | Solo auth, crypto, sync, health | La API expone además: `/v1/config`, `/v1/user/reminder-preferences`, `/v1/reminders/*`, `/v1/answers/*`, `/v1/records/:recordId/status`, `/v1/admin/*`. No documentadas en OpenAPI. |
| **Roulette (Pr4y Roulette)** | No existe en OpenAPI | El cliente Android llama a `GET /v1/public/requests` y `POST /v1/public/requests/{id}/pray`; **no existen** en `apps/api/src` (no hay rutas `public` registradas en `server.ts`). |

**Conclusión:** El cliente y la API han evolucionado más allá del contrato. Cualquier lógica que dependa del estado autoritativo del servidor (p. ej. `status` del record) debe consumirse en el cliente; hoy el cliente **no** recibe ni persiste `status`.

### 2.2 Lógica de negocio en cliente (Backend-First)

- **Estado de record (`status`):** El backend envía `status` en `GET /v1/sync/pull` (schema en `sync.ts` línea 31). En Android:
  - `SyncRecordDto` (**ApiService.kt**) **no** tiene el campo `status`.
  - `RequestEntity` (**RequestEntity.kt**) **no** tiene el campo `status`.
  - `processRemoteRecord` (**SyncRepository.kt**) **no** persiste `status`.
- **Mensajes de error:** En varios ViewModels (LoginViewModel, UnlockViewModel, VictoriasViewModel, SyncRepository) se usan cadenas fijas en español ("No autenticado", "Acceso denegado", "Error de red o seguridad"). Según `docs/CAUSA-ADMIN-NO-ENTRA.md` y el estándar de errores en `docs/api.md`, los mensajes deben venir del backend (`error.message`) y el cliente solo renderizarlos. AuthRepository sí propaga `errorBody()` como mensaje en `AuthError`, pero los consumidores a menudo sustituyen por mensajes genéricos.

**Archivos a refactorizar (coherencia / Backend-First):**

| Archivo | Acción |
|---------|--------|
| `docs/api-openapi.yaml` | Añadir `status` a `EncryptedRecord`; añadir paths: `/config`, `/user/reminder-preferences`, `/reminders`, `/answers`, `/records/:recordId/status`, `/public/requests`, `/public/requests/{id}/pray` (cuando existan en API). |
| `apps/mobile-android/.../data/remote/ApiService.kt` | Añadir campo `status: String` a `SyncRecordDto`. |
| `apps/mobile-android/.../data/local/entity/RequestEntity.kt` | Añadir campo `status: String` (PENDING/IN_PROCESS/ANSWERED) y migración Room si aplica. |
| `apps/mobile-android/.../data/sync/SyncRepository.kt` | En `processRemoteRecord`, mapear y persistir `rec.status` en `RequestEntity` (y equivalente en journal si aplica). |
| `apps/mobile-android/.../ui/viewmodel/LoginViewModel.kt` | Preferir mensaje del backend cuando exista (parsear `error.message` de API); usar mensaje genérico solo como fallback. |
| `apps/mobile-android/.../ui/viewmodel/UnlockViewModel.kt` | Idem: priorizar cuerpo de error del backend. |
| `apps/mobile-android/.../ui/viewmodel/VictoriasViewModel.kt` | Idem. |
| `apps/mobile-android/.../data/sync/SyncRepository.kt` | Para `SyncResult.Error`, si el backend devuelve `error.message`, propagarlo en lugar de genérico. |

---

## 3. Validación de identidad (naming Pr4y Ledger / Pr4y Roulette)

- **Pr4y:** Correctamente usado en paquete y UI (Pr4yApp, Pr4yTheme, Pr4yTopAppBar, Pr4yLog).
- **Pr4y Ledger:** No existe módulo o clase con nombre "Ledger". La bitácora E2EE se implementa como sync + Room (`AppDatabase`, `RequestDao`, `JournalDao`, `SyncRepository`). No es obligatorio renombrar, pero si se desea alinear con la marca, considerar un módulo o capa nombrada "Ledger" (p. ej. `data.ledger`) que agrupe sync + DAOs de records locales.
- **Pr4y Roulette:** En **ApiService.kt** los comentarios dicen "Roulette (Intercesión Anónima)" y los DTOs son `PublicRequestDto`, `getPublicRequests`, `prayForPublicRequest`. No hay fugas de naming; la fuga es de **identidad (UID)** en headers (ver siguiente sección).

**Archivos a considerar (opcional):**

| Archivo | Acción |
|---------|--------|
| Estructura `apps/mobile-android/.../data/` | Opcional: introducir paquete `ledger` (o similar) que agrupe sync + DAOs de bitácora para alinear con "Pr4y Ledger". |

---

## 4. Pr4y Roulette – Privacy Stripping (fuga de UID)

### 4.1 Requerimiento

El supervisor debe buscar **fugas de UID en los headers** de las peticiones hacia la Roulette. Las llamadas a Roulette deben ser anónimas (sin Bearer / sin identificación de usuario).

### 4.2 Hallazgo crítico

- En **ApiService.kt**, `getPublicRequests` y `prayForPublicRequest` se definen **sin** `@Header("Authorization")` y con `@Header("X-Anonymous") anon: String = "true"`.
- En **RetrofitClient.kt**, el `authInterceptor` añade `Authorization: Bearer <token>` a **todas** las peticiones donde `original.header("Authorization") == null`. Por tanto, las llamadas a Roulette **reciben el JWT** cuando el usuario está logueado → **fuga de UID** (el backend podría identificar al usuario en una ruta que debe ser anónima).
- El comentario en ApiService dice "X-Anonymous: true para activar el **stripping** en el interceptor", pero **no existe** ningún interceptor que quite el `Authorization` cuando `X-Anonymous: true`.

**Validación prayer_count:**  
El cliente solo incrementa el contador mediante `POST /v1/public/requests/{id}/pray`. Correcto en intención. La API actual **no** expone estas rutas en `apps/api` (no hay implementación Roulette en este repo), por lo que o bien están en otro servicio o están pendientes de implementación. Mientras tanto, el cliente no debe enviar JWT a esas rutas.

**Archivos a refactorizar (Roulette):**

| Archivo | Acción |
|---------|--------|
| `apps/mobile-android/.../data/remote/RetrofitClient.kt` | En el interceptor de auth: si la request tiene `X-Anonymous: true`, **no** añadir el header `Authorization`. Así las peticiones a `public/requests` y `public/requests/{id}/pray` salen sin Bearer. |

---

## 5. Revisión de inyección (Hilt) – AppContainer vs módulos

### 5.1 Estado actual

- **AppContainer** (`apps/mobile-android/.../di/AppContainer.kt`) sigue siendo un singleton que expone `AppDatabase` y se usa en múltiples pantallas y repositorios.
- **No** existen en el repo `NetworkModule` ni `DatabaseModule` (Hilt). No hay `@HiltViewModel` ni módulos `@Module` en el código indexado.
- En **Pr4yApp.kt** se mantiene "inicialización de AppContainer para compatibilidad legacy durante la migración a Hilt".

**Conclusión:** La migración a Hilt (NetworkModule, DatabaseModule) **no está hecha**. No se pueden evaluar dependencias circulares hasta que existan esos módulos.

**Archivos a refactorizar (Hilt):**

| Archivo | Acción |
|---------|--------|
| `apps/mobile-android/.../di/` | Crear `NetworkModule` (proveer `ApiService` / `OkHttpClient`) y `DatabaseModule` (proveer `AppDatabase` y DAOs). Asegurar que `ApiService` no dependa de `AppDatabase` y que `AppDatabase` no dependa de `ApiService` para evitar ciclos. |
| `apps/mobile-android/.../Pr4yApp.kt` | Anotar con `@HiltAndroidApp` y eliminar (o condicionar) `AppContainer.init(this)` cuando la migración esté completa. |
| Pantallas y ViewModels que usan `AppContainer.db` o `RetrofitClient.create` | Sustituir por inyección vía constructor (ViewModel con `@HiltViewModel`, pantallas con `@AndroidEntryPoint` y `@Inject` donde corresponda). |
| Archivos que referencian AppContainer (listados en sección 2) | Migrar a inyección Hilt. |

Referencias actuales a **AppContainer** (para sustituir por Hilt):

- `SyncRepository.kt`
- `HomeScreen.kt`, `HomeViewModelFactory.kt`, `Pr4yNavHost.kt`
- `SearchScreen.kt`, `FocusModeScreen.kt`, `DetailScreen.kt`, `NewEditScreen.kt`, `NewJournalScreen.kt`, `JournalScreen.kt`, `JournalEntryScreen.kt`, `SettingsScreen.kt`
- `AuthRepository.kt`
- `Pr4yApp.kt`

---

## 6. Check de telemetría y aislamiento Ledger

### 6.1 TelemetryInterceptor (backend)

- En **apps/api** **no** existe un middleware ni interceptor llamado "TelemetryInterceptor" que capture latencia y códigos 4xx/5xx por ruta.
- **docs/METRICAS-BACKEND-ADMIN.md** describe la necesidad de métricas a nivel request (latencia, 4xx/5xx por ruta) vía middleware.
- **docs/TELEMETRIA-MCP-ADMIN-2026-02-20.md** y **TELEMETRIA-MCP-SECUENCIA-COMPLETA.md** se refieren a telemetría del **navegador MCP** (cursor-ide-browser), no a un interceptor de backend.

**Conclusión:** El backend no tiene hoy un TelemetryInterceptor. Cuando se implemente, debe:

1. Capturar **latencia** y **códigos HTTP** (4xx/5xx) por ruta o grupo (auth, sync, admin, etc.).
2. **No** incluir en logs ni métricas: cuerpo de request/response de sync (Ledger), headers de autorización, ni ningún dato que permita identificar contenido cifrado o usuario más allá de agregados (p. ej. conteos por ruta y código).

### 6.2 Aislamiento Ledger en logs

- En **apps/api/src/services/sync.ts**, `logConflict` y `logOwnershipMismatch` registran solo `recordId`, `reason` y metadatos de versión. **No** se registra `encryptedPayloadB64` ni contenido descifrado. Cumple con no enviar datos del Ledger a telemetría/logs.
- Cualquier futuro middleware de telemetría debe **excluir** de los logs el body en rutas de sync y crypto, y no persistir headers sensibles (según **docs/AUDIT-USER-ISOLATION.md** y buenas prácticas de docs de telemetría).

**Archivos a crear/refactorizar (telemetría):**

| Archivo | Acción |
|---------|--------|
| `apps/api/src/` (nuevo middleware o hook) | Implementar middleware/hook de telemetría que registre por ruta: latencia y conteo de 2xx/4xx/5xx, sin incluir body de sync/crypto ni datos de usuario. Documentar en línea con `docs/TELEMETRIA-MCP-ADMIN-2026-02-20.md` que los datos del Ledger no se envían a telemetría. |

---

## 7. Pr4y Ledger – transacciones atómicas y LedgerDao

### 7.1 Nomenclatura

- En el repo no existe una clase llamada "LedgerDao". La persistencia local de la bitácora (records) está en **RequestDao**, **JournalDao**, **OutboxDao**, **SyncStateDao** (Room). La capa que aplica transacciones es **SyncRepository** usando `db.runInTransaction { ... }`.

### 7.2 Transacciones atómicas

- **SyncRepository.kt:**  
  - Pull: procesamiento de registros remotos dentro de `db.runInTransaction { runBlocking { ... processRemoteRecord ... } }` (aprox. líneas 124–129).  
  - Borrador de diario: `journalDao.insert` + `outboxDao.insert` + `JournalDraftStore.clearDraft` dentro de `db.runInTransaction` (aprox. 165–189).  
- **apps/api/src/services/answers.ts:** creación de answer y actualización de status del record en `prisma.$transaction([...])`. Correcto.

**Conclusión:** Los puntos críticos de escritura local (pull masivo y creación de journal + outbox) usan transacciones atómicas. No hay LedgerDao como tal; la validación se aplica a la capa que hace de "Ledger" (SyncRepository + Room). No se detectan gaps adicionales en atomicidad en el código revisado.

---

## 8. Lista consolidada de archivos para refactorización inmediata

### Prioridad alta (seguridad / contrato)

1. **apps/mobile-android/app/src/main/java/com/pr4y/app/data/remote/RetrofitClient.kt**  
   - No añadir `Authorization` cuando la request tenga `X-Anonymous: true` (Privacy Stripping Roulette).

2. **apps/mobile-android/app/src/main/java/com/pr4y/app/data/remote/ApiService.kt**  
   - Añadir `status` a `SyncRecordDto`.

3. **apps/mobile-android/app/src/main/java/com/pr4y/app/data/local/entity/RequestEntity.kt**  
   - Añadir campo `status` y migración Room si aplica.

4. **apps/mobile-android/app/src/main/java/com/pr4y/app/data/sync/SyncRepository.kt**  
   - Persistir `rec.status` en `RequestEntity` en `processRemoteRecord`; propagar mensaje de error del backend cuando exista.

5. **docs/api-openapi.yaml**  
   - Incluir `status` en `EncryptedRecord`; documentar rutas existentes (config, user, reminders, answers, records) y, cuando existan, public/requests y pray.

### Prioridad media (Backend-First y UX de errores)

6. **apps/mobile-android/.../ui/viewmodel/LoginViewModel.kt**  
   - Usar mensaje del backend (p. ej. parseando `error.message` del cuerpo de error API) como mensaje principal; mensajes genéricos solo como fallback.

7. **apps/mobile-android/.../ui/viewmodel/UnlockViewModel.kt**  
   - Idem.

8. **apps/mobile-android/.../ui/viewmodel/VictoriasViewModel.kt**  
   - Idem.

### Prioridad media (Hilt y deuda técnica)

9. **apps/mobile-android/app/src/main/java/com/pr4y/app/di/**  
   - Crear `NetworkModule` y `DatabaseModule`; proveer `ApiService` y `AppDatabase`/DAOs sin ciclos.

10. **apps/mobile-android/.../Pr4yApp.kt**  
    - Integrar `@HiltAndroidApp`; retirar o condicionar `AppContainer.init`.

11. **Pantallas y ViewModels que usan AppContainer** (listados en sección 5)  
    - Sustituir uso de `AppContainer` por inyección Hilt.

### Prioridad menor (observabilidad)

12. **apps/api/src/server.ts** (o nuevo archivo de middleware)  
    - Añadir hook/middleware de telemetría (latencia + 4xx/5xx por ruta) sin registrar body de sync/crypto ni PII; alinear con AUDIT-USER-ISOLATION y docs de telemetría.

---

## 9. Checklist de cumplimiento Backend-First

| Requerimiento | Estado |
|---------------|--------|
| Cliente no calcula estados complejos; recibe estado final del servidor | Parcial: API envía `status`; cliente no lo consume ni persiste. |
| Mensajes de error vienen del backend; cliente solo renderiza | Parcial: backend envía `error.message`; cliente a menudo sobrescribe con mensajes fijos. |
| Roulette: sin UID en headers | Incumplido: interceptor añade Bearer a todas las requests; falta stripping para X-Anonymous. |
| prayer_count solo vía POST .../pray | Correcto en cliente; backend Roulette no implementado en este repo. |
| Ledger: transacciones atómicas en escrituras críticas | Cumplido en SyncRepository y answers. |
| Ledger: datos no en telemetría | Cumplido en sync actual; falta implementar TelemetryInterceptor sin Ledger/PII. |
| OpenAPI alineado con API real | Incumplido: falta status y varias rutas. |
| Hilt: migración de AppContainer a módulos | No iniciada. |

---

*Fin del reporte. Para dudas o ampliación de algún punto, usar este documento como referencia de supervisión.*
