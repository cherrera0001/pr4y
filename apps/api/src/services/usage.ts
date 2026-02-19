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

/** Tipo para el endpoint de detalle: pulso de la app (DAU + volumen de sincronización) sin tocar contenido de usuario. */
export interface UsageStatsDetailRow {
  dau: {
    today: number;
    byDay: Array< { day: string; usersActive: number } >;
  };
  syncVolume: {
    syncsToday: number;
    bytesPushedToday: string;
    bytesPulledToday: string;
    byDay: Array< { day: string; bytesPushed: string; bytesPulled: string } >;
  };
  totals: {
    totalUsers: number;
    totalRecords: number;
    totalBlobBytes: string;
  };
}

/**
 * Estadísticas detalladas para GET /v1/admin/stats/detail.
 * Permite al administrador ver el pulso de la app (DAU, volumen de sincronización) sin acceder a contenido.
 */
export async function getUsageStatsDetail(daysBack: number = 7): Promise<UsageStatsDetailRow> {
  const full = await getUsageStats(daysBack);
  return {
    dau: {
      today: full.byDay.find((d) => d.day === new Date().toISOString().slice(0, 10))?.usersActive ?? 0,
      byDay: full.byDay.map((d) => ({ day: d.day, usersActive: d.usersActive })),
    },
    syncVolume: {
      syncsToday: full.syncsToday,
      bytesPushedToday: String(full.bytesPushedToday),
      bytesPulledToday: String(full.bytesPulledToday),
      byDay: full.byDay.map((d) => ({
        day: d.day,
        bytesPushed: String(d.bytesPushed),
        bytesPulled: String(d.bytesPulled),
      })),
    },
    totals: {
      totalUsers: full.totalUsers,
      totalRecords: full.totalRecords,
      totalBlobBytes: String(full.totalBlobBytes),
    },
  };
}

/** Formatea "hace X min/horas/días" en español. */
function formatLastActivity(at: Date | null): string {
  if (!at) return 'Nunca';
  const now = new Date();
  const diffMs = now.getTime() - at.getTime();
  const diffMin = Math.floor(diffMs / 60_000);
  const diffHours = Math.floor(diffMs / 3_600_000);
  const diffDays = Math.floor(diffMs / 86_400_000);
  if (diffMin < 1) return 'Hace un momento';
  if (diffMin < 60) return `Hace ${diffMin} min`;
  if (diffHours < 24) return `Hace ${diffHours} h`;
  if (diffDays < 7) return `Hace ${diffDays} días`;
  return at.toISOString().slice(0, 10);
}

export interface StatsDetailRow {
  lastSyncActivity: string;
  recordsByTypeByDay: Array<{ day: string; type: string; count: number }>;
}

/**
 * Estadísticas de detalle para admin: última actividad de sync y volumen por tipo/día.
 * Sin IDs de usuarios ni correos (solo pulso del sistema, Zero-Knowledge).
 */
export async function getStatsDetail(daysBack: number = 7): Promise<StatsDetailRow> {
  const since = new Date();
  since.setUTCDate(since.getUTCDate() - daysBack);
  since.setUTCHours(0, 0, 0, 0);

  const [lastSyncRow, byDayTypeRows] = await Promise.all([
    prisma.record.findFirst({
      orderBy: { serverUpdatedAt: 'desc' },
      select: { serverUpdatedAt: true },
    }),
    prisma.$queryRaw<
      Array<{ day: Date; type: string; count: bigint }>
    >`
      SELECT (server_updated_at)::date AS day, type, COUNT(*)::bigint AS count
      FROM records
      WHERE server_updated_at >= ${since}
      GROUP BY (server_updated_at)::date, type
      ORDER BY day ASC, type ASC
    `,
  ]);

  const lastSyncAt = lastSyncRow?.serverUpdatedAt ?? null;
  const recordsByTypeByDay = byDayTypeRows.map((r) => ({
    day: r.day instanceof Date ? r.day.toISOString().slice(0, 10) : String(r.day).slice(0, 10),
    type: r.type,
    count: Number(r.count),
  }));

  return {
    lastSyncActivity: formatLastActivity(lastSyncAt),
    recordsByTypeByDay,
  };
}
