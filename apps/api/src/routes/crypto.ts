import { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import * as cryptoService from '../services/crypto';
import { sendError } from '../lib/errors';

const wrappedDekBodySchema = {
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

const wrappedDekResponseSchema = {
  type: 'object',
  properties: { ok: { type: 'boolean' } },
  required: ['ok'],
  additionalProperties: false,
};

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

export default async function cryptoRoutes(server: FastifyInstance) {
  server.put(
    '/v1/crypto/wrapped-dek',
    {
      schema: { body: wrappedDekBodySchema, response: { 200: wrappedDekResponseSchema } },
      preHandler: [server.authenticate],
    },
    async (request: FastifyRequest, reply: FastifyReply) => {
      const userId = (request.user as { sub: string }).sub;
      const body = request.body as { kdf?: { name?: string; params?: Record<string, unknown>; saltB64?: string }; wrappedDekB64?: string };
      if (!body?.kdf?.name || typeof body.kdf.saltB64 !== 'string' || typeof body.wrappedDekB64 !== 'string') {
        sendError(reply as any, 400, 'validation_error', 'Invalid body.', {});
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
    '/v1/crypto/wrapped-dek',
    {
      schema: { response: { 200: wrappedDekGetResponseSchema } },
      preHandler: [server.authenticate],
    },
    async (request: FastifyRequest, reply: FastifyReply) => {
      const userId = (request.user as { sub: string }).sub;
      const data = await cryptoService.getWrappedDek(userId);
      if (!data) {
        sendError(reply as any, 404, 'not_found', 'Wrapped DEK not found.', {});
        return;
      }
      reply.code(200).send(data);
    }
  );
}
