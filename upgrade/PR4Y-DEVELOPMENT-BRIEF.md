# PR4Y.cl — Development Brief Unificado

**Fecha:** 2026-03-09
**Tipo:** Documento maestro de desarrollo — único archivo para guiar todas las iteraciones
**Estado:** En evolución

---

## 1. Qué es PR4Y hoy

Prayer journal E2EE (zero-knowledge) con sync offline-first.

| Capa | Stack | Estado |
|------|-------|--------|
| **API** | Fastify 5 + Prisma + PostgreSQL (Railway) | Producción |
| **Web** | Next.js 14 + Tailwind v4 + shadcn/ui (Vercel) | Producción (SaaS admin) |
| **Mobile** | Kotlin + Jetpack Compose + Room (Google Play) | Bloqueado (validación identidad) |
| **Crypto** | AES-256-GCM client-side, PBKDF2/Argon2id KEK | Funcional |
| **Auth** | JWT + Google OAuth + Refresh Tokens | Funcional |

**URL producción:** https://www.pr4y.cl
**API producción:** https://pr4yapi-production.up.railway.app
**Monorepo:** pnpm workspaces (`apps/api`, `apps/web`, `apps/mobile-android`)

### Features actuales
- Registro/login email + Google OAuth
- Journal de oración E2EE (crear, editar, eliminar)
- Sync bidireccional (push/pull con last-write-wins)
- Recordatorios configurables (multi-schedule)
- Victorias/respuestas de oración
- Peticiones públicas anónimas ("pedir-oracion") ← proto-roulette
- Panel admin (usuarios, stats, contenido global)
- Modo offline completo
- Preferencias de display (tema, fuente)

---

## 2. Hacia dónde va PR4Y: Prayer Roulette

### El gap de mercado
**Nadie en el mundo ofrece matching aleatorio 1:1 para orar en tiempo real.**

- Hallow ($51M rev, $157M funding): meditación guiada individual
- Pray.com ($11M rev): red social asíncrona de peticiones
- Prayroom: distribuye peticiones a intercesores, NO matching en vivo
- Instapray (Peter Thiel): feed social, botón "pray" asíncrono

**PR4Y sería first-mover absoluto en prayer roulette.**

Mercado global: $2.52B (2025), CAGR 14.6%, proyección $9.91B (2035).

### Concepto core
1. Usuario presiona "Orar" → entra a cola de matching
2. Sistema conecta 2 personas aleatorias
3. Oran juntas en tiempo real (audio/texto/video)
4. Sesión termina → pueden reconectar o matchear con alguien nuevo
5. Opcional: agregar como "prayer partner" recurrente

---

## 3. Estrategia técnica: Web-first + WebView

Google tarda validando identidad → **no esperar**. La jugada:

```
┌─────────────────────────────────────┐
│         pr4y.cl (Next.js PWA)       │
│                                     │
│  ┌──────────┐  ┌──────────────────┐ │
│  │  Admin    │  │  User App       │ │
│  │  /admin/* │  │  /app/*         │ │
│  │  (actual) │  │  (NUEVO)        │ │
│  └──────────┘  │  - Journal E2EE  │ │
│                │  - Prayer Roulette│ │
│                │  - Partners      │ │
│                │  - Recordatorios  │ │
│                └──────────────────┘ │
└─────────────────────────────────────┘
         ↕ API (Fastify/Railway)
         ↕ WebSocket (Signaling)
         ↕ WebRTC (Audio/Video P2P)

Mobile: WebView wrapping pr4y.cl/app
        + Push notifications nativas
        + Service Worker (offline)
```

### Ventajas
- **1 codebase** para web, Android (WebView), iOS futuro
- **PWA** = instalable sin store, push notifications, offline
- **SaaS admin ya existe** — se extiende, no se reescribe
- **Cuando Google valide** → wrapper nativo con WebView + notificaciones nativas

---

## 4. Arquitectura del Prayer Roulette

### 4.1 Componentes necesarios

```
Cliente (Browser/WebView)
  ├── UI: Cola de espera, pantalla de oración, timer
  ├── WebSocket client: Socket.io-client
  ├── WebRTC: getUserMedia (audio), RTCPeerConnection
  └── Estado: Zustand (matching state, call state)

Servidor (Fastify + extensión)
  ├── Socket.io server (mismo proceso o sidecar)
  ├── Matching engine: cola FIFO con filtros opcionales
  ├── Signaling: intercambio SDP offer/answer + ICE candidates
  ├── Room management: crear/destruir salas de oración
  ├── Moderation: rate limiting, report system, bans
  └── Analytics: duración sesiones, matches/día, retención

Infraestructura
  ├── TURN/STUN server: Cloudflare TURN o coturn self-hosted
  └── Redis (opcional): cola de matching distribuida si escala
```

### 4.2 Flujo de matching

