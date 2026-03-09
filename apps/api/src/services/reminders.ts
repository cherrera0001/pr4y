import { prisma } from '../lib/db';

const TIME_PATTERN = /^([01]?\d|2[0-3]):([0-5]\d)$/;

export type ReminderCreate = {
  recordId: string;
  time: string;
  daysOfWeek: number[];
  isEnabled?: boolean;
};

export type ReminderUpdate = {
  time?: string;
  daysOfWeek?: number[];
  isEnabled?: boolean;
};

function isValidTime(s: string): boolean {
  return TIME_PATTERN.test(s);
}

function isValidDaysOfWeek(arr: unknown): arr is number[] {
  return Array.isArray(arr) && arr.length <= 7 && arr.every((d) => typeof d === 'number' && d >= 0 && d <= 6);
}

/** Verifica que el record exista y pertenezca al usuario. */
async function ensureRecordOwnership(userId: string, recordId: string): Promise<boolean> {
  const record = await prisma.record.findUnique({
    where: { id: recordId },
    select: { userId: true },
  });
  return record?.userId === userId;
}

export async function listByUser(userId: string) {
  const reminders = await prisma.reminder.findMany({
    where: { record: { userId } },
    include: { record: { select: { id: true, type: true } } },
    orderBy: [{ recordId: 'asc' }, { time: 'asc' }],
  });
  return reminders.map((r) => ({
    id: r.id,
    recordId: r.recordId,
    time: r.time,
    daysOfWeek: r.daysOfWeek as number[],
    isEnabled: r.isEnabled,
    createdAt: r.createdAt.toISOString(),
    updatedAt: r.updatedAt.toISOString(),
  }));
}

export async function getById(userId: string, id: string) {
  const r = await prisma.reminder.findFirst({
    where: { id, record: { userId } },
    include: { record: { select: { id: true, type: true } } },
  });
  if (!r) return null;
  return {
    id: r.id,
    recordId: r.recordId,
    time: r.time,
    daysOfWeek: r.daysOfWeek as number[],
    isEnabled: r.isEnabled,
    createdAt: r.createdAt.toISOString(),
    updatedAt: r.updatedAt.toISOString(),
  };
}

export async function create(userId: string, data: ReminderCreate) {
  if (!isValidTime(data.time)) {
    return { ok: false as const, error: 'time must be HH:mm (e.g. 09:00)' };
  }
  if (!isValidDaysOfWeek(data.daysOfWeek)) {
    return { ok: false as const, error: 'daysOfWeek must be an array of 0-6 (0=Sunday)' };
  }
  const owned = await ensureRecordOwnership(userId, data.recordId);
  if (!owned) {
    return { ok: false as const, error: 'record not found or not owned' };
  }
  const reminder = await prisma.reminder.create({
    data: {
      recordId: data.recordId,
      time: data.time,
      daysOfWeek: data.daysOfWeek,
      isEnabled: data.isEnabled ?? true,
    },
  });
  return {
    ok: true as const,
    reminder: {
      id: reminder.id,
      recordId: reminder.recordId,
      time: reminder.time,
      daysOfWeek: reminder.daysOfWeek as number[],
      isEnabled: reminder.isEnabled,
      createdAt: reminder.createdAt.toISOString(),
      updatedAt: reminder.updatedAt.toISOString(),
    },
  };
}

export async function update(userId: string, id: string, data: ReminderUpdate) {
  if (data.time !== undefined && !isValidTime(data.time)) {
    return { ok: false as const, error: 'time must be HH:mm' };
  }
  if (data.daysOfWeek !== undefined && !isValidDaysOfWeek(data.daysOfWeek)) {
    return { ok: false as const, error: 'daysOfWeek must be an array of 0-6' };
  }
  const existing = await prisma.reminder.findFirst({
    where: { id, record: { userId } },
  });
  if (!existing) {
    return { ok: false as const, error: 'reminder not found' };
  }
  const reminder = await prisma.reminder.update({
    where: { id },
    data: {
      ...(data.time !== undefined && { time: data.time }),
      ...(data.daysOfWeek !== undefined && { daysOfWeek: data.daysOfWeek }),
      ...(data.isEnabled !== undefined && { isEnabled: data.isEnabled }),
    },
  });
  return {
    ok: true as const,
    reminder: {
      id: reminder.id,
      recordId: reminder.recordId,
      time: reminder.time,
      daysOfWeek: reminder.daysOfWeek as number[],
      isEnabled: reminder.isEnabled,
      createdAt: reminder.createdAt.toISOString(),
      updatedAt: reminder.updatedAt.toISOString(),
    },
  };
}

export async function remove(userId: string, id: string) {
  const existing = await prisma.reminder.findFirst({
    where: { id, record: { userId } },
  });
  if (!existing) {
    return { ok: false as const, error: 'reminder not found' };
  }
  await prisma.reminder.delete({ where: { id } });
  return { ok: true as const };
}
