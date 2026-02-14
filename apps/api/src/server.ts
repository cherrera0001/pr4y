// Cargar env lo primero (antes de cualquier import que use process.env)
import 'dotenv/config';

import Fastify, { FastifyInstance, FastifyReply, FastifyRequest } from 'fastify';
import cors from '@fastify/cors';
import helmet from '@fastify/helmet';
import rateLimit from '@fastify/rate-limit';
import jwt from '@fastify/jwt';
import { prisma } from './lib/db';
import authRoutes from './routes/auth';
import syncRoutes from './routes/sync';
import cryptoRoutes from './routes/crypto';
import healthRoutes from './routes/health';
import adminRoutes from './routes/admin';

const BODY_LIMIT = 2 * 1024 * 1024; // 2MB global

// Crear instancia
const server: FastifyInstance = Fastify({ logger: true, bodyLimit: BODY_LIMIT });

// Cabeceras de seguridad (API JSON: CSP desactivada)
server.register(helmet, { contentSecurityPolicy: false });

// CORS: pr4y.cl en producción y orígenes locales (desarrollo + App Web)
const allowedOrigins = [
  'https://pr4y.cl',
  'http://localhost:3000',
  'http://127.0.0.1:3000',
  'http://localhost:3001',
  'http://127.0.0.1:3001',
];
server.register(cors, {
  origin: (origin: string | undefined, cb: (err: Error | null, allow: boolean) => void) => {
    if (!origin) return cb(null, true); // p. ej. Postman o app móvil sin Origin
    if (allowedOrigins.includes(origin)) return cb(null, true);
    cb(null, false); // rechazar sin enviar error al cliente
  },
  credentials: true,
});

// Rate limiting: global: false para que los límites por ruta en auth (login/register) se apliquen
server.register(rateLimit, {
  global: false,
  max: 300,
  timeWindow: '1 minute',
});

// JWT (para endpoints protegidos)
server.register(jwt, {
  secret: process.env.JWT_SECRET || 'changeme',
});

// Decorador de autenticación
server.decorate('authenticate', async function (request: FastifyRequest, reply: FastifyReply) {
  try {
    await request.jwtVerify();
  } catch (err) {
    reply.code(401).send({ error: { code: 'unauthorized', message: 'Invalid token', details: {} } });
  }
});

// Decorador requireAdmin: debe usarse después de authenticate. Restringe métricas/marketing a role admin.
server.decorate('requireAdmin', async function (request: FastifyRequest, reply: FastifyReply) {
  const user = request.user as { sub?: string; email?: string; role?: string };
  const role = user?.role;
  if (role !== 'admin' && role !== 'super_admin') {
    reply.code(403).send({ error: { code: 'forbidden', message: 'Admin role required', details: {} } });
  }
});

// Error handler global: no filtrar stack ni mensajes de Prisma al cliente
server.setErrorHandler((error: Error & { validation?: unknown }, request, reply) => {
  if (error.validation) {
    return reply.code(400).send({
      error: {
        code: 'validation_error',
        message: 'Invalid input',
        details: error.validation,
      },
    });
  }
  server.log.error(error);
  return reply.code(500).send({
    error: {
      code: 'internal_error',
      message: 'An unexpected error occurred.',
      details: {},
    },
  });
});

// Límite por defecto para rutas que no definen el suyo (auth tiene límites más estrictos)
const defaultRateLimit = { max: 300, timeWindow: '1 minute' as const };

// Listener de rutas: al arranque se imprimen en logs (Railway) como "Mapped {/v1/auth/login, POST}"
const routesLog: Array<{ path: string; method: string }> = [];
server.addHook('onRoute', (routeOptions: { url?: string; path?: string; method?: string }) => {
  const path = (routeOptions.url ?? routeOptions.path ?? '').trim() || '/';
  const method = (routeOptions.method ?? 'GET').toUpperCase();
  routesLog.push({ path, method });
});

// Prefijo /v1 de forma explícita: la URL final es prefijo + ruta (ej. /v1 + /health => /v1/health).
// Cada módulo define solo la ruta relativa (ej. /health, /auth/register); no incluir /v1 en las rutas.
server.register(healthRoutes, { prefix: '/v1' });
server.register(authRoutes, { prefix: '/v1' });
server.register(syncRoutes, { prefix: '/v1' });
server.register(cryptoRoutes, { prefix: '/v1' });
server.register(adminRoutes, { prefix: '/v1' });

// Arranque: Railway inyecta PORT y DATABASE_URL. Escuchar en 0.0.0.0 es vital para Railway.
const port = Number(process.env.PORT) || 3000;
const start = async () => {
  try {
    await prisma.$queryRaw`SELECT 1`;
    console.log('Conexión a la base de datos establecida (DATABASE_URL)');
  } catch (err) {
    console.error('Error de conexión a la base de datos al arrancar:', err);
    if (err instanceof Error) {
      console.error('Mensaje:', err.message);
      if (err.stack) console.error('Stack:', err.stack);
    }
    process.exit(1);
  }
  try {
    await server.ready();
    console.log('=== Rutas registradas ===');
    for (const r of routesLog) {
      console.log(`Mapped {${r.path}, ${r.method}}`);
    }
    console.log('=== Fin rutas ===');
    // Log inmediatamente antes de listen: estructura exacta expuesta (para consola Railway).
    const routesTable = server.printRoutes();
    console.log('--- printRoutes() ---\n' + (routesTable || '(vacío)') + '\n--- fin printRoutes ---');
    await server.listen({ port, host: '0.0.0.0' });
    console.log(`API running on port ${port}`);
  } catch (err) {
    server.log.error(err);
    process.exit(1);
  }
};

start();