```
1. POST /v1/roulette/join {filters?: {language, denomination}}
   → Server agrega usuario a cola
   → WebSocket emite "waiting" con posición en cola

2. Matching engine (cada 2s):
   → Busca par compatible en cola
   → Crea room_id
   → WebSocket emite "matched" a ambos con room_id

3. Signaling (WebSocket):
   → User A envía SDP offer → Server relay → User B
   → User B envía SDP answer → Server relay → User A
   → ICE candidates intercambiados

4. Conexión P2P establecida (WebRTC):
   → Audio/video directo entre peers
   → Server solo monitorea heartbeat

5. Fin de sesión:
   → "end_prayer" → Server destruye room
   → Opciones: nuevo match, agregar partner, calificar
```

### 4.3 Modelo de datos nuevo (extensión Prisma)

```prisma
model PrayerRoom {
  id          String   @id @default(cuid())
  status      String   @default("active") // active, ended, reported
  startedAt   DateTime @default(now())
  endedAt     DateTime?
  duration    Int?     // segundos
  userA       String   // referencia a User
  userB       String
  language    String?
  @@index([status])
}

model PrayerPartner {
  id          String   @id @default(cuid())
  userId      String
  partnerId   String
  createdAt   DateTime @default(now())
  lastPrayed  DateTime?
  prayerCount Int      @default(0)
  user        User     @relation("userPartners", fields: [userId], references: [id])
  partner     User     @relation("partnerOf", fields: [partnerId], references: [id])
  @@unique([userId, partnerId])
}

model PrayerReport {
  id          String   @id @default(cuid())
  roomId      String
  reporterId  String
  reason      String   // inappropriate, spam, harassment
  createdAt   DateTime @default(now())
  status      String   @default("pending") // pending, reviewed, actioned
  room        PrayerRoom @relation(fields: [roomId], references: [id])
}

model MatchingQueue {
  id          String   @id @default(cuid())
  userId      String   @unique
  joinedAt    DateTime @default(now())
  filters     Json?    // {language, denomination, topic}
  status      String   @default("waiting") // waiting, matched, expired
  @@index([status, joinedAt])
}
```

### 4.4 Endpoints nuevos

```
# Roulette
POST   /v1/roulette/join          → Unirse a cola de matching
DELETE /v1/roulette/leave         → Salir de cola
GET    /v1/roulette/status        → Estado actual (waiting/matched/in_prayer)

# Prayer Partners
GET    /v1/partners               → Listar prayer partners
POST   /v1/partners/:userId      → Agregar partner post-sesión
DELETE /v1/partners/:partnerId   → Eliminar partner

# Rooms (interno, manejado por WebSocket mayormente)
POST   /v1/rooms/:roomId/end     → Terminar sesión
POST   /v1/rooms/:roomId/report  → Reportar usuario
GET    /v1/rooms/:roomId/summary → Resumen post-sesión

# WebSocket events
→ "join_queue"      : Entrar a cola
→ "queue_position"  : Actualización de posición
→ "matched"         : Match encontrado {roomId, peerId}
→ "signal"          : SDP offer/answer/ICE relay
→ "prayer_started"  : Ambos conectados
→ "prayer_ended"    : Sesión terminada
→ "partner_disconnected" : Peer se desconectó
→ "reported"        : Reporte recibido
```

---

## 5. Remediación de seguridad (auditoría C4A-CERT-2026-008)

7 vulnerabilidades abiertas. Remediación integrada en el desarrollo:

### Plazo 30 días (antes o durante build de roulette)

| ID | Vuln | Fix | Esfuerzo |
|----|------|-----|----------|
| VULN-001 | API URL hardcodeada en JS bundle | Implementar proxy `/api/proxy/*` en Next.js. Eliminar `NEXT_PUBLIC_API_URL`. Restringir OAuth Client ID a dominios autorizados en GCP Console. | 2h |
| VULN-003 | Errores AJV exponen schema | En producción: `setErrorHandler` que retorne `{"error": "Invalid request"}` sin details. Mantener detallado solo en `NODE_ENV=development`. | 1h |
| VULN-005 | Sin Content-Security-Policy | Agregar en `next.config.js`: CSP, X-Content-Type-Options, X-Frame-Options, Permissions-Policy. Actualizar CSP cuando se agregue WebRTC (agregar `connect-src` para STUN/TURN). | 30min |
| VULN-006 | Auth endpoints exponen estructura | Misma solución que VULN-003 — error handler genérico en producción. | Incluido en VULN-003 |

### Plazo 90 días (post-lanzamiento roulette)

| ID | Vuln | Fix | Esfuerzo |
|----|------|-----|----------|
| VULN-002 | Health endpoint verboso | Retornar solo `{"status":"ok"}` en prod. Mover health detallado a `/v1/admin/health`. | 15min |
| VULN-004 | Enumeración rutas admin (401 vs 404) | Middleware que verifica auth ANTES del routing para `/v1/admin/*`. Sin token válido → siempre 404. | 1h |
| VULN-007 | CORS `*` en frontend Vercel | Configurar `vercel.json` headers. Solo assets estáticos con `*`. API routes con origin específico. | 30min |

