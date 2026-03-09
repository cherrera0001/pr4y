package com.pr4y.app.ui

object Routes {
    const val LOGIN = "login"
    const val UNLOCK = "unlock"
    const val WELCOME = "welcome"
    const val MAIN = "main"
    const val HOME = "home"
    const val NEW_EDIT = "new_edit"
    const val NEW_EDIT_ID = "new_edit/{id}"
    const val DETAIL = "detail/{id}"
    const val JOURNAL = "journal"
    const val NEW_JOURNAL = "new_journal"
    const val JOURNAL_ENTRY = "journal_entry/{id}"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val FOCUS_MODE = "focus_mode"
    const val VICTORIAS = "victorias"
    const val ROULETTE = "roulette"

    fun detail(id: String) = "detail/$id"
    fun newEdit(id: String?) = if (id != null) "new_edit/$id" else NEW_EDIT
    fun journalEntry(id: String) = "journal_entry/$id"
}
