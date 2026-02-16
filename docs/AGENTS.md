# PR4Y – AGENTS (Reglas para agentes de IA y colaboradores)

Este archivo define cómo deben trabajar agentes (Cursor/LLM) y humanos en el repo PR4Y.

**Requerimientos detallados y rol (backend = foco, front = conecta):** Ver **docs/PROMPT-PR4Y-MEGA.md**. Ahí se listan las funciones como requerimientos (1 función = 1 requerimiento), incluidos recordatorios editables (horario, 1/2/todos, múltiples). Cualquier feature nueva debe respetar: primero backend/API, luego front.

## 0) Prioridades
1) Privacidad y seguridad del contenido (E2EE).
2) Simplicidad de UX (cuaderno).
3) Offline-first.
4) Sync opcional, incremental y robusto.
5) Calidad: tests + tipos + docs.

## 1) Reglas de no-invención
- NO inventar métricas, números o supuestos como hechos.
- Si falta información, usar:
  - [PREGUNTA] para pedir dato
  - [SUPUESTO] para proponer una decisión explícita
- NO crear features sociales.

## 2) Estándares de seguridad
- Nunca registrar contenido (texto de pedidos/notas) en logs.
- Nunca persistir texto en claro en backend.
- Todo contenido sincronizado debe estar cifrado del lado cliente.
- Claves derivadas desde passphrase del usuario. El servidor nunca recibe la passphrase.
- Rate limiting en auth y endpoints de sync.
- Validación estricta de input (Zod en web, schemas en API).

## 3) Estructura del repo (esperada)
- apps/
  - web/            Next.js (Vercel)
  - api/            Fastify (Railway)
- packages/
  - shared/         Tipos, esquemas Zod, utilidades crypto comunes
- docs/
  - architecture.md
  - api.md
  - decisions/      ADRs (Architecture Decision Records)
- scripts/
  - db/             migraciones/seed
  - ci/

## 4) Flujo de trabajo
- Toda tarea grande se parte en PRs pequeños.
- Cada PR incluye:
  - Qué cambia
  - Por qué
  - Cómo probar
  - Riesgos/impacto
- Mantener compatibilidad hacia atrás del contrato API (versionado).

## 5) Convenciones de código
- TypeScript estricto.
- Lint + format (eslint + prettier).
- Rutas API bajo `/v1`.
- Errores API en formato estándar:
  `{ "error": { "code": "string", "message": "string", "details": {...} } }`
- Nombres:
  - `snake_case` en DB
  - `camelCase` en JSON

## 6) Validación antes de merge
- `pnpm lint`
- `pnpm test`
- `pnpm typecheck`
- API docs actualizadas si cambian endpoints.
- Revisión rápida de seguridad:
  - CORS
  - Headers
  - logs
  - secrets

## 7) Scope y límites
- No construir chat ni comunidad.
- No “racha” obligatoria ni rankings.
- Recordatorios deben ser “suaves” y opcionales.
