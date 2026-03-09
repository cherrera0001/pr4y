/**
 * Prayer Partners service: relaciones recurrentes entre usuarios post-sesión.
 */

import { prisma } from '../lib/db';

/** Listar partners del usuario. */
export async function listPartners(userId: string) {
  const partners = await prisma.prayerPartner.findMany({
    where: { userId },
    orderBy: { lastPrayed: { sort: 'desc', nulls: 'last' } },
    include: {
      partner: { select: { id: true, email: true, createdAt: true } },
    },
  });
  return partners.map((p) => ({
    id: p.id,
    partnerId: p.partnerId,
    partnerEmail: p.partner.email,
    createdAt: p.createdAt.toISOString(),
    lastPrayed: p.lastPrayed?.toISOString() ?? null,
    prayerCount: p.prayerCount,
  }));
}

/** Agregar partner (bidireccional: crea ambas direcciones). */
export async function addPartner(userId: string, partnerId: string) {
  if (userId === partnerId) throw new Error('CANNOT_PARTNER_SELF');

  // Verificar que el partner existe
  const partner = await prisma.user.findUnique({ where: { id: partnerId } });
  if (!partner) throw new Error('PARTNER_NOT_FOUND');

  // Upsert bidireccional
  await prisma.$transaction([
    prisma.prayerPartner.upsert({
      where: { userId_partnerId: { userId, partnerId } },
      update: {},
      create: { userId, partnerId },
    }),
    prisma.prayerPartner.upsert({
      where: { userId_partnerId: { userId: partnerId, partnerId: userId } },
      update: {},
      create: { userId: partnerId, partnerId: userId },
    }),
  ]);

  return { userId, partnerId };
}

/** Eliminar partner (bidireccional). */
export async function removePartner(userId: string, partnerId: string) {
  await prisma.$transaction([
    prisma.prayerPartner.deleteMany({ where: { userId, partnerId } }),
    prisma.prayerPartner.deleteMany({ where: { userId: partnerId, partnerId: userId } }),
  ]);
}

/** Incrementar conteo tras una sesión juntos. */
export async function recordPrayerTogether(userAId: string, userBId: string) {
  const now = new Date();
  await prisma.$transaction([
    prisma.prayerPartner.updateMany({
      where: { userId: userAId, partnerId: userBId },
      data: { prayerCount: { increment: 1 }, lastPrayed: now },
    }),
    prisma.prayerPartner.updateMany({
      where: { userId: userBId, partnerId: userAId },
      data: { prayerCount: { increment: 1 }, lastPrayed: now },
    }),
  ]);
}