### Seguridad adicional para Prayer Roulette

| Aspecto | Implementación |
|---------|---------------|
| **Moderación** | Rate limit en join (max 10 joins/hora), report system con auto-ban (3 reports = suspensión), blacklist de palabras en texto |
| **Anti-abuse** | Cooldown entre sesiones (30s), require email verificado para roulette, ban por IP en casos graves |
| **Privacidad** | No compartir email/nombre real entre peers (solo nombre display o anónimo), no grabar sesiones |
| **CSP para WebRTC** | Actualizar CSP: `connect-src 'self' wss://*.pr4y.cl stun:* turn:*` |
| **TURN auth** | Credenciales TURN temporales generadas server-side (time-limited) |

---

## 6. PWA Configuration

### Manifest
```json
{
  "name": "PR4Y - Prayer Roulette",
  "short_name": "PR4Y",
  "description": "Conecta con alguien y oren juntos",
  "start_url": "/app",
  "display": "standalone",
  "background_color": "#0a0a0a",
  "theme_color": "#7c3aed",
  "icons": [
    {"src": "/icons/icon-192.png", "sizes": "192x192", "type": "image/png"},
    {"src": "/icons/icon-512.png", "sizes": "512x512", "type": "image/png"}
  ]
}
```

### Service Worker strategy
- **App shell**: cache-first (layout, CSS, JS)
- **API calls**: network-first con fallback offline
- **WebSocket**: no cacheable — mostrar UI "sin conexión" si no hay red
- **Push notifications**: recordatorios de oración, "prayer hours" programadas

### Installability
- `next-pwa` o `@serwist/next` para integrar con Next.js
- Prompt de instalación personalizado (no el genérico del browser)
- Banner "Instala PR4Y" en primera visita mobile

---

## 7. Cold Start Strategy: Prayer Hours

El mayor riesgo del roulette es necesitar masa crítica simultánea.

**Solución: "Horas de Oración"** — franjas donde la comunidad se compromete:

| Hora (Chile) | Nombre | Target |
|---|---|---|
| 07:00 | Oración Matutina | Antes del trabajo |
| 12:00 | Oración del Mediodía | Pausa almuerzo |
| 21:00 | Oración Nocturna | Antes de dormir |

- Push notification 5min antes de cada hora
- Countdown visible en la app
- Mostrar "X personas esperando" en cada franja
- Fuera de prayer hours: matching disponible pero sin garantía de inmediatez
- Fallback si no hay match en 60s: mostrar petición pública para orar solo

---

## 8. Modelo de negocio

| Tier | Precio | Incluye |
|------|--------|---------|
| **Free** | $0 | 3 sesiones roulette/día, journal ilimitado, prayer hours |
| **Premium** | $4.99/mes | Roulette ilimitado, filtros (idioma, denominación, tema), prayer partners, historial de sesiones, temas de oración personalizados |
| **Church** | $19.99/mes | Dashboard para iglesia, métricas comunitarias, prayer hours custom, hasta 50 miembros |

**Implementación:** Stripe (ya está en el stack de Forge) o RevenueCat para mobile.

---

## 9. Roadmap de desarrollo

### Fase 1: Fundación (Sprint 1-2)
- [ ] **Seguridad P1:** Fix VULN-001, 003, 005, 006 (errores genéricos + CSP + proxy API)
- [ ] **PWA setup:** manifest.json, service worker, installability
- [ ] **App shell web:** `/app` route group con layout mobile-first (sidebar colapsable, bottom nav)
- [ ] **Migrar journal a web:** Crear interfaz web del journal (actualmente solo mobile)
- [ ] **Auth web usuario:** Login/registro para usuarios normales en web (actualmente solo admin usa web)

### Fase 2: Prayer Roulette Core (Sprint 3-4)
- [ ] **WebSocket server:** Socket.io integrado en Fastify
- [ ] **Matching engine:** Cola FIFO, matching por disponibilidad
- [ ] **Signaling server:** Relay SDP/ICE para WebRTC
- [ ] **STUN/TURN:** Configurar Cloudflare TURN o coturn
- [ ] **UI matching:** Cola de espera con animación, countdown, "buscando..."
- [ ] **UI prayer room:** Timer de sesión, botón end, audio controls
- [ ] **Modelo datos:** PrayerRoom, MatchingQueue, PrayerPartner, PrayerReport

### Fase 3: Social + Retención (Sprint 5-6)
- [ ] **Prayer partners:** Agregar post-sesión, lista de partners, orar de nuevo
- [ ] **Prayer hours:** Franjas programadas, countdown, push notifications
- [ ] **Report system:** Reportar usuario, auto-ban, review admin
- [ ] **Streaks:** Racha de oración diaria (gamification sutil)
- [ ] **Filtros premium:** Idioma, denominación, tema de oración

