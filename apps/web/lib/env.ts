/**
 * Configuración estrictamente desde variables de entorno.
 * Zero hardcoding: sin URLs ni IDs en código. Configurar en .env y en Vercel/Railway.
 */

/** Base URL de la API (ej. Railway). Obligatoria en producción. */
export function getApiBaseUrl(): string {
  const url = process.env.NEXT_PUBLIC_API_URL;
  return (typeof url === 'string' ? url.trim() : '').replace(/\/$/, '') || '';
}

/** Client ID de Google OAuth Web para el frontend (mismo valor que GOOGLE_WEB_CLIENT_ID del backend). */
export function getGoogleWebClientId(): string {
  const id = process.env.NEXT_PUBLIC_GOOGLE_WEB_CLIENT_ID;
  return typeof id === 'string' ? id.trim() : '';
}

/** Host canónico para redirección (ej. vercel.app → pr4y.cl). Opcional. */
export function getCanonicalHost(): string {
  const host = process.env.NEXT_PUBLIC_CANONICAL_HOST;
  return typeof host === 'string' ? host.trim() : '';
}
