import { FastifyInstance } from 'fastify';
import { z } from 'zod';

export default async function syncRoutes(server: FastifyInstance) {
  // Pull incremental (dummy)
  server.get('/v1/sync/pull', {
    schema: {
      querystring: z.object({ cursor: z.string().optional(), limit: z.number().optional() }),
      response: { 200: z.object({
        nextCursor: z.string(),
        records: z.array(z.object({
          recordId: z.string(),
          type: z.string(),
          version: z.number(),
          encryptedPayloadB64: z.string(),
          clientUpdatedAt: z.string(),
          serverUpdatedAt: z.string(),
          deleted: z.boolean(),
        }))
      }) },
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
  server.post('/v1/sync/push', {
    schema: {
      body: z.object({
        records: z.array(z.object({
          recordId: z.string(),
          type: z.string(),
          version: z.number(),
          encryptedPayloadB64: z.string(),
          clientUpdatedAt: z.string(),
          deleted: z.boolean(),
        }))
      }),
      response: { 200: z.object({
        accepted: z.array(z.string()),
        rejected: z.array(z.object({ recordId: z.string(), reason: z.string() })),
        serverTime: z.string(),
      }) },
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
