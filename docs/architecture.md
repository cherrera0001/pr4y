# PR4Y – Arquitectura

## 1) Objetivo
Entregar un cuaderno personal de oración con:
- offline-first (funciona sin internet)
- sync opcional multi-dispositivo (web y futuro Android)
- E2EE: contenido cifrado en cliente, servidor “zero knowledge”

## 2) Componentes

### 2.1 Web (Next.js / Vercel)
Responsabilidades:
- UI (lista, crear, detalle, journal, búsqueda, ajustes)
- Persistencia local (IndexedDB)
- Cifrado/descifrado en cliente
- Export/Import de backup cifrado
- Sync: push/pull incremental con API

Almacenamiento local recomendado:
- IndexedDB (Dexie o idb)
- Estructura local:
  - requests (en claro SOLO local)
  - journal entries (en claro SOLO local)
  - sync metadata (cursor, lastSyncAt)
  - local key material (derivado; no almacenar passphrase)

### 2.2 API (Fastify / Railway)
Responsabilidades:
- Autenticación (magic link o email+password)
- Gestión de dispositivos/sesiones
- Endpoints de sincronización (almacena blobs cifrados)
- Rate limiting, auditoría mínima sin contenido
- Healthchecks

### 2.3 DB (PostgreSQL / Railway)
Almacena:
- usuarios y sesiones
- “records” cifrados y versionados
- cursores de sync

No almacena:
- texto en claro de pedidos/notas

## 3) Modelo de cifrado (E2EE)

### 3.1 Derivación de clave
- Usuario define una passphrase (o PIN largo) para su “cuaderno”.
- En cliente: derivar una clave maestra (KEK) con Argon2id o PBKDF2 (si web limita), usando salt único por usuario.
- Generar DEK (data encryption key) aleatoria para cifrar datos.
- Envolver (wrap) DEK con KEK y guardar el DEK envuelto (wrap) en backend como metadata.  
  Backend nunca ve KEK/passphrase.

### 3.2 Cifrado de datos
- AES-256-GCM por registro (request/journal).
- Cada registro cifrado produce:
  - `ciphertext`
  - `nonce/iv`
  - `authTag` (si aplica)
  - `aad` opcional (p.ej. recordId/version)

### 3.3 Sync
- El cliente sube “records” cifrados.
- El cliente baja “records” cifrados y los descifra localmente.

## 4) Estructura de datos de sincronización

En vez de sincronizar entidades normalizadas, sincronizar “records” tipo evento/documento:

Record (server-side):
- recordId (UUID)
- userId
- type: `prayer_request` | `journal_entry` | `tombstone`
- version (int)
- encryptedPayload (bytea/base64)
- createdAt, updatedAt
- deletedAt (nullable)
- clientUpdatedAt (timestamp del cliente)
- hash (opcional, integridad)

Resolución de conflictos:
- MVP: “last write wins” usando `clientUpdatedAt` + `version`.
- Futuro: CRDT o merge por campos (probablemente innecesario para cuaderno simple).

## 5) Dominios de producto (modelo lógico)
Entidades (cliente):
- PrayerRequest:
  - id, title, description?, person?, tags[], status, createdAt, updatedAt, answeredAt?, history[]
- JournalEntry:
  - id, text, createdAt, linkedRequestId?
- Reminder:
  - id, time, frequency, gentleMode, enabled

En servidor se almacenan como records cifrados, no como tablas en claro.

## 6) Seguridad de plataforma
- CORS estricto a dominio Vercel.
- Rate limit en auth y sync.
- Protección contra enumeración de correos (respuestas genéricas).
- Logs estructurados sin PII sensible.
- Headers: HSTS, CSP (web), secure cookies si aplica.

## 7) Deploy
- Web en Vercel (build Next.js).
- API en Railway (Node/Fastify).
- DB Postgres en Railway.

## 8) Roadmap técnico
MVP:
- Web offline + export/import + auth + sync básico
Fase 2:
- Android (Flutter/Kotlin) reutilizando API + crypto
Fase 3:
- Sync avanzado + conflicto mejorado + observabilidad
