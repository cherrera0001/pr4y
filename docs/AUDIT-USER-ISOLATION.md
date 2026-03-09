# Auditoría de aislamiento por usuario (User Isolation)

## Objetivo

Garantizar que **todas** las queries de Prisma que tocan datos de usuario incluyan un filtro por `userId` (o `record: { userId }` donde aplique), de modo que un usuario nunca pueda leer o modificar datos de otro.

## Regla

- **Record, Answer, Reminder, WrappedDek, UsageLog**: toda operación de lectura/escritura debe restringirse por usuario.
  - Formas válidas: `where: { userId }`, `where: { record: { userId } }`, `where: { id, userId }`, `where: { userId_day: { userId, day } }`.
- **User**: `findMany` sin `userId` solo está permitido en rutas **admin** (`requireAdmin`) que listan usuarios para el backoffice (ej. `listUsers`).
- **GlobalContent**: no es dato por usuario; no requiere `userId`.

## Excepciones documentadas (aceptables)

| Ubicación | Query | Motivo |
|-----------|--------|--------|
| `services/usage.ts` | `prisma.usageLog.findMany({ where: { day } })` | Estadísticas agregadas del día; solo métricas, sin PII. Ruta: admin. |
| `services/usage.ts` | `prisma.record.findFirst({ orderBy: { serverUpdatedAt: 'desc' } })` | Última actividad global de sync (solo timestamp). Ruta: admin stats/detail. |
| `services/admin.ts` | `prisma.user.findMany(...)` | Listado de usuarios para panel admin. Protegido por `requireAdmin`. |
| `services/admin.ts` | `prisma.globalContent.findMany/update/delete` | Contenido global; no asociado a usuario. |
| `services/auth.ts` | `prisma.user.findUnique({ where: { email } })` etc. | Autenticación por email/token; no expone datos de otro usuario. |

## Checklist manual

- [ ] Ningún endpoint de usuario (no admin) ejecuta `findMany`/`findFirst`/`findUnique` sobre `Record`, `Answer`, `Reminder`, `WrappedDek`, `UsageLog` sin `userId` o `record: { userId }`.
- [ ] Los endpoints admin que no filtran por usuario (`listUsers`, stats por día, etc.) están protegidos por `requireAdmin` y no devuelven contenido cifrado ni PII de un usuario concreto.
- [ ] Sync push/pull: solo registros del `userId` del JWT (validado en servicio).
- [ ] Records, answers, reminders: siempre pasan `userId` desde el JWT al servicio y el servicio filtra por él.

## Ejecución del script de auditoría

Desde la raíz del repo:

```bash
npx ts-node scripts/audit-user-isolation.ts
```

El script lista todas las llamadas a Prisma sobre modelos con datos de usuario y marca si incluyen filtro por usuario o son excepciones documentadas (admin/usage). Si alguna queda sin cubrir, el script termina con código 1.

## Modelos y relación con usuario

- **Record**: `userId` directo.
- **Answer**: vía `record.userId` → `where: { record: { userId } }`.
- **Reminder**: vía `record.userId` → `where: { record: { userId } }`.
- **WrappedDek**: `userId` directo.
- **UsageLog**: `userId` (o compuesto `userId_day` en upsert).
