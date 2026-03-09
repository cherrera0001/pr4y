package com.pr4y.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.displayPrefsDataStore by preferencesDataStore(name = "display_prefs")

data class DisplayPrefs(
    val theme: String = "system",           // light | dark | system
    val fontSize: String = "md",            // sm | md | lg | xl
    val fontFamily: String = "system",      // system | serif | mono
    val lineSpacing: String = "normal",     // compact | normal | relaxed
    val contemplativeMode: Boolean = false,
)

object DisplayPrefsStore {
    private val KEY_THEME = stringPreferencesKey("theme")
    private val KEY_FONT_SIZE = stringPreferencesKey("font_size")
    private val KEY_FONT_FAMILY = stringPreferencesKey("font_family")
    private val KEY_LINE_SPACING = stringPreferencesKey("line_spacing")
    private val KEY_CONTEMPLATIVE = booleanPreferencesKey("contemplative_mode")

    fun observe(context: Context): Flow<DisplayPrefs> =
        context.displayPrefsDataStore.data.map { p ->
            DisplayPrefs(
                theme = p[KEY_THEME] ?: "system",
                fontSize = p[KEY_FONT_SIZE] ?: "md",
                fontFamily = p[KEY_FONT_FAMILY] ?: "system",
                lineSpacing = p[KEY_LINE_SPACING] ?: "normal",
                contemplativeMode = p[KEY_CONTEMPLATIVE] ?: false,
            )
        }

    suspend fun readOnce(context: Context): DisplayPrefs = observe(context).first()

    suspend fun save(context: Context, prefs: DisplayPrefs) {
        context.displayPrefsDataStore.edit { store ->
            store[KEY_THEME] = prefs.theme
            store[KEY_FONT_SIZE] = prefs.fontSize
            store[KEY_FONT_FAMILY] = prefs.fontFamily
            store[KEY_LINE_SPACING] = prefs.lineSpacing
            store[KEY_CONTEMPLATIVE] = prefs.contemplativeMode
        }
    }
}
