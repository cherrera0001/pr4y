import { FastifyInstance } from 'fastify';
import { prisma } from '../lib/db';

const defaultRateLimit = { max: 300, timeWindow: '1 minute' as const };

const isProd = process.env.NODE_ENV === 'production';

// /v1/health — public health check. En prod solo retorna {status} sin detalles internos (VULN-002).
export default async function healthRoute(server: FastifyInstance) {
  server.get('/health', { config: { rateLimit: defaultRateLimit } }, async () => {
    let database: 'connected' | 'error' = 'error';
    try {
      await prisma.$queryRaw`SELECT 1`;
      database = 'connected';
    } catch {
      // DATABASE_URL no configurada o Postgres inaccesible
    }
    const status = database === 'connected' ? 'ok' : 'degraded';
    if (isProd) return { status };
    return { status, version: '1.0.0', database };
  });
}
