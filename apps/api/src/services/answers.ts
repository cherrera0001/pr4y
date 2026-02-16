import { prisma } from '../lib/db';
import { sanitizeTextInputs } from '../lib/sanitize';

/** Crear testimonio (marcar pedido como respondido y opcionalmente texto). */
export async function createAnswer(
  userId: string,
  recordId: string,
  testimony?: string | null
): Promise<{ ok: true; answer: object } | { ok: false; error: string }> {
  const record = await prisma.record.findUnique({
    where: { id: recordId },
    select: { userId: true },
  });
  if (!record || record.userId !== userId) {
    return { ok: false, error: 'record not found or not owned' };
  }

  let sanitizedTestimony: string | null = null;
  if (testimony != null && String(testimony).trim() !== '') {
    const sanitized = sanitizeTextInputs({ testimony: String(testimony).trim() }, ['testimony']);
    if (!sanitized.success) {
      return { ok: false, error: sanitized.error };
    }
    sanitizedTestimony = (sanitized.data as { testimony: string }).testimony;
  }

  const [answer] = await prisma.$transaction([
    prisma.answer.create({
      data: {
        recordId,
        testimony: sanitizedTestimony,
      },
    }),
    prisma.record.update({
      where: { id: recordId },
      data: { status: 'ANSWERED' },
    }),
  ]);

  return {
    ok: true,
    answer: {
      id: answer.id,
      recordId: answer.recordId,
      answeredAt: answer.answeredAt.toISOString(),
      testimony: answer.testimony,
    },
  };
}

/** Listar testimonios del usuario (pedidos respondidos) para el Muro de Fe. */
export async function listByUser(userId: string) {
  const answers = await prisma.answer.findMany({
    where: { record: { userId } },
    include: { record: { select: { id: true, type: true, clientUpdatedAt: true } } },
    orderBy: { answeredAt: 'desc' },
  });
  return answers.map((a) => ({
    id: a.id,
    recordId: a.recordId,
    answeredAt: a.answeredAt.toISOString(),
    testimony: a.testimony,
    record: {
      id: a.record.id,
      type: a.record.type,
      clientUpdatedAt: a.record.clientUpdatedAt.toISOString(),
    },
  }));
}

export async function getById(userId: string, id: string) {
  const a = await prisma.answer.findFirst({
    where: { id, record: { userId } },
    include: { record: { select: { id: true, type: true, clientUpdatedAt: true } } },
  });
  if (!a) return null;
  return {
    id: a.id,
    recordId: a.recordId,
    answeredAt: a.answeredAt.toISOString(),
    testimony: a.testimony,
    record: {
      id: a.record.id,
      type: a.record.type,
      clientUpdatedAt: a.record.clientUpdatedAt.toISOString(),
    },
  };
}
