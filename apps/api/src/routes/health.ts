import { FastifyInstance } from 'fastify';

export default async function healthRoute(server: FastifyInstance) {
  server.get('/v1/health', async () => ({ status: 'ok', version: '1.0.0' }));
}
