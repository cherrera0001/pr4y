# Plan de trabajo: Dev Mobile y producto público

Documento único que ordena el proceso de trabajo para la línea **dev mobile** y un **producto público** (internet y/o marketplace). Basado en la documentación existente del repo.

---

## 1. Resumen ejecutivo

| Objetivo | Descripción |
|----------|-------------|
| **Línea dev mobile** | Desarrollo ordenado de la app Android (Kotlin, Jetpack Compose): flujo de ramas, CI, pruebas en dispositivo, sin romper firma/paquete. |
| **Producto público** | Un producto listo para estar en internet (pr4y.cl) y en un marketplace (Google Play): estable, documentado, con checklist de publicación. |

**Principio:** Nada toca producción sin pasar por integración. Backend primero; front (web y mobile) conecta y consume.

---

## 2. Estado actual (según documentación)

### 2.1 Ya definido y en uso

- **Ramas:** `main` (producción), `dev` (integración), `feat/` y `fix/` (trabajo). Ver [CICD.md](CICD.md) y [.cursor/rules/branching-cicd.mdc](../.cursor/rules/branching-cicd.mdc).
- **CI:** PR a `dev` → pre-flight + sanitización; push a `main` → deploy API (Railway) si cambian `apps/api` o `packages`. Ver [.github/workflows/](../.github/workflows/).
- **Seguridad E2EE:** Flujo DEK/KEK, sync solo cifrado, sanitización. Ver [CONTRIBUTING.md](../CONTRIBUTING.md), [AGENTS.md](AGENTS.md), [AUDIT-FLOWS.md](AUDIT-FLOWS.md).
- **Requerimientos por función:** Backend = foco; front = consumidor. Ver [PROMPT-PR4Y-MEGA.md](PROMPT-PR4Y-MEGA.md).
- **Estructura de datos:** [DB-STRUCTURE.md](DB-STRUCTURE.md). API bajo `/v1`, errores estándar, `snake_case` en DB, `camelCase` en JSON.

### 2.2 Implementado (mobile + producto)

- **Auth:** Google Sign-In, refresh, logout; tokens en EncryptedSharedPreferences.
- **DEK/KEK:** GET/PUT wrapped DEK, desbloqueo con passphrase y/o huella.
- **Sync:** push/pull cifrado, Room, outbox, merge last-write-wins.
- **Pantallas:** Login, Unlock, Home, NewEdit, Detail, Journal, Search, Settings, Victorias.
- **Recordatorios:** solo locales (WorkManager ~9:00); sin API de preferencias aún.
- **API:** auth, crypto, sync, records, reminders, answers; sanitización en [apps/api/src/lib/sanitize.ts](../apps/api/src/lib/sanitize.ts).
- **Deploy:** API en Railway (api.pr4y.cl), web en Vercel (pr4y.cl), DNS en Cloudflare. Ver [DEPLOY.md](../DEPLOY.md).

### 2.3 Pendiente o en mejora

- **Recordatorios editables:** Horario, 1/2/todos, múltiples slots → requieren modelo y API (requerimientos 4.1–4.4 en PROMPT-PR4Y-MEGA).
- **Rama `dev`:** Crear y usar de forma sistemática si aún no está creada (ver CICD.md).
- **Play Store:** Ficha completa, capturas según directrices, política de privacidad. Ver [DEPLOY-PLAY-STORE.md](../apps/mobile-android/DEPLOY-PLAY-STORE.md), [PLAY-STORE-SCREENSHOTS.md](PLAY-STORE-SCREENSHOTS.md).
- **Admin web:** Login estable y UX del dashboard (selector de días, manejo de errores). Ver [PLAN-ADMIN-LOGIN-Y-METRICAS.md](PLAN-ADMIN-LOGIN-Y-METRICAS.md).

---

## 3. Orden del proceso de trabajo (reglas fijas)

### 3.1 Flujo de ramas (The Prayer Flow)

1. **Trabajo nuevo:** Crear rama desde `dev`: `feat/nombre` o `fix/nombre`.
2. **Integración:** PR hacia `dev`. El CI debe pasar (pre-flight, sanitización, lint, typecheck).
3. **Producción:** Solo tras validar en `dev`, merge `dev` → `main`. No hotfixes directos a `main` (riesgo de firma/paquete en dispositivos).
4. **Deploys:** API (Railway) solo si cambian `apps/api` o `packages`; web (Vercel) según configuración; Android se publica manualmente (AAB a Play Console).

### 3.2 Reglas de desarrollo

- **Backend primero:** Cualquier función nueva con datos o reglas de negocio → primero API (modelo, servicio, ruta), luego web/mobile.
- **Una función = un requerimiento:** Partir tareas grandes en 4.1, 4.2, etc. (ejemplo: recordatorios en PROMPT-PR4Y-MEGA).
- **Seguridad:** No loguear contenido ni passphrase; no enviar texto en claro al backend; validación estricta (Zod/schemas).
- **Pre-vuelo:** Antes de cada push, ejecutar `./scripts/pre-flight-check.sh` (o equivalente en Windows).

### 3.3 Calidad antes de merge

