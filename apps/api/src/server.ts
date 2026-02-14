// Cargar env lo primero (antes de cualquier import que use process.env)
import 'dotenv/config';

import Fastify, { FastifyInstance, FastifyReply, FastifyRequest } from 'fastify';
import cors from '@fastify/cors';
import helmet from '@fastify/helmet';
import rateLimit from '@fastify/rate-limit';
import jwt from '@fastify/jwt';
import authRoutes from './routes/auth';
import syncRoutes from './routes/sync';
import cryptoRoutes from './routes/crypto';

const BODY_LIMIT = 2 * 1024 * 1024; // 2MB global

// Crear instancia
const server: FastifyInstance = Fastify({ logger: true, bodyLimit: BODY_LIMIT });

// Cabeceras de seguridad (API JSON: CSP desactivada)
server.register(helmet, { contentSecurityPolicy: false });

// CORS solo a Vercel origin (ajustar en prod)
server.register(cors, {
  origin: process.env.CORS_ORIGIN || 'http://localhost:3000',
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

// Health endpoint
server.get(
  '/v1/health',
  { config: { rateLimit: defaultRateLimit } },
  async () => ({ status: 'ok', version: '1.0.0' })
);

// Rutas
authRoutes(server);
syncRoutes(server);
cryptoRoutes(server);

// Arranque (Railway inyecta PORT, p. ej. 8080)
const port = Number(process.env.PORT) || 4000;
const start = async () => {
  try {
    await server.listen({ port, host: '0.0.0.0' });
    console.log(`API running on port ${port}`);
  } catch (err) {
    server.log.error(err);
    process.exit(1);
  }
};

start();
