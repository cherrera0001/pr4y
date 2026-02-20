import { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import { z } from 'zod';
import * as answersService from '../services/answers';
import { sendError } from '../lib/errors';
import { sanitizeSchema, LIMITS } from '../lib/sanitize';

const defaultRateLimit = { max: 300, timeWindow: '1 minute' as const };

const createBodySchema = z.object({
  recordId: z.string().min(1).max(64),
  testimony: sanitizeSchema.testimony.optional().nullable(),
}).strict();

export default async function answersRoutes(server: FastifyInstance) {
  server.get(
    '/answers/stats',
    {
      config: { rateLimit: defaultRateLimit },
      preHandler: [server.authenticate],
    },
    async (request: FastifyRequest, reply: FastifyReply) => {
      const userId = (request.user as { sub: string }).sub;
      const answeredCount = await answersService.getAnsweredCount(userId);
      reply.code(200).send({ answeredCount });
    }
  );

  server.get(
    '/answers',
    {
      config: { rateLimit: defaultRateLimit },
      preHandler: [server.authenticate],
    },
    async (request: FastifyRequest, reply: FastifyReply) => {
      const userId = (request.user as { sub: string }).sub;
      const list = await answersService.listByUser(userId);
      reply.code(200).send({ answers: list });
    }
  );

  server.get<{ Params: { id: string } }>(
    '/answers/:id',
    {
      config: { rateLimit: defaultRateLimit },
      schema: { params: { type: 'object', properties: { id: { type: 'string' } }, required: ['id'], additionalProperties: false } },
      preHandler: [server.authenticate],
    },
    async (request, reply) => {
      const userId = (request.user as { sub: string }).sub;
      const answer = await answersService.getById(userId, request.params.id);
      if (!answer) {
        sendError(reply, 404, 'not_found', 'Answer not found.', {});
        return;
      }
      reply.code(200).send(answer);
    }
  );

  server.post(
    '/answers',
    {
      config: { rateLimit: defaultRateLimit },
      schema: {
        body: {
          type: 'object',
          properties: {
            recordId: { type: 'string', minLength: 1, maxLength: 64 },
            testimony: { type: 'string', maxLength: LIMITS.testimony },
          },
          required: ['recordId'],
          additionalProperties: false,
        },
      },
      preHandler: [server.authenticate],
    },
    async (request: FastifyRequest, reply: FastifyReply) => {
      const userId = (request.user as { sub: string }).sub;
      const parsed = createBodySchema.safeParse(request.body);
      if (!parsed.success) {
        const msg = parsed.error.errors.map((e) => e.message).join('; ');
        sendError(reply, 400, 'validation_error', msg, {});
        return;
      }
      const result = await answersService.createAnswer(userId, parsed.data.recordId, parsed.data.testimony ?? undefined);
      if (!result.ok) {
        const code = result.error === 'record not found or not owned' ? 404 : 400;
        sendError(reply, code, 'validation_error', result.error, {});
        return;
      }
      reply.code(201).send(result.answer);
    }
  );
}
