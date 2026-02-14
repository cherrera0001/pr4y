import { prisma } from '../lib/db';

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
}

export async function pull(userId: string, cursor: string | undefined, limit: number): Promise<{ nextCursor: string; records: SyncRecord[] }> {
  const take = Math.min(Math.max(1, limit || DEFAULT_LIMIT), MAX_LIMIT);
  const orderBy = { clientUpdatedAt: 'asc' as const };
  const where = { userId };
  const records = cursor
    ? await prisma.record.findMany({
        where,
        orderBy,
        take: take + 1,
        cursor: { id: cursor },
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
  }));
  const nextCursor = list.length > 0 ? list[list.length - 1].recordId : '';
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
    const existing = await prisma.record.findUnique({ where: { id: rec.recordId } });
    if (existing) {
      if (existing.userId !== userId) {
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
    } catch (e) {
      rejected.push({ recordId: rec.recordId, reason: 'database error' });
    }
  }

  return { accepted, rejected, serverTime };
}