- `pnpm lint`, `pnpm test`, `pnpm typecheck`.
- En Android: `./gradlew lint` en `apps/mobile-android`.
- API docs actualizadas si cambian endpoints.

### 3.4 Orden recomendado de ejecución (seguridad → admin → sentimiento → memoria)

Para que las tareas tengan éxito, se recomienda este orden:

| Paso | Área | Dónde | Qué hacer | Motivo |
|------|------|--------|-----------|--------|
| **1** | **Seguridad** | Android Studio | Ejecutar la **tarea 1 de Android** (Keystore TEE / hardware). | Si la huella digital no es segura a nivel de hardware, el "búnker" es solo una metáfora. Con el Keystore TEE, se vuelve real. |
| **2** | **Admin** | Cursor | Ejecutar la **tarea 1 y 2** (login admin + dashboard/métricas). | Necesitas ver cuánta gente usa el búnker para motivarte a seguir desarrollando. Ver [PLAN-ADMIN-LOGIN-Y-METRICAS.md](PLAN-ADMIN-LOGIN-Y-METRICAS.md). |
| **3** | **Sentimiento** | Android Studio | Ejecutar la **tarea 2 de Android**. | Es lo que transformará la "app de notas" en una experiencia espiritual íntima. |
| **4** | **Memoria** | Android | La métrica **"Hasta aquí nos ha ayudado Dios"** se construye con la **tarea 3 de Android**. | Cierre del ciclo: pedir, esperar y ver la victoria. Ver pantalla Victorias y [PROMPT-PR4Y-MEGA.md](PROMPT-PR4Y-MEGA.md) / answers. |

Este orden permite avanzar en seguridad, diseño y gestión simultáneamente, creando un espacio donde el usuario se sienta realmente seguro para abrir su corazón.

**Referencias técnicas:** Keystore/TEE → [AUDITORIA_SYNC_Y_MOBILE.md](AUDITORIA_SYNC_Y_MOBILE.md), [apps/mobile-android/.cursor/android-studio-audit-errors-prompt.md](../apps/mobile-android/.cursor/android-studio-audit-errors-prompt.md). Admin → [PLAN-ADMIN-LOGIN-Y-METRICAS.md](PLAN-ADMIN-LOGIN-Y-METRICAS.md). Victorias/answers → [PROMPT-PR4Y-MEGA.md](PROMPT-PR4Y-MEGA.md), [BACKEND-FIRST-API.md](BACKEND-FIRST-API.md).

---

## 4. Plan a avanzar (fases)

### Fase 0 — Preparación (una vez)

| # | Tarea | Referencia |
|---|--------|-------------|
| 0.1 | Crear rama `dev` desde `main` y pushearla; configurar en GitHub regla de rama para `main` (PRs desde `dev`, CI obligatorio). | [CICD.md](CICD.md) |
| 0.2 | Confirmar que los workflows de GitHub (dev-validation, deploy-railway) están operativos. | `.github/workflows/` |
| 0.3 | Tener keystore de release y `local.properties` configurado para builds de release (SHA-1 en Google Cloud para prod). | [DEPLOY-PLAY-STORE.md](../apps/mobile-android/DEPLOY-PLAY-STORE.md), [COMO-RESOLVER-LOGIN.md](../apps/mobile-android/COMO-RESOLVER-LOGIN.md) |

### Fase 1 — Estabilidad del producto público (web + API)

| # | Tarea | Referencia |
|---|--------|-------------|
| 1.1 | Asegurar login admin web (URIs en Google Cloud, deploy Vercel, usuario con rol admin en BD). | [PLAN-ADMIN-LOGIN-Y-METRICAS.md](PLAN-ADMIN-LOGIN-Y-METRICAS.md) |
| 1.2 | Dashboard admin: mensaje claro ante fallo de stats, selector de período (7/14/30 días). | [PLAN-ADMIN-LOGIN-Y-METRICAS.md](PLAN-ADMIN-LOGIN-Y-METRICAS.md) |
| 1.3 | Política de privacidad accesible y URL estable para Play Store y web. | [DEPLOY-PLAY-STORE.md](../apps/mobile-android/DEPLOY-PLAY-STORE.md) |

### Fase 2 — App lista para marketplace (Google Play)

| # | Tarea | Referencia |
|---|--------|-------------|
| 2.1 | Capturas de pantalla según directrices: barra de estado “limpia”, 1080×1920, sin API Debug en ficha. | [PLAY-STORE-SCREENSHOTS.md](PLAY-STORE-SCREENSHOTS.md) |
| 2.2 | Ficha de la tienda completa: título, descripción corta/larga, icono 512×512, clasificación de contenido. | [DEPLOY-PLAY-STORE.md](../apps/mobile-android/DEPLOY-PLAY-STORE.md) |
| 2.3 | Versionado: incrementar `versionCode` por cada publicación; mantener `.last-release-versioncode` o `-PlastReleasedVersionCode`. | [DEPLOY-PLAY-STORE.md](../apps/mobile-android/DEPLOY-PLAY-STORE.md) |
| 2.4 | Build release: `.\gradlew bundleProdRelease`; subir AAB a Play Console (Producción o pista interna). | [DEPLOY-PLAY-STORE.md](../apps/mobile-android/DEPLOY-PLAY-STORE.md) |

