package com.pr4y.app

import android.app.Application
import android.os.Process
import android.util.Log
import com.pr4y.app.di.AppContainer
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class Pr4yApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // #region agent log
        val pid = Process.myPid()
        val uid = Process.myUid()
        val userId = uid / 100000 
        Log.i("PR4Y_DEBUG", "Pr4yApp.onCreate|pkg=$packageName|pid=$pid|uid=$uid|userId=$userId|hypothesisId=H1,H5")
        // #endregion
        
        // Mantener inicialización de AppContainer para compatibilidad legacy durante la migración a Hilt
        AppContainer.init(this)
    }
}
