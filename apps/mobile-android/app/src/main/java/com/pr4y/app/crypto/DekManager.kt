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
import android.content.SharedPreferences
import androidx.core.content.edit
import com.pr4y.app.util.Pr4yLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/**
 * Gestiona la DEK con anclaje al hardware (Android Keystore).
 * - Master Key: AES-256 en TEE/StrongBox (KeyGenParameterSpec), solo PURPOSE_ENCRYPT | PURPOSE_DECRYPT.
 * - DEK en memoria; persistida cifrada con la Master Key en EncryptedSharedPreferences.
 */
object DekManager {
    private const val GCM_TAG_LENGTH = 128
    private const val GCM_IV_LENGTH = 12
    private const val PBKDF2_ITERATIONS = 120_000
    private const val PBKDF2_KEY_LENGTH = 256
    private const val SALT_LENGTH = 16

    private const val KEYSTORE_ALIAS = "pr4y_dek_master"
    private const val DEK_PREFS_NAME = "pr4y_dek_store"
    private const val PREFS_KEY_WRAPPED_DEK = "wrapped_dek"

    @Volatile
    private var dek: SecretKey? = null

    private var dekPrefs: SharedPreferences? = null

    @Synchronized
    fun init(context: Context) {
        if (dekPrefs != null) return
        val app = context.applicationContext
        try {
            Pr4yLog.crypto("Inicializando almacén de llaves de hardware...")
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
        } catch (e: Exception) {
            Pr4yLog.e("Error crítico al inicializar Keystore", e)
        }
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
            .setRandomizedEncryptionRequired(true)
            .build()
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        keyGenerator.init(spec)
        keyGenerator.generateKey()
    }

    private fun getMasterKey(): SecretKey? {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey)
    }

    fun getDek(): SecretKey? = dek

    fun setDek(key: SecretKey) {
        dek = key
        persistDek(key)
    }

    private fun persistDek(key: SecretKey) {
        val prefs = dekPrefs ?: return
        val master = getMasterKey() ?: return
        try {
            val dekBytes = key.encoded
            val iv = ByteArray(GCM_IV_LENGTH).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, master, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            val cipherText = cipher.doFinal(dekBytes)
            val combined = iv + cipherText
            prefs.edit { 
                putString(PREFS_KEY_WRAPPED_DEK, Base64.encodeToString(combined, Base64.NO_WRAP))
            }
            Pr4yLog.crypto("Llave de privacidad persistida en hardware.")
        } catch (e: Exception) {
            Pr4yLog.e("Error al persistir DEK", e)
        }
    }

    @Synchronized
    fun tryRecoverDekSilently(): Boolean {
        if (dek != null) return true
        val prefs = dekPrefs ?: return false
        val wrappedB64 = prefs.getString(PREFS_KEY_WRAPPED_DEK, null) ?: return false
        val master = getMasterKey() ?: return false
        
        return try {
            Pr4yLog.crypto("Intentando recuperación silenciosa de la llave...")
            val combined = Base64.decode(wrappedB64, Base64.NO_WRAP)
            if (combined.size < GCM_IV_LENGTH) return false
            val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
            val cipherText = combined.copyOfRange(GCM_IV_LENGTH, combined.size)
            
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, master, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            val dekBytes = cipher.doFinal(cipherText)
            dek = SecretKeySpec(dekBytes, "AES")
            
            Pr4yLog.crypto("Llave recuperada con éxito desde Keystore.")
            true
        } catch (_: KeyPermanentlyInvalidatedException) {
            Pr4yLog.w("La Master Key ha sido invalidada (cambio de biometría).")
            clearDek()
            false
        } catch (e: Exception) {
            if (e.message?.contains("-28") == true) {
                Pr4yLog.e("Keystore saturado (Error -28). El hardware de seguridad está ocupado.")
            }
            Pr4yLog.e("Fallo en recuperación silenciosa", e)
            false
        }
    }

    fun clearDek() {
        dek = null
        dekPrefs?.edit { 
            remove(PREFS_KEY_WRAPPED_DEK)
        }
        Pr4yLog.crypto("Memoria criptográfica limpia.")
    }

    fun generateDek(): SecretKey = LocalCrypto.generateKey()

    fun deriveKek(passphrase: CharArray, saltB64: String): SecretKey {
        val salt = Base64.decode(saltB64, Base64.NO_WRAP)
        val spec = PBEKeySpec(
            passphrase,
            salt,
            PBKDF2_ITERATIONS,
            PBKDF2_KEY_LENGTH,
        )
        val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(keyBytes.take(32).toByteArray(), "AES")
    }

    fun generateSaltB64(): String {
        val salt = ByteArray(SALT_LENGTH)
        SecureRandom().nextBytes(salt)
        return Base64.encodeToString(salt, Base64.NO_WRAP)
    }

    fun wrapDek(dekToWrap: SecretKey, kek: SecretKey): String {
        val dekBytes = dekToWrap.encoded
        return LocalCrypto.encrypt(dekBytes, kek)
    }

    fun unwrapDek(wrappedDekB64: String, kek: SecretKey): SecretKey {
        val dekBytes = LocalCrypto.decrypt(wrappedDekB64, kek)
        return LocalCrypto.keyFromBytes(dekBytes)
    }
}
