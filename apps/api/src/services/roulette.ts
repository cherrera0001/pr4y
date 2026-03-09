/**
 * Roulette service: matching engine + room lifecycle.
 *
 * Cola FIFO simple: primer usuario espera, segundo se matchea.
 * Sin filtros complejos por ahora — matching aleatorio puro.
 */

import { Prisma } from '@prisma/client';
import { prisma } from '../lib/db';

export interface MatchResult {
  roomId: string;
  userAId: string;
  userBId: string;
}

export type RouletteFilters = Prisma.InputJsonValue;

/** Unir usuario a la cola de matching. Retorna el entry de cola. */
export async function joinQueue(userId: string, filters?: Prisma.InputJsonValue) {
  // Si ya está en cola, retornar el existente
  const existing = await prisma.matchingQueue.findUnique({ where: { userId } });
  if (existing && existing.status === 'waiting') return existing;

  // Upsert: si tenía un entry expired/matched, reemplazar
  return prisma.matchingQueue.upsert({
    where: { userId },
    update: { status: 'waiting', joinedAt: new Date(), filters: filters ?? Prisma.JsonNull },
    create: { userId, filters: filters ?? Prisma.JsonNull, status: 'waiting' },
  });
}

/** Sacar usuario de la cola. */
export async function leaveQueue(userId: string) {
  return prisma.matchingQueue.deleteMany({ where: { userId } });
}

/** Verificar si el usuario está en cola. */
export async function getQueueEntry(userId: string) {
  return prisma.matchingQueue.findUnique({ where: { userId } });
}

/**
 * Intentar un match FIFO: busca el primer usuario waiting que NO sea el que llama.
 * Si lo encuentra, crea PrayerRoom y marca ambos como matched.
 *
 * Retorna null si no hay match disponible.
 */
export async function tryMatch(userId: string): Promise<MatchResult | null> {
  // Buscar el primer waiting que no sea el usuario actual, ordenado por joinedAt ASC (FIFO)
  const candidate = await prisma.matchingQueue.findFirst({
    where: {
      status: 'waiting',
      userId: { not: userId },
    },
    orderBy: { joinedAt: 'asc' },
  });

  if (!candidate) return null;

  // Transacción: crear room + marcar ambos como matched + borrar de cola
  const result = await prisma.$transaction(async (tx) => {
    // Re-verificar que el candidato sigue waiting (evitar race condition)
    const still = await tx.matchingQueue.findUnique({ where: { userId: candidate.userId } });
    if (!still || still.status !== 'waiting') return null;

    // Crear room
    const room = await tx.prayerRoom.create({
      data: {
        userAId: candidate.userId,
        userBId: userId,
        status: 'active',
      },
    });

    // Borrar ambos de la cola
    await tx.matchingQueue.deleteMany({
      where: { userId: { in: [candidate.userId, userId] } },
    });

    return { roomId: room.id, userAId: candidate.userId, userBId: userId };
  });

  return result;
}

/** Obtener room activa del usuario. */
export async function getActiveRoom(userId: string) {
  return prisma.prayerRoom.findFirst({
    where: {
      status: 'active',
      OR: [{ userAId: userId }, { userBId: userId }],
    },
    orderBy: { startedAt: 'desc' },
  });
}

/** Terminar una sesión de oración. */
export async function endRoom(roomId: string, userId: string) {
  const room = await prisma.prayerRoom.findUnique({ where: { id: roomId } });
  if (!room) return null;
  if (room.userAId !== userId && room.userBId !== userId) return null;
  if (room.status !== 'active') return room;

  const now = new Date();
  const duration = Math.round((now.getTime() - room.startedAt.getTime()) / 1000);

  return prisma.prayerRoom.update({
    where: { id: roomId },
    data: { status: 'ended', endedAt: now, duration },
  });
}

/** Reportar una sesión. */
export async function reportRoom(
  roomId: string,
  reporterId: string,
  reason: string,
  details?: string
) {
  const room = await prisma.prayerRoom.findUnique({ where: { id: roomId } });
  if (!room) return null;
  if (room.userAId !== reporterId && room.userBId !== reporterId) return null;

  const report = await prisma.prayerReport.create({
    data: { roomId, reporterId, reason, details },
  });

  // Auto-ban: si el reportado acumula 3+ reportes actioned, suspenderlo
  const reportedId = room.userAId === reporterId ? room.userBId : room.userAId;
  const reportCount = await prisma.prayerReport.count({
    where: {
      room: { OR: [{ userAId: reportedId }, { userBId: reportedId }] },
      reporterId: { not: reportedId },
      status: { in: ['pending', 'actioned'] },
    },
  });

  if (reportCount >= 3) {
    await prisma.user.update({
      where: { id: reportedId },
      data: { status: 'banned' },
    });
  }

  return report;
}

/** Obtener resumen post-sesión. */
export async function getRoomSummary(roomId: string, userId: string) {
  const room = await prisma.prayerRoom.findUnique({ where: { id: roomId } });
  if (!room) return null;
  if (room.userAId !== userId && room.userBId !== userId) return null;

  const partnerId = room.userAId === userId ? room.userBId : room.userAId;

  // Verificar si ya son partners
  const existingPartner = await prisma.prayerPartner.findUnique({
    where: { userId_partnerId: { userId, partnerId } },
  });

  return {
    roomId: room.id,
    status: room.status,
    startedAt: room.startedAt.toISOString(),
    endedAt: room.endedAt?.toISOString() ?? null,
    duration: room.duration,
    partnerId,
    isPartner: !!existingPartner,
  };
}

/** Contar usuarios en cola (para UI "X personas esperando"). */
export async function getQueueCount() {
  return prisma.matchingQueue.count({ where: { status: 'waiting' } });
}

/** Limpiar entradas expiradas (>5 min sin match). */
export async function cleanExpiredEntries() {
  const fiveMinAgo = new Date(Date.now() - 5 * 60 * 1000);
  return prisma.matchingQueue.deleteMany({
    where: { status: 'waiting', joinedAt: { lt: fiveMinAgo } },
  });
}
