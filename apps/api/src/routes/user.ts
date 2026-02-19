import { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import * as userService from '../services/user';

const purgeResponseSchema = {
  type: 'object',
  properties: {
    ok: { type: 'boolean' },
    recordsDeleted: { type: 'number' },
    usageLogsDeleted: { type: 'number' },
  },
  required: ['ok', 'recordsDeleted', 'usageLogsDeleted'],
  additionalProperties: false,
};

export default async function userRoutes(server: FastifyInstance) {
  server.post(
    '/user/purge-data',
    {
      schema: { response: { 200: purgeResponseSchema } },
      preHandler: [server.authenticate],
    },
    async (request: FastifyRequest, reply: FastifyReply) => {
      const userId = (request.user as { sub: string }).sub;
      const result = await userService.purgeUserData(userId);
      reply.code(200).send({
        ok: true,
        recordsDeleted: result.recordsDeleted,
        usageLogsDeleted: result.usageLogsDeleted,
      });
    }
  );
}
