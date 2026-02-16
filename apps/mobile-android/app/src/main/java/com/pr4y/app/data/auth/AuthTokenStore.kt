package com.pr4y.app.data.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Almacena access token, refresh token y passphrase (para biometría) de forma segura.
 */
class AuthTokenStore(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "pr4y_auth",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS, null)
    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH, null)

    fun setTokens(accessToken: String, refreshToken: String) {
        prefs.edit()
            .putString(KEY_ACCESS, accessToken)
            .putString(KEY_REFRESH, refreshToken)
            .apply()
    }

    // --- Soporte Biometría ---
    /** Guarda la frase de forma síncrona (commit) para que esté disponible antes de navegar. */
    fun savePassphrase(passphrase: String) {
        prefs.edit().putString(KEY_PASSPHRASE, passphrase).commit()
    }

    fun getPassphrase(): String? = prefs.getString(KEY_PASSPHRASE, null)

    fun clearPassphrase() {
        prefs.edit().remove(KEY_PASSPHRASE).apply()
    }

    fun isBiometricEnabled(): Boolean = prefs.contains(KEY_PASSPHRASE)

    // --- Estado de Bienvenida ---
    fun hasSeenWelcome(): Boolean = prefs.getBoolean(KEY_HAS_SEEN_WELCOME, false)
    fun setHasSeenWelcome(seen: Boolean) {
        prefs.edit().putBoolean(KEY_HAS_SEEN_WELCOME, seen).apply()
    }

    /** Borra sesión (tokens y passphrase). Mantiene hasSeenWelcome para no repetir Welcome al volver a entrar. */
    fun clear() {
        prefs.edit()
            .remove(KEY_ACCESS)
            .remove(KEY_REFRESH)
            .remove(KEY_PASSPHRASE)
            .apply()
    }

    companion object {
        private const val KEY_ACCESS = "access_token"
        private const val KEY_REFRESH = "refresh_token"
        private const val KEY_PASSPHRASE = "passphrase"
        private const val KEY_HAS_SEEN_WELCOME = "has_seen_welcome"
    }
}
