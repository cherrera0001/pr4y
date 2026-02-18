# Backend First – API implementada (PR4Y)

Resumen de lo implementado en el backend para recordatorios flexibles, sanitización, estados espirituales y muro de testimonios. El cliente Android debe consumir estos endpoints y alinear lógica (p. ej. PBKDF2, sanitización).

## 1. Recordatorios (`/v1/reminders`)

- **GET /v1/reminders** – Lista todos los recordatorios del usuario (por record).
- **GET /v1/reminders/:id** – Obtiene un recordatorio por id.
- **POST /v1/reminders** – Crea recordatorio. Body: `{ recordId, time, daysOfWeek, isEnabled? }`.
  - `time`: string `"HH:mm"` (ej. `"09:00"`, `"21:30"`).
  - `daysOfWeek`: array de 0–6 (0 = domingo).
  - `isEnabled`: boolean, default true.
- **PATCH /v1/reminders/:id** – Actualiza `time`, `daysOfWeek` y/o `isEnabled`.
- **DELETE /v1/reminders/:id** – Elimina recordatorio.

El `recordId` debe corresponder a un record del usuario. El cliente debe programar WorkManager según la lista descargada de la API (no un horario global fijo).

## 2. Sanitización y seguridad

- **Módulo** `apps/api/src/lib/sanitize.ts`: validación con Zod, eliminación de HTML/scripts y caracteres de control; permite letras, números, espacios, puntuación básica y **emojis** (para expresión emocional).
- **Uso**: testimonios en `POST /v1/answers` (campo `testimony`) se sanitizan en backend.
- **Android**: usar la misma lógica en `InputSanitizer` y en `NewEditScreen`/`NewJournalScreen` (filtros en `OutlinedTextField`, mensaje al pegar contenido sospechoso).

## 3. Estados del record y testimonios

- **Record.status** (en DB y en respuesta de sync): `PENDING` | `IN_PROCESS` | `ANSWERED`.
- **GET /v1/sync/pull**: cada record incluye ahora el campo `status`.
- **PATCH /v1/records/:recordId/status** – Actualiza estado. Body: `{ status: "PENDING" | "IN_PROCESS" | "ANSWERED" }`.
- **Tabla `answers`**: registra cuándo/cómo respondió Dios.
  - **GET /v1/answers/stats** – Conteo de oraciones respondidas. Respuesta: `{ answeredCount: number }` (para dashboard / Victorias).
  - **GET /v1/answers** – Lista testimonios del usuario (Muro de Fe).
  - **GET /v1/answers/:id** – Detalle de un testimonio.
  - **POST /v1/answers** – Crear testimonio. Body: `{ recordId, testimony? }`. Marca el record como `ANSWERED` y opcionalmente guarda texto (sanitizado).

## 4. Criptografía (frase de recuperación)

- El backend **valida** que el DEK enviado use KDF compatible con Android:
  - `kdf.name === "pbkdf2"`
  - `kdf.params.iterations === 120000`
- Así se evita guardar un DEK con parámetros distintos y que la frase deje de funcionar. Android debe mantener `PBKDF2_ITERATIONS = 120_000` y enviar exactamente estos params al hacer PUT `/v1/crypto/wrapped-dek`.

## 5. Definition of Done (cliente Android)

1. **Room**: actualizar entidades para incluir `status` en Record y tablas locales para reminders/answers si se cachean.
2. **Reminders**: consumir GET `/v1/reminders`, crear/editar/borrar vía API; ReminderScheduler debe programar WorkManager según la lista de recordatorios descargada.
3. **UI**: selector de hora y frecuencia (múltiples horarios por pedido); pantalla Testimonios filtrando records con `status === ANSWERED` (y/o GET `/v1/answers`).
4. **Sanitización**: filtros en campos de texto y mensaje al pegar contenido no permitido; alinear reglas con `lib/sanitize.ts`.
5. **DekManager**: derivación PBKDF2 idéntica al backend (iterations 120000, mismo uso de salt).

## 6. Migración

- **Migración**: `20250216000000_add_reminders_status_answers` (tablas `reminders`, `answers`, columna `status` en `records`).
- **En local**: Con Postgres corriendo, define `DATABASE_URL` en `apps/api/.env` y ejecuta desde `apps/api`: `npm run db:migrate` (o `npx prisma migrate deploy`).
- **En Railway**: Al desplegar, `npm start` ya ejecuta `prisma migrate deploy` antes de arrancar el servidor, así que las migraciones se aplican solas.
- **Cliente Prisma**: Se genera con `npx prisma generate` (o al hacer `npm run build`). Si no tienes `DATABASE_URL`, `prisma.config.ts` usa un fallback para que `generate` funcione sin base de datos.