### Fase 4: Monetización + Escala (Sprint 7-8)
- [ ] **Stripe integration:** Free/Premium/Church tiers
- [ ] **Church dashboard:** Métricas, prayer hours custom, gestión de miembros
- [ ] **WebView wrapper Android:** Cuando Google valide identidad
- [ ] **Analytics:** PostHog o Mixpanel para métricas de retención
- [ ] **Seguridad P2:** Fix VULN-002, 004, 007

### Fase 5: Crecimiento
- [ ] **Contenido:** Devocionales diarios, lecturas bíblicas (contenido propio o curado)
- [ ] **iOS wrapper:** WebView con capacitor o similar
- [ ] **Multilenguaje:** i18n (español, inglés, portugués)
- [ ] **AI moderation:** Detección automática de contenido inapropiado en audio
- [ ] **Redis:** Cola de matching distribuida si el tráfico lo requiere

---

## 10. Stack final consolidado

| Componente | Tecnología | Estado |
|---|---|---|
| **Runtime** | Node.js 20+ | ✅ Actual |
| **API** | Fastify 5 + TypeScript | ✅ Actual |
| **DB** | PostgreSQL 16 + Prisma | ✅ Actual |
| **Web** | Next.js 14 (App Router) + Tailwind v4 + shadcn/ui | ✅ Actual |
| **Auth** | JWT + Google OAuth + Refresh Tokens | ✅ Actual |
| **Crypto** | AES-256-GCM + PBKDF2/Argon2id | ✅ Actual |
| **Real-time** | Socket.io (WebSocket) | 🆕 Agregar |
| **Video/Audio** | WebRTC (native browser API) | 🆕 Agregar |
| **TURN/STUN** | Cloudflare TURN o coturn | 🆕 Agregar |
| **PWA** | next-pwa / @serwist/next | 🆕 Agregar |
| **State (client)** | Zustand | 🆕 Agregar |
| **Data fetching** | React Query / TanStack Query | 🆕 Agregar |
| **Payments** | Stripe | 🆕 Agregar (Fase 4) |
| **Analytics** | PostHog | 🆕 Agregar (Fase 4) |
| **Mobile** | WebView wrapper (Kotlin) | 🔄 Adaptar actual |
| **Deploy** | Railway (API) + Vercel (Web) | ✅ Actual |

---

## 11. Decisiones de arquitectura

| Decisión | Justificación |
|---|---|
| **Web-first, no native-first** | Google bloquea mobile → web PWA es más rápido y cubre todos los dispositivos |
| **Socket.io sobre ws puro** | Reconexión automática, fallback polling, rooms nativos, broadcast — menos código manual |
| **WebRTC para audio/video** | P2P = no pasa por servidor = privacidad + bajo costo de infra |
| **Matching FIFO simple** | No necesitamos ML ni scoring complejo. Aleatorio es el punto. Filtros opcionales son suficientes |
| **Mismo proceso Fastify** | Socket.io se puede montar en el mismo server Fastify. No necesita sidecar hasta que escale |
| **Zustand sobre Redux** | Más liviano, menos boilerplate, suficiente para estado de matching + call |
| **No E2EE en roulette** | Las sesiones son efímeras y con desconocidos. E2EE se mantiene para el journal privado |
| **Audio-first, video opcional** | Reduce barrera de entrada. Muchos no quieren mostrar su cara para orar |

---

## 12. Métricas de éxito

| Métrica | Target MVP | Target 6 meses |
|---|---|---|
| Usuarios registrados | 100 | 1,000 |
| Sesiones roulette/día | 10 | 100 |
| Duración promedio sesión | >3 min | >5 min |
| Retención D7 | 20% | 35% |
| Prayer partners creados | 10 | 200 |
| Conversión free→premium | - | 5% |

---

## 13. Mejoras al framework C4A Forge

PR4Y revela gaps en las capacidades de build de Forge. Propuestas:

### Skills/commands nuevos necesarios

| Comando | Propósito |
|---|---|
| `/pwa` | Setup PWA: manifest, service worker, offline strategy, install prompt |
| `/realtime` | Setup WebSocket (Socket.io) + eventos + reconexión |
| `/webrtc` | Setup WebRTC: signaling, TURN/STUN config, media controls |
| `/data-layer` | Setup React Query + Zustand + API client tipado |
| `/test` | Setup Vitest/Jest + component tests + API tests |

### Templates que faltan

| Template | Contenido |
|---|---|
| `templates/pwa/` | manifest.json, sw.ts, offline page, install banner component |
| `templates/realtime/` | Socket.io server plugin Fastify, client hook, event types |
| `templates/app-shell/` | Layout mobile-first con bottom nav, sidebar, top bar — optimizado para WebView |
| `templates/auth-flow/` | Login/registro/forgot password pages con shadcn/ui + validación |

