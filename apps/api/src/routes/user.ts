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

const displayPrefsSchema = {
  type: 'object',
  properties: {
    theme: { type: 'string', enum: ['light', 'dark', 'system'] },
    fontSize: { type: 'string', enum: ['sm', 'md', 'lg', 'xl'] },
    fontFamily: { type: 'string', enum: ['system', 'serif', 'mono'] },
    lineSpacing: { type: 'string', enum: ['compact', 'normal', 'relaxed'] },
    contemplativeMode: { type: 'boolean' },
  },
  required: ['theme', 'fontSize', 'fontFamily', 'lineSpacing', 'contemplativeMode'],
  additionalProperties: false,
};

const displayPrefsBodySchema = z.object({
  theme: z.enum(['light', 'dark', 'system']).optional(),
  fontSize: z.enum(['sm', 'md', 'lg', 'xl']).optional(),
  fontFamily: z.enum(['system', 'serif', 'mono']).optional(),
  lineSpacing: z.enum(['compact', 'normal', 'relaxed']).optional(),
  contemplativeMode: z.boolean().optional(),
}).refine((d) => Object.keys(d).length > 0, { message: 'Al menos un campo es requerido' });

const faithStatsResponseSchema = {
  type: 'object',
  properties: {
    totalRecords: { type: 'number' },
    totalAnswered: { type: 'number' },
    totalInProcess: { type: 'number' },
    totalPending: { type: 'number' },
    streakDays: { type: 'number' },
    longestStreakDays: { type: 'number' },
    firstEntryAt: { type: ['string', 'null'] },
    recordsByType: {
      type: 'array',
      items: {
        type: 'object',
        properties: { type: { type: 'string' }, count: { type: 'number' } },
        required: ['type', 'count'],
        additionalProperties: false,
      },
    },
  },
  required: ['totalRecords', 'totalAnswered', 'totalInProcess', 'totalPending', 'streakDays', 'longestStreakDays', 'firstEntryAt', 'recordsByType'],
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

const scheduleItemSchema = {
  type: 'object',
  properties: {
    time: { type: 'string', pattern: '^([01]?\\d|2[0-3]):[0-5]\\d$' },
    daysOfWeek: { type: 'array', items: { type: 'integer', minimum: 0, maximum: 6 }, maxItems: 7 },
    enabled: { type: 'boolean' },
  },
  required: ['time', 'daysOfWeek', 'enabled'],
  additionalProperties: false,
};

const reminderSchedulesSchema = {
  type: 'object',
  properties: {
    schedules: { type: 'array', items: scheduleItemSchema, maxItems: 5 },
  },
  required: ['schedules'],
  additionalProperties: false,
};

const reminderSchedulesBodySchema = z.object({
  schedules: z.array(
    z.object({
      time: z.string().regex(TIME_PATTERN, 'time debe ser HH:mm'),
      daysOfWeek: z.array(z.number().int().min(0).max(6)).max(7),
      enabled: z.boolean(),
    })
  ).max(5),
});

const defaultRateLimit = { max: 300, timeWindow: '1 minute' as const };

export default async function userRoutes(server: FastifyInstance) {
  server.get(
    '/user/display-preferences',
    {
      config: { rateLimit: defaultRateLimit },
      schema: { response: { 200: displayPrefsSchema } },
      preHandler: [server.authenticate],
    },
    async (request: FastifyRequest, reply: FastifyReply) => {
      const userId = (request.user as { sub: string }).sub;
      const prefs = await userService.getDisplayPreferences(userId);
      reply.code(200).send(prefs);
    }
  );

  server.put(
    '/user/display-preferences',
    {
      config: { rateLimit: defaultRateLimit },
      schema: {
        body: {
          type: 'object',
          properties: {
            theme: { type: 'string', enum: ['light', 'dark', 'system'] },
            fontSize: { type: 'string', enum: ['sm', 'md', 'lg', 'xl'] },
            fontFamily: { type: 'string', enum: ['system', 'serif', 'mono'] },
            lineSpacing: { type: 'string', enum: ['compact', 'normal', 'relaxed'] },
            contemplativeMode: { type: 'boolean' },
          },
          additionalProperties: false,
        },
        response: { 200: displayPrefsSchema },
      },
      preHandler: [server.authenticate],
    },
    async (request: FastifyRequest, reply: FastifyReply) => {
      const userId = (request.user as { sub: string }).sub;
      const parsed = displayPrefsBodySchema.safeParse(request.body);
      if (!parsed.success) {
        const msg = parsed.error.errors.map((e) => e.message).join('; ');
        sendError(reply, 400, 'validation_error', msg, {});
        return;
      }
      const prefs = await userService.updateDisplayPreferences(userId, parsed.data);
      reply.code(200).send(prefs);
    }
  );

  server.get(
    '/user/faith-stats',
    {
      config: { rateLimit: defaultRateLimit },
      schema: { response: { 200: faithStatsResponseSchema } },
      preHandler: [server.authenticate],
    },
    async (request: FastifyRequest, reply: FastifyReply) => {
      const userId = (request.user as { sub: string }).sub;
      const stats = await userService.getFaithStats(userId);
      reply.code(200).send(stats);
    }
  );

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

  server.get(
    '/user/reminder-schedules',
    {
      config: { rateLimit: defaultRateLimit },
      schema: { response: { 200: reminderSchedulesSchema } },
      preHandler: [server.authenticate],
    },
    async (request: FastifyRequest, reply: FastifyReply) => {
      const userId = (request.user as { sub: string }).sub;
      const schedules = await userService.getUserReminderSchedules(userId);
      reply.code(200).send({ schedules });
    }
  );

  server.put(
    '/user/reminder-schedules',
    {
      config: { rateLimit: defaultRateLimit },
      schema: {
        body: reminderSchedulesSchema,
        response: { 200: reminderSchedulesSchema },
      },
      preHandler: [server.authenticate],
    },
    async (request: FastifyRequest, reply: FastifyReply) => {
      const userId = (request.user as { sub: string }).sub;
      const parsed = reminderSchedulesBodySchema.safeParse(request.body);
      if (!parsed.success) {
        const msg = parsed.error.errors.map((e) => e.message).join('; ');
        sendError(reply, 400, 'validation_error', msg, {});
        return;
      }
      const schedules = await userService.updateUserReminderSchedules(userId, parsed.data.schedules);
      reply.code(200).send({ schedules });
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
