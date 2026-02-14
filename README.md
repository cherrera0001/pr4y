# PR4Y

PR4Y es un cuaderno personal de oración: pedidos + notas + seguimiento + recordatorios suaves.  
Es **personal** y **privado**, con enfoque **mobile-first** (web usable en móvil) y backend para **sincronización opcional**. El contenido se cifra del lado cliente (E2EE): el servidor almacena blobs cifrados, no puede leerlos.

## Stack
- **Android**: Kotlin + Jetpack Compose (app principal MVP)
- **API**: Node.js + Fastify + TypeScript (Railway)
- **DB**: PostgreSQL (Railway) + Prisma
- **Web**: Next.js (opcional; landing/PWA)
- `docs/`: arquitectura y contrato API

## Repo
- `apps/api`: Fastify – auth (email+password, JWT+refresh), crypto (wrapped DEK), sync (pull/push)
- `apps/mobile-android`: Android Kotlin + Compose – offline-first, Room, crypto, sync
- `apps/web`: placeholder (landing/PWA cuando se necesite)
- `packages/*`: shared (cuando se añadan)
- `docs/`: api.md, api-openapi.yaml

## Principios
- Offline-first: la web funciona sin conexión (IndexedDB).
- Privacidad: E2EE. El backend nunca guarda contenido en claro.
- Simplicidad: sin funciones sociales.

## Setup local

### Requisitos
- Node.js LTS
- pnpm
- PostgreSQL local (opcional; recomendado para correr API completo)

### Variables de entorno API

Copia `apps/api/.env.example` a `apps/api/.env` y rellena:

- `DATABASE_URL=postgresql://user:password@localhost:5432/pr4y`
- `JWT_SECRET=` (mínimo 32 caracteres; en prod usar valor seguro)
- `CORS_ORIGIN=http://localhost:3000` (opcional)

### Instalar dependencias

```bash
pnpm install
```

---

## 1) Levantar API local con Postgres

**Opción A – Postgres en tu máquina**

- Crea una base de datos: `createdb pr4y` (o desde tu cliente).
- En `apps/api/.env` pon `DATABASE_URL=postgresql://user:password@localhost:5432/pr4y`.

**Opción B – Postgres con Docker**

```bash
docker run -d --name pr4y-postgres -e POSTGRES_USER=pr4y -e POSTGRES_PASSWORD=pr4y -e POSTGRES_DB=pr4y -p 5432:5432 postgres:16-alpine
```

Luego en `apps/api/.env`:  
`DATABASE_URL=postgresql://pr4y:pr4y@localhost:5432/pr4y`

**Arrancar la API**

```bash
pnpm --filter @pr4y/api db:migrate
pnpm --filter @pr4y/api dev
```

La API queda en `http://localhost:4000`. Health: `GET http://localhost:4000/v1/health`.

---

## 2) Correr migraciones

Desde la raíz del repo:

```bash
pnpm --filter @pr4y/api db:migrate
```

Para desarrollo con migraciones nuevas (crear migración):

```bash
pnpm --filter @pr4y/api db:migrate:dev
```

---

## 3) Ejecutar app Android en emulador

- Android Studio: abre la carpeta `apps/mobile-android`, sincroniza Gradle.
- Si no tienes Gradle wrapper: `gradle wrapper` (o usa el de Android Studio).
- Crea un AVD (API 26+) e inicia el emulador.
- Run > Run 'app', o desde terminal:

```bash
cd apps/mobile-android
./gradlew installDebug
```

La app por defecto apunta la API a `http://10.0.2.2:4000` (emulador → localhost).

---

## 4) Flujo: registrar usuario, crear pedido offline, sincronizar

1. **API y DB**: Postgres corriendo, `DATABASE_URL` y `JWT_SECRET` en `apps/api/.env`, migraciones aplicadas (`pnpm --filter @pr4y/api db:migrate`), API en marcha (`pnpm --filter @pr4y/api dev`).
2. **Android**: Instala la app en el emulador. [PENDIENTE] Añadir pantalla de login/registro en la app que llame a `POST /v1/auth/register` y guarde tokens en `AuthTokenStore`.
3. **Registrar usuario**: Desde la app (cuando exista la UI de auth) o con curl:

   ```bash
   curl -X POST http://localhost:4000/v1/auth/register -H "Content-Type: application/json" -d "{\"email\":\"test@example.com\",\"password\":\"password123\"}"
   ```

4. **Crear pedido offline**: En la app, pantalla "Nuevo pedido" (NewEditScreen); al guardar, [PENDIENTE] persistir en Room y encolar en outbox (cifrado) para sync.
5. **Sincronizar**: [PENDIENTE] En Settings > "Sincronizar ahora", ejecutar push (outbox) → pull → aplicar cambios (last-write-wins).

---

## Deploy a Railway

- Conecta el repo a Railway y configura el servicio para la API (root o `apps/api`).
- Variables en Railway: `DATABASE_URL` (Postgres añadido en Railway), `JWT_SECRET`, `CORS_ORIGIN` (opcional).
- Build: `pnpm install --no-frozen-lockfile && pnpm --filter @pr4y/api build`
- Start: `pnpm --filter @pr4y/api start`
- Migraciones: en el mismo servicio o en un job: `pnpm --filter @pr4y/api db:migrate`

Ver también `DEPLOY.md` y `docs/api.md`.
