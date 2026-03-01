package com.pr4y.app.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.content.SharedPreferences
import androidx.biometric.BiometricPrompt
import androidx.core.content.edit
import com.pr4y.app.util.Pr4yLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Tech Lead Note: Gestiona la DEK con anclaje al hardware (TEE/StrongBox).
 * Refactorizado para "Seguridad Infranqueable" y resiliencia ante corrupción profunda del Keystore.
 */
object DekManager {
    private const val GCM_TAG_LENGTH = 128
    private const val PBKDF2_ITERATIONS = 120_000
    private const val PBKDF2_KEY_LENGTH = 256
    private const val SALT_LENGTH = 16

    private const val KEYSTORE_ALIAS = "pr4y_dek_master_v2"
    private const val DEK_PREFS_NAME = "pr4y_dek_store"
    private const val PREFS_KEY_WRAPPED_DEK = "wrapped_dek"
    private const val PREFS_KEY_DEK_STORAGE_MODE = "dek_storage_mode"

    @Volatile
    private var dek: SecretKey? = null

    private var dekPrefs: SharedPreferences? = null

    suspend fun init(context: Context) = withContext(Dispatchers.IO) {
        if (dekPrefs != null) return@withContext
        val app = context.applicationContext
        
        try {
            ensureMasterKeyExists()
            dekPrefs = createEncryptedPrefs(app)
            Pr4yLog.crypto("Búnker persistente inicializado.")
        } catch (e: Exception) {
            Pr4yLog.e("DekManager: Error crítico en inicialización. Iniciando limpieza profunda...", e)
            nukeSecurityState(app)
            try {
                dekPrefs = createEncryptedPrefs(app)
            } catch (e2: Exception) {
                Pr4yLog.e("DekManager: Fallo total en recuperación de búnker tras nuke", e2)
            }
        }
    }

    private fun createEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            DEK_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private fun nukeSecurityState(context: Context) {
        try {
            // 1. Borrar archivo físico de DEK
            context.deleteSharedPreferences(DEK_PREFS_NAME)
            
            // 2. Borrar llaves del Keystore
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            keyStore.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            keyStore.deleteEntry(KEYSTORE_ALIAS) // Borrar también nuestra llave maestra custom
            
            Pr4yLog.i("DekManager: Llaves y preferencias purgadas satisfactoriamente.")
        } catch (e: Exception) {
            Pr4yLog.e("DekManager: Error durante purga de seguridad", e)
        }
    }

    private fun ensureMasterKeyExists() {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (keyStore.containsAlias(KEYSTORE_ALIAS)) return
        
        Pr4yLog.crypto("Generando Llave Maestra Infranqueable en TEE...")
        val spec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationValidityDurationSeconds(-1)
            .setInvalidatedByBiometricEnrollment(true)
            .setRandomizedEncryptionRequired(true)
            .build()
        
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        keyGenerator.init(spec)
        keyGenerator.generateKey()
    }

    private fun getMasterKey(): SecretKey? {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey
    }

    fun getInitializedCipherForRecovery(): BiometricPrompt.CryptoObject? {
        val wrappedB64 = dekPrefs?.getString(PREFS_KEY_WRAPPED_DEK, null) ?: return null
        val master = getMasterKey() ?: return null
        
        return try {
            val combined = Base64.decode(wrappedB64, Base64.NO_WRAP)
            val ivSize = 12
            if (combined.size < ivSize) return null
            val iv = combined.copyOfRange(0, ivSize)
            
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, master, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            BiometricPrompt.CryptoObject(cipher)
        } catch (e: Exception) {
            Pr4yLog.e("No se pudo inicializar el Cipher de recuperación", e)
            null
        }
    }

