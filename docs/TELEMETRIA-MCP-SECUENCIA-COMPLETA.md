# Telemetría MCP – Secuencia con pausas (request/response capturados)

**Fecha:** 2026-02-20  
**Flujo:** Navegación → pausas 2–3 s entre cada captura → snapshot, network, console, screenshot en cada fase.

## Secuencia ejecutada

| Paso | Acción | Pausa después |
|------|--------|----------------|
| 1 | `browser_navigate` → https://pr4y.cl/admin/login | 3 s |
| 2 | `browser_snapshot` | — |
| 3 | `browser_network_requests` | 2 s |
| 4 | `browser_console_messages` | 2 s |
| 5 | `browser_take_screenshot` → **mcp-paso1-google-consent.png** | 2 s |
| 6 | Espera 4 s | — |
| 7 | `browser_snapshot` (URL: Google OAuth consent) | 2 s |
| 8 | `browser_network_requests` | 2 s |
| 9 | `browser_console_messages` | 2 s |
| 10 | `browser_take_screenshot` → **mcp-paso2-consent.png** | — |
| 11 | Espera 8 s (tiempo para completar consent y redirect) | — |
| 12 | `browser_snapshot` | — |
| 13 | Espera 2 s, `browser_network_requests` | 2 s |
| 14 | `browser_console_messages` | 2 s |
| 15 | `browser_take_screenshot` → **mcp-paso3-final-admin-required.png** | — |

## URLs capturadas (snapshots)

| Momento | URL |
|---------|-----|
| Tras navegar + 3 s | https://www.pr4y.cl/admin/login |
| Tras paso 5 + 4 s | https://accounts.google.com/signin/oauth/legacy/consent?authuser=0&part=...&flowName=GeneralOAuthFlow&client_id=583962207001-... |
| Tras paso 10 + 8 s | **https://www.pr4y.cl/admin/login?error=admin_required** |

## Conclusión

1. **pr4y.cl/admin/login** → carga correcta del formulario.
2. **Google OAuth consent** → el flujo redirige a Google (consent); se capturó en medio del consent.
3. **Vuelta a pr4y.cl** → redirect final a **/admin/login?error=admin_required**, es decir el backend (Next.js + API) rechazó el acceso por rol.

**Causa:** El usuario (crherrera@c4a.cl) en la base de datos tiene `role = 'user'`. La API devuelve ese rol en `/auth/me` y la web redirige con `error=admin_required`.  
**Solución:** Ejecutar en Railway (Postgres) el SQL de `apps/api/scripts/set-admin-users.sql` para asignar `role = 'super_admin'` a ese correo.

## Capturas guardadas

- **mcp-paso1-google-consent.png** – Estado inicial o tras primer carga.
- **mcp-paso2-consent.png** – Durante/tras consent de Google.
- **mcp-paso3-final-admin-required.png** – Estado final: login con error.

Ruta típica: `%LOCALAPPDATA%\Temp\cursor\screenshots\` (o la que indique el cliente MCP).

## Red y consola

En cada paso se llamó a `browser_network_requests` y `browser_console_messages` (con pausa de 2 s entre request y siguiente). El contenido detallado lo devuelve el MCP; en este informe se registra la secuencia y las URLs obtenidas por snapshot.
