/**
 * Socket.io server: integrado en Fastify.
 *
 * Eventos:
 * - join_queue: usuario entra a cola de matching
 * - leave_queue: usuario sale de cola
 * - signal: relay SDP/ICE para WebRTC
 * - end_prayer: terminar sesión
 * - disconnect: cleanup
 *
 * El matching loop corre cada 2s buscando pares en la cola.
 */

import { Server as HttpServer } from 'http';
import { Server, Socket } from 'socket.io';
import jwt from 'jsonwebtoken';
import { prisma } from './db';
import * as rouletteService from '../services/roulette';
import * as partnersService from '../services/partners';

interface AuthPayload {
  sub: string;
  email: string;
  role?: string;
}

interface AuthenticatedSocket extends Socket {
  userId?: string;
  email?: string;
}

let io: Server | null = null;

// Map userId → socketId para routing de señales
const userSockets = new Map<string, string>();

export function getIO(): Server | null {
  return io;
}

export function initSocketIO(httpServer: HttpServer, jwtSecret: string, corsOrigins: string[]) {
  io = new Server(httpServer, {
    cors: {
      origin: corsOrigins.length > 0 ? corsOrigins : false,
      credentials: true,
    },
    path: '/ws',
    transports: ['websocket', 'polling'],
  });

  // Auth middleware: verificar JWT en handshake
  io.use((socket: AuthenticatedSocket, next) => {
    const token = socket.handshake.auth?.token || socket.handshake.headers?.authorization?.replace('Bearer ', '');
    if (!token) return next(new Error('AUTH_REQUIRED'));

    try {
      const payload = jwt.verify(token, jwtSecret) as AuthPayload;
      socket.userId = payload.sub;
      socket.email = payload.email;
      next();
    } catch {
      next(new Error('AUTH_INVALID'));
    }
  });

  io.on('connection', (rawSocket: Socket) => {
    const socket = rawSocket as AuthenticatedSocket;
    const userId = socket.userId!;
    userSockets.set(userId, socket.id);

    socket.on('join_queue', async (data?: { filters?: Record<string, string> }) => {
      try {
        await rouletteService.joinQueue(userId, data?.filters as rouletteService.RouletteFilters | undefined);
        socket.emit('queue_status', { status: 'waiting' });

        // Intentar match inmediato
        const match = await rouletteService.tryMatch(userId);
        if (match) {
          notifyMatch(match);
        }
      } catch (err) {
        socket.emit('error', { message: 'Error joining queue' });
      }
    });

    socket.on('leave_queue', async () => {
      try {
        await rouletteService.leaveQueue(userId);
        socket.emit('queue_status', { status: 'left' });
      } catch {
        // silent
      }
    });

    // WebRTC signaling relay
    socket.on('signal', (data: { roomId: string; type: string; payload: unknown }) => {
      relaySignal(userId, data);
    });

    socket.on('end_prayer', async (data: { roomId: string }) => {
      try {
        const room = await rouletteService.endRoom(data.roomId, userId);
        if (room) {
          const partnerId = room.userAId === userId ? room.userBId : room.userAId;
          // Notificar al partner
          const partnerSocketId = userSockets.get(partnerId);
          if (partnerSocketId) {
            io!.to(partnerSocketId).emit('prayer_ended', { roomId: room.id, endedBy: userId });
          }
          socket.emit('prayer_ended', { roomId: room.id, endedBy: userId });

          // Incrementar conteo si son partners
          await partnersService.recordPrayerTogether(room.userAId, room.userBId).catch(() => {});
        }
      } catch {
        socket.emit('error', { message: 'Error ending prayer' });
      }
    });

    socket.on('disconnect', async () => {
      userSockets.delete(userId);
      // Limpiar de cola si estaba esperando
      await rouletteService.leaveQueue(userId).catch(() => {});

      // Si estaba en una room activa, notificar al partner
      const room = await rouletteService.getActiveRoom(userId);
      if (room) {
        const partnerId = room.userAId === userId ? room.userBId : room.userAId;
        const partnerSocketId = userSockets.get(partnerId);
        if (partnerSocketId) {
          io!.to(partnerSocketId).emit('partner_disconnected', { roomId: room.id });
        }
      }
    });
  });

  // Matching loop: cada 2 segundos buscar pares en cola
  startMatchingLoop();

  // Cleanup loop: cada 60s limpiar entradas expiradas
  setInterval(async () => {
    try {
      await rouletteService.cleanExpiredEntries();
    } catch {
      // silent
    }
  }, 60_000);

  return io;
}

function notifyMatch(match: rouletteService.MatchResult) {
  if (!io) return;

  registerRoomPeers(match.roomId, match.userAId, match.userBId);

  const socketA = userSockets.get(match.userAId);
  const socketB = userSockets.get(match.userBId);

  const payload = {
    roomId: match.roomId,
    role: '',
  };

  if (socketA) {
    io.to(socketA).emit('matched', { ...payload, role: 'offerer', peerId: match.userBId });
  }
  if (socketB) {
    io.to(socketB).emit('matched', { ...payload, role: 'answerer', peerId: match.userAId });
  }
}

// Mapa roomId → [userAId, userBId] para validación de señales (en memoria, se llena al match)
const roomPeers = new Map<string, [string, string]>();

function registerRoomPeers(roomId: string, userAId: string, userBId: string) {
  roomPeers.set(roomId, [userAId, userBId]);
}

function relaySignal(fromUserId: string, data: { roomId: string; type: string; payload: unknown }) {
  if (!io) return;

  const peers = roomPeers.get(data.roomId);
  if (!peers || !peers.includes(fromUserId)) return;

  const toUserId = peers[0] === fromUserId ? peers[1] : peers[0];
  const toSocketId = userSockets.get(toUserId);
  if (!toSocketId) return;

  io.to(toSocketId).emit('signal', {
    roomId: data.roomId,
    type: data.type,
    payload: data.payload,
    from: fromUserId,
  });
}

async function startMatchingLoop() {
  setInterval(async () => {
    if (!io) return;
    try {
      // Buscar todos los waiting, intentar emparejar de a 2
      const waiting = await rouletteService.getQueueCount();
      if (waiting < 2) return;

      // Tomar los primeros 2 de la cola
      const entries = await prisma.matchingQueue.findMany({
        where: { status: 'waiting' },
        orderBy: { joinedAt: 'asc' },
        take: 2,
      });

      if (entries.length < 2) return;

      const match = await rouletteService.tryMatch(entries[1].userId);
      if (match) {
        notifyMatch(match);
      }
    } catch {
      // silent — no crashear el loop
    }
  }, 2000);
}
