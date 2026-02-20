/**
 * Únicas cuentas que pueden tener rol admin/super_admin y acceder al panel.
 * Cualquier otra cuenta queda rechazada aunque en BD tuviera role admin.
 */
const ALLOWED_ADMIN_EMAILS = [
  'crherrera@c4a.cl',
  'herrera.jara.cristobal@gmail.com',
].map((e) => e.toLowerCase());

export function isAllowedAdminEmail(email: string | undefined): boolean {
  if (!email || typeof email !== 'string') return false;
  return ALLOWED_ADMIN_EMAILS.includes(email.trim().toLowerCase());
}
