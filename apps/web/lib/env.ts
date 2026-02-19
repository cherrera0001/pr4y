/**
 * Configuración estrictamente desde variables de entorno.
 * Zero hardcoding: sin URLs ni IDs en código. Configurar en .env y en Vercel/Railway.
 */

/**
 * Base URL de la API (ej. Railway). Obligatoria en producción.
 * Acepta NEXT_PUBLIC_API_URL o NEXT_PUBLIC_API_BASE_URL (Vercel).
 * Si la URL no termina en /v1, se añade automáticamente para que auth/google, auth/me y admin/* resuelvan (las rutas están bajo /v1 en la API).
 * Si el valor no empieza por http:// o https://, se antepone https://.
 */
export function getApiBaseUrl(): string {
  const raw =
    process.env.NEXT_PUBLIC_API_URL ?? process.env.NEXT_PUBLIC_API_BASE_URL ?? '';
  let url = (typeof raw === 'string' ? raw.trim() : '').replace(/\/$/, '') || '';
  if (!url) return '';
  if (!/^https?:\/\//i.test(url)) url = `https://${url}`;
  if (!/\/v1$/i.test(url)) url = `${url}/v1`;
  return url;
}

/** Cliente Web: solo para la versión web (Sign in with Google en /admin). No usar el de Android aquí. */
export function getGoogleWebClientId(): string {
  const id = process.env.NEXT_PUBLIC_GOOGLE_WEB_CLIENT_ID;
  return typeof id === 'string' ? id.trim() : '';
}

/** Host canónico para redirección (ej. vercel.app → pr4y.cl). Opcional. */
export function getCanonicalHost(): string {
  const host = process.env.NEXT_PUBLIC_CANONICAL_HOST;
  return typeof host === 'string' ? host.trim() : '';
}
