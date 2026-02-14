import { FastifyInstance } from 'fastify';
import { prisma } from '../lib/db';

const defaultRateLimit = { max: 300, timeWindow: '1 minute' as const };

// Ruta /health (sin /v1): el prefijo se aplica en server.ts (prefix: '/v1') => URL final /v1/health
export default async function healthRoute(server: FastifyInstance) {
  server.get('/health', { config: { rateLimit: defaultRateLimit } }, async () => {
    let database: 'connected' | 'error' = 'error';
    try {
      await prisma.$queryRaw`SELECT 1`;
      database = 'connected';
    } catch {
      // DATABASE_URL no configurada o Postgres inaccesible
    }
    return {
      status: database === 'connected' ? 'ok' : 'degraded',
      version: '1.0.0',
      database,
    };
  });
}
