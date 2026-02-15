# Deploy en Railway (api.pr4y.cl)

Si `curl https://api.pr4y.cl/v1/health` devuelve **404 "Application not found"** con cabecera `x-railway-fallback: true`, el proxy de Railway no encuentra ningún proceso escuchando. Suele ser por: (1) la app no se ha desplegado con los últimos cambios, (2) la app se cae al arrancar (p. ej. falta `DATABASE_URL`), o (3) puerto incorrecto.

## Forzar la base de datos AHORA (Railway dice "No tables")

Si en Railway la sección **Database** muestra "No tables" o el registro/login devuelve 500 por `relation "public.users" does not exist`, hay que **aplicar las migraciones de Prisma** contra la base de producción.

**Opción recomendada — desde tu máquina (una sola vez):**

1. En **Railway Dashboard** → Proyecto → **Postgres** → pestaña **Variables** (o **Connect**) y copia la **DATABASE_URL** (o la connection string interna).
2. En tu terminal, desde la **raíz del monorepo**:
   ```bash
   set DATABASE_URL=<pega aquí la URL de Postgres de Railway>
   pnpm run db:deploy:prod
   ```
   (En PowerShell usa `$env:DATABASE_URL="..."`; en bash/mac `export DATABASE_URL="..."`.)
3. Verás la salida de `prisma migrate deploy` aplicando las migraciones. Cuando termine, en Railway → **Database** → pestaña **Data** (o **Tables**) deberías ver las tablas: `users`, `refresh_tokens`, `wrapped_dek`, `records`, `usage_logs`, `global_content`.

**Alternativas:**

- **Redeploy del servicio API:** Si el `railway.json` del repo tiene en **Start** algo como `cd apps/api && npx prisma migrate deploy && ... && node dist/server.js`, un **nuevo deploy** del servicio API ejecutará las migraciones al arrancar. Tras el deploy, revisa los logs y la sección Database.
- **Railway CLI:** `railway link` (elegir proyecto y servicio API), luego `railway run pnpm run db:deploy:prod` (o `railway run bash -c "cd apps/api && npx prisma migrate deploy"`).

Cuando la sección Database en Railway muestre las tablas, el endpoint de registro y el panel en https://pr4y.cl podrán operar con normalidad.

## Checklist obligatorio

1. **Variables de entorno (servicio API)** — red privada, sin egress
   - **DATABASE_URL** = `${{Postgres.DATABASE_URL}}` (referencia al servicio Postgres; Railway resuelve por red privada interna).
   - **No uses** `DATABASE_PUBLIC_URL` ni referencias a `RAILWAY_TCP_PROXY_DOMAIN` / `metro.proxy.rlwy.net`: generan tráfico por endpoint público, egress y latencia.
   - Si tienes `DATABASE_PUBLIC_URL` en Variables, elimínala. Solo debe existir `DATABASE_URL` con el valor `${{Postgres.DATABASE_URL}}`.

2. **Puerto**
   - En **Settings → Networking** del servicio, el puerto debe ser **8080** (o dejar el que Railway asigne; la app usa `process.env.PORT || 8080`).

3. **Build y Start (monorepo)**
   - El `railway.json` en la raíz ya define:
     - Build: `pnpm install --no-frozen-lockfile && pnpm --filter @pr4y/api build`
     - Start: `pnpm --filter @pr4y/api start`
   - No cambies el **Root Directory** si despliegas el monorepo desde la raíz (donde está `railway.json`).

4. **Después del deploy**
   - En **Logs** del servicio en Railway deberías ver:
     - `[PR4Y] Arranque: PORT=8080 → usando 8080, host 0.0.0.0`
     - `Conexión establecida vía red privada interna` (confirma que la API usa la URL privada de Postgres; si falta DATABASE_URL o está mal, verás error de DB).
     - `API PR4Y activa en puerto 8080 y host 0.0.0.0`
   - Si ves un error de base de datos pero **no** ves "API PR4Y activa...", la app puede estar saliendo por falta de `DATABASE_URL` al cargar el módulo de Prisma. Solución: añadir/enlazar la variable `DATABASE_URL` y volver a desplegar.

5. **Migraciones de base de datos**
   - El comando de **Start** en `railway.json` ejecuta `cd apps/api && npx prisma migrate deploy && node scripts/verify-db.mjs && node dist/server.js`, así que en cada deploy se aplican las migraciones (tablas `users`, `refresh_tokens`, etc.) antes de arrancar el servidor.
   - **Si la base está vacía o ves 500 en `/v1/auth/register` o `/v1/auth/login`:** ejecuta las migraciones de forma inmediata:
     - **Opción A (Railway Dashboard):** Servicio API → **Settings** → "Run Command" o one-off job: `cd apps/api && npx prisma migrate deploy` (mismas variables que el servicio, sobre todo `DATABASE_URL`).
     - **Opción B (Railway CLI):** `railway run --service <nombre-servicio-api> bash -c "cd apps/api && npx prisma migrate deploy"`.
     - **Opción C (desde tu máquina):** Exporta `DATABASE_URL` con la URL de Postgres de Railway y ejecuta: `cd apps/api && npx prisma migrate deploy`.
   - Tras aplicar migraciones, register/login deberían dejar de devolver 500 si el fallo era por tablas inexistentes. Revisa los logs de Railway: el error exacto (Prisma, etc.) se imprime en consola.

6. **Comprobar**
   ```bash
   curl -s https://api.pr4y.cl/v1/health
   ```
   Debe devolver JSON con `status`, `version` y `database` (p. ej. `"database":"connected"` o `"degraded"` si la DB falla).

## CORS y rate limit
- **CORS**: Se aceptan peticiones sin cabecera `Origin` (p. ej. app móvil, Postman). Orígenes permitidos: `https://pr4y.cl`, `https://www.pr4y.cl`, localhost.
- **Rate limit**: Registro tiene **5 peticiones/minuto** por IP; login **10/min**. Las cabeceras `x-ratelimit-remaining` indican cuántas quedan; si recibes **429** es que se superó el límite. Para pruebas legítimas, 5/min suele ser suficiente.

## CORS (Panel Admin SaaS)

**Confirmado:** La API permite peticiones desde **https://pr4y.cl** y **https://www.pr4y.cl** (landing y Panel Admin). Está configurado en `apps/api/src/server.ts` → `allowedOrigins`. El panel en pr4y.cl puede operar contra la API sin errores de CORS.

## Cloudflare

El backend está detrás de Cloudflare. Fastify tiene `trustProxy: true` para que las cabeceras `X-Forwarded-*` sean correctas. No hace falta configurar nada más en el código.
