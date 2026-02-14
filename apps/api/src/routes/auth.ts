import { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import * as authService from '../services/auth';
import { sendError } from '../lib/errors';

const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const PASSWORD_MIN = 8;
const PASSWORD_MAX = 256;

function validateEmail(email: unknown): string | null {
  if (typeof email !== 'string') return null;
  const trimmed = email.trim().toLowerCase();
  return trimmed.length > 0 && emailRegex.test(trimmed) ? trimmed : null;
}

function validatePassword(password: unknown): string | null {
  if (typeof password !== 'string') return null;
  if (password.length < PASSWORD_MIN || password.length > PASSWORD_MAX) return null;
  return password;
}

export default async function authRoutes(server: FastifyInstance) {
  const rateLimitAuth = { config: { rateLimit: { max: 10, timeWindow: '1 minute' } } };

  const registerBodySchema = {
    type: 'object',
    properties: {
      email: { type: 'string', format: 'email' },
      password: { type: 'string', minLength: PASSWORD_MIN, maxLength: PASSWORD_MAX },
    },
    required: ['email', 'password'],
    additionalProperties: false,
  };
  const loginBodySchema = registerBodySchema;
  const refreshBodySchema = {
    type: 'object',
    properties: { refreshToken: { type: 'string' } },
    required: ['refreshToken'],
    additionalProperties: false,
  };
  const logoutBodySchema = refreshBodySchema;

  const authResponseSchema = {
    type: 'object',
    properties: {
      accessToken: { type: 'string' },
      refreshToken: { type: 'string' },
      expiresIn: { type: 'number' },
      user: {
        type: 'object',
        properties: {
          id: { type: 'string' },
          email: { type: 'string' },
          role: { type: 'string' },
          createdAt: { type: 'string' },
        },
        required: ['id', 'email', 'role', 'createdAt'],
        additionalProperties: false,
      },
    },
    required: ['accessToken', 'refreshToken', 'expiresIn', 'user'],
    additionalProperties: false,
  };
  const okSchema = { type: 'object', properties: { ok: { type: 'boolean', const: true } }, required: ['ok'], additionalProperties: false };
  const meResponseSchema = {
    type: 'object',
    properties: { id: { type: 'string' }, email: { type: 'string' }, role: { type: 'string' } },
    required: ['id', 'email', 'role'],
    additionalProperties: false,
  };

  const signAccess = (payload: { sub: string; email: string; role?: string }) =>
    server.jwt.sign(payload, { expiresIn: authService.getAccessTokenTtl() });

  server.post(
    '/auth/register',
    { config: { rateLimit: { max: 5, timeWindow: '1 minute' } }, schema: { body: registerBodySchema, response: { 200: authResponseSchema } } },
    async (request: FastifyRequest<{ Body: { email: string; password: string } }>, reply: FastifyReply) => {
      const email = validateEmail(request.body?.email);
      const password = validatePassword(request.body?.password);
      if (!email || !password) {
        sendError(reply, 400, 'validation_error', 'Invalid email or password.', {});
        return;
      }
      const result = await authService.register(email, password, signAccess);
      if (!result.ok) {
        if (result.conflict) {
          sendError(reply, 409, 'conflict', 'Email already registered.', {});
          return;
        }
        sendError(reply, 400, 'bad_request', 'Registration failed.', {});
        return;
      }
      reply.code(200).send(result);
    }
  );

  server.post(
    '/auth/login',
    { config: { rateLimit: { max: 10, timeWindow: '1 minute' } }, schema: { body: loginBodySchema, response: { 200: authResponseSchema } } },
    async (request: FastifyRequest<{ Body: { email: string; password: string } }>, reply: FastifyReply) => {
      const email = validateEmail(request.body?.email);
      const password = validatePassword(request.body?.password);
      if (!email || !password) {
        sendError(reply, 400, 'validation_error', 'Invalid email or password.', {});
        return;
      }
      const result = await authService.login(email, password, signAccess);
      if (!result.ok) {
        if (result.invalidCredentials) {
          sendError(reply, 401, 'unauthorized', 'Invalid email or password.', {});
          return;
        }
        sendError(reply, 400, 'bad_request', 'Login failed.', {});
        return;
      }
      reply.code(200).send(result);
    }
  );

  server.post(
    '/auth/refresh',
    { config: { rateLimit: { max: 20, timeWindow: '1 minute' } }, schema: { body: refreshBodySchema, response: { 200: authResponseSchema } } },
    async (request: FastifyRequest<{ Body: { refreshToken: string } }>, reply: FastifyReply) => {
      const refreshToken = typeof request.body?.refreshToken === 'string' && request.body.refreshToken.length > 0 ? request.body.refreshToken : null;
      if (!refreshToken) {
        sendError(reply, 400, 'validation_error', 'refreshToken is required.', {});
        return;
      }
      const result = await authService.refresh(refreshToken, signAccess);
      if (!result.ok) {
        if (result.invalidToken) {
          sendError(reply, 401, 'unauthorized', 'Invalid or expired refresh token.', {});
          return;
        }
        sendError(reply, 400, 'bad_request', 'Refresh failed.', {});
        return;
      }
      reply.code(200).send(result);
    }
  );

  server.get(
    '/auth/me',
    {
      config: { rateLimit: { max: 60, timeWindow: '1 minute' } },
      preHandler: [server.authenticate],
      schema: { response: { 200: meResponseSchema } },
    },
    async (request: FastifyRequest, reply: FastifyReply) => {
      const user = request.user as { sub: string; email: string; role?: string };
      reply.code(200).send({ id: user.sub, email: user.email, role: user.role ?? 'user' });
    }
  );

  server.post(
    '/auth/logout',
    { config: { rateLimit: { max: 20, timeWindow: '1 minute' } }, schema: { body: logoutBodySchema, response: { 200: okSchema } } },
    async (request: FastifyRequest<{ Body: { refreshToken: string } }>, reply: FastifyReply) => {
      const refreshToken = typeof request.body?.refreshToken === 'string' ? request.body.refreshToken : '';
      await authService.logout(refreshToken);
      reply.code(200).send({ ok: true });
    }
  );
}
