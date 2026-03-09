/**
 * Roulette REST routes: /v1/roulette/* y /v1/partners/*
 *
 * WebSocket maneja el flujo en tiempo real; estas rutas son para
 * operaciones que no requieren real-time (join/leave vía REST fallback,
 * report, summary, partners CRUD).
 */

import { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import * as rouletteService from '../services/roulette';
import * as partnersService from '../services/partners';

type JwtUser = { sub: string; email: string; role?: string };

const userRateLimit = { max: 300, timeWindow: '1 minute' as const };
const joinRateLimit = { max: 10, timeWindow: '1 hour' as const }; // anti-abuse: max 10 joins/hora

export default async function rouletteRoutes(server: FastifyInstance) {
  // ──────────────── Roulette ────────────────

  // JOIN: entrar a cola de matching
  server.post(
    '/roulette/join',
    {
      config: { rateLimit: joinRateLimit },
      preHandler: [server.authenticate],
      schema: {
        body: {
          type: 'object',
          properties: {
            filters: {
              type: 'object',
              properties: {
                language: { type: 'string' },
              },
              additionalProperties: false,
            },
          },
          additionalProperties: false,
        },
      },
    },
    async (request: FastifyRequest, reply: FastifyReply) => {
      const user = request.user as JwtUser;
      const body = request.body as { filters?: rouletteService.RouletteFilters } | undefined;
      const entry = await rouletteService.joinQueue(user.sub, body?.filters);
      reply.code(200).send({ status: entry.status, joinedAt: entry.joinedAt });
    }
  );

  // LEAVE: salir de cola
  server.delete(
    '/roulette/leave',
    {
      config: { rateLimit: userRateLimit },
      preHandler: [server.authenticate],
    },
    async (request: FastifyRequest, reply: FastifyReply) => {
      const user = request.user as JwtUser;
      await rouletteService.leaveQueue(user.sub);
      reply.code(200).send({ status: 'left' });
    }
  );

  // STATUS: estado actual del usuario en roulette
  server.get(
    '/roulette/status',
    {
      config: { rateLimit: userRateLimit },
      preHandler: [server.authenticate],
    },
    async (request: FastifyRequest, reply: FastifyReply) => {
      const user = request.user as JwtUser;

      // Verificar si está en cola
      const queueEntry = await rouletteService.getQueueEntry(user.sub);
      if (queueEntry) {
        const queueCount = await rouletteService.getQueueCount();
        return reply.code(200).send({
          state: 'waiting',
          queueCount,
          joinedAt: queueEntry.joinedAt,
        });
      }

      // Verificar si está en una room activa
      const room = await rouletteService.getActiveRoom(user.sub);
      if (room) {
        const partnerId = room.userAId === user.sub ? room.userBId : room.userAId;
        return reply.code(200).send({
          state: 'in_prayer',
          roomId: room.id,
          partnerId,
          startedAt: room.startedAt,
        });
      }

      reply.code(200).send({ state: 'idle' });
    }
  );

  // ──────────────── Rooms ────────────────

  // END: terminar sesión de oración
  server.post(
    '/rooms/:roomId/end',
    {
      config: { rateLimit: userRateLimit },
      preHandler: [server.authenticate],
      schema: {
        params: {
          type: 'object',
          properties: { roomId: { type: 'string' } },
          required: ['roomId'],
        },
      },
    },
    async (request: FastifyRequest, reply: FastifyReply) => {
      const user = request.user as JwtUser;
      const { roomId } = request.params as { roomId: string };
      const room = await rouletteService.endRoom(roomId, user.sub);
      if (!room) {
        return reply.code(404).send({ error: { code: 'not_found', message: 'Room not found', details: {} } });
      }
      reply.code(200).send({
        roomId: room.id,
        status: room.status,
        duration: room.duration,
      });
    }
  );

  // REPORT: reportar usuario en sesión
  server.post(
    '/rooms/:roomId/report',
    {
      config: { rateLimit: { max: 5, timeWindow: '1 hour' as const } },
      preHandler: [server.authenticate],
      schema: {
        params: {
          type: 'object',
          properties: { roomId: { type: 'string' } },
          required: ['roomId'],
        },
        body: {
          type: 'object',
          properties: {
            reason: { type: 'string', enum: ['inappropriate', 'spam', 'harassment'] },
            details: { type: 'string', maxLength: 500 },
          },
          required: ['reason'],
          additionalProperties: false,
        },
      },
    },
    async (request: FastifyRequest, reply: FastifyReply) => {
      const user = request.user as JwtUser;
      const { roomId } = request.params as { roomId: string };
      const { reason, details } = request.body as { reason: string; details?: string };

      const report = await rouletteService.reportRoom(roomId, user.sub, reason, details);
      if (!report) {
        return reply.code(404).send({ error: { code: 'not_found', message: 'Room not found or not your room', details: {} } });
      }
      reply.code(201).send({ reportId: report.id, status: report.status });
    }
  );

  // SUMMARY: resumen post-sesión
  server.get(
    '/rooms/:roomId/summary',
    {
      config: { rateLimit: userRateLimit },
      preHandler: [server.authenticate],
      schema: {
        params: {
          type: 'object',
          properties: { roomId: { type: 'string' } },
          required: ['roomId'],
        },
      },
    },
    async (request: FastifyRequest, reply: FastifyReply) => {
      const user = request.user as JwtUser;
      const { roomId } = request.params as { roomId: string };
      const summary = await rouletteService.getRoomSummary(roomId, user.sub);
      if (!summary) {
        return reply.code(404).send({ error: { code: 'not_found', message: 'Room not found', details: {} } });
      }
      reply.code(200).send(summary);
    }
  );

  // ──────────────── Partners ────────────────

  // LIST: listar prayer partners
  server.get(
    '/partners',
    {
      config: { rateLimit: userRateLimit },
      preHandler: [server.authenticate],
    },
    async (request: FastifyRequest, reply: FastifyReply) => {
      const user = request.user as JwtUser;
      const partners = await partnersService.listPartners(user.sub);
      reply.code(200).send({ partners });
    }
  );

  // ADD: agregar partner post-sesión
  server.post(
    '/partners/:partnerId',
    {
      config: { rateLimit: userRateLimit },
      preHandler: [server.authenticate],
      schema: {
        params: {
          type: 'object',
          properties: { partnerId: { type: 'string' } },
          required: ['partnerId'],
        },
      },
    },
    async (request: FastifyRequest, reply: FastifyReply) => {
      const user = request.user as JwtUser;
      const { partnerId } = request.params as { partnerId: string };
      try {
        await partnersService.addPartner(user.sub, partnerId);
        reply.code(201).send({ status: 'added' });
      } catch (err) {
        const msg = err instanceof Error ? err.message : 'Error';
        if (msg === 'CANNOT_PARTNER_SELF') {
          return reply.code(400).send({ error: { code: 'bad_request', message: 'Cannot partner with yourself', details: {} } });
        }
        if (msg === 'PARTNER_NOT_FOUND') {
          return reply.code(404).send({ error: { code: 'not_found', message: 'User not found', details: {} } });
        }
        throw err;
      }
    }
  );

  // DELETE: eliminar partner
  server.delete(
    '/partners/:partnerId',
    {
      config: { rateLimit: userRateLimit },
      preHandler: [server.authenticate],
      schema: {
        params: {
          type: 'object',
          properties: { partnerId: { type: 'string' } },
          required: ['partnerId'],
        },
      },
    },
    async (request: FastifyRequest, reply: FastifyReply) => {
      const user = request.user as JwtUser;
      const { partnerId } = request.params as { partnerId: string };
      await partnersService.removePartner(user.sub, partnerId);
      reply.code(200).send({ status: 'removed' });
    }
  );
}
