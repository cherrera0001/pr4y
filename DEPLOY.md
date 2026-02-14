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

### Importante

- El `buildCommand` actual es: `pnpm --filter apps/web build` y el `outputDirectory` es `apps/web/.next`.
- **`apps/web` aún no tiene `package.json` ni código** (solo README y ejemplos). Cuando hagas push, Vercel intentará el build y fallará hasta que exista una app Next.js (o lo que quieras) en `apps/web`.
- Opciones:
  1. Crear la app web en `apps/web` (p. ej. Next.js) y desplegar en Vercel.
  2. O no conectar este repo a Vercel hasta tener la web; o usar otro proyecto de Vercel que apunte solo a la carpeta/rama que corresponda.

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
