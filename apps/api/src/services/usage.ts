import { prisma } from '../lib/db';

/** Fecha "día" en UTC (sin hora). */
function toDay(date: Date): Date {
  const d = new Date(date);
  d.setUTCHours(0, 0, 0, 0);
  return d;
}

/**
 * Registra metadatos de uso de sync (bytes subidos/bajados). No almacena contenido; compatible con E2EE.
 */
export async function recordSyncUsage(
  userId: string,
  kind: 'push' | 'pull',
  bytes: number,
  count: number
): Promise<void> {
  const day = toDay(new Date());
  try {
    if (kind === 'push') {
      await prisma.usageLog.upsert({
        where: {
          userId_day: { userId, day },
        },
        create: {
          userId,
          day,
          bytesPushed: BigInt(bytes),
          bytesPulled: BigInt(0),
          pushCount: count,
          pullCount: 0,
        },
        update: {
          bytesPushed: { increment: BigInt(bytes) },
          pushCount: { increment: count },
        },
      });
    } else {
      await prisma.usageLog.upsert({
        where: {
          userId_day: { userId, day },
        },
        create: {
          userId,
          day,
          bytesPushed: BigInt(0),
          bytesPulled: BigInt(bytes),
          pushCount: 0,
          pullCount: count,
        },
        update: {
          bytesPulled: { increment: BigInt(bytes) },
          pullCount: { increment: count },
        },
      });
    }
  } catch (e) {
    // No fallar la operación de sync por métricas
    if (process.env.NODE_ENV !== 'test') {
      console.warn('[usage] recordSyncUsage failed', e);
    }
  }
}

export interface UsageStatsRow {
  totalUsers: number;
  totalRecords: number;
  totalBlobBytes: bigint;
  syncsToday: number;
  bytesPushedToday: bigint;
  bytesPulledToday: bigint;
}

/** Estadísticas agregadas para panel admin (sin PII). Incluye uso de almacenamiento de blobs cifrados. */
export async function getUsageStats(daysBack: number = 7): Promise<UsageStatsRow & { byDay: Array<{ day: string; usersActive: number; bytesPushed: bigint; bytesPulled: bigint }> }> {
  const since = new Date();
  since.setUTCDate(since.getUTCDate() - daysBack);
  since.setUTCHours(0, 0, 0, 0);

  const [totalUsers, totalRecords, blobResult, todayLogs, byDayRows] = await Promise.all([
    prisma.user.count(),
    prisma.record.count(),
    prisma.$queryRaw<[{ total_blob_bytes: bigint }]>`SELECT COALESCE(SUM(LENGTH(encrypted_payload_b64)), 0)::bigint AS total_blob_bytes FROM records`,
    prisma.usageLog.findMany({
      where: { day: toDay(new Date()) },
      select: { bytesPushed: true, bytesPulled: true, pushCount: true, pullCount: true },
    }),
    prisma.$queryRaw<
      Array<{ day: Date; users_active: number; bytes_pushed: bigint; bytes_pulled: bigint }>
    >`
      SELECT day, COUNT(DISTINCT user_id)::int AS users_active,
             COALESCE(SUM(bytes_pushed), 0)::bigint AS bytes_pushed,
             COALESCE(SUM(bytes_pulled), 0)::bigint AS bytes_pulled
      FROM usage_logs
      WHERE day >= ${since}
      GROUP BY day
      ORDER BY day ASC
    `,
  ]);

  const syncsToday = todayLogs.reduce((s, l) => s + l.pushCount + l.pullCount, 0);
  const bytesPushedToday = todayLogs.reduce((s, l) => s + l.bytesPushed, BigInt(0));
  const bytesPulledToday = todayLogs.reduce((s, l) => s + l.bytesPulled, BigInt(0));

  const byDay = byDayRows.map((r) => ({
    day: r.day.toISOString().slice(0, 10),
    usersActive: Number(r.users_active),
    bytesPushed: r.bytes_pushed,
    bytesPulled: r.bytes_pulled,
  }));

  const totalBlobBytes = blobResult[0]?.total_blob_bytes ?? BigInt(0);

  return {
    totalUsers,
    totalRecords,
    totalBlobBytes,
    syncsToday,
    bytesPushedToday,
    bytesPulledToday,
    byDay,
  };
}