### Fase 3 — Dev mobile sostenible (ciclo continuo)

| # | Tarea | Referencia |
|---|--------|-------------|
| 3.1 | Todo desarrollo móvil en ramas `feat/` o `fix/` desde `dev`; PR a `dev`; merge a `main` solo cuando esté validado. | [CICD.md](CICD.md), [AGENTS.md](AGENTS.md) |
| 3.2 | Probar en dispositivo físico (dev debug) antes de marcar listo para producción; usar script `list-and-install-on-device.ps1` si hay varios dispositivos. | [apps/mobile-android/README.md](../apps/mobile-android/README.md) |
| 3.3 | Documentar cambios que afecten a usuarios (ej. nuevo flujo de desbloqueo o recordatorios) en README o docs específicos. | [docs/README.md](README.md) |

### Fase 4 — Funciones nuevas (recordatorios y demás)

| # | Tarea | Referencia |
|---|--------|-------------|
| 4.1 | Recordatorios editables: implementar en backend (modelo, preferencias por usuario, endpoints GET/PUT) según 4.1–4.4. | [PROMPT-PR4Y-MEGA.md](PROMPT-PR4Y-MEGA.md), [AUDIT-FLOWS.md](AUDIT-FLOWS.md) |
| 4.2 | App Android: pantalla/ajustes para horario, activar/desactivar, y (cuando exista API) múltiples slots; WorkManager según preferencias. | [PROMPT-PR4Y-MEGA.md](PROMPT-PR4Y-MEGA.md) |
| 4.3 | Cualquier otra función: mismo patrón — backend/API primero, luego web o mobile. | [AGENTS.md](AGENTS.md) |

---

## 5. Checklist de publicación (producto público)

### Web (pr4y.cl / Vercel)

- [ ] Root Directory en Vercel = `apps/web`.
- [ ] `NEXT_PUBLIC_API_URL` apuntando a API en producción (api.pr4y.cl).
- [ ] CORS en Railway incluye dominio web.
- [ ] Política de privacidad accesible desde la web.

### API (api.pr4y.cl / Railway)

- [ ] `DATABASE_URL` vinculada al add-on Postgres.
- [ ] `JWT_SECRET` seguro (≥32 caracteres).
- [ ] Migraciones aplicadas (`db:migrate`).
- [ ] `GET /v1/health` responde `database: "ok"`.

### Android (Google Play)

- [ ] Keystore de release creado y guardado de forma segura.
- [ ] SHA-1 de release registrada en Google Cloud (cliente OAuth Android).
- [ ] `versionCode` incrementado respecto al último publicado.
- [ ] AAB generado con `bundleProdRelease`.
- [ ] Ficha: capturas, descripción, icono, clasificación, política de privacidad.

---

## 6. Referencias (índice de documentación)

| Documento | Uso |
|-----------|-----|
| [README.md](../README.md) | Cara pública, inicio rápido, value proposition. |
| [AGENTS.md](AGENTS.md) | Reglas para agentes y colaboradores; prioridades y estándares. |
| [CONTRIBUTING.md](../CONTRIBUTING.md) | E2EE, estándares de código, lockfile, pre-vuelo. |
| [CICD.md](CICD.md) | Ramas, workflows, secretos, creación de `dev`. |
| [PROMPT-PR4Y-MEGA.md](PROMPT-PR4Y-MEGA.md) | Requerimientos por dominio (auth, DEK, sync, recordatorios). |
| [AUDIT-FLOWS.md](AUDIT-FLOWS.md) | Flujos auth, DEK, sync, recordatorios, sanitización. |
| [DB-STRUCTURE.md](DB-STRUCTURE.md) | Tablas, límites, índices, migraciones. |
| [DEPLOY.md](../DEPLOY.md) | Railway, Vercel, DNS, variables. |
| [apps/mobile-android/README.md](../apps/mobile-android/README.md) | Build, ejecución, Google Sign-In, sesión y desbloqueo. |
| [DEPLOY-PLAY-STORE.md](../apps/mobile-android/DEPLOY-PLAY-STORE.md) | Keystore, AAB, Play Console, versionado. |
| [PLAY-STORE-SCREENSHOTS.md](PLAY-STORE-SCREENSHOTS.md) | Directrices de capturas para la ficha. |
| [PLAN-ADMIN-LOGIN-Y-METRICAS.md](PLAN-ADMIN-LOGIN-Y-METRICAS.md) | Login admin y mejoras del dashboard. |
| [docs/README.md](README.md) | Índice general de documentación. |

---

## 7. Resumen en una frase

**Orden del proceso:** Rama `dev` como integración → trabajo en `feat/`/`fix/` → PR a `dev` → CI verde → merge `dev` → `main` para producción. Backend primero; mobile y web consumen la API. Producto público = web estable + API estable + app en Play Store con ficha y capturas correctas; todo documentado y sin saltarse el flujo de ramas.
