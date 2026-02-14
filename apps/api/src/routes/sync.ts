import { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import * as syncService from '../services/sync';
import { sendError } from '../lib/errors';

const pullQuerySchema = {
  type: 'object',
  properties: {
    cursor: { type: 'string' },
    limit: { type: 'number' },
  },
  required: [],
  additionalProperties: false,
};

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
  },
  required: ['recordId', 'type', 'version', 'encryptedPayloadB64', 'clientUpdatedAt', 'serverUpdatedAt', 'deleted'],
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

const pushRecordSchema = pullRecordSchema;
const pushBodySchema = {
  type: 'object',
  properties: {
    records: { type: 'array', items: pushRecordSchema },
  },
  required: ['records'],
  additionalProperties: false,
};

const pushRejectedSchema = {
  type: 'object',
  properties: {
    recordId: { type: 'string' },
    reason: { type: 'string' },
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

export default async function syncRoutes(server: FastifyInstance) {
  server.get(
    '/v1/sync/pull',
    {
      schema: { querystring: pullQuerySchema, response: { 200: pullResponseSchema } },
      preHandler: [server.authenticate],
    },
    async (request: FastifyRequest, reply: FastifyReply) => {
      const userId = (request.user as { sub: string }).sub;
      const query = request.query as { cursor?: string; limit?: number };
      const cursor = typeof query?.cursor === 'string' ? query.cursor : undefined;
      const rawLimit = query?.limit;
const limit = typeof rawLimit === 'number' ? rawLimit : typeof rawLimit === 'string' ? parseInt(rawLimit, 10) : 100;
      const result = await syncService.pull(userId, cursor, limit);
      reply.code(200).send(result);
    }
  );

  server.post(
    '/v1/sync/push',
    {
      schema: { body: pushBodySchema, response: { 200: pushResponseSchema } },
      preHandler: [server.authenticate],
    },
    async (request: FastifyRequest, reply: FastifyReply) => {
      const userId = (request.user as { sub: string }).sub;
      const body = request.body as { records?: Array<{ recordId?: string; type?: string; version?: number; encryptedPayloadB64?: string; clientUpdatedAt?: string; serverUpdatedAt?: string; deleted?: boolean }> };
      const records = body?.records;
      if (!Array.isArray(records)) {
        sendError(reply as any, 400, 'validation_error', 'records array is required.', {});
        return;
      }
      const limit = 100;
      if (records.length > limit) {
        sendError(reply as any, 400, 'validation_error', `Maximum ${limit} records per request.`, {});
        return;
      }
      const input = records.map((r) => ({
        recordId: String(r.recordId),
        type: String(r.type),
        version: Number(r.version),
        encryptedPayloadB64: String(r.encryptedPayloadB64),
        clientUpdatedAt: String(r.clientUpdatedAt),
        deleted: Boolean(r.deleted),
      }));
      const result = await syncService.push(userId, input);
      reply.code(200).send(result);
    }
  );
}
