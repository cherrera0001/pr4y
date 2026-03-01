// Cargar env lo primero (antes de cualquier import que use process.env)
import 'dotenv/config';

import Fastify, { FastifyInstance, FastifyReply, FastifyRequest } from 'fastify';
import cors from '@fastify/cors';
import helmet from '@fastify/helmet';
import rateLimit from '@fastify/rate-limit';
import jwt from '@fastify/jwt';
import { prisma } from './lib/db';
import { safeDetailsFromError } from './lib/errors';
import { isAllowedAdminEmail, validateAdminAllowlistAtStartup } from './lib/admin-allowlist';
import authRoutes from './routes/auth';
import syncRoutes from './routes/sync';
import cryptoRoutes from './routes/crypto';
import healthRoutes from './routes/health';
import configRoutes from './routes/config';
import adminRoutes from './routes/admin';
import remindersRoutes from './routes/reminders';
import answersRoutes from './routes/answers';
import recordsRoutes from './routes/records';
import userRoutes from './routes/user';
import publicRequestsRoutes from './routes/public-requests';

const BODY_LIMIT = 2 * 1024 * 1024; // 2MB global

// Crear instancia. trustProxy: true necesario para Cloudflare (X-Forwarded-* correctos).
const server: FastifyInstance = Fastify({
  logger: true,
  bodyLimit: BODY_LIMIT,
  trustProxy: true,
});

// Cabeceras de seguridad (API JSON: CSP desactivada)
server.register(helmet, { contentSecurityPolicy: false });

// CORS: orígenes permitidos solo desde env (comma-separated). Ej: CORS_ORIGINS=https://pr4y.cl,http://localhost:3000
function getAllowedOrigins(): string[] {
  const raw = process.env.CORS_ORIGINS;
  if (typeof raw !== 'string' || !raw.trim()) return [];
  return raw.split(',').map((s) => s.trim()).filter(Boolean);
}
const allowedOrigins = getAllowedOrigins();
server.register(cors, {
  origin: (origin: string | undefined, cb: (err: Error | null, allow: boolean) => void) => {
    // Sin Origin: Postman, curl, app Android (OkHttp/Retrofit) → permitido. No hace falta añadir com.pr4y.app.dev (es package, no origen).
    if (!origin) return cb(null, true);
    if (allowedOrigins.length > 0 && allowedOrigins.includes(origin)) return cb(null, true);
    cb(null, false); // rechazar sin enviar error al cliente
  },
  credentials: true,
});

// Rate limiting: global: false para que los límites por ruta se apliquen.
// keyGenerator: por usuario autenticado (userId) para mitigar DoS por cuenta; por IP si no hay JWT.
server.register(rateLimit, {
  global: false,
  max: 300,
  timeWindow: '1 minute',
  keyGenerator: (request: FastifyRequest) => {
    const user = (request as FastifyRequest & { user?: { sub?: string } }).user;
    return (user?.sub ?? request.ip) as string;
  },
});

// JWT: obligatorio desde env; sin valor por defecto en producción
const jwtSecret = process.env.JWT_SECRET;
if (!jwtSecret || jwtSecret.trim() === '') {
  throw new Error('JWT_SECRET is required. Set it in .env or in Railway variables.');
}
server.register(jwt, { secret: jwtSecret });

// Google OAuth: al menos uno de los dos Client IDs (Web o Android) para validar tokens.
const hasWeb = Boolean(process.env.GOOGLE_WEB_CLIENT_ID?.trim());
const hasAndroid = Boolean(process.env.GOOGLE_ANDROID_CLIENT_ID?.trim());
if (!hasWeb && !hasAndroid) {
  throw new Error(
    'Al menos uno de GOOGLE_WEB_CLIENT_ID o GOOGLE_ANDROID_CLIENT_ID es necesario para Google OAuth. Web para la versión web, Android para la app.'
  );
}

// Decorador de autenticación
server.decorate('authenticate', async function (request: FastifyRequest, reply: FastifyReply) {
  try {
    await request.jwtVerify();
  } catch (err) {
    reply.code(401).send({ error: { code: 'unauthorized', message: 'Invalid token', details: {} } });
  }
});

// Mensaje único para quien no es admin: amigable y claro (API y web).
const MSG_NOT_ADMIN = 'No eres administrador. Gracias, pero puedes usar la app prontamente.';

