import type { FastifyReply } from 'fastify';

export type ErrorCode =
  | 'bad_request'
  | 'unauthorized'
  | 'forbidden'
  | 'not_found'
  | 'conflict'
  | 'validation_error'
  | 'internal_error';

export interface ApiErrorBody {
  error: {
    code: ErrorCode;
    message: string;
    details?: Record<string, unknown>;
  };
}

export function sendError(
  reply: FastifyReply,
  status: number,
  code: ErrorCode,
  message: string,
  details?: Record<string, unknown>
): void {
  reply.code(status).send({
    error: { code, message, details: details ?? {} },
  });
}

/** Códigos Prisma que indican tabla/relación o columna inexistente (esquema no aplicado). */
const PRISMA_SCHEMA_ERROR_CODES = new Set(['P2021', 'P2018', 'P2003']);
const SCHEMA_HINT =
  'Database schema may not be ready. Run migrations (e.g. npx prisma migrate deploy) and retry.';

/**
 * Devuelve detalles seguros para el cliente a partir de un error (sin exponer stack ni mensaje interno).
 * Para errores de esquema Prisma, incluye un hint genérico para que el frontend pueda mostrar algo útil.
 */
export function safeDetailsFromError(err: unknown): Record<string, unknown> {
  if (err instanceof Error) {
    const code = (err as { code?: string }).code;
    const msg = (err.message || '').toLowerCase();
    const isSchemaError =
      (code && PRISMA_SCHEMA_ERROR_CODES.has(code)) ||
      msg.includes('does not exist') ||
      msg.includes('relation') ||
      msg.includes('column');
    if (isSchemaError) {
      return { hint: SCHEMA_HINT };
    }
  }
  return {};
}
