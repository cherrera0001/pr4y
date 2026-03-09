# Por qué no deja ingresar al administrador

## ¿Existe backend/API/servicios?

**Sí.** El flujo está implementado:

| Capa | Qué hace |
|------|----------|
| **API (Railway)** | `POST /v1/auth/google` – valida idToken de Google, crea/actualiza usuario, devuelve accessToken. `GET /v1/auth/me` – con Bearer token devuelve `{ id, email, role }` desde la BD. |
| **Web (Vercel)** | `POST /api/admin/login` – recibe el credential de Google, llama a la API auth/google y auth/me, comprueba **rol** y **email en allowlist**, setea cookie y redirige a `/admin` o a `/admin/login?error=admin_required`. |
| **Allowlist** | Solo `crherrera@c4a.cl` y `herrera.jara.cristobal@gmail.com` pueden tener acceso admin (código en API y Web). |

## Causa del rechazo

El rechazo ocurre en **apps/web/app/api/admin/login/route.ts** (líneas 119-127):

1. **Si `user.role` no es `admin` ni `super_admin`** → redirect con `?error=admin_required`.  
   → Eso significa que en la **base de datos** ese usuario tiene `role = 'user'`.  
   → **Solución:** Ejecutar en Railway (Postgres) el SQL de `apps/api/scripts/set-admin-users.sql` para poner `role = 'super_admin'` a esos dos correos.

2. **Si el email no está en la allowlist** → mismo redirect (solo esos dos correos están permitidos en código).

Los logs de Railway que ves (`checkpoint starting/complete`) son de **Postgres**, no de la app; no explican el login. El motivo real se ve en **logs del servicio API** (no Postgres) o en los logs de **Vercel** (función `/api/admin/login`), donde está el `console.warn('[admin/login] role not allowed', { email, role })`.

## Resumen

- Backend/API/servicios **sí existen** y están bien encadenados.
- El no poder entrar es porque en la **BD** el usuario con el que pruebas sigue con `role = 'user'`.
- Ejecuta en Railway → Database → Query el contenido de `apps/api/scripts/set-admin-users.sql` y vuelve a intentar el login.