    fun recoverDekWithCipher(cipher: Cipher): Boolean {
        val wrappedB64 = dekPrefs?.getString(PREFS_KEY_WRAPPED_DEK, null) ?: return false
        return try {
            val combined = Base64.decode(wrappedB64, Base64.NO_WRAP)
            val ivSize = 12
            val cipherText = combined.copyOfRange(ivSize, combined.size)
            
            val dekBytes = cipher.doFinal(cipherText)
            dek = SecretKeySpec(dekBytes, "AES")
            Pr4yLog.crypto("DEK liberada desde el chip de seguridad.")
            true
        } catch (e: Exception) {
            Pr4yLog.e("Fallo al liberar DEK con Cipher autenticado", e)
            false
        }
    }

    fun getDek(): SecretKey? = dek

    fun hasPersistedDekForBiometric(): Boolean {
        val prefs = dekPrefs ?: return false
        return prefs.getString(PREFS_KEY_WRAPPED_DEK, null) != null &&
            prefs.getString(PREFS_KEY_DEK_STORAGE_MODE, null) == "tee_v2"
    }

    fun tryRecoverDekSilently(): Boolean = (dek != null)

    suspend fun setDek(key: SecretKey) = withContext(Dispatchers.Default) {
        dek = key
    }

    fun getInitializedCipherForEncrypt(): BiometricPrompt.CryptoObject? {
        val master = getMasterKey() ?: return null
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, master)
            BiometricPrompt.CryptoObject(cipher)
        } catch (e: Exception) {
            Pr4yLog.e("No se pudo inicializar Cipher para guardar DEK", e)
            null
        }
    }

    fun persistDekWithCipher(key: SecretKey, cipher: Cipher): Boolean {
        val prefs = dekPrefs ?: return false
        return try {
            val cipherText = cipher.doFinal(key.encoded)
            val iv = cipher.iv
            val combined = iv + cipherText
            prefs.edit {
                putString(PREFS_KEY_WRAPPED_DEK, Base64.encodeToString(combined, Base64.NO_WRAP))
                putString(PREFS_KEY_DEK_STORAGE_MODE, "tee_v2")
            }
            Pr4yLog.crypto("DEK protegida en TEE (solo accesible con huella).")
            true
        } catch (e: Exception) {
            Pr4yLog.e("Error al persistir DEK con Cipher", e)
            false
        }
    }

    private var dekClearedListener: (() -> Unit)? = null

    fun setDekClearedListener(listener: (() -> Unit)?) {
        dekClearedListener = listener
    }

    fun clearDek() {
        dek = null
        dekPrefs?.edit {
            remove(PREFS_KEY_WRAPPED_DEK)
            remove(PREFS_KEY_DEK_STORAGE_MODE)
        }
        dekClearedListener?.invoke()
        Pr4yLog.crypto("Memoria criptográfica limpia.")
    }

    /** Desactiva el acceso por huella sin limpiar la DEK en memoria. */
    fun disableBiometric() {
        dekPrefs?.edit {
            remove(PREFS_KEY_WRAPPED_DEK)
            remove(PREFS_KEY_DEK_STORAGE_MODE)
        }
        Pr4yLog.crypto("Acceso por huella desactivado.")
    }

    suspend fun generateDek(): SecretKey = withContext(Dispatchers.Default) { LocalCrypto.generateKey() }
    
    suspend fun deriveKek(passphrase: CharArray, saltB64: String): SecretKey = withContext(Dispatchers.Default) {
        val salt = Base64.decode(saltB64, Base64.NO_WRAP)
        val spec = PBEKeySpec(passphrase, salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        spec.clearPassword()
        SecretKeySpec(keyBytes.take(32).toByteArray(), "AES")
    }

    fun generateSaltB64(): String {
        val salt = ByteArray(SALT_LENGTH)
        SecureRandom().nextBytes(salt)
        return Base64.encodeToString(salt, Base64.NO_WRAP)
    }

    suspend fun wrapDek(dekToWrap: SecretKey, kek: SecretKey): String = withContext(Dispatchers.Default) {
        LocalCrypto.encrypt(dekToWrap.encoded, kek)
    }

    suspend fun unwrapDek(wrappedDekB64: String, kek: SecretKey): SecretKey = withContext(Dispatchers.Default) {
        LocalCrypto.keyFromBytes(LocalCrypto.decrypt(wrappedDekB64, kek))
    }
}
