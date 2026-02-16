/**
 * Sanitización y validación de texto (título, cuerpo, notas).
 * Elimina HTML/scripts y caracteres de control; permite solo alfanumérico y puntuación básica.
 * Usado en API y debe replicarse en Android (InputSanitizer) para consistencia.
 */

import { z } from 'zod';

const CONTROL_AND_DANGEROUS = /[\x00-\x08\x0B\x0C\x0E-\x1F\x7F]|<[^>]*>|script\s*:|javascript\s*:|on\w+\s*=/gi;

/** Elimina etiquetas HTML, scripts y caracteres de control. */
export function stripHtmlAndControlChars(value: string): string {
  if (typeof value !== 'string') return '';
  return value
    .replace(CONTROL_AND_DANGEROUS, '')
    .replace(/\s+/g, ' ')
    .trim();
}

/** Regex: solo letras, números, espacios y puntuación básica (evita inyección). */
const SAFE_TEXT = /^[\p{L}\p{N}\s.,;:!?'"\-()¿¡—–áéíóúüñÁÉÍÓÚÜÑ]+$/u;

/** Longitudes máximas por tipo (alineado con cliente). */
export const LIMITS = {
  title: 200,
  body: 10_000,
  notes: 2_000,
  testimony: 5_000,
} as const;

const safeStringSchema = (maxLen: number) =>
  z
    .string()
    .max(maxLen, `Máximo ${maxLen} caracteres`)
    .transform(stripHtmlAndControlChars)
    .refine((s) => s.length <= maxLen, `Máximo ${maxLen} caracteres tras limpieza`)
    .refine(
      (s) => s === '' || SAFE_TEXT.test(s),
      'Solo se permiten letras, números, espacios y puntuación básica'
    );

export const sanitizeSchema = {
  title: safeStringSchema(LIMITS.title).optional().nullable(),
  body: safeStringSchema(LIMITS.body).optional().nullable(),
  notes: safeStringSchema(LIMITS.notes).optional().nullable(),
  testimony: safeStringSchema(LIMITS.testimony).optional().nullable(),
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
