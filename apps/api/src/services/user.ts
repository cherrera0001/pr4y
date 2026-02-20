import { prisma } from '../lib/db';

const TIME_PATTERN = /^([01]?\d|2[0-3]):([0-5]\d)$/;

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
