# Prompt + rol: corrección quirúrgica del login admin web (pr4y.cl)

## Rol que debes asumir

Eres un **Tech Lead frontend con foco en seguridad y compatibilidad navegador**. Tu tarea es analizar solo lo necesario y aplicar los cambios mínimos que corrijan los errores de consola en **https://www.pr4y.cl/admin/login** sin romper el resto de la app. No refactorices por refactorizar; cada cambio debe tener causa-efecto clara.

---

## Contexto técnico

- **Frontend:** Next.js 14 (App Router) en **apps/web**, desplegado en **Vercel** como **pr4y.cl** (y www.pr4y.cl).
- **Login admin:** Página **apps/web/app/admin/login/page.tsx**. Usa **Google Identity Services (GIS)**: script `https://accounts.google.com/gsi/client`, `window.google.accounts.id.initialize` + `renderButton`, callback con `response.credential` (idToken). Luego hace:
  1. `POST` a `${apiBase}/auth/google` (API en Railway) con `{ idToken }`.
  2. Si OK, `POST` a `/api/admin/session` (ruta Next.js en el mismo origen) con `{ token }` para fijar cookie y redirigir a `/admin`.
- **API:** Fastify en Railway, base URL `https://pr4yapi-production.up.railway.app/v1`. CORS configurado por `CORS_ORIGINS` (variable de entorno); solo los orígenes listados son aceptados. La ruta `POST /auth/google` existe y espera `{ idToken }`.
- **Variables de entorno críticas:**
  - **Vercel (web):** `NEXT_PUBLIC_API_URL` (o `NEXT_PUBLIC_API_BASE_URL`) = URL de la API con `/v1`; `NEXT_PUBLIC_GOOGLE_WEB_CLIENT_ID` = cliente OAuth **Web** de Google. Sin ellas el botón de Google no se muestra o falla por configuración.
  - **Railway (API):** `CORS_ORIGINS` debe incluir **https://pr4y.cl** y **https://www.pr4y.cl** para que el navegador permita las peticiones desde el admin. Si falta el origen, CORS falla (a veces reportado como error de red o método).

---

## Errores a corregir (consola del navegador en /admin/login)

1. **Cross-Origin-Opener-Policy policy would block the window.postMessage call.**  
   Aparece dos veces, asociado a `client:347` (script de Google GIS).
2. **Failed to load resource: the server responded with a status of 405 ()**  
   Referencia `login:1` (documento de la página de login).

---

## Análisis quirúrgico requerido

### Error 1: COOP y postMessage

- **Causa:** El script de Google GIS (cuenta/iframe) usa `postMessage` para comunicar el resultado del login al documento que abrió el flujo. Si la **página** que sirve Vercel (o un recurso que ella carga) envía la cabecera **Cross-Origin-Opener-Policy: same-origin**, el navegador bloquea ese `postMessage` desde el origen de Google hacia la ventana de pr4y.cl.
- **Comprobación:** En DevTools → pestaña **Network** → recargar /admin/login → inspeccionar la respuesta del **documento** (la URL que sea pr4y.cl/admin/login). Revisar si en **Response Headers** aparece `Cross-Origin-Opener-Policy`. Si es `same-origin`, confirma la causa.
- **Corrección:** Para la ruta del login admin (o solo para `/admin/login`) hay que **evitar** que se envíe `Cross-Origin-Opener-Policy: same-origin`, o usar un valor compatible con popups/iframes de Google. Opciones válidas:
  - **Cross-Origin-Opener-Policy: same-origin-allow-popups**  
    Permite que ventanas popup (o el contexto de Google) sigan pudiendo comunicarse por `postMessage` con la ventana que las abrió.
  - O **no enviar** COOP en esa página (dejar que el navegador use su valor por defecto).
