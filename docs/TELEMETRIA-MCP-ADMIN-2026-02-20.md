# Telemetría MCP – Admin login (navegador embebido)

**Fecha:** 2026-02-20  
**Herramienta:** cursor-ide-browser (MCP).

## Flujo ejecutado

1. `browser_navigate` → https://pr4y.cl/admin/login
2. `browser_lock` → bloqueo para telemetría
3. `browser_snapshot` (interactive, compact)
4. `browser_network_requests` – peticiones desde carga
5. `browser_console_messages` – mensajes de consola
6. `browser_take_screenshot` → **admin-login-telemetry-inicio.png**
7. `browser_wait_for` 3 s
8. `browser_snapshot` + **admin-login-telemetry-despues.png**
9. `browser_network_requests` y `browser_console_messages` de nuevo

## Estado capturado

| Momento | URL | Notas |
|--------|-----|--------|
| Inicio | https://www.pr4y.cl/admin/login | Página Admin PR4Y, botón Google (crherrera@c4a.cl) |
| +3 s  | https://www.pr4y.cl/admin/login | Sin cambio (sin click en login aún) |

## Capturas

- **admin-login-telemetry-inicio.png** – Estado inicial (formulario login)
- **admin-login-telemetry-despues.png** – Estado tras 3 s (misma pantalla)

Ruta típica: `%LOCALAPPDATA%\Temp\cursor\screenshots\` (o la que indique el cliente MCP).

## Red y consola (MCP)

`browser_network_requests` y `browser_console_messages` se invocaron correctamente; el contenido (lista de peticiones y logs) lo devuelve el MCP en un formato que este cliente no muestra aquí. Para inspeccionar red/consola en tu prueba:

- Abre DevTools (F12) en la pestaña del navegador embebido y revisa **Network** y **Console** mientras haces el login, o
- Usa un cliente que exponga el payload completo de esas herramientas MCP.

## Cómo probar el cambio (allowlist)

1. En el navegador embebido, haz click en **Acceder como Cristóbal Ramón** (crherrera@c4a.cl).
2. Completa el flujo de Google si hace falta.
3. Resultado esperado si en BD ya tienen `super_admin`: redirect a **pr4y.cl/admin** (dashboard).
4. Si en BD aún no tienen rol: redirect a **pr4y.cl/admin/login?error=admin_required**.

Si quieres otra pasada de telemetría MCP después de tu prueba, pídela y se repite snapshot + network + console + screenshot.

---

## Segunda captura (con flujo completado por usuario)

**Pasos:** Navegar a /admin/login → lock → snapshot + network + console + **mcp-admin-1-inicio.png** → unlock → espera 15 s (usuario completa flujo) → lock → snapshot + **mcp-admin-2-despues-flujo.png** + network + console.

**URL tras espera:** `https://www.pr4y.cl/admin/login` (sin cambio; si el flujo llevó a Google y volvió con error, la URL podría ser `...?error=admin_required` en otro momento).

**Causa de que no entre:** Ver **docs/CAUSA-ADMIN-NO-ENTRA.md**: el backend/API existen; el rechazo es por `role = 'user'` en la BD. Ejecutar el SQL en Railway para dar `super_admin` a los dos correos.