### Mejoras a commands existentes

| Comando | Mejora |
|---|---|
| `/landing` | Agregar variantes: dark mode, video hero, animated hero |
| `/saas` | Soportar real-time features, WebSocket setup opcional |
| `/component` | Library de componentes pre-construidos (prayer card, timer, audio controls) |
| `/audit` | Incluir checks de seguridad de la auditoría C4A-CERT |

---

## 14. Archivos de referencia

| Archivo | Contenido |
|---|---|
| `intelligence/pr4y/market-research.md` | Análisis competitivo completo (Hallow, Pray.com, Prayroom, etc.) |
| `intelligence/pr4y/cert/informe-certificacion.md` | Auditoría de seguridad completa (7 vulns) |
| `intelligence/pr4y/cert/reflection.md` | Lecciones aprendidas de la auditoría |
| `intelligence/pr4y/code/` | Código fuente completo del monorepo |
| `intelligence/pr4y/code/docs/architecture.md` | Arquitectura E2EE y sync |
| `intelligence/pr4y/code/docs/api.md` | Contrato API completo |
| `intelligence/pr4y/code/CLAUDE.md` | Reglas de desarrollo del proyecto |
| `intelligence/pr4y/code/apps/api/prisma/schema.prisma` | Schema actual de BD |

---

---

## 15. Auditoría UX/UI — "Por qué PR4Y se siente tosca"

### Diagnóstico raíz

Tres factores combinados producen la sensación de app tosca y fría:

1. **Identidad visual de "bunker de seguridad"** — `Icons.Default.Security` (escudo) usado en 6 pantallas donde debería estar el icono real de PR4Y (persona orando). Terminología militar ("bunker"), colores fríos (Electric Cyan sobre Midnight Blue). Se siente como gestor de passwords.
2. **Botones genéricos sin personalidad** — Material 3 defaults, `TextButton` que parecen enlaces perdidos, sin elevation ni border radius consistente. No invitan al toque.
3. **Cero transiciones + cero micro-interacciones** — cada navegación es corte seco. Sin animaciones de entrada, sin feedback al guardar, sin haptics en el swipe.

### Iconos: el escudo vs el icono real

**El icono real de PR4Y ya existe en el repositorio:**
- Mobile launcher: `apps/mobile-android/app/src/main/res/drawable/ic_launcher_foreground.png` — gota con P en cyan
- Web icon: `apps/web/app/icon.png` — persona orando de rodillas (azul sobre dark)
- Web favicon: `apps/web/public/favicon.png` — mismo diseño

**Ubicaciones donde se usa `Icons.Default.Security` (escudo) que deben reemplazarse por el icono real:**

| Archivo | Línea | Contexto |
|---|---|---|
| `LoginScreen.kt` | :100 | Icono principal de login |
| `WelcomeScreen.kt` | :47 | Icono de bienvenida |
| `HomeScreen.kt` | :397 | EmptyRequestsState |
| `SettingsScreen.kt` | :173 | Sección de seguridad |
| `UnlockScreen.kt` | :253 | `Icons.Default.Lock` en acceso privado |
| `Pr4yNavHost.kt` | :298 | `Icons.Default.Lock` en navegación |

**Fix:** Crear un `@Composable Pr4yLogo()` que cargue `painterResource(R.drawable.ic_launcher_foreground)` y reemplazar en todos los puntos.

---

### 15.1 Web — Hallazgos (4 Críticos, 8 Mayores, 9 Menores, 10 Mejoras)

#### CRÍTICOS

| ID | Problema | Archivo | Fix |
|---|---|---|---|
| WC-01 | `className="dark"` hardcodeado en `<html>` rompe modo claro. Páginas públicas usan `bg-slate-950` hardcoded — modo claro nunca funciona | `app/layout.tsx:48` | Eliminar `className="dark"`, dejar que `next-themes` maneje. Reemplazar `bg-slate-950` por `bg-background` en páginas públicas |
| WC-02 | Formulario "pedir oración" usa `<input>/<textarea>` nativos en vez de componentes shadcn/ui (`<Input>`, `<Textarea>`) que ya existen | `app/pedir-oracion/page.tsx:96-121` | Reemplazar por `<Input>` y `<Textarea>` de `components/ui/` |
| WC-03 | Página Privacy sin navegación de retorno — usuario atrapado. Usa `bg-background` distinto al `bg-slate-950` de otras páginas | `app/privacy/page.tsx` | Agregar botón "Volver al inicio" y unificar fondo |
| WC-04 | No existe `app/not-found.tsx` — 404 genérica de Next.js sin branding PR4Y | Inexistente | Crear `not-found.tsx` con marca PR4Y y link de retorno |

#### MAYORES

