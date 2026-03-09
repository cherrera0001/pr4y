# Auditoría: Login Admin Web (pr4y.cl/admin)

**Rol:** Tech Lead frontend — seguridad y compatibilidad en el navegador  
**Ámbito:** apps/web (Vercel → pr4y.cl)  
**Objetivo:** Corregir fallo de login en https://www.pr4y.cl/admin/login y asegurar la carga del dashboard.

---

## 1. Revisión del protocolo de login y COOP

### Flujo actual

- **Login con Google (One Tap / redirect):** `login_uri` se fija en el cliente a `origin + '/api/admin/login'`, por tanto el callback de Google hace **POST directamente a `/api/admin/login`** (no a `/admin/login`).
- El **middleware** solo reescribe **POST `/admin/login` → `/api/admin/login`** por si en Google Cloud Console el callback estuviera configurado como `https://www.pr4y.cl/admin/login`. Con `login_uri` en `/api/admin/login`, ese rewrite no se usa en el flujo actual.

### COOP (Cross-Origin-Opener-Policy)

- **next.config.js:** cabecera `Cross-Origin-Opener-Policy: same-origin-allow-popups` para `/admin/login` y `/admin/:path*`. Correcto para que el `postMessage` de Google Identity Services no quede bloqueado.
- **middleware.ts:** se aplica la misma cabecera en respuestas para `/admin/login`, `/admin/gate` y en las rutas bajo `/admin` tras validar token. El middleware **no** aplica cabeceras a rutas bajo `/api/*` (el matcher incluye todo salvo estáticos, pero la lógica solo modifica respuestas cuando `pathname.startsWith('/admin')`).
- **apps/web/app/api/admin/login/route.ts:** en redirecciones (GET y redirect tras POST) se setea `COOP: same-origin-allow-popups`.

**Conclusión:** COOP está bien configurado; no hay evidencia de que Vercel o el middleware lo sobrescriban. La página de login y las redirecciones del login llevan la cabecera adecuada.

---

## 2. Error 405 Method Not Allowed

### Rutas auditadas

| Ruta | GET | POST | DELETE | Notas |
|------|-----|------|--------|--------|
| `/api/admin/login` | ✅ Redirige a /admin/login | ✅ Procesa credential | — | No debería devolver 405 en uso normal. |
| `/api/admin/session` | ❌ no definido | ✅ Crea sesión (cookie) | ✅ Cierra sesión | GET no implementado → **GET /api/admin/session devolvería 405**. El front solo usa DELETE. |
| `/api/admin/stats` | ✅ | — | — | Correcto. |
| `/api/admin/stats/detail` | ✅ | — | — | Correcto. |

### Posibles causas del 405 que ves

1. **GET a `/api/admin/session`:** si algo (navegador, extensión o código) hace GET a esta ruta, Next devuelve 405. El layout y LogoutButton solo usan DELETE; no hay GET en el diseño actual.
2. **POST a una ruta que solo tiene GET:** por ejemplo, un proxy o caché que convierte el POST de Google en GET, o un deployment antiguo donde la ruta de login solo tenía GET.
3. **Callback de Google apuntando a `/admin/login`:** entonces el navegador hace POST a `/admin/login`. El middleware reescribe a `/api/admin/login`. Si por algún motivo el rewrite no se aplica en el edge (p. ej. configuración de Vercel), la petición llegaría como POST a la **página** `/admin/login`, que en App Router es un documento (GET); eso podría terminar en 405.

**Recomendación:** Confirmar en Google Cloud Console que la URI de redirección/callback para la web sea exactamente `https://www.pr4y.cl/api/admin/login`. Si está como `https://www.pr4y.cl/admin/login`, el rewrite del middleware debe aplicar; verificar en Vercel que el middleware se ejecuta en el edge y que el rewrite está activo.

---

## 3. Error "Error temporal del servidor" (error=server)

La URL `?error=server` se devuelve desde el **catch** del handler POST en `apps/web/app/api/admin/login/route.ts` (línea 108–110). Es decir, **alguna excepción no controlada** en ese handler (p. ej. al leer body, al hacer `fetch` a la API o al construir la redirección).

Causas probables:

1. **NEXT_PUBLIC_API_URL vacío en Vercel:**  
   Antes del `fetch` se comprueba `apiBase` y se redirige con `error=config`. Si `apiBase` está definido pero la URL es incorrecta (sin `/v1`, o dominio erróneo), el `fetch` a Railway puede fallar (red, timeout, 4xx/5xx) y al manejar la respuesta podría lanzarse una excepción, o el `fetch` mismo puede lanzar (red).
2. **API en Railway no alcanzable desde Vercel:** timeout, DNS, firewall, o la API devuelve error y el manejo de la respuesta lanza.
3. **Base URL sin prefijo `/v1`:** la API en Railway expone `/v1/auth/google` y `/v1/auth/me`. Si `NEXT_PUBLIC_API_URL` es `https://xxx.railway.app` (sin `/v1`), las llamadas serían a `/auth/google` y `/auth/me`, que en el servidor Fastify no existen (las rutas están bajo el prefijo `/v1`), lo que produciría 404 y posiblemente excepciones al parsear la respuesta.

