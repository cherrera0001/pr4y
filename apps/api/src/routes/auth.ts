import { FastifyInstance } from 'fastify';
import { z } from 'zod';
import fromZodSchema from 'zod-to-json-schema';

export default async function authRoutes(server: FastifyInstance) {
  // Solicitar magic link (dummy, SMTP pendiente)
  const requestBodySchema = z.object({ email: z.string().email() }).strict();
  const requestResponseSchema = z.object({ ok: z.boolean() });
  server.post('/v1/auth/magic-link/request', {
    schema: {
      body: fromZodSchema(requestBodySchema).schema,
      response: { 200: fromZodSchema(requestResponseSchema).schema },
    },
  }, async (request, reply) => {
    // [PENDIENTE] Enviar email real
    return { ok: true };
  });

  // Consumir magic link (dummy, sin validación real)
  const consumeBodySchema = z.object({ token: z.string() }).strict();
  const consumeResponseSchema = z.object({
    accessToken: z.string(),
    user: z.object({ id: z.string(), email: z.string().email(), createdAt: z.string() })
  });
  server.post('/v1/auth/magic-link/consume', {
    schema: {
      body: fromZodSchema(consumeBodySchema).schema,
      response: { 200: fromZodSchema(consumeResponseSchema).schema },
    },
  }, async (request, reply) => {
    // [PENDIENTE] Validar token y buscar usuario
    return {
      accessToken: 'dummy.jwt.token',
      user: { id: 'uuid', email: 'user@example.com', createdAt: new Date().toISOString() }
    };
  });
}
