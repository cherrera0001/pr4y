# PR4Y

**Cuaderno personal de oración**: pedidos, notas, seguimiento y recordatorios suaves.  
**E2EE · Offline-first · Privacy-focused.**

El contenido se cifra en el dispositivo (cifrado de extremo a extremo). El servidor almacena solo blobs cifrados y **nunca** puede leer tus oraciones ni notas.

---

## Value proposition

- **E2EE**: Todo el contenido se cifra en el cliente. El backend no tiene las claves ni puede descifrar.
- **Offline-first**: La app funciona sin conexión; la sincronización es opcional cuando vuelves a tener red.
- **Privacy-focused**: Sin redes sociales, sin tracking. Zero-knowledge: el servidor no conoce el contenido.

---

## Stack técnico

| Capa        | Tecnología                    |
|------------|-------------------------------|
| **Android**| Kotlin, Jetpack Compose       |
| **API**    | Node.js, Fastify, TypeScript  |
| **DB**     | PostgreSQL, Prisma            |
| **Deploy** | Railway (API + Postgres)      |
| **Web**    | Next.js (placeholder / PWA)   |

- **apps/api**: Auth (email+password, JWT+refresh), crypto (wrapped DEK), sync (pull/push).
- **apps/mobile-android**: Offline-first, Room, cifrado en dispositivo, sync con la API.
- **docs/**: Arquitectura, contrato API, seguridad. Ver [docs/README.md](docs/README.md) como índice.

---

## Guía de inicio rápido

### Requisitos

- Node.js LTS, **pnpm**, PostgreSQL (local o Docker).

### 1. Clonar e instalar

```bash
git clone <repo>
cd pr4y
pnpm install
```

(Si más adelante cambias algún `package.json`, ejecuta siempre `pnpm install` desde la **raíz** y commitea `pnpm-lock.yaml` para evitar desincronización en CI/Vercel.)

### 2. Configurar el backend

Copia `apps/api/.env.example` a `apps/api/.env` y define:

| Variable       | Descripción |
|----------------|-------------|
| `DATABASE_URL` | `postgresql://user:password@localhost:5432/pr4y` |
| `JWT_SECRET`   | Mínimo 32 caracteres; en producción usar valor seguro |
| `CORS_ORIGIN`  | Opcional; p. ej. `http://localhost:3000` |

### 3. Base de datos (Postgres)

**Opción A – Local**

```bash
createdb pr4y
# En .env: DATABASE_URL=postgresql://user:password@localhost:5432/pr4y
```

**Opción B – Docker**

```bash
docker run -d --name pr4y-postgres -e POSTGRES_USER=pr4y -e POSTGRES_PASSWORD=pr4y -e POSTGRES_DB=pr4y -p 5432:5432 postgres:16-alpine
# En .env: DATABASE_URL=postgresql://pr4y:pr4y@localhost:5432/pr4y
```

### 4. Migraciones y API

```bash
pnpm --filter @pr4y/api db:migrate
pnpm --filter @pr4y/api dev
```

API en **http://localhost:4000**. Health: `GET http://localhost:4000/v1/health`.

### 5. App Android

- Abre `apps/mobile-android` en Android Studio, sincroniza Gradle.
- Crea un AVD (API 26+) y ejecuta la app (Run o `./gradlew installDebug`).
- Por defecto la app apunta la API a `http://10.0.2.2:4000` (emulador → localhost).

---

## Security disclosure (Zero-Knowledge)

PR4Y está diseñado para que el servidor **no pueda** acceder al contenido de tus oraciones ni notas:

- Las claves de cifrado se derivan de una passphrase que **solo conoces tú** y no se envían al servidor.
- El backend guarda y sincroniza **solo datos cifrados** (blobs).
- No hay recuperación de contraseña que permita al servidor descifrar tu contenido.

Para detalles de amenazas y controles, ver [docs/security/](docs/security/).

---

## Deploy

Pasos exactos para Railway (API + Postgres) y variables de entorno críticas: **[DEPLOY.md](DEPLOY.md)**.

---

## Antes de cada push (pre-flight)

Ejecuta el script de pre-vuelo para evitar subir código de prueba o retrasos artificiales:

```bash
./scripts/pre-flight-check.sh
```

En Windows (Git Bash o WSL): `bash scripts/pre-flight-check.sh`.

---

## Documentación detallada

- **Contribuir** (flujo E2EE, estándares, cómo no romper el cifrado): **[CONTRIBUTING.md](CONTRIBUTING.md)**.
- Índice de arquitectura, API, seguridad y producto: **[docs/README.md](docs/README.md)**.
