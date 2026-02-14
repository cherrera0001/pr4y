# @pr4y/api

Backend API de PR4Y (Fastify + TypeScript)

## Scripts
- `pnpm dev` — desarrollo con recarga
- `pnpm build` — compilar a dist/
- `pnpm start` — ejecutar build

## Endpoints principales
- GET /v1/health — healthcheck
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

**Diferenciación OAuth2:** En Railway pueden existir `GOOGLE_WEB_CLIENT_ID` y `GOOGLE_ANDROID_CLIENT_ID`. La API **solo** usa `GOOGLE_WEB_CLIENT_ID` (y opcionalmente `GOOGLE_WEB_CLIENT_SECRET`) para validar tokens. El backend **nunca** debe aceptar `id_token` cuyo audience sea el Android Client ID; `verifyIdToken` exige audience = `GOOGLE_WEB_CLIENT_ID`. La app Android debe obtener el token con `serverClientId` = Web Client ID para que el audience sea el correcto.

- **GOOGLE_WEB_CLIENT_ID**: obligatorio al arranque; único audience aceptado en `/v1/auth/google`.
- **GOOGLE_WEB_CLIENT_SECRET**: opcional (flujos server-side); no se usa en verifyIdToken.
- **GOOGLE_ANDROID_CLIENT_ID**: no lo lee la API; solo lo usa la app Android (package + SHA-1 en Google Cloud).
- **CORS_ORIGINS**: orígenes permitidos separados por comas (solo URLs, p. ej. `https://pr4y.cl`, `http://localhost:3000`). Las peticiones **sin** cabecera `Origin` (p. ej. app Android con OkHttp/Retrofit) se aceptan siempre; no hace falta añadir el package name (`com.pr4y.app.dev`) aquí — CORS aplica a orígenes web, no a identificadores de app.
- **Migración en Railway:** ejecutar `npx prisma migrate deploy` para crear tablas.

## Logs en Railway (Google OAuth)

Si la app Android (incl. variante `com.pr4y.app.dev`) envía un `id_token` rechazado por Google, en los logs de Railway aparecerá: `Google id_token verification failed` con el campo `verifyError` (p. ej. wrong audience, token expired). Revisar que en Google Cloud Console el **Web Client ID** coincida con `GOOGLE_WEB_CLIENT_ID` y que la app Android use ese mismo ID como `serverClientId` al obtener el token.