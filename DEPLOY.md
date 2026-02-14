# Deploy: Vercel + Railway

## Resumen del proyecto

- **Monorepo** (pnpm): `apps/api`, `apps/web` (placeholder), `apps/mobile` (futuro), `packages/*`.
- **API** (`@pr4y/api`): Fastify, auth (magic link), sync (pull/push), crypto (wrapped DEK). Base de datos: Prisma + PostgreSQL.
- **Web**: Solo README por ahora; Vercel está configurado para `apps/web` cuando exista.
- **Mobile**: Previsto (Flutter/Kotlin), no en MVP.

---

## 1. Vercel (frontend / apps/web)

### Qué se corrigió

- **Problema**: El deploy se cancelaba porque el **Ignored Build Step** ejecutaba `echo 'No ignore'`, que sale con código **0**. En Vercel, **exit 0 = no hacer build**, por eso nunca llegaba a compilar.
- **Cambio**: En `vercel.json` se dejó `"ignoreCommand": "exit 1"` para que **siempre** se ejecute el build (exit 1 = sí hacer build).

### Si sigues viendo "The Deployment has been canceled... Ignored Build Step"

- En el repo debe estar **`"ignoreCommand": "exit 1"`** en `vercel.json` (no `echo 'No ignore'`). **Exit 1** = sí hacer build; **exit 0** = no hacer build.
- Haz **push** del commit que cambia `ignoreCommand` a `exit 1` y vuelve a desplegar.

### Si el deploy está en verde pero la URL da 404 (NOT_FOUND)

En monorepos, Vercel debe usar la carpeta de la app como **Root Directory** para que Next.js se sirva bien:

1. En el proyecto de Vercel → **Settings** → **General**.
2. **Root Directory**: pulsa **Edit**, marca **Include source files outside of the Root Directory**, y pon **`apps/web`**.
3. **Build & Development Settings**:
   - **Install Command**: `cd ../.. && pnpm install --no-frozen-lockfile`
   - **Build Command**: `cd ../.. && pnpm --filter @pr4y/web build`
   - **Output Directory**: (dejar vacío o `.next`; con Root Directory = apps/web, Next.js se detecta solo).
4. Guarda y haz un **Redeploy** del último commit.

Así la URL de producción o preview debería mostrar la página de PR4Y en lugar del 404.

---

## 2. Railway (API / @pr4y/api)

### Qué se corrigió

- **Problema**: `tsc` se quedaba sin memoria (JavaScript heap out of memory) durante el build.
- **Solución definitiva**: El build de la API ya **no usa `tsc`** para generar el bundle. Usa **esbuild** (`apps/api/scripts/build.mjs`), que consume muy poca memoria y genera `dist/server.js` en segundos.
- **Comando de build**: `pnpm install --no-frozen-lockfile && pnpm --filter @pr4y/api build` (que ejecuta `prisma generate && node scripts/build.mjs`).
- El **typecheck** con `tsc --noEmit` sigue disponible con `pnpm --filter @pr4y/api typecheck` (para CI local; no se ejecuta en el deploy).

### Healthcheck

- La API expone: `GET /v1/health` → `{ "status": "ok", "version": "1.0.0" }`.
- En Railway puedes configurar el healthcheck con path `/v1/health` si lo ofrecen en la configuración del servicio.

### Variables de entorno recomendadas en Railway

- `DATABASE_URL` (PostgreSQL; si usas Prisma en producción).
- `JWT_SECRET`.
- `CORS_ORIGIN` (origen del frontend o de la app móvil).
- La URL de la base la puedes definir también en `prisma.config.ts` / env según tu setup.

---

## 3. App móvil (Android)

- Hoy solo está el README en `apps/mobile` (Flutter/Kotlin planeado).
- La API ya tiene endpoints pensados para cliente (auth, sync, crypto); cuando tengas la app Android, solo hará falta apuntar `CORS_ORIGIN` o usar una capa API que no dependa de CORS para móvil.
- Para una **sola app Android**, lo más simple es elegir una stack (p. ej. **Kotlin + Jetpack Compose** o **Flutter**) y crear `apps/mobile` con esa stack cuando arranques el MVP.
