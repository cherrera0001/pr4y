import { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import * as usageService from '../services/usage';
import * as adminService from '../services/admin';

/** Payload JWT con role para restringir métricas a admin. */
type JwtUser = { sub: string; email: string; role?: string };

const defaultRateLimit = { max: 60, timeWindow: '1 minute' as const };

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
                createdAt: { type: 'string' },
                lastLoginAt: { type: ['string', 'null'] },
                hasDek: { type: 'boolean' },
                recordCount: { type: 'number' },
              },
              required: ['id', 'email', 'role', 'createdAt', 'lastLoginAt', 'hasDek', 'recordCount'],
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
}

export function isAdmin(user: JwtUser): boolean {
  const r = user?.role;
  return r === 'admin' || r === 'super_admin';
}
