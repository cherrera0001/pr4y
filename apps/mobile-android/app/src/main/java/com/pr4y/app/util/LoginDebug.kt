package com.pr4y.app.util

import android.accounts.AccountManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import java.security.MessageDigest

/**
 * Solo para depuración: obtiene SHA-1 del certificado de firma en runtime
 * y escribe logs con hypothesisId para evaluar causas de NoCredentialException.
 * No exponer SHA-1 en UI de producción; solo en logs para diagnóstico.
 */
object LoginDebug {

    private const val TAG = "PR4Y_DEBUG"

    /** Escribe una línea de log con hypothesisId y data para análisis post-logcat. */
    fun log(hypothesisId: String, location: String, message: String, data: Map<String, Any?>) {
        val dataStr = data.entries.joinToString(",") { "${it.key}=${it.value}" }
        Log.i(TAG, "hypothesisId=$hypothesisId|location=$location|message=$message|$dataStr")
    }

    /**
     * Obtiene la huella SHA-1 del certificado con el que está firmada la app (formato AA:BB:CC:...).
     * Devuelve null si no se puede obtener (ej. en algunos emuladores o builds).
     * GET_SIGNATURES y packageInfo.signatures están deprecados; usamos por compatibilidad con minSdk 26.
     */
    @Suppress("DEPRECATION")
    fun getSigningSha1(context: Context): String? {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNATURES
            )
            val signatures = packageInfo.signatures ?: return null
            if (signatures.isEmpty()) return null
            val sig = signatures[0]
            val md = MessageDigest.getInstance("SHA-1")
            md.update(sig.toByteArray())
            md.digest().joinToString("") { "%02X".format(it) }.chunked(2).joinToString(":")
        } catch (e: Exception) {
            Log.w(TAG, "getSigningSha1 failed", e)
            null
        }
    }

    /** Cuenta de cuentas Google en el dispositivo (solo para diagnóstico; puede requerir permiso en algunas ROMs). */
    fun getGoogleAccountCount(context: Context): Int {
        return try {
            val am = context.getSystemService(Context.ACCOUNT_SERVICE) as? AccountManager
            val accounts = am?.getAccountsByType("com.google") ?: emptyArray()
            accounts.size
        } catch (e: Exception) {
            -1
        }
    }
}
