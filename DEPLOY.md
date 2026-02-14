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
2. **Vinculación de variables**: En el servicio **@pr4y/api** → **Variables**, configura `DATABASE_URL` usando la **referencia dinámica** al add-on Postgres, no un string estático:
   - **Nombre**: `DATABASE_URL`
   - **Valor**: `${{Postgres.DATABASE_URL}}`
   - Así la API siempre usará la URL actual del Postgres del proyecto (si Railway rotara credenciales o host, la API seguiría funcionando). Si tu servicio Postgres tiene otro nombre en Railway, sustituye `Postgres` por ese nombre (p. ej. `${{PostgreSQL.DATABASE_URL}}`).

---

## 3. Variables de entorno críticas

Configura estas variables en el servicio de la API (Railway → servicio @pr4y/api → Variables):

| Variable | Obligatoria | Descripción |
|----------|-------------|-------------|
| `DATABASE_URL` | **Sí** | **Usar referencia dinámica**: `${{Postgres.DATABASE_URL}}` (no pegar una URL estática; así se vincula al add-on Postgres y se evitan fallos si la URL cambia). |
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

## 6. Healthcheck y base de datos

**Health Check de base de datos**: En el panel admin de Railway, asegúrate de que el servicio **@pr4y/api** tenga la variable **`DATABASE_URL`** vinculada correctamente al ítem de Postgres (ver sección 2). Así el mantenedor puede comprobar que la API lee la base de datos y las estadísticas de la tabla `users` y uso.

La API expone:

- **GET** `/v1/health` → `{ "status": "ok" | "degraded", "version": "1.0.0", "database": "ok" | "error" }`
  - Si `database` es `"ok"`, la conexión a Postgres funciona. Si es `"error"`, revisa que `DATABASE_URL` esté definida y apunte al add-on Postgres.

Configura en Railway el health check con path `/v1/health` si la plataforma lo permite.

---

## 6.1. Troubleshooting: "Application not found" (404)

Si `curl https://api.pr4y.cl/v1/health` devuelve:

```json
{"status":"error","code":404,"message":"Application not found","request_id":"..."}
```

**Esa respuesta es de Railway**, no de nuestra API (la API devolvería `{"status":"ok","version":"1.0.0","database":"..."}` o `{"error":{...}}`). Significa que la petición **no está llegando al proceso Fastify**.

**Checklist:**

1. **Commit y push**: Los cambios de código deben estar en el repo y desplegados. Si solo modificaste en local, haz `git add`, `git commit` y `git push` para que Railway vuelva a construir y desplegar.
2. **Dominio en Railway**: En el servicio de la API → **Settings** → **Networking** / **Domains**, asegúrate de que **api.pr4y.cl** esté asignado a este servicio (custom domain).
3. **Logs del deploy**: En Railway → servicio API → **Deployments** → último deploy → **View Logs**. Comprueba que el build termina bien y que el proceso arranca (`Conexión a la base de datos establecida`, `API running on port ...`). Si el proceso hace `process.exit(1)` (p. ej. por fallo de `DATABASE_URL`), Railway no tendrá ningún proceso escuchando y devolverá "Application not found".
4. **DNS**: En Cloudflare (o tu DNS), el registro **api** (o `api.pr4y.cl`) debe ser CNAME al dominio que Railway te da (p. ej. `tu-servicio.up.railway.app`).

---

## 7. Vercel (apps/web)

Para que **pr4y.vercel.app** (o pr4y.cl apuntando a Vercel) no devuelva 404:

1. **Root Directory**: En Vercel → Project Settings → General → **Root Directory** = `apps/web`. Sin esto, Vercel construye desde la raíz del monorepo y puede no encontrar la app Next.js.
2. **Build & Dev**: si usas monorepo, en Build settings:
   - **Install**: `pnpm install` (o desde raíz: `cd ../.. && pnpm install --no-frozen-lockfile`).
   - **Build**: `pnpm run build` (o `cd ../.. && pnpm --filter @pr4y/web build` si el root es la raíz).
3. **No usar** `output: 'export'` en `next.config.js`; el panel `/admin` usa middleware y rutas de API.

**CORS (Railway)**: Para que el front en Vercel llame a la API en Railway, la API ya permite por defecto `https://pr4y.vercel.app` y `https://pr4y.cl`. Si usas otro dominio, añade la variable `CORS_ORIGIN` en Railway.

**Variables en Vercel** (apps/web): `NEXT_PUBLIC_API_URL=https://api.pr4y.cl/v1` (o la URL de tu API en Railway). Ver `apps/web/.env.example`.

---

## 8. DNS en Cloudflare (pr4y.cl → Vercel)

Para que **pr4y.cl** (raíz) apunte a Vercel y **api.pr4y.cl** siga en Railway:

1. **Eliminar** el CNAME de la raíz que apunta a Railway (`rrjx0w83.up.railway.app`).
2. **Crear** un registro **A** para la raíz (`@`) apuntando a la IP de Vercel: `76.76.21.21`.
3. **Mantener** el CNAME `api` → Railway (no modificar).

### Requisitos

- **ZONE_ID**: ID de la zona de pr4y.cl en Cloudflare (Dashboard → pr4y.cl → Overview → Zone ID).
- **CLOUDFLARE_API_TOKEN**: Token con permiso "Edit zone DNS" (My Profile → API Tokens).

### Script

Usar el script `scripts/cloudflare-dns-vercel.sh` (o los comandos curl que contiene). Ejemplo de uso:

```bash
export CLOUDFLARE_API_TOKEN="tu_token"
export ZONE_ID="tu_zone_id"
bash scripts/cloudflare-dns-vercel.sh
```

O ejecutar los pasos manualmente con curl (sustituir `ZONE_ID`, `RECORD_ID` y `CLOUDFLARE_API_TOKEN`):

```bash
# 1) Listar registros y localizar el ID del CNAME raíz que apunta a rrjx0w83.up.railway.app
curl -s "https://api.cloudflare.com/client/v4/zones/ZONE_ID/dns_records" \
  -H "Authorization: Bearer CLOUDFLARE_API_TOKEN"

# 2) Eliminar ese CNAME (usar el "id" del registro encontrado)
curl -s -X DELETE "https://api.cloudflare.com/client/v4/zones/ZONE_ID/dns_records/RECORD_ID" \
  -H "Authorization: Bearer CLOUDFLARE_API_TOKEN"

# 3) Crear registro A raíz → Vercel
curl -s -X POST "https://api.cloudflare.com/client/v4/zones/ZONE_ID/dns_records" \
  -H "Authorization: Bearer CLOUDFLARE_API_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"type\":\"A\",\"name\":\"@\",\"content\":\"76.76.21.21\",\"ttl\":1,\"proxied\":false}"

# 4) Comprobar api.pr4y.cl (no modificar; debe seguir siendo CNAME → Railway)
curl -s "https://api.cloudflare.com/client/v4/zones/ZONE_ID/dns_records?name=api.pr4y.cl" \
  -H "Authorization: Bearer CLOUDFLARE_API_TOKEN"
```
