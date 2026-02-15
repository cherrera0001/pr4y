package com.pr4y.app.util

import android.util.Log
import com.pr4y.app.BuildConfig

object Pr4yLog {
    private const val TAG = "PR4Y_APP"
    private const val NETWORK_TAG = "PR4Y_NETWORK"
    private const val ERROR_TAG = "PR4Y_ERROR"
    private const val CRYPTO_TAG = "PR4Y_CRYPTO"

    fun d(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    fun i(message: String) {
        Log.i(TAG, message)
    }

    fun w(message: String) {
        Log.w(TAG, message)
    }

    fun e(message: String, throwable: Throwable? = null) {
        Log.e(ERROR_TAG, message, throwable)
    }
    
    fun net(message: String) {
        Log.i(NETWORK_TAG, message)
    }

    fun crypto(message: String) {
        Log.i(CRYPTO_TAG, message)
    }

    fun sync(message: String) {
        Log.i(TAG, "[SYNC] $message")
    }
}