- **Dónde aplicar:** En Next.js, esto se hace en **cabeceras de respuesta**. Opciones:
  - **next.config.js:** en `headers()` (async headers), devolver para `source: '/admin/login'` (o `/admin/:path*` si quieres cubrir todo el admin) la cabecera `Cross-Origin-Opener-Policy: 'same-origin-allow-popups'`.
  - O en **middleware.ts**: para la request que corresponda a `/admin/login`, clonar la respuesta, añadir esa cabecera y devolverla.  
  No cambies COOP en la API de Railway solo para arreglar el login **web**; el problema es la página servida por Vercel/Next.js.

### Error 2: 405 en login:1

- **Causa:** Algo que se dispara desde la página de login (o desde un script que ella carga) hace una petición HTTP y recibe **405 Method Not Allowed**. El `login:1` indica que el error se atribuye al documento (primera línea del documento).
- **Comprobación obligatoria:** En DevTools → **Network** → recargar /admin/login y reproducir el flujo (clic en “Acceder como Cristóbal” o en el botón de Google). Filtrar por estado **405** o revisar en rojo la petición fallida. Anotar:
  - **URL exacta** que devuelve 405.
  - **Método** (GET, POST, OPTIONS, etc.).
- **Causas típicas y correcciones:**
  - **Petición OPTIONS (preflight)** a la API Railway (`.../v1/auth/google`). Si la API no responde 200 a OPTIONS, el navegador puede mostrar fallo. Fastify con `@fastify/cors` suele responder OPTIONS; verificar que CORS esté registrado y que **CORS_ORIGINS** en Railway incluya `https://pr4y.cl` y `https://www.pr4y.cl`.
  - **GET a una ruta que solo tiene POST.** Por ejemplo, si algo hace GET a `/api/admin/session`: la ruta en **apps/web/app/api/admin/session/route.ts** solo exporta `POST` y `DELETE`. En Next.js App Router, un GET a esa ruta devuelve 405. Solución: no hacer GET a esa URL desde el cliente; si hay un enlace o prefetch, quitarlo o cambiar a POST. No añadas un GET a `/api/admin/session` solo para devolver 200; arregla el cliente para que no envíe GET.
  - **Otra URL que devuelva 405:** una vez identificada en Network, aplicar la corrección correspondiente (método correcto en cliente o soporte del método en servidor).

---

## Checklist de secretos y configuración (sin inventar valores)

- **Vercel (proyecto de la web):**  
  - `NEXT_PUBLIC_API_URL` = `https://pr4yapi-production.up.railway.app/v1` (o la URL real de la API, sin barra final o con `/v1` según lo que espere el código).  
  - `NEXT_PUBLIC_GOOGLE_WEB_CLIENT_ID` = cliente OAuth 2.0 **Web** de Google (no el de Android).  
  Documentar en el prompt o en VERCEL.md que, si el botón no aparece o hay “Falta configuración”, revisar estas variables y hacer **Redeploy** tras cambiarlas.
- **Railway (API):**  
  - `CORS_ORIGINS` debe incluir los orígenes desde los que se abre el admin: `https://pr4y.cl` y `https://www.pr4y.cl`. Sin ellos, las peticiones desde el navegador a la API pueden fallar (CORS o método).

No escribas secretos reales en el repo; solo indica nombres de variables y qué deben contener.

---

## Entregables

1. **Cambio mínimo en Next.js** para que la página de login admin no bloquee el `postMessage` de Google (cabecera COOP en next.config.js o en middleware).
2. **Identificación exacta** de la petición que devuelve 405 (URL + método) y **cambio mínimo** para eliminarla (cliente o servidor, según corresponda).
3. **Actualización breve** de **VERCEL.md** (o del doc que use el equipo) con: (a) lista de variables de entorno necesarias para el login admin; (b) que CORS_ORIGINS en Railway debe incluir pr4y.cl y www.pr4y.cl.
4. **No** modificar la lógica de negocio del login (callback de Google, validación de admin, cookie) salvo que sea estrictamente necesario para corregir el 405 o el COOP.

---

## Resumen en una frase

Corrige el COOP en la respuesta de /admin/login para que el postMessage de Google funcione, y localiza y corrige la petición que devuelve 405 desde esa página, verificando al mismo tiempo que los secretos y CORS estén bien documentados y configurados para la versión web.
