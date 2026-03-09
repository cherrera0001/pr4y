package com.pr4y.app.util

import android.util.Log
import com.pr4y.app.BuildConfig

object Pr4yLog {
    private const val TAG = "PR4Y_APP"
    private const val NETWORK_TAG = "PR4Y_NETWORK"
    private const val ERROR_TAG = "PR4Y_ERROR"
    private const val CRYPTO_TAG = "PR4Y_CRYPTO"

    fun d(message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) Log.d(TAG, message, throwable)
    }

    fun i(message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) Log.i(TAG, message, throwable)
    }

    fun w(message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) Log.w(TAG, message, throwable)
    }

    /** Solo en release: errores críticos (sin datos sensibles). En DEBUG se ve todo. */
    fun e(message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) Log.e(ERROR_TAG, message, throwable)
        else Log.e(ERROR_TAG, message.take(200), throwable)
    }

    fun net(message: String) {
        if (BuildConfig.DEBUG) Log.i(NETWORK_TAG, message)
    }

    fun crypto(message: String) {
        if (BuildConfig.DEBUG) Log.i(CRYPTO_TAG, message)
    }

    fun sync(message: String) {
        if (BuildConfig.DEBUG) Log.i(TAG, "[SYNC] $message")
    }
}
