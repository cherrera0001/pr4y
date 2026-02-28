import { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import { prisma } from '../lib/db';
import { moderatePublicRequest } from '../lib/moderation';

const defaultRateLimit = { max: 300, timeWindow: '1 minute' as const };
/** Más estricto para crear pedidos (evitar spam). */
const createRateLimit = { max: 5, timeWindow: '5 minutes' as const };

type CreateBody = { title?: string; body: string };

export default async function publicRequestsRoutes(server: FastifyInstance) {
  // GET /v1/public/requests — listar pedidos aprobados para la Ruleta (anonimo)
  server.get(
    '/public/requests',
    { config: { rateLimit: defaultRateLimit } },
    async (_request: FastifyRequest, reply: FastifyReply) => {
      const list = await prisma.publicRequest.findMany({
        where: { status: 'approved' },
        orderBy: { createdAt: 'desc' },
        take: 100,
        select: {
          id: true,
          title: true,
          body: true,
          prayerCount: true,
          createdAt: true,
        },
      });
      const requests = list.map((r) => ({
        id: r.id,
        title: r.title,
        body: r.body,
        prayerCount: r.prayerCount,
        createdAt: r.createdAt.toISOString(),
      }));
      return reply.send({ requests });
    }
  );

  // POST /v1/public/requests — crear pedido anónimo (web pública). Moderación por blocklist.
  server.post<{ Body: CreateBody }>(
    '/public/requests',
    { config: { rateLimit: createRateLimit } },
    async (request: FastifyRequest<{ Body: CreateBody }>, reply: FastifyReply) => {
      const body = request.body?.body;
      const title = (request.body?.title ?? '').trim() || 'Pedido de oración';
      if (typeof body !== 'string') {
        return reply.code(400).send({
          error: {
            code: 'validation_error',
            message: 'Se requiere "body" con el texto del pedido.',
            details: {},
          },
        });
      }
      const result = moderatePublicRequest(title, body);
      if (!result.allowed) {
        return reply.code(400).send({
          error: {
            code: 'content_rejected',
            message: result.reason ?? 'El contenido no cumple las normas.',
            details: {},
          },
        });
      }
      const created = await prisma.publicRequest.create({
        data: {
          title: title.slice(0, 200),
          body: body.trim().slice(0, 2000),
          status: 'approved',
        },
      });
      return reply.code(201).send({
        id: created.id,
        message: 'Tu pedido de oración ha sido recibido. Será mostrado en la Ruleta para que otros oren por ti.',
      });
    }
  );

  // POST /v1/public/requests/:id/pray — incrementar contador de oraciones (anonimo)
  server.post<{ Params: { id: string } }>(
    '/public/requests/:id/pray',
    { config: { rateLimit: defaultRateLimit } },
    async (request: FastifyRequest<{ Params: { id: string } }>, reply: FastifyReply) => {
      const id = request.params?.id?.trim();
      if (!id) {
        return reply.code(400).send({
          error: { code: 'validation_error', message: 'Falta id.', details: {} },
        });
      }
      const updated = await prisma.publicRequest.updateMany({
        where: { id, status: 'approved' },
        data: { prayerCount: { increment: 1 } },
      });
      if (updated.count === 0) {
        return reply.code(404).send({
          error: { code: 'not_found', message: 'Pedido no encontrado.', details: {} },
        });
      }
      return reply.send({ ok: true });
    }
  );
}
