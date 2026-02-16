import { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import * as remindersService from '../services/reminders';
import { sendError } from '../lib/errors';

const defaultRateLimit = { max: 300, timeWindow: '1 minute' as const };

const reminderBodySchema = {
  type: 'object',
  properties: {
    recordId: { type: 'string' },
    time: { type: 'string', pattern: '^([01]?\\d|2[0-3]):([0-5]\\d)$' },
    daysOfWeek: {
      type: 'array',
      items: { type: 'integer', minimum: 0, maximum: 6 },
      maxItems: 7,
    },
    isEnabled: { type: 'boolean' },
  },
  required: ['recordId', 'time', 'daysOfWeek'],
  additionalProperties: false,
};

const reminderPatchSchema = {
  type: 'object',
  properties: {
    time: { type: 'string', pattern: '^([01]?\\d|2[0-3]):([0-5]\\d)$' },
    daysOfWeek: {
      type: 'array',
      items: { type: 'integer', minimum: 0, maximum: 6 },
      maxItems: 7,
    },
    isEnabled: { type: 'boolean' },
  },
  additionalProperties: false,
};

export default async function remindersRoutes(server: FastifyInstance) {
  server.get(
    '/reminders',
    {
      config: { rateLimit: defaultRateLimit },
      preHandler: [server.authenticate],
    },
    async (request: FastifyRequest, reply: FastifyReply) => {
      const userId = (request.user as { sub: string }).sub;
      const list = await remindersService.listByUser(userId);
      reply.code(200).send({ reminders: list });
    }
  );

  server.get<{ Params: { id: string } }>(
    '/reminders/:id',
    {
      config: { rateLimit: defaultRateLimit },
      preHandler: [server.authenticate],
    },
    async (request, reply) => {
      const userId = (request.user as { sub: string }).sub;
      const reminder = await remindersService.getById(userId, request.params.id);
      if (!reminder) {
        sendError(reply, 404, 'not_found', 'Reminder not found.', {});
        return;
      }
      reply.code(200).send(reminder);
    }
  );

  server.post(
    '/reminders',
    {
      config: { rateLimit: defaultRateLimit },
      schema: { body: reminderBodySchema },
      preHandler: [server.authenticate],
    },
    async (request: FastifyRequest, reply: FastifyReply) => {
      const userId = (request.user as { sub: string }).sub;
      const body = request.body as { recordId: string; time: string; daysOfWeek: number[]; isEnabled?: boolean };
      const result = await remindersService.create(userId, {
        recordId: body.recordId,
        time: body.time,
        daysOfWeek: body.daysOfWeek,
        isEnabled: body.isEnabled,
      });
      if (!result.ok) {
        const code = result.error === 'record not found or not owned' ? 404 : 400;
        sendError(reply, code, 'validation_error', result.error, {});
        return;
      }
      reply.code(201).send(result.reminder);
    }
  );

  server.patch<{ Params: { id: string } }>(
    '/reminders/:id',
    {
      config: { rateLimit: defaultRateLimit },
      schema: { body: reminderPatchSchema },
      preHandler: [server.authenticate],
    },
    async (request, reply) => {
      const userId = (request.user as { sub: string }).sub;
      const body = request.body as { time?: string; daysOfWeek?: number[]; isEnabled?: boolean };
      const result = await remindersService.update(userId, request.params.id, body);
      if (!result.ok) {
        const code = result.error === 'reminder not found' ? 404 : 400;
        sendError(reply, code, 'validation_error', result.error, {});
        return;
      }
      reply.code(200).send(result.reminder);
    }
  );

  server.delete<{ Params: { id: string } }>(
    '/reminders/:id',
    {
      config: { rateLimit: defaultRateLimit },
      preHandler: [server.authenticate],
    },
    async (request, reply) => {
      const userId = (request.user as { sub: string }).sub;
      const result = await remindersService.remove(userId, request.params.id);
      if (!result.ok) {
        sendError(reply, 404, 'not_found', result.error, {});
        return;
      }
      reply.code(204).send();
    }
  );
}
