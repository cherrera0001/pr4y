import { FastifyInstance } from 'fastify';
import { z } from 'zod';
import fromZodSchema from 'zod-to-json-schema';

export default async function syncRoutes(server: FastifyInstance) {
  // Pull incremental (dummy)
  const pullQuerySchema = z.object({ cursor: z.string().optional(), limit: z.number().optional() });
  const pullRecordSchema = z.object({
    recordId: z.string(),
    type: z.string(),
    version: z.number(),
    encryptedPayloadB64: z.string(),
    clientUpdatedAt: z.string(),
    serverUpdatedAt: z.string(),
    deleted: z.boolean(),
  });
  const pullResponseSchema = z.object({
    nextCursor: z.string(),
    records: z.array(pullRecordSchema),
  });
  server.get('/v1/sync/pull', {
    schema: {
      querystring: fromZodSchema(pullQuerySchema).schema,
      response: { 200: fromZodSchema(pullResponseSchema).schema },
    },
    preHandler: [server.authenticate],
  }, async (request, reply) => {
    // [PENDIENTE] Leer de DB
    return {
      nextCursor: 'opaque_cursor',
      records: []
    };
  });

  // Push batch (dummy)
  const pushRecordSchema = pullRecordSchema;
  const pushBodySchema = z.object({ records: z.array(pushRecordSchema) });
  const pushRejectedSchema = z.object({ recordId: z.string(), reason: z.string() });
  const pushResponseSchema = z.object({
    accepted: z.array(z.string()),
    rejected: z.array(pushRejectedSchema),
    serverTime: z.string(),
  });
  server.post('/v1/sync/push', {
    schema: {
      body: fromZodSchema(pushBodySchema).schema,
      response: { 200: fromZodSchema(pushResponseSchema).schema },
    },
    preHandler: [server.authenticate],
  }, async (request, reply) => {
    // [PENDIENTE] Guardar en DB y validar versiones
    return {
      accepted: [],
      rejected: [],
      serverTime: new Date().toISOString(),
    };
  });
}
