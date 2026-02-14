# PR4Y – Skills / Guía de Competencias del Proyecto

Este documento define las competencias técnicas, de producto y seguridad necesarias para construir y operar PR4Y: un cuaderno personal de oración (notas + pedidos + seguimiento + recordatorios). PR4Y es personal, privado y mobile-first, con versión web y backend para sincronización opcional.

## 1) Principios del producto (no negociables)
- Uso personal: sin feed, likes, ranking, seguidores, comentarios públicos.
- Privacidad por defecto: el contenido (texto de pedidos/notas) es sensible.
- Offline-first: el usuario debe poder usar la app sin conexión.
- Fricción mínima: capturar un pedido en < 15 segundos.
- Diseño calmado: sin gamificación agresiva, sin manipulación emocional.
- Exportación/backup: el usuario debe poder respaldar y restaurar.

## 2) Capacidades funcionales esenciales
- CRUD de Prayer Requests (pedidos).
- Estados: pendiente / en proceso / respondido.
- Historial: cambios de estado, notas vinculadas.
- Notas / Journal (texto libre, opcionalmente ligado a un pedido).
- Etiquetas y búsqueda.
- Recordatorios suaves (local notifications en móvil; web con limitaciones).
- Backup/restore (archivo cifrado del lado cliente).

## 3) Competencias técnicas necesarias
Frontend (Web):
- Next.js (App Router), TypeScript, UI minimalista (Tailwind o shadcn/ui).
- PWA (opcional) y almacenamiento local (IndexedDB).
- Sincronización incremental (pull/push) con control de conflictos.
- Cifrado en cliente: WebCrypto (AES-GCM) + derivación clave (PBKDF2/Argon2).

Backend (API):
- Node.js + Fastify + TypeScript.
- PostgreSQL (Railway).
- Autenticación: email + magic link (o email+password con Argon2). Recomendado magic link.
- Almacenamiento de “ciphertext blobs” (E2EE): server guarda y versiona, no interpreta.
- Rate limiting, audit logs (sin contenido sensible).

Base de datos:
- Diseño centrado en metadatos (no texto en claro).
- Tablas para users, devices/sessions, records (blobs cifrados), sync cursors.

DevOps:
- Railway: deploy API + Postgres + variables.
- Vercel: deploy Web + variables.
- CI: lint + test + typecheck.
- Observabilidad: logs estructurados, métricas básicas, alertas.

Seguridad:
- Threat model básico (pérdida de dispositivo, acceso no autorizado, fuga de backups, abuso de endpoints).
- Zero-knowledge: backend no puede leer contenido.
- Protección cuenta: rate limit, lockout, verificación email.
- Protección local: PIN/biometría (según plataforma), “app lock” en web (passphrase).

## 4) Definition of Done (DoD) mínimo para PRs
- Tests unitarios donde aplique (API) + smoke tests.
- Ninguna variable secreta en repo.
- Validación de esquemas (Zod) y manejo de errores consistente.
- Documentación actualizada (README + API docs).
- Migraciones DB (Prisma/SQL) versionadas.
- Revisión de seguridad: no logs de contenido, no exposición de secretos, CORS correcto.

## 5) Scope por etapas
MVP (fase 1):
- Web: cuaderno local (IndexedDB) + export/import cifrado.
- API: auth + sync de blobs cifrados.
- Sin app móvil nativa todavía (la web debe funcionar excelente en móvil).

Fase 2:
- App Android (Flutter o Kotlin) reutilizando contrato API y modelo E2EE.
- Notificaciones locales robustas (Android).

Fase 3:
- Sync avanzado multi-device con resolución de conflictos mejorada.
