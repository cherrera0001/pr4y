
import * as dotenv from 'dotenv';
dotenv.config();

import Fastify, { FastifyInstance, FastifyReply, FastifyRequest } from 'fastify';
import cors from '@fastify/cors';
import rateLimit from '@fastify/rate-limit';
import jwt from '@fastify/jwt';
import authRoutes from './routes/auth';
import syncRoutes from './routes/sync';
import cryptoRoutes from './routes/crypto';

// Crear instancia
const server: FastifyInstance = Fastify({ logger: true });

// CORS solo a Vercel origin (ajustar en prod)
server.register(cors, {
  origin: process.env.CORS_ORIGIN || 'http://localhost:3000',
  credentials: true,
});

// Rate limiting básico
server.register(rateLimit, {
  max: 60,
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

// Health endpoint
server.get('/v1/health', async () => ({ status: 'ok', version: '1.0.0' }));

// Rutas
authRoutes(server);
syncRoutes(server);
cryptoRoutes(server);

// Arranque
const start = async () => {
  try {
    await server.listen({ port: 4000, host: '0.0.0.0' });
    console.log('API running on http://localhost:4000');
  } catch (err) {
    server.log.error(err);
    process.exit(1);
  }
};

start();
