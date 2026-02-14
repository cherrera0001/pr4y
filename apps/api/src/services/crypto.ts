import { prisma } from '../lib/db';

export async function putWrappedDek(
  userId: string,
  data: { kdf: { name: string; params: Record<string, unknown>; saltB64: string }; wrappedDekB64: string }
) {
  await prisma.wrappedDek.upsert({
    where: { userId },
    create: {
      userId,
      kdfName: data.kdf.name,
      kdfParams: data.kdf.params as object,
      saltB64: data.kdf.saltB64,
      wrappedDekB64: data.wrappedDekB64,
    },
    update: {
      kdfName: data.kdf.name,
      kdfParams: data.kdf.params as object,
      saltB64: data.kdf.saltB64,
      wrappedDekB64: data.wrappedDekB64,
    },
  });
  return { ok: true };
}

export async function getWrappedDek(userId: string) {
  const row = await prisma.wrappedDek.findUnique({ where: { userId } });
  if (!row) return null;
  return {
    kdf: { name: row.kdfName, params: row.kdfParams as object, saltB64: row.saltB64 },
    wrappedDekB64: row.wrappedDekB64,
  };
}
