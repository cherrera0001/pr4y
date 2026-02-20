import { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import { z } from 'zod';
import * as syncService from '../services/sync';
import { sendError } from '../lib/errors';
import { LIMITS } from '../lib/sanitize';

const pullQuerySchema = {
  type: 'object',
  properties: {
    cursor: { type: 'string', maxLength: LIMITS.recordId },
    limit: { type: 'number', minimum: 1, maximum: 500 },
  },
  required: [],
  additionalProperties: false,
};

const MAX_PAYLOAD_B64_LENGTH = 512 * 1024; // 512KB por registro (base64 string)
const B64_PATTERN = /^[A-Za-z0-9+/]*={0,2}$/;

const pullRecordSchema = {
  type: 'object',
  properties: {
    recordId: { type: 'string' },
    type: { type: 'string' },
    version: { type: 'number' },
    encryptedPayloadB64: { type: 'string' },
    clientUpdatedAt: { type: 'string' },
    serverUpdatedAt: { type: 'string' },
    deleted: { type: 'boolean' },
    status: { type: 'string', enum: ['PENDING', 'IN_PROCESS', 'ANSWERED'] },
  },
  required: ['recordId', 'type', 'version', 'encryptedPayloadB64', 'clientUpdatedAt', 'serverUpdatedAt', 'deleted', 'status'],
  additionalProperties: false,
};

const pullResponseSchema = {
  type: 'object',
  properties: {
    nextCursor: { type: 'string' },
    records: { type: 'array', items: pullRecordSchema },
  },
  required: ['nextCursor', 'records'],
  additionalProperties: false,
};

const pushRecordSchema = z.object({
  recordId: z.string().min(1).max(LIMITS.recordId),
  type: z.string().min(1).max(LIMITS.recordType),
  version: z.number().int().nonnegative(),
  encryptedPayloadB64: z
    .string()
    .max(MAX_PAYLOAD_B64_LENGTH, `encryptedPayloadB64 máximo ${MAX_PAYLOAD_B64_LENGTH} caracteres`)
    .refine((s) => B64_PATTERN.test(s), 'encryptedPayloadB64 debe ser base64'),
  clientUpdatedAt: z.string().min(1),
  deleted: z.boolean(),
}).strict();

const pushBodySchemaZod = z.object({
  records: z.array(pushRecordSchema).min(1).max(100),
}).strict();

const pushRejectedSchema = {
  type: 'object',
  properties: {
    recordId: { type: 'string' },
    reason: { type: 'string' },
    serverVersion: { type: 'number' },
    serverUpdatedAt: { type: 'string' },
  },
  required: ['recordId', 'reason'],
  additionalProperties: false,
};

const pushResponseSchema = {
  type: 'object',
  properties: {
    accepted: { type: 'array', items: { type: 'string' } },
    rejected: { type: 'array', items: pushRejectedSchema },
    serverTime: { type: 'string' },
  },
  required: ['accepted', 'rejected', 'serverTime'],
  additionalProperties: false,
};

const defaultRateLimit = { max: 300, timeWindow: '1 minute' as const };

export default async function syncRoutes(server: FastifyInstance) {
  server.get(
    '/sync/pull',
    {
      config: { rateLimit: defaultRateLimit },
      schema: { querystring: pullQuerySchema, response: { 200: pullResponseSchema } },
      preHandler: [server.authenticate],
    },
    async (request: FastifyRequest, reply: FastifyReply) => {
      const userId = (request.user as { sub: string }).sub;
      const query = request.query as { cursor?: string; limit?: number };
      const cursor = typeof query?.cursor === 'string' && query.cursor.length <= LIMITS.recordId ? query.cursor : undefined;
      const rawLimit = query?.limit;
      const limit =
        typeof rawLimit === 'number' ? Math.min(500, Math.max(1, rawLimit)) : typeof rawLimit === 'string' ? Math.min(500, Math.max(1, parseInt(rawLimit, 10) || 100)) : 100;
      const result = await syncService.pull(userId, cursor, limit);
      reply.code(200).send(result);
    }
  );

  server.post(
    '/sync/push',
    {
      config: { rateLimit: defaultRateLimit },
      schema: {
        body: {
          type: 'object',
          properties: {
            records: {
              type: 'array',
              items: {
                type: 'object',
                properties: {
                  recordId: { type: 'string' },
                  type: { type: 'string' },
                  version: { type: 'number' },
                  encryptedPayloadB64: { type: 'string' },
                  clientUpdatedAt: { type: 'string' },
                  deleted: { type: 'boolean' },
                },
                required: ['recordId', 'type', 'version', 'encryptedPayloadB64', 'clientUpdatedAt', 'deleted'],
                additionalProperties: false,
              },
              maxItems: 100,
            },
          },
          required: ['records'],
          additionalProperties: false,
        },
        response: { 200: pushResponseSchema },
      },
      preHandler: [server.authenticate],
    },
    async (request: FastifyRequest, reply: FastifyReply) => {
      const userId = (request.user as { sub: string }).sub;
      const parsed = pushBodySchemaZod.safeParse(request.body);
      if (!parsed.success) {
        const msg = parsed.error.errors.map((e) => e.message).join('; ');
        sendError(reply, 400, 'validation_error', msg, {});
        return;
      }
      const result = await syncService.push(userId, parsed.data.records);
      reply.code(200).send(result);
    }
  );
}