**Acción:** Asegurar en Vercel que `NEXT_PUBLIC_API_URL` (o `NEXT_PUBLIC_API_BASE_URL`) sea la base **incluyendo** `/v1`, por ejemplo:  
`https://pr4yapi-production.up.railway.app/v1`

---

## 4. Disponibilidad de la API para métricas (dashboard)

- **Frontend:** `apps/web/app/admin/(panel)/page.tsx` llama a:
  - `GET /api/admin/stats?days=...`
  - `GET /api/admin/stats/detail?days=...`
  con `credentials: 'same-origin'`, por lo que la cookie `pr4y_admin_token` se envía.
- **API routes:**  
  - `apps/web/app/api/admin/stats/route.ts` y `apps/web/app/api/admin/stats/detail/route.ts` leen la cookie `pr4y_admin_token` y hacen `fetch` a `${apiBase}/admin/stats` y `${apiBase}/admin/stats/detail` con `Authorization: Bearer <token>`.
- **Backend (Railway):** Rutas registradas como `/v1/admin/stats` y `/v1/admin/stats/detail` en `apps/api/src/routes/admin.ts`.

**Conclusión:** El flujo de métricas está bien planteado: el front usa la cookie y las rutas API reenvían el token a Railway. Si `getApiBaseUrl()` devuelve la base con `/v1`, las peticiones llegan correctamente a GET /v1/admin/stats y GET /v1/admin/stats/detail.

---

## 5. Archivos auditados — resumen

| Archivo | Estado | Notas |
|---------|--------|--------|
| **apps/web/middleware.ts** | OK | Rewrite POST /admin/login → /api/admin/login correcto. COOP en rutas /admin. validateAdminToken usa `${apiBase}/auth/me` (apiBase debe incluir /v1). |
| **apps/web/app/api/admin/login/route.ts** | Ajustar | GET y POST correctos. Mejorar el catch para no devolver siempre `error=server`: log y, en desarrollo, redirigir con mensaje útil. |
| **apps/web/app/admin/(panel)/page.tsx** | OK | Llama a /api/admin/stats y /api/admin/stats/detail con credentials; maneja 401 redirigiendo a /admin/login. |
| **apps/web/lib/env.ts** | OK | Sin hardcoding; usa NEXT_PUBLIC_API_URL o NEXT_PUBLIC_API_BASE_URL. Documentar que la base debe incluir `/v1`. |

---

## 6. Checklist de verificación en Vercel

- [ ] **NEXT_PUBLIC_API_URL** (o NEXT_PUBLIC_API_BASE_URL) definida y con valor terminado en `/v1`, p. ej. `https://pr4yapi-production.up.railway.app/v1`.
- [ ] **NEXT_PUBLIC_GOOGLE_WEB_CLIENT_ID** definida (cliente OAuth Web).
- [ ] Redeploy tras cambiar variables (las NEXT_PUBLIC_* se inyectan en build).
- [ ] En Google Cloud Console, URI de redirección/callback para la web = `https://www.pr4y.cl/api/admin/login` (o, si usas callback en /admin/login, que el middleware esté activo en el edge).

## 7. Checklist en Railway (API)

- [ ] **CORS_ORIGINS** incluye exactamente `https://pr4y.cl` y `https://www.pr4y.cl` (separados por coma). Ejemplo: `https://pr4y.cl,https://www.pr4y.cl`. Sin esto, peticiones con `Origin` desde el sitio pueden ser rechazadas por CORS (el panel admin llama a la API desde el servidor Next, pero otros flujos pueden requerirlo).
- [ ] **DATABASE_URL** correcta y migraciones aplicadas (`npx prisma migrate deploy`) para que `usage.ts` y el panel de métricas puedan leer de PostgreSQL.

---

## 8. Cambios realizados en código (resumen)

- **apps/web/middleware.ts:** Al reescribir POST `/admin/login` → `/api/admin/login` se añade la cabecera `Cross-Origin-Opener-Policy: same-origin-allow-popups` en la respuesta del rewrite para evitar bloqueos de COOP.
- **apps/web/app/api/admin/login/route.ts:** `export const dynamic = 'force-dynamic'` para evitar caché en edge; `cache: 'no-store'` en los `fetch` a `/auth/google` y `/auth/me`; en el `catch` se registra el error y en desarrollo se redirige con código más específico.
- **apps/web/lib/env.ts:** Comentario que indica que la URL de la API debe incluir el prefijo `/v1`.
- **apps/api/src/routes/admin.ts:** GET `/admin/stats/detail` pasa a usar `usageService.getStatsDetail()` y devuelve `lastSyncActivity` y `recordsByTypeByDay`, alineado con lo que consume el dashboard web.
- **apps/api/.env.example:** CORS_ORIGINS documentado con ejemplo que incluye `https://pr4y.cl` y `https://www.pr4y.cl`.
