package com.pr4y.app

import android.app.Application
import com.pr4y.app.BuildConfig
import com.pr4y.app.util.Pr4yLog
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
        // Eliminar la base de datos legada compartida (anterior al modelo TenantID).
        // No contiene datos de usuario activos: ya están cifrados en el servidor.
        val deleted = deleteDatabase("pr4y_db")
        if (deleted) Pr4yLog.i("Pr4yApp: DB legada 'pr4y_db' eliminada.")
        // AppContainer.init() ya NO se llama aquí — requiere userId autenticado.
        // Se llama en MainViewModel.initBunker() tras obtener la sesión del usuario.
    }
}
