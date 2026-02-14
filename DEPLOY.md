# Deploy: Railway (API)

Guía para desplegar la API de PR4Y en **Railway** con PostgreSQL. Configuración pensada para coste cero dentro de los límites gratuitos.

---

## Flujo de despliegue (resumen)

1. Conectar el repo de GitHub a un proyecto Railway.
2. Crear un servicio para la API (root = raíz del monorepo).
3. Añadir el add-on **PostgreSQL** al proyecto y asociar `DATABASE_URL` al servicio API.
4. Configurar **todas** las variables de entorno (ver tabla más abajo).
5. Definir **Build** y **Start** (y opcionalmente migraciones al arranque).
6. Desplegar; comprobar `/v1/health`.

---

## Resumen

- **Servicio**: `@pr4y/api` (Fastify + Prisma + PostgreSQL).
- **Monorepo**: desde la raíz se ejecutan `pnpm --filter @pr4y/api` para build y start.
- **Build**: usa esbuild (no `tsc`) para evitar problemas de memoria en Railway.

---

## 1. Crear proyecto en Railway

1. Conecta el repositorio de GitHub a Railway.
2. Crea un **nuevo servicio** para la API.
3. **Root Directory** (si aplica): deja raíz del repo o indica que el build se ejecuta desde raíz (ver abajo).

---

## 2. Añadir PostgreSQL

1. En el mismo proyecto Railway, añade el **plugin/add-on de PostgreSQL**.
2. Railway te dará la variable **`DATABASE_URL`**. Conéctala al servicio de la API (en Railway suele hacerse automáticamente si están en el mismo proyecto).

---

## 3. Variables de entorno críticas

Configura estas variables en el servicio de la API (Railway → servicio → Variables):

| Variable | Obligatoria | Descripción |
|----------|-------------|-------------|
| `DATABASE_URL` | **Sí** | URL de PostgreSQL (suele inyectarse al añadir Postgres). Ejemplo: `postgresql://user:pass@host:port/railway` |
| `JWT_SECRET` | **Sí** | Secreto para firmar JWT; mínimo 32 caracteres. Generar con: `openssl rand -base64 32` |
| `CORS_ORIGIN` | No | Origen permitido para CORS (p. ej. URL de tu web o `*` para desarrollo). En producción conviene un valor concreto. |

No subas `.env` al repo; todo debe configurarse en Railway.

---

## 4. Comandos de build y start

En Railway, en la configuración del servicio:

| Paso | Comando |
|------|---------|
| **Build** | `pnpm install --no-frozen-lockfile && pnpm --filter @pr4y/api build` |
| **Start** | `pnpm --filter @pr4y/api start` |
| **Root** | Raíz del monorepo (donde está `pnpm-workspace.yaml`). |

El script `build` de `@pr4y/api` ejecuta `prisma generate` y `node scripts/build.mjs` (esbuild → `dist/server.js`).

---

## 5. Migraciones de Prisma

Las migraciones crean/actualizan las tablas en PostgreSQL. **Deben ejecutarse al menos una vez** antes de que la API reciba tráfico.

- **Opción A – Manual (recomendada para el primer deploy)**: En Railway, abre una consola one-off del servicio y ejecuta:
  ```bash
  pnpm --filter @pr4y/api db:migrate
  ```
  Equivale a `prisma migrate deploy` dentro de `apps/api` (aplica las migraciones existentes en `apps/api/prisma/migrations/`).

- **Opción B – En el comando Start**: Si quieres que cada deploy aplique migraciones automáticamente, cambia el comando de start a:
  ```bash
  pnpm --filter @pr4y/api db:migrate && pnpm --filter @pr4y/api start
  ```
  Así, en cada despliegue se ejecuta `prisma migrate deploy` y luego `node dist/server.js`.

- **Crear nuevas migraciones** (solo en desarrollo local): `pnpm --filter @pr4y/api db:migrate:dev` (genera archivos en `prisma/migrations/`). Esos archivos se suben al repo y se aplican en producción con `db:migrate`.

---

## 6. Healthcheck

La API expone:

- **GET** `/v1/health` → `{ "status": "ok", "version": "1.0.0" }`

Configura en Railway el health check con path `/v1/health` si la plataforma lo permite.

---

## 7. Vercel (apps/web) – opcional

Si en el futuro despliegas `apps/web` en Vercel:

- **Root Directory**: `apps/web`.
- **Install**: `cd ../.. && pnpm install --no-frozen-lockfile`
- **Build**: `cd ../.. && pnpm --filter @pr4y/web build`

El README principal y este DEPLOY.md se centran en Railway para la API; la web queda como referencia cuando exista.
