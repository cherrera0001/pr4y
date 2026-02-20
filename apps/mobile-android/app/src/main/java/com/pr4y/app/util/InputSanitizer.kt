package com.pr4y.app.util

/**
 * Sanitización de textos ingresados por el usuario antes de persistir o enviar.
 * - Trim de espacios.
 * - Eliminación de caracteres de control, HTML y patrones peligrosos (script:, on*=).
 * - Límite de longitud para evitar payloads desproporcionados.
 * Política alineada con API: se permiten letras, números, puntuación y emojis.
 */
object InputSanitizer {

    private val CONTROL_OR_UNPRINTABLE = Regex("[\\x00-\\x1F\\x7F]")
    private val CONTROL_AND_DANGEROUS = Regex(
        "[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]|<[^>]*>|script\\s*:|javascript\\s*:|on\\w+\\s*=",
        RegexOption.IGNORE_CASE
    )

    /**
     * Elimina etiquetas HTML, scripts y caracteres de control (alineado con API).
     */
    fun stripHtmlAndControlChars(input: CharSequence?): String {
        if (input == null) return ""
        val trimmed = input.trim().toString()
        if (trimmed.isEmpty()) return ""
        val cleaned = CONTROL_AND_DANGEROUS.replace(trimmed, "")
        return cleaned.replace(Regex("\\s+"), " ").trim()
    }

    /**
     * Sanitiza y devuelve (texto limpio, true si se eliminó contenido peligroso).
     */
    fun sanitizeWithDetection(input: CharSequence?, maxLength: Int = 50_000): Pair<String, Boolean> {
        if (input == null) return "" to false
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return "" to false
        val before = trimmed.toString()
        val cleaned = stripHtmlAndControlChars(before)
        val hadDangerous = before != cleaned
        val limited = if (cleaned.length <= maxLength) cleaned else cleaned.take(maxLength)
        return limited to hadDangerous
    }

    /**
     * Limpia y limita el string. Devuelve string vacío si null/blank.
     */
    fun sanitize(input: CharSequence?, maxLength: Int = 50_000): String {
        if (input == null) return ""
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ""
        val withoutControl = stripHtmlAndControlChars(trimmed)
        return if (withoutControl.length <= maxLength) withoutControl
        else withoutControl.take(maxLength)
    }

    /** Para títulos cortos (ej. pedido de oración). */
    fun sanitizeTitle(input: CharSequence?) = sanitize(input, maxLength = 500)

    /** Para cuerpo de texto largo. */
    fun sanitizeBody(input: CharSequence?) = sanitize(input, maxLength = 50_000)

    /** Título con detección de contenido eliminado. */
    fun sanitizeTitleWithDetection(input: CharSequence?) = sanitizeWithDetection(input, maxLength = 500)

    /** Cuerpo con detección de contenido eliminado. */
    fun sanitizeBodyWithDetection(input: CharSequence?) = sanitizeWithDetection(input, maxLength = 50_000)
}