// Decorador requireAdmin: solo role admin/super_admin Y email en allowlist pueden acceder a /admin/*.
server.decorate('requireAdmin', async function (request: FastifyRequest, reply: FastifyReply) {
  const user = request.user as { sub?: string; email?: string; role?: string };
  const role = user?.role;
  if (role !== 'admin' && role !== 'super_admin') {
    return reply.code(403).send({ error: { code: 'forbidden', message: MSG_NOT_ADMIN, details: {} } });
  }
  if (!isAllowedAdminEmail(user?.email)) {
    return reply.code(403).send({ error: { code: 'forbidden', message: MSG_NOT_ADMIN, details: {} } });
  }
});

// Error handler global: no exponer stack ni mensajes internos al cliente; solo logger Fastify
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
  const prismaCode = error && typeof error === 'object' && 'code' in error ? (error as { code: string }).code : undefined;
  request.log.error({ err: error, prismaCode }, 'Unhandled error');
  const details = safeDetailsFromError(error);
  return reply.code(500).send({
    error: {
      code: 'internal_error',
      message: 'An unexpected error occurred.',
      details,
    },
  });
});

// Listener de rutas: al arranque se imprimen en logs (Railway) como "Mapped {/v1/auth/login, POST}"
const routesLog: Array<{ path: string; method: string }> = [];
server.addHook('onRoute', (routeOptions) => {
  const path = (routeOptions.url ?? routeOptions.path ?? '').trim() || '/';
  const method = Array.isArray(routeOptions.method) ? routeOptions.method[0] : routeOptions.method ?? 'GET';
  routesLog.push({ path, method: String(method).toUpperCase() });
});

// Prefijo global /v1: cada módulo se registra con prefix '/v1' (rutas finales: /v1/health, /v1/auth/register, etc.).
server.register(healthRoutes, { prefix: '/v1' });
server.register(configRoutes, { prefix: '/v1' });
server.register(authRoutes, { prefix: '/v1' });
server.register(syncRoutes, { prefix: '/v1' });
server.register(cryptoRoutes, { prefix: '/v1' });
server.register(remindersRoutes, { prefix: '/v1' });
server.register(answersRoutes, { prefix: '/v1' });
server.register(recordsRoutes, { prefix: '/v1' });
server.register(userRoutes, { prefix: '/v1' });
server.register(publicRequestsRoutes, { prefix: '/v1' });
server.register(adminRoutes, { prefix: '/v1' });

// Arranque: Railway espera escucha en puerto 8080. Binding 0.0.0.0 obligatorio para que el proxy enrute.
const port = Number(process.env.PORT) || 8080;
const start = async () => {
  console.log(`[PR4Y] Arranque: PORT=${process.env.PORT ?? '(no set)'} → usando ${port}, host 0.0.0.0`);
  try {
    await prisma.$queryRaw`SELECT 1`;
    console.log('[PR4Y] Conexión establecida vía red privada interna');

    // Log de base de datos actual (confirmar que Prisma apunta a la DB correcta en Railway)
    const dbRows = await prisma.$queryRaw<Array<{ current_database: string }>>`SELECT current_database()`;
    const dbName = dbRows[0]?.current_database ?? '(desconocida)';
    console.log('[PR4Y] current_database:', dbName);

    // Lista de tablas en el schema public (confirmación tras migración)
    const tableRows = await prisma.$queryRaw<Array<{ tablename: string }>>`
      SELECT tablename FROM pg_tables WHERE schemaname = 'public' ORDER BY tablename
    `;
    const tableList = tableRows.map((r) => r.tablename).join(', ') || '(ninguna)';
    console.log('[PR4Y] Tablas en public:', tableList);

    // Verificación post-migración: conteo vía information_schema (visible en logs de Railway)
    const countRows = await prisma.$queryRaw<Array<{ count: string }>>`
      SELECT count(*)::text AS count FROM information_schema.tables WHERE table_schema = 'public'
    `;
    const publicTableCount = countRows[0]?.count ?? '0';
    console.log('[PR4Y] Verificación post-migración: public tables count =', publicTableCount);
  } catch (err) {
    server.log.error({ err }, 'DB connection failed at startup; server will still listen and /v1/health will report database: error');
  }
  try {
    await server.ready();
    validateAdminAllowlistAtStartup(server.log);
    console.log('=== Rutas registradas (onRoute) ===');
    for (const r of routesLog) {
      console.log(`Mapped {${r.path}, ${r.method}}`);
    }
    console.log('=== Fin rutas (onRoute) ===');
    console.log(server.printRoutes());
    await server.listen({ port: Number(process.env.PORT) || 8080, host: '0.0.0.0' });
    console.log(`API PR4Y activa en puerto ${port} y host 0.0.0.0`);
  } catch (err) {
    server.log.error(err);
    process.exit(1);
  }
};

start();
