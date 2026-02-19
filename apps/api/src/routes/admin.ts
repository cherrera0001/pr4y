import { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import { z } from 'zod';
import * as usageService from '../services/usage';
import * as adminService from '../services/admin';

/** Payload JWT con role para restringir métricas a admin. */
type JwtUser = { sub: string; email: string; role?: string };

const defaultRateLimit = { max: 60, timeWindow: '1 minute' as const };

const updateUserBodySchema = z.object({
  role: z.enum(['user', 'admin', 'super_admin']).optional(),
  status: z.enum(['active', 'banned']).optional(),
}).refine((d) => Object.keys(d).length > 0, { message: 'Al menos un campo (role o status) es requerido' });

const createContentBodySchema = z.object({
  type: z.string().min(1).max(64),
  title: z.string().min(1).max(512),
  body: z.string(),
  published: z.boolean().optional(),
  sortOrder: z.number().int().optional(),
});

const updateContentBodySchema = z.object({
  type: z.string().min(1).max(64).optional(),
  title: z.string().min(1).max(512).optional(),
  body: z.string().optional(),
  published: z.boolean().optional(),
  sortOrder: z.number().int().optional(),
}).refine((d) => Object.keys(d).length > 0, { message: 'Al menos un campo es requerido' });

const statsResponseSchema = {
  type: 'object' as const,
  properties: {
    totalUsers: { type: 'number' },
    totalRecords: { type: 'number' },
    totalBlobBytes: { type: 'string', description: 'BigInt as string' },
    syncsToday: { type: 'number' },
    bytesPushedToday: { type: 'string' },
    bytesPulledToday: { type: 'string' },
    byDay: {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          day: { type: 'string' },
          usersActive: { type: 'number' },
          bytesPushed: { type: 'string' },
          bytesPulled: { type: 'string' },
        },
      },
    },
  },
  required: ['totalUsers', 'totalRecords', 'totalBlobBytes', 'syncsToday', 'bytesPushedToday', 'bytesPulledToday', 'byDay'],
  additionalProperties: false,
};

/** Formato esperado por el dashboard web: última actividad de sync y registros por tipo/día. */
const statsDetailResponseSchema = {
  type: 'object' as const,
  properties: {
    lastSyncActivity: { type: 'string', description: 'Última actividad de sincronización (ej. "Hace 5 min")' },
    recordsByTypeByDay: {
      type: 'array',
      items: {
        type: 'object',
        properties: { day: { type: 'string' }, type: { type: 'string' }, count: { type: 'number' } },
        required: ['day', 'type', 'count'],
      },
    },
  },
  required: ['lastSyncActivity', 'recordsByTypeByDay'],
  additionalProperties: false,
};

