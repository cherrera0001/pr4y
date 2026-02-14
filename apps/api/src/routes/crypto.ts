import { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import * as cryptoService from '../services/crypto';
import { sendError } from '../lib/errors';

const MAX_WRAPPED_DEK_B64 = 4096;   // 4KB
const MAX_SALT_B64 = 256;           // 256 bytes
const B64_PATTERN = '^[A-Za-z0-9+/]*={0,2}$';

const wrappedDekBodySchema = {
  type: 'object',
  properties: {
    kdf: {
      type: 'object',
      properties: {
        name: { type: 'string' },
        params: { type: 'object' },
        saltB64: {
          type: 'string',
          maxLength: MAX_SALT_B64,
          pattern: B64_PATTERN,
        },
      },
      required: ['name', 'params', 'saltB64'],
      additionalProperties: false,
    },
    wrappedDekB64: {
      type: 'string',
      maxLength: MAX_WRAPPED_DEK_B64,
      pattern: B64_PATTERN,
    },
  },
  required: ['kdf', 'wrappedDekB64'],
  additionalProperties: false,
};

const wrappedDekResponseSchema = {
  type: 'object',
  properties: { ok: { type: 'boolean' } },
  required: ['ok'],
  additionalProperties: false,
};

// Respuesta GET: mismos límites no aplican (solo lectura), pero el schema de respuesta puede ser flexible
const wrappedDekGetResponseSchema = {
  type: 'object',
  properties: {
    kdf: {
      type: 'object',
      properties: {
        name: { type: 'string' },
        params: { type: 'object' },
        saltB64: { type: 'string' },
      },
      required: ['name', 'params', 'saltB64'],
      additionalProperties: false,
    },
    wrappedDekB64: { type: 'string' },
  },
  required: ['kdf', 'wrappedDekB64'],
  additionalProperties: false,
};

const defaultRateLimit = { max: 300, timeWindow: '1 minute' as const };

export default async function cryptoRoutes(server: FastifyInstance) {
  server.put(
    '/crypto/wrapped-dek',
    {
      config: { rateLimit: defaultRateLimit },
      schema: { body: wrappedDekBodySchema, response: { 200: wrappedDekResponseSchema } },
      preHandler: [server.authenticate],
    },
    async (request: FastifyRequest, reply: FastifyReply) => {
      const userId = (request.user as { sub: string }).sub;
      const body = request.body as { kdf?: { name?: string; params?: Record<string, unknown>; saltB64?: string }; wrappedDekB64?: string };
      if (!body?.kdf?.name || typeof body.kdf.saltB64 !== 'string' || typeof body.wrappedDekB64 !== 'string') {
        sendError(reply, 400, 'validation_error', 'Invalid body.', {});
        return;
      }
      await cryptoService.putWrappedDek(userId, {
        kdf: { name: body.kdf.name, params: body.kdf.params ?? {}, saltB64: body.kdf.saltB64 },
        wrappedDekB64: body.wrappedDekB64,
      });
      reply.code(200).send({ ok: true });
    }
  );

  server.get(
    '/crypto/wrapped-dek',
    {
      config: { rateLimit: defaultRateLimit },
      schema: { response: { 200: wrappedDekGetResponseSchema } },
      preHandler: [server.authenticate],
    },
    async (request: FastifyRequest, reply: FastifyReply) => {
      const userId = (request.user as { sub: string }).sub;
      const data = await cryptoService.getWrappedDek(userId);
      if (!data) {
        sendError(reply, 404, 'not_found', 'Wrapped DEK not found.', {});
        return;
      }
      reply.code(200).send(data);
    }
  );
}
