import { prisma } from '../lib/db';

const TIME_PATTERN = /^([01]?\d|2[0-3]):([0-5]\d)$/;

// ---------------------------------------------------------------------------
// Display Preferences
// ---------------------------------------------------------------------------

export type DisplayPreferences = {
  theme: 'light' | 'dark' | 'system';
  fontSize: 'sm' | 'md' | 'lg' | 'xl';
  fontFamily: 'system' | 'serif' | 'mono';
  lineSpacing: 'compact' | 'normal' | 'relaxed';
  contemplativeMode: boolean;
};

const DISPLAY_DEFAULTS: DisplayPreferences = {
  theme: 'system',
  fontSize: 'md',
  fontFamily: 'system',
  lineSpacing: 'normal',
  contemplativeMode: false,
};

const VALID_THEMES = ['light', 'dark', 'system'] as const;
const VALID_FONT_SIZES = ['sm', 'md', 'lg', 'xl'] as const;
const VALID_FONT_FAMILIES = ['system', 'serif', 'mono'] as const;
const VALID_LINE_SPACINGS = ['compact', 'normal', 'relaxed'] as const;

function parseDisplayPrefs(raw: unknown): DisplayPreferences {
  if (!raw || typeof raw !== 'object') return { ...DISPLAY_DEFAULTS };
  const r = raw as Record<string, unknown>;
  return {
    theme: VALID_THEMES.includes(r.theme as DisplayPreferences['theme'])
      ? (r.theme as DisplayPreferences['theme'])
      : DISPLAY_DEFAULTS.theme,
    fontSize: VALID_FONT_SIZES.includes(r.fontSize as DisplayPreferences['fontSize'])
      ? (r.fontSize as DisplayPreferences['fontSize'])
      : DISPLAY_DEFAULTS.fontSize,
    fontFamily: VALID_FONT_FAMILIES.includes(r.fontFamily as DisplayPreferences['fontFamily'])
      ? (r.fontFamily as DisplayPreferences['fontFamily'])
      : DISPLAY_DEFAULTS.fontFamily,
    lineSpacing: VALID_LINE_SPACINGS.includes(r.lineSpacing as DisplayPreferences['lineSpacing'])
      ? (r.lineSpacing as DisplayPreferences['lineSpacing'])
      : DISPLAY_DEFAULTS.lineSpacing,
    contemplativeMode: typeof r.contemplativeMode === 'boolean'
      ? r.contemplativeMode
      : DISPLAY_DEFAULTS.contemplativeMode,
  };
}

export async function getDisplayPreferences(userId: string): Promise<DisplayPreferences> {
  const user = await prisma.user.findUnique({
    where: { id: userId },
    select: { displayPreferences: true },
  });
  return parseDisplayPrefs(user?.displayPreferences);
}

export async function updateDisplayPreferences(
  userId: string,
  data: Partial<DisplayPreferences>
): Promise<DisplayPreferences> {
  const current = await getDisplayPreferences(userId);
  const merged: DisplayPreferences = { ...current, ...data };
  await prisma.user.update({
    where: { id: userId },
    data: { displayPreferences: merged },
  });
  return merged;
}

export type ReminderPreferences = {
  time: string;
  daysOfWeek: number[];
  enabled: boolean;
};

/**
 * Preferencias de recordatorio diario de oración (sin recordId).
 */
export async function getReminderPreferences(userId: string): Promise<ReminderPreferences> {
  const user = await prisma.user.findUnique({
    where: { id: userId },
    select: { reminderTime: true, reminderDaysOfWeek: true, reminderEnabled: true },
  });
  return {
    time: user?.reminderTime ?? '09:00',
    daysOfWeek: (user?.reminderDaysOfWeek as number[] | null) ?? [1, 2, 3, 4, 5, 6],
    enabled: user?.reminderEnabled ?? false,
  };
}

export async function updateReminderPreferences(
  userId: string,
  data: { time?: string; daysOfWeek?: number[]; enabled?: boolean }
): Promise<ReminderPreferences> {
  if (data.time !== undefined && !TIME_PATTERN.test(data.time)) {
    throw new Error('time must be HH:mm');
  }
  if (data.daysOfWeek !== undefined) {
    if (!Array.isArray(data.daysOfWeek) || data.daysOfWeek.some((d) => typeof d !== 'number' || d < 0 || d > 6)) {
      throw new Error('daysOfWeek must be array of 0-6');
    }
  }
  const updated = await prisma.user.update({
    where: { id: userId },
    data: {
      ...(data.time !== undefined && { reminderTime: data.time }),
      ...(data.daysOfWeek !== undefined && { reminderDaysOfWeek: data.daysOfWeek }),
      ...(data.enabled !== undefined && { reminderEnabled: data.enabled }),
    },
    select: { reminderTime: true, reminderDaysOfWeek: true, reminderEnabled: true },
  });
  return {
    time: updated.reminderTime ?? '09:00',
    daysOfWeek: (updated.reminderDaysOfWeek as number[]) ?? [],
    enabled: updated.reminderEnabled,
  };
}

