import { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import * as recordsService from '../services/records';
import { sendError } from '../lib/errors';

const defaultRateLimit = { max: 300, timeWindow: '1 minute' as const };

const statusBodySchema = {
  type: 'object',
  properties: {
    status: { type: 'string', enum: ['PENDING', 'IN_PROCESS', 'ANSWERED'] },
  },
  required: ['status'],
  additionalProperties: false,
};

export default async function recordsRoutes(server: FastifyInstance) {
  server.patch<{ Params: { recordId: string } }>(
    '/records/:recordId/status',
    {
      config: { rateLimit: defaultRateLimit },
      schema: {
        params: { type: 'object', properties: { recordId: { type: 'string' } }, required: ['recordId'], additionalProperties: false },
        body: statusBodySchema,
      },
      preHandler: [server.authenticate],
    },
    async (request, reply) => {
      const userId = (request.user as { sub: string }).sub;
      const body = request.body as { status: string };
      const result = await recordsService.updateRecordStatus(userId, request.params.recordId, body.status);
      if (!result.ok) {
        const code = result.error === 'record not found' ? 404 : 400;
        sendError(reply, code, 'validation_error', result.error, {});
        return;
      }
      reply.code(200).send(result.record);
    }
  );
}
