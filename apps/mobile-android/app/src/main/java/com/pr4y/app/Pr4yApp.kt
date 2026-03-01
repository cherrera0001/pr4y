package com.pr4y.app

import android.app.Application
import com.pr4y.app.BuildConfig
import com.pr4y.app.di.AppContainer
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class Pr4yApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            val pid = android.os.Process.myPid()
            val uid = android.os.Process.myUid()
            val userId = uid / 100000
            android.util.Log.i("PR4Y_DEBUG", "Pr4yApp.onCreate|pkg=$packageName|pid=$pid|uid=$uid|userId=$userId")
        }
        AppContainer.init(this)
    }
}