export type FaithStats = {
  totalRecords: number;
  totalAnswered: number;
  totalInProcess: number;
  totalPending: number;
  streakDays: number;
  longestStreakDays: number;
  firstEntryAt: string | null;
  recordsByType: { type: string; count: number }[];
};

/**
 * Estadísticas de fe del usuario, calculadas solo sobre metadatos no cifrados.
 * Nunca accede al contenido encryptedPayloadB64.
 */
export async function getFaithStats(userId: string): Promise<FaithStats> {
  const [statusCounts, usageLogs, firstRecord, typeCounts] = await Promise.all([
    prisma.record.groupBy({
      by: ['status'],
      where: { userId, deleted: false },
      _count: { id: true },
    }),
    prisma.usageLog.findMany({
      where: { userId },
      select: { day: true, pushCount: true, pullCount: true },
      orderBy: { day: 'desc' },
    }),
    prisma.record.findFirst({
      where: { userId, deleted: false },
      orderBy: { clientUpdatedAt: 'asc' },
      select: { clientUpdatedAt: true },
    }),
    prisma.record.groupBy({
      by: ['type'],
      where: { userId, deleted: false },
      _count: { id: true },
    }),
  ]);

  const totalAnswered = statusCounts.find((c) => c.status === 'ANSWERED')?._count.id ?? 0;
  const totalInProcess = statusCounts.find((c) => c.status === 'IN_PROCESS')?._count.id ?? 0;
  const totalPending = statusCounts.find((c) => c.status === 'PENDING')?._count.id ?? 0;
  const totalRecords = totalAnswered + totalInProcess + totalPending;

  // Conjunto de días con al menos un push o pull
  const activeDays = new Set(
    usageLogs
      .filter((l) => l.pushCount > 0 || l.pullCount > 0)
      .map((l) => l.day.toISOString().slice(0, 10))
  );

  // Streak actual: días consecutivos hacia atrás desde hoy (o ayer si hoy sin actividad)
  const today = new Date();
  today.setUTCHours(0, 0, 0, 0);
  const cursor = new Date(today);
  if (!activeDays.has(cursor.toISOString().slice(0, 10))) {
    cursor.setUTCDate(cursor.getUTCDate() - 1);
  }
  let streakDays = 0;
  while (activeDays.has(cursor.toISOString().slice(0, 10))) {
    streakDays++;
    cursor.setUTCDate(cursor.getUTCDate() - 1);
  }

  // Racha más larga histórica
  const sortedDays = Array.from(activeDays).sort();
  let longestStreakDays = 0;
  let currentStreak = 0;
  let prevMs = 0;
  for (const dayStr of sortedDays) {
    const ms = new Date(dayStr + 'T00:00:00Z').getTime();
    if (prevMs && ms - prevMs === 86_400_000) {
      currentStreak++;
    } else {
      currentStreak = 1;
    }
    if (currentStreak > longestStreakDays) longestStreakDays = currentStreak;
    prevMs = ms;
  }

  return {
    totalRecords,
    totalAnswered,
    totalInProcess,
    totalPending,
    streakDays,
    longestStreakDays,
    firstEntryAt: firstRecord?.clientUpdatedAt?.toISOString() ?? null,
    recordsByType: typeCounts.map((r) => ({ type: r.type, count: r._count.id })),
  };
}

/**
 * Purga todos los datos del usuario en el servidor (derecho al olvido en el búnker).
 * Elimina: registros (y por cascade reminders/answers), wrapped DEK, usage logs.
 * No elimina la cuenta (User) para que el usuario pueda volver a iniciar sesión con búnker vacío.
 */
export async function purgeUserData(userId: string): Promise<{ recordsDeleted: number; usageLogsDeleted: number }> {
  const recordsDeleted = await prisma.record.deleteMany({ where: { userId } });
  await prisma.wrappedDek.deleteMany({ where: { userId } });
  const usageLogsDeleted = await prisma.usageLog.deleteMany({ where: { userId } });
  return {
    recordsDeleted: recordsDeleted.count,
    usageLogsDeleted: usageLogsDeleted.count,
  };
}
