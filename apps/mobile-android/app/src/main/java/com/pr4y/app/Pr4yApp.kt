package com.pr4y.app

import android.app.Application
import com.pr4y.app.di.AppContainer

class Pr4yApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContainer.init(this)
    }
}
