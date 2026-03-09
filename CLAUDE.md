# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

PR4Y is a privacy-first, end-to-end encrypted (E2EE) prayer journal. Architecture priorities: 1) E2EE integrity, 2) UX simplicity, 3) offline-first, 4) optional sync, 5) quality.

**Never break the E2EE contract**: all user content must be encrypted client-side before leaving the device. The server stores only ciphertext and never receives passphrases or DEKs in plaintext.

---

## Monorepo Structure

pnpm workspaces (`apps/*`, `packages/*`, `scripts/*`). Package manager: **pnpm 8.15.0**.

- `apps/api/` — Fastify 5 + Prisma + PostgreSQL, deployed on Railway
- `apps/web/` — Next.js 14 (App Router) + Tailwind CSS 4, deployed on Vercel
- `apps/mobile-android/` — Kotlin + Jetpack Compose, deployed to Google Play
- `packages/` — Shared packages (core, crypto, sync, ui) — currently stubs
- `docs/` — Architecture, API contract, security docs, ADRs
- `scripts/` — Build utilities and pre-flight checks

---

## Commands

### Root (monorepo)
```bash
pnpm lint               # ESLint all apps
pnpm typecheck          # tsc --noEmit all apps
pnpm test               # Run tests (placeholder)
pnpm build              # Build API only
pnpm db:migrate         # prisma migrate deploy (production)
pnpm db:migrate:dev     # prisma migrate dev (local)
pnpm mobile:buildDebug  # gradle assembleDebug
pnpm mobile:installDebug # gradle installDebug
```

### API (`apps/api`)
```bash
pnpm --filter @pr4y/api dev          # Dev server with hot reload
pnpm --filter @pr4y/api build        # esbuild bundle to dist/
pnpm --filter @pr4y/api lint
pnpm --filter @pr4y/api typecheck
pnpm --filter @pr4y/api db:migrate:dev
```

### Web (`apps/web`)
```bash
pnpm --filter @pr4y/web dev
pnpm --filter @pr4y/web build
pnpm --filter @pr4y/web lint
```

### Android (`apps/mobile-android`)
```bash
cd apps/mobile-android
./gradlew assembleDebug
./gradlew installDebug
./gradlew lint
```

### Pre-flight (required before push)
```bash
./scripts/pre-flight-check.sh   # Scans for STRESS TEST code, artificial delays, missing types
```

---

## Architecture

### E2EE Encryption Model

```
User passphrase → PBKDF2/Argon2id → KEK (Key Encryption Key)
                                         ↓ wraps
                   Random AES-256 → DEK (Data Encryption Key)
                                         ↓ encrypts
                              All prayer/journal content
```

- **KEK** is derived locally, never leaves the device
- **DEK** is generated randomly, wrapped by KEK, and `wrappedDekB64` is stored server-side
- **Content** is stored as `encryptedPayloadB64` — server cannot decrypt it
- Crypto implementation: `apps/mobile-android/.../crypto/LocalCrypto.kt` (AES-GCM) and `DekManager.kt` (KEK/DEK lifecycle)
- API crypto routes: `apps/api/src/routes/crypto.ts` (`PUT/GET /v1/crypto/dek`)

### Sync (Conflict Resolution)

- Outbox pattern: local changes queue → push when online
- `POST /v1/sync/push` — last-write-wins via `clientUpdatedAt` + `version`; no decryption needed
- `POST /v1/sync/pull?cursor=&limit=` — incremental pull ordered by `clientUpdatedAt`
- Server stores: `{ id, userId, type, version, encryptedPayloadB64, clientUpdatedAt, deleted, status }`

### Authentication

- Email/password + Google OAuth (`idToken` validated server-side via `google-auth-library`)
- JWT access token (short-lived) + refresh token (hashed in DB, revocable)
- Token flow: `apps/api/src/routes/auth.ts`
- Android: JWT stored in `EncryptedSharedPreferences`, injected via Retrofit interceptor
- Admin access: role field (`user | admin | super_admin`) + email allowlist (`apps/api/src/lib/admin-allowlist.ts`)

### API Contract

All endpoints under `/v1`. Error format:
```json
{ "error": { "code": "string", "message": "string", "details": {} } }
```

Key route groups: `/v1/auth/*`, `/v1/sync/*`, `/v1/crypto/*`, `/v1/user/*`, `/v1/answers/*`, `/v1/reminders/*`, `/v1/admin/*` (allowlisted emails only), `/v1/public/requests` (Roulette feature).

### Android Architecture

Layers: `data/local/` (Room DB v5, DAOs, entities) → `data/remote/` (Retrofit) → `data/sync/SyncRepository.kt` → `ui/screens/` (Jetpack Compose) → `di/` (Hilt + manual DI).

Build flavors: `dev` (package suffix `.dev`) and `prod`. `compileSdk=35`, `minSdk=26`.

### Web Architecture

Next.js App Router. Environment loaded via `apps/web/lib/env.ts` (`NEXT_PUBLIC_API_URL`, `NEXT_PUBLIC_GOOGLE_WEB_CLIENT_ID`, `NEXT_PUBLIC_CANONICAL_HOST`). API client: `apps/web/lib/api.ts`.

---

## Key Conventions

- DB: `snake_case` columns; JSON API: `camelCase` fields
- TypeScript strict mode throughout (`tsconfig.json` at root)
- Rate limiting on auth and sync endpoints
- Never log prayer content, passphrase, or DEK in server logs
- New features: implement backend/API first, then frontend
- Branch strategy: `feat/*` and `fix/*` → merge to `dev` → merge to `main` (never directly to `main`)
- CI blocks merge if `STRESS TEST` strings, `delay(2000)`, or missing sync types are detected

---

## Environment Variables

### API (`apps/api/.env`)
```
DATABASE_URL=postgresql://...
JWT_SECRET=<min 32 chars>
GOOGLE_WEB_CLIENT_ID=
CORS_ORIGINS=https://pr4y.cl,http://localhost:3000
```

### Web (`apps/web/.env.local`)
```
NEXT_PUBLIC_API_URL=
NEXT_PUBLIC_API_BASE_URL=
NEXT_PUBLIC_GOOGLE_WEB_CLIENT_ID=
NEXT_PUBLIC_CANONICAL_HOST=
```

### Android
Set `googleWebClientId` in `apps/mobile-android/gradle.properties`. `API_BASE_URL` is hardcoded per `buildType` in `app/build.gradle.kts`.

---

## Deployment

- **API**: Railway — `pnpm --filter @pr4y/api build && pnpm --filter @pr4y/api start` (runs `prisma migrate deploy` on start)
- **Web**: Vercel — configured via `vercel.json`; filter `@pr4y/web`
- **Android**: Signed APK/AAB via `assembleRelease`, distributed via Google Play
