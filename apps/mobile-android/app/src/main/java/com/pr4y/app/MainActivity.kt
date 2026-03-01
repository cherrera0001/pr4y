package com.pr4y.app

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ProcessLifecycleOwner
import com.pr4y.app.crypto.DekManager
import com.pr4y.app.data.auth.AuthRepository
import com.pr4y.app.data.auth.AuthTokenStore
import com.pr4y.app.data.prefs.DisplayPrefs
import com.pr4y.app.data.prefs.DisplayPrefsStore
import com.pr4y.app.data.remote.DisplayPreferencesDto
import com.pr4y.app.data.remote.ApiService
import com.pr4y.app.data.remote.RetrofitClient
import com.pr4y.app.ui.Pr4yNavHost
import com.pr4y.app.ui.components.ShimmerLoading
import com.pr4y.app.ui.theme.Pr4yTheme
import com.pr4y.app.util.Pr4yLog
import com.pr4y.app.work.SyncScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel : ViewModel() {
    var isReady by mutableStateOf(false)
    var isUnlocked by mutableStateOf(false)
    var loggedIn by mutableStateOf(false)
    var authRepository by mutableStateOf<AuthRepository?>(null)
    var hasSeenWelcome by mutableStateOf(false)
    var initError by mutableStateOf<String?>(null)
    var displayPrefs by mutableStateOf(DisplayPrefs())

    private var tokenStore: AuthTokenStore? = null
    private var api: ApiService? = null

    fun initBunker(context: android.content.Context) {
        viewModelScope.launch {
            try {
                Pr4yLog.i("--- Iniciando Búnker PR4Y (Async) ---")

                // Cargar prefs locales inmediatamente (antes del IO)
                launch {
                    DisplayPrefsStore.observe(context).collect { prefs ->
                        displayPrefs = prefs
                    }
                }

                withContext(Dispatchers.IO) {
                    try {
                        DekManager.init(context)
                    } catch (e: Exception) {
                        Pr4yLog.e("Error en DekManager.init", e)
                    }

                    val store = AuthTokenStore(context)
                    tokenStore = store

                    val token = store.getAccessToken()
                    loggedIn = token != null

                    if (loggedIn) {
                        isUnlocked = DekManager.tryRecoverDekSilently()
                        SyncScheduler.schedulePeriodic(context)
                    }

                    val retrofitApi = RetrofitClient.create(context, store)
                    api = retrofitApi
                    authRepository = AuthRepository(retrofitApi, store)
                    hasSeenWelcome = store.hasSeenWelcome()

                    // Fetch display prefs del servidor una vez autenticado
                    if (loggedIn) {
                        try {
                            val bearer = store.getAccessToken()?.let { "Bearer $it" }
                            if (bearer != null) {
                                val res = retrofitApi.getDisplayPreferences(bearer)
                                if (res.isSuccessful) {
                                    res.body()?.let { dto ->
                                        DisplayPrefsStore.save(context, dto.toDisplayPrefs())
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Pr4yLog.w("initBunker: display prefs fetch failed (offline?): ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Pr4yLog.e("Error fatal en initBunker", e)
                initError = e.message
            } finally {
                isReady = true
            }
        }
    }

    fun updateDisplayPrefs(context: android.content.Context, prefs: DisplayPrefs) {
        viewModelScope.launch {
            DisplayPrefsStore.save(context, prefs) // reactivo: collect arriba actualiza displayPrefs
            try {
                val bearer = tokenStore?.getAccessToken()?.let { "Bearer $it" } ?: return@launch
                api?.putDisplayPreferences(bearer, prefs.toDto())
            } catch (e: Exception) {
                Pr4yLog.w("updateDisplayPrefs: remote PUT failed: ${e.message}")
            }
        }
    }

    fun setHasSeenWelcome() {
        tokenStore?.setHasSeenWelcome(true)
        hasSeenWelcome = true
    }
}

private fun DisplayPreferencesDto.toDisplayPrefs() = DisplayPrefs(
    theme            = theme,
    fontSize         = fontSize,
    fontFamily       = fontFamily,
    lineSpacing      = lineSpacing,
    contemplativeMode= contemplativeMode,
)

private fun DisplayPrefs.toDto() = DisplayPreferencesDto(
    theme            = theme,
    fontSize         = fontSize,
    fontFamily       = fontFamily,
    lineSpacing      = lineSpacing,
    contemplativeMode= contemplativeMode,
)

/** Tiempo en segundo plano tras el cual se borra la DEK de memoria (sesión "cero rastro"). */
private const val BACKGROUND_DEK_CLEAR_MS = 30_000L

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val handler = Handler(Looper.getMainLooper())
        var clearDekRunnable: Runnable? = null

        // Observador de ciclo de vida global para "Cero Rastro"
        ProcessLifecycleOwner.get().lifecycle.addObserver(LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    clearDekRunnable = Runnable {
                        DekManager.clearDek()
                        Pr4yLog.crypto("DEK borrada por sesión cero rastro (30s en segundo plano).")
                    }
                    handler.postDelayed(clearDekRunnable!!, BACKGROUND_DEK_CLEAR_MS)
                }
                Lifecycle.Event.ON_START -> {
                    clearDekRunnable?.let { handler.removeCallbacks(it) }
                    clearDekRunnable = null
                }
                else -> {}
            }
        })

        setContent {
            val vm: MainViewModel = viewModel()
            val scope = rememberCoroutineScope()

            DisposableEffect(Unit) {
                DekManager.setDekClearedListener { vm.isUnlocked = false }
                onDispose { DekManager.setDekClearedListener(null) }
            }

            LaunchedEffect(Unit) {
                delay(300)
                vm.initBunker(applicationContext)
            }

            Pr4yTheme(prefs = vm.displayPrefs) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    when {
                        !vm.isReady || vm.authRepository == null -> ShimmerLoading()
                        else -> Pr4yNavHost(
                            authRepository        = vm.authRepository!!,
                            loggedIn              = vm.loggedIn,
                            onLoginSuccess        = { vm.loggedIn = true },
                            onLogout              = {
                                scope.launch {
                                    vm.authRepository?.logout()
                                    vm.loggedIn   = false
                                    vm.isUnlocked = false
                                }
                            },
                            unlocked              = vm.isUnlocked,
                            onUnlocked            = { vm.isUnlocked = true },
                            hasSeenWelcome        = vm.hasSeenWelcome,
                            onSetHasSeenWelcome   = { vm.setHasSeenWelcome() },
                            displayPrefs          = vm.displayPrefs,
                            onUpdateDisplayPrefs  = { prefs ->
                                vm.updateDisplayPrefs(applicationContext, prefs)
                            },
                        )
                    }
                }
            }
        }
    }
}
