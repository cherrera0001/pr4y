package com.pr4y.app.util

import android.util.Log
import com.pr4y.app.BuildConfig

object Pr4yLog {
    private const val TAG = "PR4Y_APP"
    private const val ERROR_TAG = "PR4Y_ERROR"

    fun d(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    fun e(message: String, throwable: Throwable? = null) {
        Log.e(ERROR_TAG, message, throwable)
    }

    fun i(message: String) {
        Log.i(TAG, message)
    }
}
