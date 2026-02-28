# Telemetría: login admin PR4Y (MCP Browser)

**Fecha:** 2026-02-20  
**Herramienta:** Navegador embebido (cursor-ide-browser MCP).

## Flujo ejecutado

1. Navegación a `https://pr4y.cl/admin/login`.
2. Página mostró "Admin PR4Y" con botón de inicio con Google.
3. Al tener sesión de Google abierta, se mostró "Elige una cuenta" con **crherrera@c4a.cl**.
4. Tras elegir esa cuenta, redirección de vuelta a la app.
5. **URL final:** `https://www.pr4y.cl/admin/login?error=admin_required`.

## Resultado capturado

| Dato | Valor |
|------|--------|
| URL final | `https://www.pr4y.cl/admin/login?error=admin_required` |
| Cuenta usada | crherrera@c4a.cl (Cristóbal Ramón Herrera Jara) |
| Código de error en query | `admin_required` |

## Conclusión

El backend (Next.js `POST /api/admin/login`) recibe correctamente el token de Google, llama a la API en Railway (`/v1/auth/google` y `/v1/auth/me` con 200), pero **rechaza el acceso porque el usuario no tiene rol `admin` ni `super_admin`** en la base de datos. El redirect con `error=admin_required` se hace en `apps/web/app/api/admin/login/route.ts` (líneas 119-121).

**Acción requerida:** Ejecutar en la BD de Railway (Postgres):

```sql
UPDATE users SET role = 'super_admin' WHERE LOWER(email) = 'crherrera@c4a.cl';
UPDATE users SET role = 'super_admin' WHERE LOWER(email) = 'herrera.jara.cristobal@gmail.com';
```

## Capturas (MCP)

- Estado en Google: "Elige una cuenta" → `admin-login-before.png` (temp).
- Estado final en pr4y.cl: login con error → `admin-login-after-error.png` (temp).

## Telemetría detallada (red / consola)

Las herramientas MCP `browser_network_requests` y `browser_console_messages` devuelven datos en un formato que no se pudo inspeccionar en esta sesión. Para capturar red y consola en futuras pruebas:

1. **Manual:** Abrir pr4y.cl/admin/login en Chrome/Edge → F12 → pestañas **Network** y **Console**. Iniciar sesión con Google; revisar las peticiones a `/api/admin/login`, `auth/google`, `auth/me` y el redirect final (status 302 y `Location` con `?error=admin_required` o `/admin`).
2. **MCP:** Repetir el flujo con el navegador embebido; si el cliente expone el payload de `browser_network_requests` / `browser_console_messages`, guardarlo en un archivo para análisis.
