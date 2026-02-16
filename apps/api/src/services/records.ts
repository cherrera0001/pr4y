import { prisma } from '../lib/db';

const VALID_STATUSES = ['PENDING', 'IN_PROCESS', 'ANSWERED'] as const;

export async function updateRecordStatus(
  userId: string,
  recordId: string,
  status: string
): Promise<{ ok: true; record: { recordId: string; status: string } } | { ok: false; error: string }> {
  if (!VALID_STATUSES.includes(status as (typeof VALID_STATUSES)[number])) {
    return { ok: false, error: 'status must be PENDING, IN_PROCESS, or ANSWERED' };
  }
  const record = await prisma.record.findFirst({
    where: { id: recordId, userId },
  });
  if (!record) {
    return { ok: false, error: 'record not found' };
  }
  await prisma.record.update({
    where: { id: recordId },
    data: { status },
  });
  return { ok: true, record: { recordId, status } };
}