| ID | Problema | Archivo |
|---|---|---|
| WM-01 | Cero animaciones en toda la parte pública. Cards del hero aparecen de golpe. Sin Framer Motion | Todas las páginas públicas |
| WM-02 | CTA "Panel Admin" visible en hero público — leak de funcionalidad interna | `app/page.tsx:50-62` |
| WM-03 | Sin Header/Navbar compartido en páginas públicas. Sin logo visible. Navegación rota entre terms/privacy/contact | Layout público inexistente |
| WM-04 | Empty states minimalistas sin ilustración — solo `<p>` con texto gris | `admin/(panel)/content/page.tsx:335`, `users/page.tsx:311` |
| WM-05 | `window.confirm()` nativo rompe estética glassmorphism del admin | `admin/(panel)/content/page.tsx:216` |
| WM-06 | Términos de servicio extremadamente escuetos (1 párrafo) — riesgo Google Play compliance | `app/terms/page.tsx:28-31` |
| WM-07 | Loading state inconsistente: Users usa texto plano, Dashboard/Content usan spinner `<Loader2>` | `admin/(panel)/users/page.tsx:142-146` |
| WM-08 | Tabla records-by-type usa `<table>` nativa inline, no `<Table>` de shadcn como el resto | `admin/(panel)/page.tsx:243-267` |

#### MENORES

| ID | Problema | Archivo |
|---|---|---|
| Wm-01 | Font heading Georgia serif en todos los `h1-h6` + body Inter sans = contraste "template de Word" | `app/globals.css:54-56` |
| Wm-02 | `LogoutButton.tsx` no se importa en ningún lado — código muerto | `app/admin/LogoutButton.tsx` |
| Wm-03 | LogoutButton mezcla CSS custom props (`--color-muted`) con tokens Tailwind — dos sistemas | `app/admin/LogoutButton.tsx:16` |
| Wm-04 | Sidebar mobile sin trigger visible — `SidebarTrigger` exportado pero no usado. Solo Ctrl+B funciona | `admin/(panel)/layout.tsx` |
| Wm-05 | `useEffect` sin dependency array completo (router). ESLint silenciado con `ignoreDuringBuilds: true` | `admin/(panel)/users/page.tsx:64-89` |
| Wm-06 | Fechas `.slice(5)` pierden año — ambiguo si cruza diciembre-enero | `admin/(panel)/page.tsx:124,261` |
| Wm-07 | Sin `aria-label` en formulario público de oración | `app/pedir-oracion/page.tsx:91` |
| Wm-08 | Toaster `theme="system"` pero root es `dark` hardcoded — posible mismatch | `components/providers.tsx:18` |
| Wm-09 | `formType` default `'prayer'` pero ese tipo no existe en `CONTENT_TYPES` | `admin/(panel)/content/page.tsx:95` |

#### MEJORAS WEB

| ID | Mejora |
|---|---|
| WE-01 | Agregar Framer Motion al hero y cards (fade-in con stagger) |
| WE-02 | Skeleton screens en admin con `<Skeleton>` (ya disponible en shadcn) |
| WE-03 | Header compartido con logo PR4Y + navegación persistente |
| WE-04 | Página 404 custom con branding |
| WE-05 | Formulario de contacto inline (no solo email) |
| WE-06 | Logo/icono PR4Y en hero (actualmente solo texto) |
| WE-07 | Open Graph image para previews en redes sociales |
| WE-08 | Exponer modo contemplativo a usuarios públicos (hoy solo en admin sidebar) |
| WE-09 | Rate limiting visual en formulario público post-envío |
| WE-10 | AlertDialog de shadcn para confirmaciones de delete (reemplazar `window.confirm`) |

---

### 15.2 Mobile (Android) — Hallazgos (5 Críticos, 10 Mayores, 8 Menores, 7 Mejoras)

#### CRÍTICOS

| ID | Problema | Archivos | Fix |
|---|---|---|---|
| MC-01 | `Color.White` hardcodeado → texto **invisible** en tema claro | `HomeScreen.kt:373,405`, `LoginScreen.kt:115`, `UnlockScreen.kt:269` | Reemplazar por `MaterialTheme.colorScheme.onBackground` / `onSurface` |
| MC-02 | Botones con `Color.White/Black` hardcodeados ignoran tema | `LoginScreen.kt:141-143`, `UnlockScreen.kt:340`, `HomeFABs:114` | Usar tokens `primary` / `onPrimary` del tema |
| MC-03 | Sin `contentDescription` en iconos interactivos — no accesible | `HomeScreen.kt:326,331,336`, `RouletteScreen.kt:177`, `FocusModeScreen.kt:144`, `VictoriasScreen.kt:145-149` | Agregar descripciones en todos los `Icon()` dentro de elementos interactivos |
| MC-04 | Colores `HopeGreen`, `SoftGold` definidos como constantes locales fuera del tema — no cambian entre temas | `DetailScreen.kt:53`, `VictoriasScreen.kt:40-41` | Mover a ColorScheme extendido o definir como semánticos en Theme |
| MC-05 | SwipeToDeliver sin feedback háptico, sin indicador de progreso, thumb 48dp pequeño, sin texto dinámico, sin fallback visible | `NewEditScreen.kt:226-279` | Agregar haptics, barra de progreso, thumb más grande, texto que cambie al acercarse al threshold |

