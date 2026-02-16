package com.pr4y.app.util

/**
 * Sanitización de textos ingresados por el usuario antes de persistir o enviar.
 * - Trim de espacios.
 * - Eliminación de caracteres de control (ASCII 0-31, 127) que pueden causar problemas.
 * - Límite de longitud para evitar payloads desproporcionados.
 */
object InputSanitizer {

    private val CONTROL_OR_UNPRINTABLE = Regex("[\\x00-\\x1F\\x7F]")

    /**
     * Limpia y limita el string. Devuelve string vacío si null/blank.
     */
    fun sanitize(input: CharSequence?, maxLength: Int = 50_000): String {
        if (input == null) return ""
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ""
        val withoutControl = CONTROL_OR_UNPRINTABLE.replace(trimmed, "")
        return if (withoutControl.length <= maxLength) withoutControl
        else withoutControl.take(maxLength)
    }

    /** Para títulos cortos (ej. pedido de oración). */
    fun sanitizeTitle(input: CharSequence?) = sanitize(input, maxLength = 500)

    /** Para cuerpo de texto largo. */
    fun sanitizeBody(input: CharSequence?) = sanitize(input, maxLength = 50_000)
}
