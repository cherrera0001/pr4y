# @pr4y/api

Backend API de PR4Y (Fastify + TypeScript)

## Scripts
- `pnpm dev` — desarrollo con recarga
- `pnpm build` — compilar a dist/
- `pnpm start` — ejecutar build

## Endpoints principales
- GET /v1/health — healthcheck
- GET /v1/config — configuración pública: devuelve **los dos** Client IDs (`googleWebClientId`, `googleAndroidClientId`) desde las variables de Railway. La app Android prefiere `googleAndroidClientId` como serverClientId; si no está en BuildConfig ni en esta respuesta, puede usar `googleWebClientId` como fallback. Sin autenticación.
- **POST /v1/auth/google** — login/registro con Google OAuth. Body: `{ "idToken": "..." }`. Devuelve `accessToken`, `refreshToken`, `user`. Si el usuario no existe, se crea con rol `user`.
- POST /v1/auth/refresh — renovar access token con refresh token
- GET /v1/auth/me — perfil del usuario (requiere JWT)
- POST /v1/auth/logout — revocar refresh token
- POST /v1/auth/register y POST /v1/auth/login — **deshabilitados (410 Gone)**; usar Google.

## Configuración
- Variables de entorno en `.env` (ver `.env.example`)
- CORS restringido a origen web

## Hashing de contraseñas
- Se usa **argon2** (argon2id), no bcrypt. Es compatible con el entorno de Railway (sin dependencias nativas problemáticas). Si en el futuro hubiera fallos de compilación nativa en otro entorno, se podría valorar cambiar a `bcryptjs` (JS puro).

## Railway + Cloudflare
- El servidor escucha en `PORT` (por defecto **8080**) y `host: '0.0.0.0'` para recibir tráfico de Railway.
- `trustProxy: true` en Fastify para que Cloudflare (X-Forwarded-*) funcione correctamente.
- En Railway → Variables: **DATABASE_URL** = `${{Postgres.DATABASE_URL}}` (referencia dinámica al servicio Postgres, no URL estática).
- GET **/v1/health** devuelve JSON (`status`, `version`, `database`) para validar que el 404 desapareció y la DB está conectada.

## Google OAuth y CORS (solo desde env)

**Consumo separado OAuth2:** Hay dos Client IDs. Cada cliente usa el suyo y el backend acepta ambos.

- **GOOGLE_WEB_CLIENT_ID**: para la **versión web**. La web (Vercel) usa `NEXT_PUBLIC_GOOGLE_WEB_CLIENT_ID` con este valor. Tokens con audience = Web se aceptan en `/v1/auth/google`.
- **GOOGLE_ANDROID_CLIENT_ID**: para la **app Android**. La app usa este valor como `serverClientId` en login. Tokens con audience = Android se aceptan en `/v1/auth/google`.
- Al menos uno de los dos debe estar definido en Railway. Si solo tienes web, define Web; si solo app Android, define Android; si tienes ambos, define ambos.
- **GOOGLE_WEB_CLIENT_SECRET**: opcional (flujos server-side); no se usa en verifyIdToken.
- **CORS_ORIGINS**: orígenes permitidos separados por comas (solo URLs, p. ej. `https://pr4y.cl`, `http://localhost:3000`). Las peticiones **sin** cabecera `Origin` (p. ej. app Android con OkHttp/Retrofit) se aceptan siempre; no hace falta añadir el package name (`com.pr4y.app.dev`) aquí — CORS aplica a orígenes web, no a identificadores de app.
- **Migración en Railway:** ejecutar `npx prisma migrate deploy` para crear tablas.

## Logs en Railway (Google OAuth)

Si un `id_token` es rechazado, en los logs de Railway aparecerá `Google id_token verification failed` con `verifyError`. Revisar: la web debe usar el cliente Web; la app Android debe usar el cliente Android (`GOOGLE_ANDROID_CLIENT_ID`) como `serverClientId` y el backend debe tener ese mismo valor en Railway para aceptar el audience Android.