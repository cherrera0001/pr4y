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
  reply: { code: (n: number) => { send: (body: ApiErrorBody) => void } },
  status: number,
  code: ErrorCode,
  message: string,
  details?: Record<string, unknown>
) {
  reply.code(status).send({
    error: { code, message, details: details ?? {} },
  });
}
