package com.pr4y.app.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.pr4y.app.util.Pr4yLog
import java.security.KeyStore

/**
 * Almacena access token, refresh token, userId y passphrase de forma segura.
 * Implementa limpieza profunda del Keystore ante corrupción AEAD.
 */
class AuthTokenStore(context: Context) {
    private val prefs: SharedPreferences by lazy {
        try {
            createEncryptedPrefs(context)
        } catch (e: Exception) {
            Pr4yLog.e("AuthTokenStore: Corrupción crítica detectada. Iniciando limpieza profunda...", e)
            nukeSecurityState(context)
            createEncryptedPrefs(context)
        }
    }

    private fun createEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private fun nukeSecurityState(context: Context) {
        try {
            // 1. Borrar el archivo físico
            context.deleteSharedPreferences(PREFS_NAME)
            
            // 2. Borrar la MasterKey del Keystore de Android
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            keyStore.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            
            Pr4yLog.i("AuthTokenStore: Estado de seguridad reseteado correctamente.")
        } catch (e: Exception) {
            Pr4yLog.e("AuthTokenStore: Error al resetear estado de seguridad", e)
        }
    }

    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS, null)
    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH, null)
    fun getUserId(): String? = prefs.getString(KEY_USER_ID, null)

    fun setTokens(accessToken: String, refreshToken: String, userId: String) {
        prefs.edit()
            .putString(KEY_ACCESS, accessToken)
            .putString(KEY_REFRESH, refreshToken)
            .putString(KEY_USER_ID, userId)
            .apply()
    }

    fun savePassphrase(passphrase: String) {
        prefs.edit().putString(KEY_PASSPHRASE, passphrase).commit()
    }

    fun getPassphrase(): String? = prefs.getString(KEY_PASSPHRASE, null)

    fun clearPassphrase() {
        prefs.edit().remove(KEY_PASSPHRASE).apply()
    }

    fun isBiometricEnabled(): Boolean = prefs.contains(KEY_PASSPHRASE)

    fun hasSeenWelcome(): Boolean = prefs.getBoolean(KEY_HAS_SEEN_WELCOME, false)
    fun setHasSeenWelcome(seen: Boolean) {
        prefs.edit().putBoolean(KEY_HAS_SEEN_WELCOME, seen).apply()
    }

    fun clear() {
        prefs.edit()
            .remove(KEY_ACCESS)
            .remove(KEY_REFRESH)
            .remove(KEY_USER_ID)
            .remove(KEY_PASSPHRASE)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "pr4y_auth"
        private const val KEY_ACCESS = "access_token"
        private const val KEY_REFRESH = "refresh_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_PASSPHRASE = "passphrase"
        private const val KEY_HAS_SEEN_WELCOME = "has_seen_welcome"
    }
}
