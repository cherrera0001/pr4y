import { prisma } from '../lib/db';
import { recordSyncUsage } from './usage';

const DEFAULT_LIMIT = 100;
const MAX_LIMIT = 500;

export interface SyncRecord {
  recordId: string;
  type: string;
  version: number;
  encryptedPayloadB64: string;
  clientUpdatedAt: string;
  serverUpdatedAt: string;
  deleted: boolean;
  status: string;
}

/**
 * Pull: aislamiento estricto por usuario. Solo se devuelven registros del userId del JWT.
 * El cursor se valida para que no se pueda paginar usando un recordId de otro usuario.
 */
export async function pull(userId: string, cursor: string | undefined, limit: number): Promise<{ nextCursor: string; records: SyncRecord[] }> {
  const take = Math.min(Math.max(1, limit || DEFAULT_LIMIT), MAX_LIMIT);
  const orderBy = { clientUpdatedAt: 'asc' as const };
  const where = { userId };

  let effectiveCursor: string | undefined = cursor;
  if (cursor && cursor.trim() !== '') {
    const cursorRecord = await prisma.record.findUnique({ where: { id: cursor }, select: { userId: true } });
    if (cursorRecord && cursorRecord.userId !== userId) {
      effectiveCursor = undefined;
    }
  }

  const records = effectiveCursor
    ? await prisma.record.findMany({
        where,
        orderBy,
        take: take + 1,
        cursor: { id: effectiveCursor },
      })
    : await prisma.record.findMany({
        where,
        orderBy,
        take: take + 1,
      });
  const hasMore = records.length > take;
  const list = (hasMore ? records.slice(0, take) : records).map((r) => ({
    recordId: r.id,
    type: r.type,
    version: r.version,
    encryptedPayloadB64: r.encryptedPayloadB64,
    clientUpdatedAt: r.clientUpdatedAt.toISOString(),
    serverUpdatedAt: r.serverUpdatedAt.toISOString(),
    deleted: r.deleted,
    status: r.status,
  }));
  const nextCursor = list.length > 0 ? list[list.length - 1].recordId : '';
  const bytesPulled = list.reduce((sum, r) => sum + Buffer.byteLength(r.encryptedPayloadB64, 'utf8'), 0);
  recordSyncUsage(userId, 'pull', bytesPulled, list.length).catch(() => {});
  return { nextCursor: hasMore ? nextCursor : '', records: list };
}

export interface PushRecordInput {
  recordId: string;
  type: string;
  version: number;
  encryptedPayloadB64: string;
  clientUpdatedAt: string;
  deleted: boolean;
}

export interface RejectedItem {
  recordId: string;
  reason: string;
  /** Present when reason === 'version conflict': server's current version and timestamp for client resolution */
  serverVersion?: number;
  serverUpdatedAt?: string;
}

export interface PushResult {
  accepted: string[];
  rejected: RejectedItem[];
  serverTime: string;
}

/** Log conflict for internal metrics (no PII). */
function logConflict(recordId: string, reason: string, meta?: { serverVersion?: number }) {
  const payload = { event: 'sync_push_conflict', recordId, reason, ...meta };
  if (process.env.NODE_ENV !== 'test') {
    console.info('[sync]', JSON.stringify(payload));
  }
}

/** Log when a device tries to sync a record that belongs to another user (ownership mismatch / desincronización). */
function logOwnershipMismatch(recordId: string) {
  const payload = { event: 'sync_push_ownership_mismatch', recordId, message: 'Device attempted to push record owned by another user' };
  if (process.env.NODE_ENV !== 'test') {
    console.warn('[sync]', JSON.stringify(payload));
  }
}

/**
 * Push: validación de propiedad estricta. Cada recordId debe pertenecer al userId del JWT.
 * Si un registro ya existe bajo otro dueño, se rechaza con 'forbidden'. El servidor es el
 * guardián final para que un usuario no pueda, ni por error, subir datos como si fueran de otro.
 */
export async function push(userId: string, records: PushRecordInput[]): Promise<PushResult> {
  const serverTime = new Date().toISOString();
  const accepted: string[] = [];
  const rejected: RejectedItem[] = [];

  for (const rec of records) {
    const clientUpdatedAt = new Date(rec.clientUpdatedAt);
    if (isNaN(clientUpdatedAt.getTime())) {
      rejected.push({ recordId: rec.recordId, reason: 'invalid clientUpdatedAt' });
      continue;
    }
    const existing = await prisma.record.findUnique({ where: { id: rec.recordId }, select: { userId: true, version: true, serverUpdatedAt: true } });
    if (existing) {
      if (existing.userId !== userId) {
        logOwnershipMismatch(rec.recordId);
        rejected.push({ recordId: rec.recordId, reason: 'forbidden' });
        continue;
      }
      if (rec.version <= existing.version) {
        logConflict(rec.recordId, 'version conflict', { serverVersion: existing.version });
        rejected.push({
          recordId: rec.recordId,
          reason: 'version conflict',
          serverVersion: existing.version,
          serverUpdatedAt: existing.serverUpdatedAt.toISOString(),
        });
        continue;
      }
    }
    try {
      await prisma.record.upsert({
        where: { id: rec.recordId },
        create: {
          id: rec.recordId,
          userId,
          type: rec.type,
          version: rec.version,
          encryptedPayloadB64: rec.encryptedPayloadB64,
          clientUpdatedAt,
          deleted: rec.deleted,
        },
        update: {
          type: rec.type,
          version: rec.version,
          encryptedPayloadB64: rec.encryptedPayloadB64,
          clientUpdatedAt,
          deleted: rec.deleted,
        },
      });
      accepted.push(rec.recordId);
    } catch (e: unknown) {
      const prismaCode = e && typeof e === 'object' && 'code' in e ? (e as { code: string }).code : undefined;
      if (prismaCode === 'P2002') {
        const recheck = await prisma.record.findUnique({ where: { id: rec.recordId }, select: { userId: true } });
        if (recheck && recheck.userId !== userId) {
          logOwnershipMismatch(rec.recordId);
          rejected.push({ recordId: rec.recordId, reason: 'forbidden' });
        } else {
          rejected.push({ recordId: rec.recordId, reason: 'database error' });
        }
      } else {
        rejected.push({ recordId: rec.recordId, reason: 'database error' });
      }
    }
  }

  const bytesPushed = records
    .filter((r) => accepted.includes(r.recordId))
    .reduce((sum, r) => sum + Buffer.byteLength(r.encryptedPayloadB64, 'utf8'), 0);
  recordSyncUsage(userId, 'push', bytesPushed, accepted.length).catch(() => {});

  return { accepted, rejected, serverTime };
}
