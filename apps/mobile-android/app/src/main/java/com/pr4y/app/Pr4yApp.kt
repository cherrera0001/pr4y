package com.pr4y.app

import android.app.Application
import android.os.Process
import android.util.Log
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class Pr4yApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        val pid = Process.myPid()
        val uid = Process.myUid()
        val userId = uid / 100000 
        Log.i("PR4Y_DEBUG", "Pr4yApp.onCreate|pkg=$packageName|pid=$pid|uid=$uid|userId=$userId")
        
        // AppContainer ya no se inicializa aquí porque requiere el userId de la sesión.
        // Se inicializa en MainViewModel.initBunker tras recuperar el userId de AuthTokenStore.
    }
}
