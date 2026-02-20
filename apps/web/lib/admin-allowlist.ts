/**
 * Únicas cuentas que pueden acceder al panel admin.
 * Debe coincidir con la allowlist de la API (apps/api/src/lib/admin-allowlist.ts).
 */
const ALLOWED_ADMIN_EMAILS = [
  'crherrera@c4a.cl',
  'herrera.jara.cristobal@gmail.com',
].map((e) => e.toLowerCase());

export function isAllowedAdminEmail(email: string | undefined): boolean {
  if (!email || typeof email !== 'string') return false;
  return ALLOWED_ADMIN_EMAILS.includes(email.trim().toLowerCase());
}
