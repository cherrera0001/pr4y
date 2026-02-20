import { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import { z } from 'zod';
import * as userService from '../services/user';
import { sendError, safeDetailsFromError } from '../lib/errors';

const TIME_PATTERN = /^([01]?\d|2[0-3]):([0-5]\d)$/;

const reminderPrefsBodySchema = z.object({
  time: z.string().regex(TIME_PATTERN, 'time debe ser HH:mm (ej. 09:00)'),
  daysOfWeek: z.array(z.number().int().min(0).max(6)).min(1).max(7),
  enabled: z.boolean(),
}).strict();

const purgeResponseSchema = {
  type: 'object',
  properties: {
    ok: { type: 'boolean' },
    recordsDeleted: { type: 'number' },
    usageLogsDeleted: { type: 'number' },
  },
  required: ['ok', 'recordsDeleted', 'usageLogsDeleted'],
  additionalProperties: false,
};

const reminderPrefsSchema = {
  type: 'object',
  properties: {
    time: { type: 'string' },
    daysOfWeek: { type: 'array', items: { type: 'integer' } },
    enabled: { type: 'boolean' },
  },
  required: ['time', 'daysOfWeek', 'enabled'],
  additionalProperties: false,
};

const defaultRateLimit = { max: 300, timeWindow: '1 minute' as const };

export default async function userRoutes(server: FastifyInstance) {
  server.get(
    '/user/reminder-preferences',
    {
      config: { rateLimit: defaultRateLimit },
      schema: { response: { 200: reminderPrefsSchema } },
      preHandler: [server.authenticate],
    },
    async (request: FastifyRequest, reply: FastifyReply) => {
      const userId = (request.user as { sub: string }).sub;
      const prefs = await userService.getReminderPreferences(userId);
      reply.code(200).send(prefs);
    }
  );

  server.put(
    '/user/reminder-preferences',
    {
      config: { rateLimit: defaultRateLimit },
      schema: {
        body: {
          type: 'object',
          properties: {
            time: { type: 'string', pattern: '^([01]?\\d|2[0-3]):([0-5]\\d)$' },
            daysOfWeek: { type: 'array', items: { type: 'integer', minimum: 0, maximum: 6 }, minItems: 1, maxItems: 7 },
            enabled: { type: 'boolean' },
          },
          required: ['time', 'daysOfWeek', 'enabled'],
          additionalProperties: false,
        },
        response: { 200: reminderPrefsSchema },
      },
      preHandler: [server.authenticate],
    },
    async (request: FastifyRequest, reply: FastifyReply) => {
      const userId = (request.user as { sub: string }).sub;
      const parsed = reminderPrefsBodySchema.safeParse(request.body);
      if (!parsed.success) {
        const msg = parsed.error.errors.map((e) => e.message).join('; ');
        sendError(reply, 400, 'validation_error', msg, {});
        return;
      }
      try {
        const prefs = await userService.updateReminderPreferences(userId, parsed.data);
        reply.code(200).send(prefs);
      } catch (e: unknown) {
        request.log.warn({ err: e }, 'user/reminder-preferences update failed');
        const details = safeDetailsFromError(e);
        sendError(reply, 400, 'validation_error', 'Invalid request', details);
      }
    }
  );

  server.post(
    '/user/purge-data',
    {
      config: { rateLimit: defaultRateLimit },
      schema: { response: { 200: purgeResponseSchema } },
      preHandler: [server.authenticate],
    },
    async (request: FastifyRequest, reply: FastifyReply) => {
      const userId = (request.user as { sub: string }).sub;
      const result = await userService.purgeUserData(userId);
      reply.code(200).send({
        ok: true,
        recordsDeleted: result.recordsDeleted,
        usageLogsDeleted: result.usageLogsDeleted,
      });
    }
  );
}
