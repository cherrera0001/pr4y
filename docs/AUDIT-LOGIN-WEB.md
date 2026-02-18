# Auditoría login admin web (telemetría y protocolo)

## Revisión full (COOP + 405)

### Popup bloqueado / postMessage null

Si en consola aparece **"Failed to open popup window"** o **"Cannot read properties of null (reading 'postMessage')"**, el script de Google está intentando usar **popup** y el navegador (p. ej. Cursor embebido) lo bloquea. El login admin usa **solo redirect** vía **API HTML de GSI**: `data-ux_mode="redirect"` y `data-login_uri` en el DOM (`g_id_onload`), sin callback ni popup. Prueba en **Chrome o Edge (ventana normal)**; el navegador embebido puede bloquear ventanas emergentes.

- **405 en POST /admin/login:** Lo dispara el script de Google (`client:349`), no nuestro código. Nosotros usamos `login_uri: /api/admin/login`. Para que ese POST no devuelva 405, el **middleware** reescribe `POST /admin/login` → `POST /api/admin/login` (rewrite interno); así el handler de la API responde siempre (200 sin credential o 302 con cookie).
- **COOP (postMessage):** Cabecera `Cross-Origin-Opener-Policy: same-origin-allow-popups` en middleware y en `next.config.js` (incl. `source: '/admin/login'`). Si el aviso sigue, en Vercel → Settings → Security/Headers quitar o ajustar cualquier COOP a nivel de proyecto.

Uso: navegador embebido (Cursor) o Chrome/Edge con DevTools. Verificar tras cada cambio en login (redirect URI, COOP, POST handler).

## 1. Precondiciones

- [ ] Deploy en Vercel con el último commit (login_uri → `/api/admin/login`).
- [ ] Google Cloud Console: **Authorized redirect URIs** incluyen `https://www.pr4y.cl/api/admin/login` y `https://pr4y.cl/api/admin/login`.
- [ ] Variables en Vercel: `NEXT_PUBLIC_API_URL`, `NEXT_PUBLIC_GOOGLE_WEB_CLIENT_ID`.

## 2. Navegador embebido (MCP)

1. **Abrir:** `https://www.pr4y.cl/admin/login`
2. **Snapshot:** revisar que la página muestra el botón "Iniciar sesión con Google" (o similar).
3. **Al hacer clic:** debe producirse un **redirect** (misma pestaña) a `accounts.google.com`, no un popup. La URL de vuelta debe ser `https://www.pr4y.cl/api/admin/login` (POST con credential).

## 3. Telemetría en DevTools (Chrome/Edge)

### Pestaña Network

- **Filtrar:** Fetch/XHR o All.
- **Recargar** `/admin/login` y **reproducir** el flujo (clic en Google).

Comprobar:

| Comportamiento | Esperado |
|----------------|----------|
| POST a `/admin/login` | **No** debe aparecer (o si aparece, status **200**, no 405). |
| POST a `/api/admin/login` | Debe aparecer **después** de volver de Google; status **302** (redirect a `/admin`) o 200. |
| GET documento `admin/login` | Status 200; **Response Headers** → `Cross-Origin-Opener-Policy: same-origin-allow-popups` (opcional; reduce avisos COOP). |

### Pestaña Console

- **Antes del cambio:** podían verse `Cross-Origin-Opener-Policy policy would block the window.postMessage call` y `POST .../admin/login 405`.
- **Después del cambio:** el 405 debe desaparecer (el POST va a `/api/admin/login`). Los avisos COOP pueden seguir si el script de Google usa postMessage; no bloquean el flujo en modo redirect.

## 4. Flujo completo (checklist)

1. Ir a `https://www.pr4y.cl/admin/login`.
2. Clic en el botón de Google.
3. Redirige a Google; elegir cuenta y consentir.
4. Google redirige a `https://www.pr4y.cl/api/admin/login` (POST con `credential`).
5. El servidor responde **302** a `https://www.pr4y.cl/admin` con `Set-Cookie: pr4y_admin_token=...`.
6. El navegador carga `/admin` con la cookie; el usuario ve el panel.

Si en el paso 4 Google muestra "Redirect URI mismatch", añadir las URIs de la sección 1 en la consola de Google.

## 5. Resumen de cambios auditados

- **login_uri:** `${origin}/api/admin/login` (no `/admin/login`).
- **Handler POST:** `app/api/admin/login/route.ts` (API route; no compite con la página).
- **Documentación:** VERCEL.md con URIs de redirect para Google.