#### MAYORES

| ID | Problema | Archivo |
|---|---|---|
| MM-01 | Sin `Shapes` definidos en Theme — border radius ad-hoc: 28dp, 16dp, 24dp sin consistencia | `Theme.kt` |
| MM-02 | Sin transiciones entre pantallas — `NavHost` sin `enterTransition`/`exitTransition`. Corte seco en toda la app | `Pr4yNavHost.kt` |
| MM-03 | Botones genéricos sin estados visuales claros. Sin estado de loading en operaciones async (excepto DetailScreen) | `WelcomeScreen.kt:73-87`, `JournalScreen.kt:108`, `DetailScreen.kt:206-211` |
| MM-04 | FidelidadCard labels de semanas invertidos — label[0]="Esta semana" se asocia con dato más antiguo por `.reversed()` | `HomeScreen.kt:185-190` |
| MM-05 | RouletteScreen TopAppBar muestra icono Settings (engranaje) en vez de flecha atrás | `RouletteScreen.kt:45` |
| MM-06 | NewJournalScreen fuerza `MidnightBlue` hardcoded — siempre oscura, rompe tema claro y contemplativo | `NewJournalScreen.kt:80,82,99` |
| MM-07 | FocusModeScreen y NewEditScreen usan gradiente hardcoded `MidnightBlue`+`ElectricCyan` | `FocusModeScreen.kt:182-185`, `NewEditScreen.kt:198-203` |
| MM-08 | JournalItem sin touch target mínimo 48dp — `padding(12.dp)` puede ser insuficiente si contenido corto | `JournalScreen.kt:143-158` |
| MM-09 | Empty states inconsistentes — cada pantalla tiene su propio diseño custom en vez de usar `EmptyStatePlaceholder` | `HomeScreen.kt:388`, `JournalScreen.kt:87`, `VictoriasScreen.kt:94` |
| MM-10 | **Carousel (HorizontalPager) en FocusModeScreen es pasivo** — solo deslizar y leer. Sin botón "Orar por esto", sin indicador de dots, sin interacción. Comparar con RouletteScreen que SÍ tiene "Me uno en oración" | `FocusModeScreen.kt:115-166` |

#### MENORES

| ID | Problema | Archivo |
|---|---|---|
| Mm-01 | TopAppBar no personaliza colores — defaults M3 no coinciden con paleta oscura | `Pr4yTopAppBar.kt:21-38` |
| Mm-02 | ShimmerLoading usa `Color.DarkGray`/`LightGray` hardcodeados — no respeta tema | `ShimmerLoading.kt:26-28` |
| Mm-03 | SearchScreen sin icono de lupa en `OutlinedTextField` — patrón estándar de búsqueda ausente | `SearchScreen.kt:78-85` |
| Mm-04 | SettingsScreen sección "Cuenta" solo tiene "Cerrar sesión" — no muestra email ni info de cuenta | `SettingsScreen.kt:251-262` |
| Mm-05 | RequestItem sin fecha ni metadatos — solo título y body | `HomeScreen.kt:362-386` |
| Mm-06 | EmptyStatePlaceholder CTA usa `primaryContainer` (tonal) en vez de `primary` (enfático) | `EmptyStatePlaceholder.kt:53-61` |
| Mm-07 | Strings hardcodeadas en todo el código — sin `stringResource()`. Bloquea localización | Todos los archivos UI |
| Mm-08 | Sin `@Preview` functions en ninguna pantalla ni componente | Todos los archivos UI |

#### MEJORAS MOBILE

| ID | Mejora |
|---|---|
| ME-01 | Tipografía custom (Lora, Merriweather) en vez de `FontFamily.Default` — calidez editorial |
| ME-02 | Reemplazar `Icons.Default.Security` por icono real PR4Y (`R.drawable.ic_launcher_foreground`) en 6 ubicaciones |
| ME-03 | Animaciones de entrada en listas (`animateItemPlacement`, `AnimatedVisibility`) |
| ME-04 | Bottom Navigation Bar en vez de chips para Diario/Ruleta/Victorias |
| ME-05 | Pull-to-refresh en HomeScreen para sincronización manual |
| ME-06 | Animación de confirmación al guardar (check verde, sobre que vuela) |
| ME-07 | Timer de oración en FocusModeScreen (1/3/5 min por pedido) |

---

### 15.3 Carousel: Diagnóstico específico

**Ubicación:** `apps/mobile-android/.../ui/screens/FocusModeScreen.kt:115-166`

