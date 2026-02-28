package com.pr4y.app.crypto

import android.util.Base64
import com.pr4y.app.util.Pr4yLog
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Cifrado local AES-GCM para payloads de sync y Ledger.
 * Zero-Knowledge: El contenido se cifra antes de persistir.
 */
object LocalCrypto {
    private const val GCM_TAG_LENGTH = 128
    private const val GCM_IV_LENGTH = 12

    /** Cifra un ByteArray y devuelve el resultado en Base64 (IV + Ciphertext). */
    fun encrypt(plainBytes: ByteArray, key: SecretKey): String {
        Pr4yLog.d("Cifrando contenido (E2EE)...")
        val iv = ByteArray(GCM_IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val cipherText = cipher.doFinal(plainBytes)
        val combined = iv + cipherText
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /** Descifra un Base64 (IV + Ciphertext) y devuelve el ByteArray original. */
    fun decrypt(encryptedB64: String, key: SecretKey): ByteArray {
        // #region agent log
        Pr4yLog.d("Descifrando contenido (E2EE)... [thread=${Thread.currentThread().name}]")
        // #endregion
        val combined = Base64.decode(encryptedB64, Base64.NO_WRAP)
        val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
        val cipherText = combined.copyOfRange(GCM_IV_LENGTH, combined.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return cipher.doFinal(cipherText)
    }

    /** Genera un hash SHA-256 para validación de integridad (Spec: contentHash). */
    fun generateHash(content: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(content.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    fun keyFromBytes(bytes: ByteArray): SecretKey = SecretKeySpec(bytes, "AES")

    fun generateKey(): SecretKey {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return SecretKeySpec(bytes, "AES")
    }
}
