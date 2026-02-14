package com.pr4y.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.pr4y.app.crypto.DekManager
import com.pr4y.app.data.auth.AuthRepository
import com.pr4y.app.data.auth.AuthTokenStore
import com.pr4y.app.data.remote.RetrofitClient
import com.pr4y.app.ui.Pr4yNavHost
import com.pr4y.app.ui.theme.Pr4yTheme
import com.pr4y.app.work.SyncScheduler

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tokenStore = AuthTokenStore(applicationContext)
        DekManager.init(applicationContext)
        val api = RetrofitClient.create(applicationContext)
        val authRepository = AuthRepository(api, tokenStore)
        var loggedIn by mutableStateOf(tokenStore.getAccessToken() != null)
        if (tokenStore.getAccessToken() != null) {
            SyncScheduler.schedulePeriodic(applicationContext)
        }
        var unlocked by mutableStateOf(
            loggedIn && DekManager.tryRecoverDekSilently()
        )

        setContent {
            Pr4yTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Pr4yNavHost(
                        authRepository = authRepository,
                        loggedIn = loggedIn,
                        onLoginSuccess = { loggedIn = true },
                        onLogout = {
                            DekManager.clearDek()
                            loggedIn = false
                            unlocked = false
                        },
                        unlocked = unlocked,
                        onUnlocked = { unlocked = true },
                    )
                }
            }
        }
    }
}
