import { FastifyInstance } from 'fastify';
import { z } from 'zod';

export default async function cryptoRoutes(server: FastifyInstance) {
  // Guardar wrapped DEK (dummy)
  server.put('/v1/crypto/wrapped-dek', {
    schema: {
      body: z.object({
        kdf: z.object({ name: z.string(), params: z.object({}), saltB64: z.string() }),
        wrappedDekB64: z.string(),
      }).strict(),
      response: { 200: z.object({ ok: z.boolean() }) },
    },
    preHandler: [server.authenticate],
  }, async (request, reply) => {
    // [PENDIENTE] Guardar en DB
    return { ok: true };
  });

  // Obtener wrapped DEK (dummy)
  server.get('/v1/crypto/wrapped-dek', {
    schema: {
      response: { 200: z.object({
        kdf: z.object({ name: z.string(), params: z.object({}), saltB64: z.string() }),
        wrappedDekB64: z.string(),
      }) },
    },
    preHandler: [server.authenticate],
  }, async (request, reply) => {
    // [PENDIENTE] Leer de DB
    return {
      kdf: { name: 'argon2id', params: {}, saltB64: '...' },
      wrappedDekB64: '...'
    };
  });
}
