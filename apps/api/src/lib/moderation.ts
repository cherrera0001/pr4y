/**
 * Moderación ligera para pedidos públicos de oración.
 * No se registra contenido en logs. Solo listas de bloqueo y reglas de longitud/URL.
 */

const BODY_MIN = 10;
const BODY_MAX = 2000;
const TITLE_MAX = 200;

/** Palabras o expresiones que provocan rechazo (minúsculas, sin acentos para comparación). */
const BLOCKLIST = new Set([
  'puta', 'puto', 'mierda', 'coño', 'carajo', 'joder', 'verga', 'pene', 'vagina',
  'culiar', 'follar', 'fuck', 'shit', 'ass', 'bitch', 'dick', 'cunt', 'pussy',
  'idiota', 'estupido', 'imbecil', 'maricon', 'maricón', 'gilipollas', 'cabron',
  'cabrón', 'tonto', 'retrasado', 'negro', 'negra', 'mata', 'matar', 'bomba',
  'terrorista', 'hitler', 'nazi', 'pedofilo', 'pedófilo', 'violador', 'violar',
]);

function normalizeForCheck(text: string): string {
  return text
    .toLowerCase()
    .normalize('NFD')
    .replace(/\p{M}/gu, '') // quitar acentos
    .replace(/\s+/g, ' ');
}

/** Detecta si el texto contiene alguna palabra bloqueada. */
function containsBlockedWord(text: string): boolean {
  const normalized = normalizeForCheck(text);
  const words = normalized.split(/\s+/);
  for (const word of words) {
    const cleaned = word.replace(/[^a-z0-9\u00f1]/gi, '');
    if (cleaned.length >= 3 && BLOCKLIST.has(cleaned)) return true;
  }
  // También buscar subcadenas largas (evitar "pu**" rodeado de espacios)
  for (const blocked of BLOCKLIST) {
    if (blocked.length >= 4 && normalized.includes(blocked)) return true;
  }
  return false;
}

/** Rechaza si hay URLs o patrones de spam (muchos enlaces). */
function containsDisallowedPatterns(text: string): boolean {
  const urlLike = /https?:\/\/|www\.|\.com|\.net|\.org|\.es|bit\.ly|tinyurl/i;
  const count = (text.match(urlLike) || []).length;
  return count >= 2 || (text.includes('http') && text.length > 100);
}

export interface ModerationResult {
  allowed: boolean;
  reason?: string;
}

/**
 * Evalúa si un pedido público puede ser aprobado.
 * No loguea el contenido.
 */
export function moderatePublicRequest(title: string, body: string): ModerationResult {
  const t = (title ?? '').trim();
  const b = (body ?? '').trim();

  if (b.length < BODY_MIN) {
    return { allowed: false, reason: 'El pedido es demasiado corto.' };
  }
  if (b.length > BODY_MAX) {
    return { allowed: false, reason: 'El pedido es demasiado largo.' };
  }
  if (t.length > TITLE_MAX) {
    return { allowed: false, reason: 'El título es demasiado largo.' };
  }
  if (containsBlockedWord(t) || containsBlockedWord(b)) {
    return { allowed: false, reason: 'El contenido no es adecuado para un pedido de oración.' };
  }
  if (containsDisallowedPatterns(t) || containsDisallowedPatterns(b)) {
    return { allowed: false, reason: 'No se permiten enlaces o mensajes promocionales.' };
  }
  return { allowed: true };
}

export const LIMITS = { BODY_MIN, BODY_MAX, TITLE_MAX } as const;
