import { FastifyInstance } from 'fastify';
import { z } from 'zod';

export default async function authRoutes(server: FastifyInstance) {
  // Solicitar magic link (dummy, SMTP pendiente)
  server.post('/v1/auth/magic-link/request', {
    schema: {
      body: z.object({ email: z.string().email() }).strict(),
      response: { 200: z.object({ ok: z.boolean() }) },
    },
  }, async (request, reply) => {
    // [PENDIENTE] Enviar email real
    return { ok: true };
  });

  // Consumir magic link (dummy, sin validación real)
  server.post('/v1/auth/magic-link/consume', {
    schema: {
      body: z.object({ token: z.string() }).strict(),
      response: { 200: z.object({
        accessToken: z.string(),
        user: z.object({ id: z.string(), email: z.string().email(), createdAt: z.string() })
      }) },
    },
  }, async (request, reply) => {
    // [PENDIENTE] Validar token y buscar usuario
    return {
      accessToken: 'dummy.jwt.token',
      user: { id: 'uuid', email: 'user@example.com', createdAt: new Date().toISOString() }
    };
  });
}
