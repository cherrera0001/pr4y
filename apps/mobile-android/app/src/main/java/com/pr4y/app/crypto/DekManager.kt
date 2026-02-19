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
import androidx.core.content.edit
import com.pr4y.app.util.Pr4yLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import javax.crypto.SecretKeyFactory

/**
 * Tech Lead Note: Gestiona la DEK con anclaje al hardware (Android Keystore).
 * Corregido para Xiaomi/Android 13+: 
 * 1. El Keystore genera el IV automáticamente (Randomized Encryption).
 * 2. Operaciones movidas a Dispatchers.Default para liberar el Main Thread.
 */
object DekManager {
    private const val GCM_TAG_LENGTH = 128
    private const val PBKDF2_ITERATIONS = 120_000
    private const val PBKDF2_KEY_LENGTH = 256
    private const val SALT_LENGTH = 16

    private const val KEYSTORE_ALIAS = "pr4y_dek_master"
    private const val DEK_PREFS_NAME = "pr4y_dek_store"
    private const val PREFS_KEY_WRAPPED_DEK = "wrapped_dek"
    /** Si "esp_only": DEK guardada solo en EncryptedSharedPreferences (sin Cipher+Keystore). Evita InvalidAlgorithmParameterException en Xiaomi/Android 13+. */
    private const val PREFS_KEY_DEK_STORAGE_MODE = "dek_storage_mode"

    @Volatile
    private var dek: SecretKey? = null

    private var dekPrefs: SharedPreferences? = null

    /**
     * Inicializa el almacén. Se ejecuta fuera del hilo principal.
     */
    suspend fun init(context: Context) = withContext(Dispatchers.Default) {
        if (dekPrefs != null) return@withContext
        val app = context.applicationContext
        
        var lastException: Exception? = null
        for (i in 1..3) {
            try {
                Pr4yLog.crypto("Inicializando almacén de hardware (Intento $i)...")
                ensureMasterKeyExists()
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
                Pr4yLog.crypto("Almacén cifrado listo.")
                return@withContext 
            } catch (e: Exception) {
                lastException = e
                Pr4yLog.w("Reintento de inicialización por error: ${e.message}")
                delay(200L * i) // Back-off exponencial asíncrono
            }
        }
        Pr4yLog.e("Fallo definitivo tras reintentos en Keystore", lastException)
    }

    private fun ensureMasterKeyExists() {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (keyStore.containsAlias(KEYSTORE_ALIAS)) return
        
        Pr4yLog.crypto("Generando Master Key en TEE...")
        val spec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true) // Forzamos IV generado por el sistema
            .build()
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        keyGenerator.init(spec)
        keyGenerator.generateKey()
    }

    private fun getMasterKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey
        } catch (_: Exception) { null }
    }

    fun getDek(): SecretKey? = dek

    suspend fun setDek(key: SecretKey) = withContext(Dispatchers.Default) {
        dek = key
        persistDek(key)
    }

    private fun persistDek(key: SecretKey) {
        val prefs = dekPrefs ?: return
        val master = getMasterKey()
        if (master != null) {
            try {
                val dekBytes = key.encoded
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, master)
                val cipherText = cipher.doFinal(dekBytes)
                val iv = cipher.iv
                val combined = iv + cipherText
                prefs.edit {
                    putString(PREFS_KEY_DEK_STORAGE_MODE, "cipher")
                    putString(PREFS_KEY_WRAPPED_DEK, Base64.encodeToString(combined, Base64.NO_WRAP))
                }
                Pr4yLog.crypto("Llave de privacidad persistida (Keystore+Cipher).")
                return
            } catch (e: Exception) {
                Pr4yLog.w("Keystore no permite IV o falló en este dispositivo; usando fallback ESP. Error: ${e.message}")
            }
        }
        // Fallback: guardar DEK como valor cifrado solo por EncryptedSharedPreferences (sin Cipher+Keystore).
        // Evita "Caller-provided IV not permitted" en Xiaomi / Android 13+.
        try {
            prefs.edit {
                putString(PREFS_KEY_DEK_STORAGE_MODE, "esp_only")
                putString(PREFS_KEY_WRAPPED_DEK, Base64.encodeToString(key.encoded, Base64.NO_WRAP))
            }
            Pr4yLog.crypto("Llave de privacidad persistida (fallback ESP).")
        } catch (e: Exception) {
            Pr4yLog.e("Error al persistir DEK (fallback)", e)
        }
    }

    suspend fun tryRecoverDekSilently(): Boolean = withContext(Dispatchers.Default) {
        if (dek != null) return@withContext true
        val prefs = dekPrefs ?: return@withContext false
        val wrappedB64 = prefs.getString(PREFS_KEY_WRAPPED_DEK, null) ?: return@withContext false
        val mode = prefs.getString(PREFS_KEY_DEK_STORAGE_MODE, "cipher")

        if (mode == "esp_only") {
            return@withContext recoverDekFromEspOnly(prefs, wrappedB64)
        }

        val delays = listOf(150L, 350L, 600L, 1000L)
        for ((attempt, waitMs) in delays.withIndex()) {
            try {
                val master = getMasterKey() ?: return@withContext false
                val combined = Base64.decode(wrappedB64, Base64.NO_WRAP)
                val ivSize = 12
                if (combined.size < ivSize) return@withContext false
                val iv = combined.copyOfRange(0, ivSize)
                val cipherText = combined.copyOfRange(ivSize, combined.size)

                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, master, GCMParameterSpec(GCM_TAG_LENGTH, iv))
                val dekBytes = cipher.doFinal(cipherText)
                dek = SecretKeySpec(dekBytes, "AES")

                Pr4yLog.crypto("Llave recuperada con éxito (Keystore+Cipher).")
                return@withContext true
            } catch (_: KeyPermanentlyInvalidatedException) {
                clearDek()
                return@withContext false
            } catch (e: Exception) {
                Pr4yLog.w("Reintento recuperación DEK (${attempt + 1}/${delays.size}): ${e.message}")
                if (attempt < delays.lastIndex) delay(waitMs)
            }
        }
        return@withContext false
    }

    private fun recoverDekFromEspOnly(prefs: SharedPreferences, wrappedB64: String): Boolean {
        return try {
            val dekBytes = Base64.decode(wrappedB64, Base64.NO_WRAP)
            if (dekBytes.size < 16) return false
            dek = SecretKeySpec(dekBytes, "AES")
            Pr4yLog.crypto("Llave recuperada (fallback ESP).")
            true
        } catch (e: Exception) {
            Pr4yLog.e("Error al recuperar DEK (ESP)", e)
            false
        }
    }

    fun clearDek() {
        dek = null
        dekPrefs?.edit {
            remove(PREFS_KEY_WRAPPED_DEK)
            remove(PREFS_KEY_DEK_STORAGE_MODE)
        }
        Pr4yLog.crypto("Memoria criptográfica limpia.")
    }

    suspend fun generateDek(): SecretKey = withContext(Dispatchers.Default) {
        LocalCrypto.generateKey()
    }

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
        val dekBytes = dekToWrap.encoded
        LocalCrypto.encrypt(dekBytes, kek)
    }

    suspend fun unwrapDek(wrappedDekB64: String, kek: SecretKey): SecretKey = withContext(Dispatchers.Default) {
        val dekBytes = LocalCrypto.decrypt(wrappedDekB64, kek)
        LocalCrypto.keyFromBytes(dekBytes)
    }
}
