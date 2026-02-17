# Un solo prompt: mejorar login front web (COOP + 405)

Copia y pega el bloque siguiente cuando pidas a la IA que corrija el login del **front web** (pr4y.cl/admin/login).

---

## Prompt (rol + precisión)

```
Rol: Eres un Tech Lead frontend con foco en seguridad y compatibilidad en el navegador. Tu ámbito es solo el front web (Next.js en apps/web, desplegado en Vercel como pr4y.cl). No toques la API en Railway salvo que la causa del error sea CORS y esté documentado.

Contexto: La página de login admin está en apps/web/app/admin/login/page.tsx. Usa Google Identity Services (GIS): script accounts.google.com/gsi/client, initialize + renderButton, callback con credential; luego POST a la API (auth/google) y POST a /api/admin/session para fijar cookie. La API está en Railway; CORS se controla con CORS_ORIGINS (debe incluir https://pr4y.cl y https://www.pr4y.cl).

Errores exactos en consola (en /admin/login):
1. "Cross-Origin-Opener-Policy policy would block the window.postMessage call." (client:347, script de Google GIS, aparece dos veces)
2. "Failed to load resource: the server responded with a status of 405 ()" (login:1 — atribuido al documento de la página)

Precisión requerida:
- Error 1 (COOP): La página servida por Next.js/Vercel no debe enviar Cross-Origin-Opener-Policy: same-origin en /admin/login (o debe usar same-origin-allow-popups). Revisar next.config.js headers() y, si Vercel añade COOP por defecto, middleware o headers específicos para esa ruta. El objetivo es que el postMessage del iframe/popup de Google no sea bloqueado.
- Error 2 (405): En DevTools → Network, identificar la petición que devuelve 405 (URL exacta y método). Causas típicas: (a) OPTIONS a la API sin CORS bien configurado en Railway; (b) GET a /api/admin/session (esa ruta solo tiene POST y DELETE en apps/web/app/api/admin/session/route.ts — si algo hace GET, quitar o cambiar en el cliente). Corregir solo esa petición (cliente o CORS en API según corresponda).

Entregables: (1) Cambio mínimo para que COOP permita postMessage de Google en /admin/login. (2) Identificación de la petición 405 y corrección mínima. (3) No refactorizar el flujo de login; no inventar variables de entorno ni secretos en el repo.
```

---

## Uso

1. Abre la conversación con la IA (Cursor, Copilot, etc.).
2. Pega el contenido entre las líneas de código (todo el bloque desde "Rol:" hasta el último punto).
3. Si ya aplicaste cabecera COOP en next.config.js y el error persiste, indica en el prompt: "Ya tenemos Cross-Origin-Opener-Policy: same-origin-allow-popups para /admin/:path* en next.config.js; comprobar si Vercel o el middleware la sobrescriben."

---

## Referencia rápida

- **Doc detallado (análisis quirúrgico):** `docs/PROMPT-FIX-ADMIN-LOGIN-WEB.md`
- **Cabeceras Next:** `apps/web/next.config.js` → `headers()`
- **Login página:** `apps/web/app/admin/login/page.tsx`
- **Session API:** `apps/web/app/api/admin/session/route.ts` (POST, DELETE; no GET)
- **Variables web:** `NEXT_PUBLIC_API_URL`, `NEXT_PUBLIC_GOOGLE_WEB_CLIENT_ID` (VERCEL.md)
