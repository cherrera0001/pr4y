# Plan: resolver problemas de login admin y mejorar acceso a métricas

## Parte 1 — Cómo resolver los problemas de login

### 1.1 Checklist técnico (ya aplicado en el repo)

| Problema | Solución en código | Verificación en producción |
|----------|--------------------|----------------------------|
| **405 GET /api/admin/login** | Handler GET que redirige a `/admin/login` en `app/api/admin/login/route.ts` | Abrir `https://www.pr4y.cl/api/admin/login` → debe redirigir, no mostrar error. |
| **405 POST /admin/login** | Middleware reescribe `POST /admin/login` → `/api/admin/login` | El script de Google que hace POST a la página ya no recibe 405. |
| **COOP / postMessage** | Cabecera `Cross-Origin-Opener-Policy: same-origin-allow-popups` en middleware y next.config para `/admin` y `/admin/login` | En Vercel no debe haber otra COOP más estricta (Settings → Security/Headers). |
| **Popup bloqueado** | Login con GSI en modo **redirect** (API HTML: `data-ux_mode="redirect"`, `data-login_uri="/api/admin/login"`) | Probar en Chrome/Edge ventana normal; el navegador embebido puede seguir bloqueando popups. |

### 1.2 Pasos que dependen de ti (no de código)

1. **Deploy actualizado:** En Vercel, confirmar que el último commit está desplegado (Redeploy si hace falta).
2. **Google Cloud:** En el cliente OAuth 2.0 (Web), **Authorized redirect URIs** deben incluir:
   - `https://www.pr4y.cl/api/admin/login`
   - `https://pr4y.cl/api/admin/login`
3. **Probar en navegador normal:** Evitar solo el embebido de Cursor; usar Chrome o Edge para el flujo completo.
4. **Usuario admin en BD:** El usuario que inicia sesión (ej. crherrera@c4a.cl) debe tener `role = 'admin'` o `'super_admin'` en la tabla `users` (script `create-test-user` con `SUPER_ADMIN=true`).

### 1.3 Si el login sigue fallando

- Revisar **Vercel → Logs** del deployment: ver si llegan GET/POST a `/api/admin/login` y qué status devuelven.
- En **Network** (DevTools): comprobar si el redirect de Google hace POST a `https://www.pr4y.cl/api/admin/login` y si la respuesta es 302 a `/admin` o un error.
- Documentación de apoyo: `docs/FORENSIC-405-API-ADMIN-LOGIN.md`, `docs/AUDIT-LOGIN-WEB.md`.

---

## Parte 2 — Experiencia del administrador y métrica suficiente

### 2.1 Estado actual

- **Rutas admin:** Dashboard (`/admin`), Usuarios (`/admin/users`), Contenidos (`/admin/content`).
- **Métricas ya disponibles (API):** `GET /v1/admin/stats?days=N` devuelve:
  - totalUsers, totalRecords, totalBlobBytes
  - syncsToday, bytesPushedToday, bytesPulledToday
  - byDay: usuarios activos (DAU), bytes subidos/bajados por día.
- **Dashboard web:** Muestra esas métricas (cards + gráficos DAU y tráfico por día), pide `days=14`.

El administrador **puede** acceder a métricas suficientes para uso básico (usuarios, registros, sync hoy, almacenamiento, DAU y tráfico por día) **si consigue entrar** al panel.

### 2.2 Cambios recomendados para mejorar la experiencia

#### A) Garantizar acceso al dashboard aunque falle una petición

- **Problema:** Si `/api/admin/stats` devuelve 401/503, el dashboard muestra solo error o spinner.
- **Mejora:** Mostrar un mensaje claro (“Sesión expirada” o “No se pudieron cargar las métricas”) con botón “Reintentar” y/o “Ir al login”, y conservar la navegación lateral para no dejar al admin bloqueado.

#### B) Selector de período (días)

- **Actual:** Siempre 14 días.
- **Mejora:** Selector (ej. 7, 14, 30 días) que cambie el query `days` y recargue las estadísticas. Así el admin puede ver métrica suficiente para distintas ventanas sin tocar código.

#### C) Resumen “última actividad” en el dashboard

- **Mejora opcional:** Una línea o card con “Última sincronización registrada: hace X min/horas” o “Usuarios activos en las últimas 24 h” (si la API lo expone o se puede derivar de `usage_logs`). Ayuda a saber si el sistema está en uso reciente.

#### D) Acceso rápido a métricas sin depender del login web (opcional)

- **Idea:** Si el login web sigue dando problemas en algún entorno, tener un **informe de métricas vía API** protegido por API key (solo para el backend o un script interno) que devuelva los mismos datos que `/admin/stats`, para poder consultarlos por script o herramienta hasta que el panel web sea 100 % fiable.

### 2.3 Prioridad sugerida

1. **Crítico:** Asegurar que login funcione (Parte 1) y que el deploy esté al día.
2. **Alto:** Mensaje claro y reintento cuando falle la carga de stats (2.2 A).
3. **Alto:** Selector de días 7/14/30 en el dashboard (2.2 B).
4. **Opcional:** Última actividad o métrica “últimas 24 h” (2.2 C) y/o endpoint de métricas por API key (2.2 D).

---

## Resumen

- **Problemas de login:** Se resuelven con el código ya en el repo (GET en `/api/admin/login`, rewrite en middleware, GSI redirect, COOP) + deploy actualizado + URIs en Google Cloud + prueba en navegador normal.
- **Métrica suficiente:** Ya existe (totales, DAU, tráfico por día). La mejora de experiencia pasa por: (1) poder entrar de forma fiable al panel, (2) manejo claro de errores al cargar stats, (3) selector de período en el dashboard y, opcionalmente, (4) más contexto de actividad reciente o acceso vía API.
