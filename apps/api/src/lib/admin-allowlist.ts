/**
 * Únicas cuentas que pueden tener rol admin/super_admin y acceder al panel.
 * Fuente única: variable de entorno ADMIN_EMAILS (correos separados por comas).
 * Cualquier otra cuenta queda rechazada aunque en BD tuviera role admin.
 */

const FALLBACK_EMAILS = [
  'crherrera@c4a.cl',
  'herrera.jara.cristobal@gmail.com',
].map((e) => e.toLowerCase());

function parseAdminEmailsFromEnv(): string[] {
  const raw = process.env.ADMIN_EMAILS;
  if (typeof raw === 'string' && raw.trim()) {
    return raw
      .split(',')
      .map((e) => e.trim().toLowerCase())
      .filter(Boolean);
  }
  return [];
}

let _cached: string[] | null = null;

/** Lista normalizada (solo minúsculas, sin duplicados). Fuente: ADMIN_EMAILS en env o fallback. */
export function getAllowedAdminEmails(): string[] {
  if (_cached !== null) return _cached;
  const fromEnv = parseAdminEmailsFromEnv();
  if (fromEnv.length > 0) {
    _cached = [...new Set(fromEnv)];
    return _cached;
  }
  if (process.env.NODE_ENV === 'production') {
    console.error(
      '[admin-allowlist] ADMIN_EMAILS no está definida en producción. Ningún admin podrá acceder. Defínela en Railway (ej: ADMIN_EMAILS=email1@ejemplo.com,email2@ejemplo.com).'
    );
    _cached = [];
    return _cached;
  }
  console.warn(
    '[admin-allowlist] ADMIN_EMAILS no definida; usando lista por defecto. En producción define ADMIN_EMAILS en el entorno.'
  );
  _cached = [...new Set(FALLBACK_EMAILS)];
  return _cached;
}

export function isAllowedAdminEmail(email: string | undefined): boolean {
  if (!email || typeof email !== 'string') return false;
  const list = getAllowedAdminEmails();
  if (list.length === 0) return false;
  return list.includes(email.trim().toLowerCase());
}

/** Validación en arranque: comprobar que la allowlist está definida y no vacía (solo en producción). */
export function validateAdminAllowlistAtStartup(log: { warn: (o: unknown, msg: string) => void }): void {
  const list = getAllowedAdminEmails();
  if (list.length === 0 && process.env.NODE_ENV === 'production') {
    log.warn({ adminEmails: [] }, 'Admin allowlist vacía en producción: nadie podrá acceder a /admin hasta definir ADMIN_EMAILS.');
  } else if (list.length > 0 && !process.env.ADMIN_EMAILS?.trim() && process.env.NODE_ENV !== 'production') {
    log.warn({ adminEmails: list }, 'Usando allowlist por defecto. Define ADMIN_EMAILS para producción.');
  }
}
