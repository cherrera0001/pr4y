import { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import * as adminService from '../services/admin';

const defaultRateLimit = { max: 120, timeWindow: '1 minute' as const };

/**
 * GET /v1/public/content — listar contenido global publicado (Palabras de Aliento, Avisos).
 * Sin autenticación. Lo que el admin publica aquí llega a todos los usuarios (web y apps).
 */
export default async function publicContentRoutes(server: FastifyInstance) {
  server.get<{ Querystring: { type?: string } }>(
    '/public/content',
    { config: { rateLimit: defaultRateLimit } },
    async (request: FastifyRequest<{ Querystring: { type?: string } }>, reply: FastifyReply) => {
      const type = request.query?.type?.trim() || undefined;
      const list = await adminService.listPublishedContent(type);
      return reply.send({ items: list });
    }
  );
}
