package com.pr4y.app.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import java.security.InvalidAlgorithmParameterException
import android.content.SharedPreferences
import androidx.biometric.BiometricPrompt
import androidx.core.content.edit
import com.pr4y.app.util.Pr4yLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import javax.crypto.SecretKeyFactory

/**
 * Tech Lead Note: Gestiona la DEK con anclaje al hardware (TEE/StrongBox).
 * Refactorizado para "Seguridad Infranqueable":
 * 1. La Master Key ahora requiere setUserAuthenticationRequired(true).
 * 2. La DEK solo se puede recuperar si el chip de seguridad recibe una huella válida.
 */
object DekManager {
    private const val GCM_TAG_LENGTH = 128
    private const val PBKDF2_ITERATIONS = 120_000
    private const val PBKDF2_KEY_LENGTH = 256
    private const val SALT_LENGTH = 16

    private const val KEYSTORE_ALIAS = "pr4y_dek_master_v2" // Nueva versión para forzar auth
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
            // El MasterKey de security-crypto se usa para las preferencias, 
            // pero la DEK se envuelve con nuestra propia llave del Keystore con auth biométrica.
            val masterKey = MasterKey.Builder(app)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            dekPrefs = EncryptedSharedPreferences.create(
                app,
                DEK_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
            Pr4yLog.crypto("Búnker persistente inicializado.")
        } catch (e: Exception) {
            Pr4yLog.e("Error inicializando DekManager", e)
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
            .setUserAuthenticationRequired(true) // REQUERIDO: Huella para cada uso
            .setUserAuthenticationValidityDurationSeconds(-1) // Cada uso requiere auth (cero rastro)
            .setInvalidatedByBiometricEnrollment(true) // Seguridad extra si cambian huellas
            .setRandomizedEncryptionRequired(true) // IV generado por el sistema (Xiaomi/OEM)
            .build()
        
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        keyGenerator.init(spec)
        keyGenerator.generateKey()
    }

    private fun getMasterKey(): SecretKey? {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey
    }

    /**
     * Devuelve un CryptoObject para ser usado con BiometricPrompt.
     * Esto es necesario porque la llave está protegida por hardware.
     */
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

    /**
     * Completa la recuperación de la DEK tras una autenticación exitosa.
     */
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

    /**
     * Indica si hay DEK persistida en TEE (solo desbloqueable con huella).
     * Usado por la UI para mostrar "Usar Biometría" cuando aplica.
     */
    fun hasPersistedDekForBiometric(): Boolean {
        val prefs = dekPrefs ?: return false
        return prefs.getString(PREFS_KEY_WRAPPED_DEK, null) != null &&
            prefs.getString(PREFS_KEY_DEK_STORAGE_MODE, null) == "tee_v2"
    }

    /**
     * Intento de recuperación silenciosa. Con clave TEE que exige biometría, la DEK solo existe
     * en memoria; tras muerte del proceso siempre se requiere desbloqueo (huella o passphrase).
     */
    fun tryRecoverDekSilently(): Boolean = (dek != null)

    /**
     * Coloca la DEK en memoria. No persiste en dispositivo; para persistir y permitir
     * desbloqueo con huella hay que llamar a persistDekWithCipher tras autenticación biométrica.
     */
    suspend fun setDek(key: SecretKey) = withContext(Dispatchers.Default) {
        dek = key
    }

    /**
     * Devuelve un CryptoObject para persistir la DEK tras autenticación biométrica.
     * La llave TEE solo se usa dentro del chip; sin huella no se puede escribir.
     */
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

    /**
     * Persiste la DEK usando el Cipher ya desbloqueado por BiometricPrompt.
     * Llamar solo tras autenticación biométrica exitosa (guardado infranqueable).
     */
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

    /** Registra un callback que se invoca cuando la DEK se borra (cero rastro o logout). */
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

    // Métodos auxiliares permanecen en Dispatchers.Default
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