export default async function adminRoutes(server: FastifyInstance) {
  server.get(
    '/admin/stats',
    {
      config: { rateLimit: defaultRateLimit },
      preHandler: [server.authenticate, server.requireAdmin],
      schema: {
        querystring: {
          type: 'object',
          properties: { days: { type: 'number', default: 7 } },
          additionalProperties: false,
        },
        response: { 200: statsResponseSchema },
      },
    },
    async (request: FastifyRequest<{ Querystring: { days?: number } }>, reply: FastifyReply) => {
      const days = Math.min(31, Math.max(1, request.query?.days ?? 7));
      const stats = await usageService.getUsageStats(days);
      reply.code(200).send({
        totalUsers: stats.totalUsers,
        totalRecords: stats.totalRecords,
        totalBlobBytes: String(stats.totalBlobBytes),
        syncsToday: stats.syncsToday,
        bytesPushedToday: String(stats.bytesPushedToday),
        bytesPulledToday: String(stats.bytesPulledToday),
        byDay: stats.byDay.map((d) => ({
          day: d.day,
          usersActive: d.usersActive,
          bytesPushed: String(d.bytesPushed),
          bytesPulled: String(d.bytesPulled),
        })),
      });
    }
  );

  server.get(
    '/admin/stats/detail',
    {
      config: { rateLimit: defaultRateLimit },
      preHandler: [server.authenticate, server.requireAdmin],
      schema: {
        querystring: {
          type: 'object',
          properties: { days: { type: 'number', default: 7 } },
          additionalProperties: false,
        },
        response: { 200: statsDetailResponseSchema },
      },
    },
    async (request: FastifyRequest<{ Querystring: { days?: number } }>, reply: FastifyReply) => {
      const days = Math.min(31, Math.max(1, request.query?.days ?? 7));
      const detail = await usageService.getStatsDetail(days);
      reply.code(200).send(detail);
    }
  );

  server.get(
    '/admin/users',
    {
      config: { rateLimit: defaultRateLimit },
      preHandler: [server.authenticate, server.requireAdmin],
      schema: {
        response: {
          200: {
            type: 'array',
            items: {
              type: 'object',
              properties: {
                id: { type: 'string' },
                email: { type: 'string' },
                role: { type: 'string' },
                status: { type: 'string' },
                createdAt: { type: 'string' },
                lastLoginAt: { type: ['string', 'null'] },
                hasDek: { type: 'boolean' },
                recordCount: { type: 'number' },
              },
              required: ['id', 'email', 'role', 'status', 'createdAt', 'lastLoginAt', 'hasDek', 'recordCount'],
              additionalProperties: false,
            },
          },
        },
      },
    },
    async (_request: FastifyRequest, reply: FastifyReply) => {
      const users = await adminService.listUsers();
      reply.code(200).send(users);
    }
  );

  server.patch(
    '/admin/users/:id',
    {
      config: { rateLimit: defaultRateLimit },
      preHandler: [server.authenticate, server.requireAdmin],
      schema: {
        params: { type: 'object', properties: { id: { type: 'string' } }, required: ['id'], additionalProperties: false },
        response: { 200: { type: 'object', additionalProperties: true } },
      },
    },
    async (request: FastifyRequest<{ Params: { id: string }; Body: unknown }>, reply: FastifyReply) => {
      const parsed = updateUserBodySchema.safeParse(request.body);
      if (!parsed.success) {
        return reply.code(400).send({ error: { code: 'validation_error', message: 'Invalid input', details: parsed.error.flatten() } });
      }
      const updated = await adminService.updateUser(request.params.id, parsed.data);
      if (!updated) {
        return reply.code(400).send({ error: { code: 'validation_error', message: 'Nada que actualizar' } });
      }
      reply.code(200).send(updated);
    }
  );

  server.get(
    '/admin/content',
    {
      config: { rateLimit: defaultRateLimit },
      preHandler: [server.authenticate, server.requireAdmin],
      schema: {
        querystring: { type: 'object', properties: { type: { type: 'string' } }, additionalProperties: false },
        response: { 200: { type: 'array', items: { type: 'object', additionalProperties: true } } },
      },
    },
    async (request: FastifyRequest<{ Querystring: { type?: string } }>, reply: FastifyReply) => {
      const list = await adminService.listContent(request.query?.type);
      reply.code(200).send(list);
    }
  );

  server.post(
    '/admin/content',
    {
      config: { rateLimit: defaultRateLimit },
      preHandler: [server.authenticate, server.requireAdmin],
      schema: {
        response: { 201: { type: 'object', additionalProperties: true } },
      },
    },
    async (request: FastifyRequest<{ Body: unknown }>, reply: FastifyReply) => {
      const parsed = createContentBodySchema.safeParse(request.body);
      if (!parsed.success) {
        return reply.code(400).send({ error: { code: 'validation_error', message: 'Invalid input', details: parsed.error.flatten() } });
      }
      const created = await adminService.createContent(parsed.data);
      reply.code(201).send(created);
    }
  );

  server.patch(
    '/admin/content/:id',
    {
      config: { rateLimit: defaultRateLimit },
      preHandler: [server.authenticate, server.requireAdmin],
      schema: {
        params: { type: 'object', properties: { id: { type: 'string' } }, required: ['id'], additionalProperties: false },
        response: { 200: { type: 'object', additionalProperties: true } },
      },
    },
    async (request: FastifyRequest<{ Params: { id: string }; Body: unknown }>, reply: FastifyReply) => {
      const parsed = updateContentBodySchema.safeParse(request.body);
      if (!parsed.success) {
        return reply.code(400).send({ error: { code: 'validation_error', message: 'Invalid input', details: parsed.error.flatten() } });
      }
      const updated = await adminService.updateContent(request.params.id, parsed.data);
      if (!updated) {
        return reply.code(404).send({ error: { code: 'not_found', message: 'Contenido no encontrado' } });
      }
      reply.code(200).send(updated);
    }
  );

  server.delete(
    '/admin/content/:id',
    {
      config: { rateLimit: defaultRateLimit },
      preHandler: [server.authenticate, server.requireAdmin],
      schema: {
        params: { type: 'object', properties: { id: { type: 'string' } }, required: ['id'], additionalProperties: false },
        response: { 204: { type: 'null' } },
      },
    },
    async (request: FastifyRequest<{ Params: { id: string } }>, reply: FastifyReply) => {
      await adminService.deleteContent(request.params.id);
      reply.code(204).send();
    }
  );
}

export function isAdmin(user: JwtUser): boolean {
  const r = user?.role;
  return r === 'admin' || r === 'super_admin';
}
