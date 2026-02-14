package com.pr4y.app.crypto

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
/**
 * Gestiona la DEK en memoria. KEK derivada de passphrase con PBKDF2.
 * Wrap/unwrap DEK para enviar/recibir del servidor.
 */
object DekManager {
    private const val GCM_TAG_LENGTH = 128
    private const val GCM_IV_LENGTH = 12
    private const val PBKDF2_ITERATIONS = 120_000
    private const val PBKDF2_KEY_LENGTH = 256
    private const val SALT_LENGTH = 16

    @Volatile
    private var dek: SecretKey? = null

    fun getDek(): SecretKey? = dek

    fun setDek(key: SecretKey) {
        dek = key
    }

    fun clearDek() {
        dek = null
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

    /** Cifra la DEK con la KEK para guardar en servidor. */
    fun wrapDek(dekToWrap: SecretKey, kek: SecretKey): String {
        val dekBytes = dekToWrap.encoded
        return LocalCrypto.encrypt(dekBytes, kek)
    }

    /** Descifra la DEK desde el servidor usando KEK. */
    fun unwrapDek(wrappedDekB64: String, kek: SecretKey): SecretKey {
        val dekBytes = LocalCrypto.decrypt(wrappedDekB64, kek)
        return LocalCrypto.keyFromBytes(dekBytes)
    }
}
