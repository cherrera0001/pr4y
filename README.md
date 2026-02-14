# PR4Y

PR4Y es un cuaderno personal de oración: pedidos + notas + seguimiento + recordatorios suaves.  
Es **personal** y **privado**, con enfoque **mobile-first** (web usable en móvil) y backend para **sincronización opcional**. El contenido se cifra del lado cliente (E2EE): el servidor almacena blobs cifrados, no puede leerlos.

## Stack
- Web: Next.js + TypeScript (Vercel)
- API: Node.js + Fastify + TypeScript (Railway)
- DB: PostgreSQL (Railway)
- Shared: Zod schemas + utilidades crypto (packages/shared)

## Repo
- `apps/web`: UI + offline storage + cifrado + sync
- `apps/api`: auth + sync endpoints
- `packages/shared`: tipos, esquemas, helpers
- `docs/`: arquitectura y contrato API

## Principios
- Offline-first: la web funciona sin conexión (IndexedDB).
- Privacidad: E2EE. El backend nunca guarda contenido en claro.
- Simplicidad: sin funciones sociales.

## Setup local

### Requisitos
- Node.js LTS
- pnpm
- PostgreSQL local (opcional; recomendado para correr API completo)

### Variables de entorno

`apps/api/.env`
- `DATABASE_URL=postgresql://...`
- `APP_BASE_URL=http://localhost:3000` (web)
- `API_BASE_URL=http://localhost:4000`
- `JWT_SECRET=...` (si se usa JWT)
- `MAGIC_LINK_SECRET=...` (si se usa magic link)
- `SMTP_HOST=...` `SMTP_PORT=...` `SMTP_USER=...` `SMTP_PASS=...` (si envías emails)
- `CORS_ORIGIN=http://localhost:3000`

`apps/web/.env.local`
- `NEXT_PUBLIC_API_BASE_URL=http://localhost:4000`
- `NEXT_PUBLIC_APP_NAME=PR4Y`

### Instalar
```bash
pnpm install
