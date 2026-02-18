# Estructura de la base de datos PR4Y

Referencia de tablas, columnas clave, índices y límites de tamaño. Cualquier cambio en API o app que afecte persistencia debe partir de esta referencia.

**Motor:** PostgreSQL. **ORM:** Prisma. Schema en `apps/api/prisma/schema.prisma`.

---

## Tablas y relaciones

| Tabla | Uso | Relaciones |
|-------|-----|------------|
| **users** | Auth, rol, estado (active/banned) | refresh_tokens, wrapped_dek, records, usage_logs |
| **global_content** | Contenido admin (oraciones, avisos) | — |
| **usage_logs** | Métricas de sync (bytes, counts) por usuario/día | users |
| **refresh_tokens** | JWT refresh | users |
| **wrapped_dek** | DEK envuelta por usuario (1:1) | users |
| **records** | Sync genérico (pedidos, diario); contenido en encrypted_payload_b64 | users, reminders, answers |
| **reminders** | Recordatorios por pedido (horario, días) | records |
| **answers** | Testimonios; vincula record con status ANSWERED | records |

---

## Columnas clave y tipos

### users
- `id` (uuid PK), `email` (unique), `password_hash`, `google_id` (unique), `role`, `status`, `last_login_at`, `created_at`.

### records
- `record_id` (uuid PK), `user_id` (FK users), `type` (metadato: p. ej. prayer_request, journal_entry), `version`, **`encrypted_payload_b64`** (Text), `client_updated_at`, `server_updated_at`, `deleted`, **`status`** (PENDING | IN_PROCESS | ANSWERED).
- **encrypted_payload_b64:** tipo `text` en DB para soportar payloads grandes. Límite aplicado en API: **512 KB** por registro (base64).

### answers
- `id` (uuid PK), `record_id` (FK records), `answered_at`, **`testimony`** (Text, nullable). Testimony sanitizado; límite en API: **5_000** caracteres.

### global_content
- `body` (Text). Contenido largo ya soportado.

### wrapped_dek
- `wrapped_dek_b64`: string; límites en API (ej. 4KB) definidos en rutas crypto.

---

## Índices

- **records:** `(user_id)`, `(user_id, client_updated_at)` (pull ordenado), `(user_id, status)`.
- **reminders:** `(record_id)`.
- **answers:** `(record_id)`.
- **usage_logs:** `(user_id, day)` unique, `(day)`.
- **refresh_tokens:** `(user_id)`, `(token_hash)`.
- **global_content:** `(type, published)`.

---

## Límites de tamaño (API y consistencia con app)

| Campo / concepto | Límite | Dónde se aplica |
|------------------|--------|------------------|
| encryptedPayloadB64 por record | 512 KB | API sync push (`apps/api/src/routes/sync.ts`) |
| title (pedidos, etc.) | 200 caracteres | API sanitize LIMITS; Android InputSanitizer 500 |
| body (cuerpo) | 10_000 caracteres | API sanitize; Android 50_000 (alinear si hace falta) |
| notes | 2_000 caracteres | API sanitize |
| testimony | 5_000 caracteres | API sanitize y ruta answers |

La sanitización (texto permitido: letras, números, puntuación, emojis) se define en `apps/api/src/lib/sanitize.ts` y debe replicarse en Android para consistencia antes de cifrar.

---

## Migraciones

Tras cambios en `schema.prisma`, generar y aplicar:

```bash
cd apps/api && npx prisma migrate dev --name descripcion_cambio
```

Producción: `npx prisma migrate deploy`.
