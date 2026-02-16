import { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import * as answersService from '../services/answers';
import { sendError } from '../lib/errors';

const defaultRateLimit = { max: 300, timeWindow: '1 minute' as const };

const createBodySchema = {
  type: 'object',
  properties: {
    recordId: { type: 'string' },
    testimony: { type: 'string', maxLength: 5000 },
  },
  required: ['recordId'],
  additionalProperties: false,
};

export default async function answersRoutes(server: FastifyInstance) {
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
      schema: { body: createBodySchema },
      preHandler: [server.authenticate],
    },
    async (request: FastifyRequest, reply: FastifyReply) => {
      const userId = (request.user as { sub: string }).sub;
      const body = request.body as { recordId: string; testimony?: string };
      const result = await answersService.createAnswer(userId, body.recordId, body.testimony);
      if (!result.ok) {
        const code = result.error === 'record not found or not owned' ? 404 : 400;
        sendError(reply, code, 'validation_error', result.error, {});
        return;
      }
      reply.code(201).send(result.answer);
    }
  );
}