**Problema:** El `HorizontalPager` actual es 100% pasivo:
```
[Card Petición 1] → desliza → [Card Petición 2] → desliza → [Card Petición 3]
```
- No hay botón de acción en cada card
- No hay indicador de página (dots)
- No hay forma de "seleccionar" una petición para orar por ella
- No hay feedback al llegar al final
- La experiencia es: deslizar → leer → deslizar → leer. Fría y sin propósito.

**Solución propuesta:**
```
[Card Petición 1]
  ├── Título + Body visible
  ├── Dots indicator (posición actual / total)
  ├── Botón "🙏 Orar por esto" → navega a detalle o inicia timer
  ├── Botón secundario "Siguiente →"
  └── Swipe natural también funciona

Al tocar "Orar por esto":
  → Incrementa prayerCount en backend
  → Muestra timer de oración (1/3/5 min)
  → Al terminar: animación + "Gracias por orar"
  → Opción: "Orar por otro" (siguiente card)
```

**Comparación con RouletteScreen:** La `RouletteScreen.kt` SÍ tiene un botón `"Me uno en oración"` con `Icons.Default.Favorite`. El FocusModeScreen debería tener equivalente o mejor.

---

### 15.4 Plan de corrección UX/UI integrado al roadmap

Se inserta como **Fase 0** antes de la Fase 1 original:

#### Fase 0A: Correcciones críticas (1-2 días)
- [ ] **MC-01/MC-02:** Eliminar todos los `Color.White`/`Color.Black` hardcodeados → usar tokens del tema
- [ ] **MC-03:** Agregar `contentDescription` a todos los iconos interactivos
- [ ] **ME-02:** Crear `@Composable Pr4yLogo()` con `painterResource(R.drawable.ic_launcher_foreground)` y reemplazar `Icons.Default.Security` en 6 ubicaciones
- [ ] **WC-01:** Eliminar `className="dark"` hardcodeado, reemplazar `bg-slate-950` por `bg-background`
- [ ] **WC-02:** Reemplazar inputs nativos por componentes shadcn/ui en formulario público
- [ ] **WC-03:** Agregar navegación de retorno en Privacy
- [ ] **WC-04:** Crear `not-found.tsx` con branding PR4Y

#### Fase 0B: Identidad visual cálida (2-3 días)
- [ ] **MM-01:** Definir `Shapes` en Theme.kt (small=8dp, medium=16dp, large=24dp)
- [ ] **ME-01:** Integrar tipografía custom (Lora o similar para headings)
- [ ] **MC-04:** Mover `HopeGreen`, `SoftGold` al tema como colores semánticos
- [ ] **MM-06/MM-07:** Reemplazar `MidnightBlue` hardcoded por `MaterialTheme.colorScheme.background`
- [ ] **Wm-01:** Evaluar tipografía web — Inter + serif heading o unificar a una sola familia
- [ ] **WM-02:** Eliminar "Panel Admin" del hero público
- [ ] **WM-03:** Crear layout público con header compartido (logo PR4Y + nav)

#### Fase 0C: Transiciones y micro-interacciones (2-3 días)
- [ ] **MM-02:** Agregar `fadeIn + slideInVertically` / `fadeOut` en NavHost
- [ ] **MC-05:** Mejorar SwipeToDeliver: haptics, progress bar, texto dinámico
- [ ] **ME-03:** `animateItemPlacement` en LazyColumns del journal y home
- [ ] **ME-06:** Animación de confirmación al guardar/entregar
- [ ] **WM-01:** Agregar Framer Motion al hero y cards con fade-in + stagger
- [ ] **WM-05/WE-10:** Reemplazar `window.confirm()` por `AlertDialog` de shadcn

#### Fase 0D: Carousel interactivo (1-2 días)
- [ ] **MM-10:** Rediseñar FocusModeScreen HorizontalPager:
  - Agregar dots indicator
  - Agregar botón "Orar por esto" por card
  - Agregar timer de oración opcional
  - Incrementar `prayerCount` al confirmar
  - Animación de agradecimiento al terminar
- [ ] **ME-07:** Timer visual de oración (1/3/5 min seleccionable)

#### Fase 0E: Consistencia y pulido (1-2 días)
- [ ] **MM-09:** Unificar empty states con `EmptyStatePlaceholder` en todas las pantallas
- [ ] **MM-04:** Corregir labels invertidos de FidelidadCard
- [ ] **MM-05:** Corregir icono de RouletteScreen TopAppBar (flecha atrás, no engranaje)
- [ ] **WM-07:** Unificar loading states (spinner `<Loader2>` everywhere)
- [ ] **WM-08:** Unificar tablas con componente `<Table>` de shadcn
- [ ] **ME-04:** Evaluar Bottom Navigation Bar vs chips actuales

**Estimación total Fase 0:** 7-12 días de desarrollo.

---

*Este documento es la fuente única de verdad para el desarrollo de PR4Y. Actualizar conforme se avance en cada fase.*
