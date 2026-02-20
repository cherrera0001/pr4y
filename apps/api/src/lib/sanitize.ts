/**
 * Sanitización y validación de texto (título, cuerpo, notas, testimonio, contenido admin).
 * Elimina HTML/scripts, caracteres de control y rechaza patrones típicos de inyección.
 * Usado en API y debe replicarse en Android (InputSanitizer) para consistencia.
 */

import { z } from 'zod';

const CONTROL_AND_DANGEROUS = /[\x00-\x08\x0B\x0C\x0E-\x1F\x7F]|<[^>]*>|script\s*:|javascript\s*:|on\w+\s*=/gi;

/** Patrones que rechazamos en texto libre (defensa en profundidad; Prisma ya usa consultas parametrizadas). */
const SQLI_SUSPICIOUS = /[\x00]|;\s*--|\'\s*or\s*\'|\bunion\s+select\b/i;

/** Elimina etiquetas HTML, scripts y caracteres de control. */
export function stripHtmlAndControlChars(value: string): string {
  if (typeof value !== 'string') return '';
  return value
    .replace(CONTROL_AND_DANGEROUS, '')
    .replace(/\s+/g, ' ')
    .trim();
}

/** Devuelve true si el string contiene patrones sospechosos de inyección (rechazar, no limpiar). */
export function hasSuspiciousInjectionPattern(value: string): boolean {
  return typeof value === 'string' && SQLI_SUSPICIOUS.test(value);
}

/** Regex: letras, números, espacios, puntuación básica y emojis (evita inyección HTML). */
const SAFE_TEXT = /^[\p{L}\p{N}\s.,;:!?'"\-()¿¡—–áéíóúüñÁÉÍÓÚÜÑ\p{Emoji}]+$/u;

/** Longitudes máximas por tipo (alineado con cliente). */
export const LIMITS = {
  title: 200,
  body: 10_000,
  notes: 2_000,
  testimony: 5_000,
  adminContentTitle: 512,
  adminContentBody: 50_000,
  recordId: 64,
  recordType: 64,
} as const;

export type SanitizeObjectOptions = {
  /** Claves que son strings a sanitizar (stripHtmlAndControlChars + longitud máxima). */
  stringKeys: string[];
  /** Longitud máxima por clave (default desde LIMITS si existe). */
  maxLengths?: Partial<Record<string, number>>;
  /** Si true, rechazar strings con patrones SQLi sospechosos. */
  rejectSqliPatterns?: boolean;
  /** Si true, eliminar claves no listadas en stringKeys (y no en preserveKeys). */
  dropUnknown?: boolean;
  /** Claves a preservar sin sanitizar (p. ej. encryptedPayloadB64). */
  preserveKeys?: string[];
};

/**
 * Sanitiza recursivamente un objeto: aplica stripHtmlAndControlChars y límites a los strings indicados.
 * No modifica el contenido cifrado (usar preserveKeys para excluirlos).
 */
export function sanitizeObject<T extends Record<string, unknown>>(
  data: T,
  options: SanitizeObjectOptions
): { success: true; data: T } | { success: false; error: string } {
  const { stringKeys, maxLengths = {}, rejectSqliPatterns = true, dropUnknown = false, preserveKeys = [] } = options;
  const out = { ...data } as Record<string, unknown>;

  for (const key of Object.keys(out)) {
    const value = out[key];
    if (preserveKeys.includes(key)) continue;
    if (stringKeys.includes(key)) {
      if (typeof value !== 'string') {
        return { success: false, error: `Campo "${key}" debe ser string` };
      }
      if (rejectSqliPatterns && hasSuspiciousInjectionPattern(value)) {
        return { success: false, error: `Campo "${key}" contiene caracteres no permitidos` };
      }
      const maxLen = maxLengths[key] ?? (LIMITS as Record<string, number>)[key] ?? 10_000;
      const cleaned = stripHtmlAndControlChars(value);
      if (cleaned.length > maxLen) {
        return { success: false, error: `Campo "${key}" supera el máximo de ${maxLen} caracteres` };
      }
      out[key] = cleaned;
    } else if (dropUnknown) {
      delete out[key];
    }
  }
  return { success: true, data: out as T };
}

const safeStringSchema = (maxLen: number) =>
  z
    .string()
    .max(maxLen, `Máximo ${maxLen} caracteres`)
    .refine((s) => !hasSuspiciousInjectionPattern(s), 'Caracteres no permitidos')
    .transform(stripHtmlAndControlChars)
    .refine((s) => s.length <= maxLen, `Máximo ${maxLen} caracteres tras limpieza`)
    .refine(
      (s) => s === '' || SAFE_TEXT.test(s),
      'Solo se permiten letras, números, espacios, puntuación y emojis'
    );

export const sanitizeSchema = {
  title: safeStringSchema(LIMITS.title).optional().nullable(),
  body: safeStringSchema(LIMITS.body).optional().nullable(),
  notes: safeStringSchema(LIMITS.notes).optional().nullable(),
  testimony: safeStringSchema(LIMITS.testimony).optional().nullable(),
};

/** Esquemas para contenido admin (title/body/type con sanitización). */
export const adminContentSanitizeSchema = {
  type: z.string().min(1).max(LIMITS.recordType).transform(stripHtmlAndControlChars),
  title: safeStringSchema(LIMITS.adminContentTitle),
  body: z
    .string()
    .max(LIMITS.adminContentBody, `Máximo ${LIMITS.adminContentBody} caracteres`)
    .refine((s) => !hasSuspiciousInjectionPattern(s), 'Caracteres no permitidos')
    .transform(stripHtmlAndControlChars),
};

/** Valida y sanitiza un objeto con campos de texto. */
export function sanitizeTextInputs<T extends Record<string, unknown>>(
  data: T,
  keys: Array<keyof typeof sanitizeSchema>
): { success: true; data: T } | { success: false; error: string } {
  const out = { ...data } as T;
  for (const key of keys) {
    const schema = sanitizeSchema[key];
    if (!schema || !(key in data)) continue;
    const parsed = schema.safeParse((data as Record<string, unknown>)[key]);
    if (!parsed.success) {
      const msg = parsed.error.errors.map((e) => e.message).join('; ');
      return { success: false, error: msg };
    }
    (out as Record<string, unknown>)[key] = parsed.data ?? (data as Record<string, unknown>)[key];
  }
  return { success: true, data: out };
}
