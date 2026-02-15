package com.pr4y.app

import android.app.Application
import android.os.Process
import android.util.Log
import com.pr4y.app.di.AppContainer

class Pr4yApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // #region agent log
        val pid = Process.myPid()
        val uid = Process.myUid()
        val userId = uid / 100000 // userId desde uid (evita UserHandle.getUserId, no siempre disponible)
        Log.i("PR4Y_DEBUG", "Pr4yApp.onCreate|pkg=$packageName|pid=$pid|uid=$uid|userId=$userId|hypothesisId=H1,H5")
        // #endregion
        AppContainer.init(this)
    }
}
