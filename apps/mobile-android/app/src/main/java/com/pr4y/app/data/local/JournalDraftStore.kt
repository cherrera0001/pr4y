package com.pr4y.app.data.local

import android.content.Context

/**
 * Almacén de borrador de diario cuando el usuario escribe sin DEK (espera de desbloqueo).
 * Un solo slot: la última entrada guardada como borrador.
 */
object JournalDraftStore {
    private const val PREFS_NAME = "pr4y_drafts"
    private const val KEY_CONTENT = "journal_draft_content"
    private const val KEY_UPDATED_AT = "journal_draft_updated_at"

    fun saveDraft(context: Context, content: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_CONTENT, content)
            .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
            .apply()
    }

    fun getDraft(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CONTENT, null)?.takeIf { it.isNotBlank() }
    }

    fun clearDraft(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .remove(KEY_CONTENT)
            .remove(KEY_UPDATED_AT)
            .apply()
    }
}
