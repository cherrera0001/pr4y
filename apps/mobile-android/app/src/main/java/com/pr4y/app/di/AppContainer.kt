package com.pr4y.app.di

import android.content.Context
import com.pr4y.app.data.local.AppDatabase

object AppContainer {
    lateinit var db: AppDatabase
        private set

    fun init(context: Context) {
        db = AppDatabase.getInstance(context)
    }
}
