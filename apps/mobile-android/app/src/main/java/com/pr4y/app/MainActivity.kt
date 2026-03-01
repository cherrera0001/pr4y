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
import com.pr4y.app.data.remote.ReminderScheduleDto
import com.pr4y.app.data.remote.ReminderSchedulesResponse
import com.pr4y.app.data.remote.RetrofitClient
import com.pr4y.app.ui.Pr4yNavHost
import com.pr4y.app.ui.components.ShimmerLoading
import com.pr4y.app.ui.theme.Pr4yTheme
import com.pr4y.app.di.AppContainer
import com.pr4y.app.util.Pr4yLog
import com.pr4y.app.work.ReminderScheduler
import com.pr4y.app.work.SyncScheduler
import java.util.Calendar
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
    var reminderSchedules by mutableStateOf<List<ReminderScheduleDto>>(emptyList())

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
                        // Inicializar la bóveda aislada del usuario autenticado (patrón TenantID).
                        // Si hay una sesión anterior de otro usuario, AppContainer elimina su archivo.
                        val userId = store.getUserId()
                        if (userId != null) {
                            AppContainer.init(context, userId)
                        }
                        isUnlocked = DekManager.tryRecoverDekSilently()
                        SyncScheduler.schedulePeriodic(context)
                    }

                    val retrofitApi = RetrofitClient.create(context, store)
                    api = retrofitApi
                    authRepository = AuthRepository(retrofitApi, store)
                    hasSeenWelcome = store.hasSeenWelcome()

                    // Fetch display prefs y reminder schedules del servidor una vez autenticado
                    if (loggedIn) {
                        val bearer = store.getAccessToken()?.let { "Bearer $it" }
                        if (bearer != null) {
                            try {
                                val res = retrofitApi.getDisplayPreferences(bearer)
                                if (res.isSuccessful) {
                                    res.body()?.let { dto ->
                                        DisplayPrefsStore.save(context, dto.toDisplayPrefs())
                                    }
                                }
                            } catch (e: Exception) {
                                Pr4yLog.w("initBunker: display prefs fetch failed (offline?): ${e.message}")
                            }
                            try {
                                val res = retrofitApi.getReminderSchedules(bearer)
                                if (res.isSuccessful) {
                                    reminderSchedules = res.body()?.schedules ?: emptyList()
                                }
                            } catch (e: Exception) {
                                Pr4yLog.w("initBunker: reminder schedules fetch failed (offline?): ${e.message}")
                            }
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

    /**
     * Inicializa la bóveda del usuario que acaba de autenticarse (login en caliente).
     * Necesario cuando el usuario hace login SIN reinicio de app (initBunker ya corrió sin userId).
     */
    fun initVaultForCurrentUser(context: android.content.Context) {
        val userId = tokenStore?.getUserId() ?: return
        AppContainer.init(context, userId)
    }

    fun updateReminderSchedules(context: android.content.Context, schedules: List<ReminderScheduleDto>) {
        reminderSchedules = schedules
        ReminderScheduler.scheduleAll(context, schedules)
        viewModelScope.launch {
            try {
                val bearer = tokenStore?.getAccessToken()?.let { "Bearer $it" } ?: return@launch
                api?.putReminderSchedules(bearer, ReminderSchedulesResponse(schedules))
            } catch (e: Exception) {
                Pr4yLog.w("updateReminderSchedules: remote PUT failed: ${e.message}")
            }
        }
    }

    fun setHasSeenWelcome() {
        tokenStore?.setHasSeenWelcome(true)
        hasSeenWelcome = true
    }
}

/**
 * Retorna true si algún recordatorio habilitado está dentro de ±30min de la hora actual
 * en un día de la semana activo. Usado para el auto-dark cuando theme == "system".
 */
private fun checkPrayerWindow(schedules: List<ReminderScheduleDto>): Boolean {
    if (schedules.isEmpty()) return false
    val cal = Calendar.getInstance()
    // Calendar.DAY_OF_WEEK: 1=Dom..7=Sáb → convertimos a 0=Dom..6=Sáb
    val todayDow = cal.get(Calendar.DAY_OF_WEEK) - 1
    val nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    return schedules.any { s ->
        if (!s.enabled || !s.daysOfWeek.contains(todayDow)) return@any false
        val parts = s.time.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: return@any false
        val m = parts.getOrNull(1)?.toIntOrNull() ?: return@any false
        val schedMin = h * 60 + m
        nowMin in (schedMin - 30)..(schedMin + 30)
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

            // Auto-tema: si hay un recordatorio activo en ventana ±30min, oscurecer en modo "system"
            var prayerWindowActive by remember { mutableStateOf(false) }
            LaunchedEffect(vm.reminderSchedules) {
                while (true) {
                    prayerWindowActive = checkPrayerWindow(vm.reminderSchedules)
                    delay(60_000L)
                }
            }
            val effectivePrefs = remember(vm.displayPrefs, prayerWindowActive) {
                if (prayerWindowActive && vm.displayPrefs.theme == "system")
                    vm.displayPrefs.copy(theme = "dark")
                else
                    vm.displayPrefs
            }

            Pr4yTheme(prefs = effectivePrefs) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    when {
                        !vm.isReady || vm.authRepository == null -> ShimmerLoading()
                        else -> Pr4yNavHost(
                            authRepository             = vm.authRepository!!,
                            loggedIn                   = vm.loggedIn,
                            onLoginSuccess             = {
                                // Inicializar la bóveda del usuario que acaba de autenticarse.
                                // tokenStore ya tiene el userId tras AuthRepository.login/googleLogin.
                                vm.initVaultForCurrentUser(applicationContext)
                                vm.loggedIn = true
                            },
                            onLogout                   = {
                                scope.launch {
                                    vm.authRepository?.logout() // clear tokens + clearAllTables()
                                    vm.loggedIn   = false
                                    vm.isUnlocked = false
                                    // La próxima llamada a AppContainer.init() con otro userId
                                    // eliminará automáticamente el archivo de esta bóveda.
                                }
                            },
                            unlocked                   = vm.isUnlocked,
                            onUnlocked                 = { vm.isUnlocked = true },
                            hasSeenWelcome             = vm.hasSeenWelcome,
                            onSetHasSeenWelcome        = { vm.setHasSeenWelcome() },
                            displayPrefs               = vm.displayPrefs,
                            onUpdateDisplayPrefs       = { prefs ->
                                vm.updateDisplayPrefs(applicationContext, prefs)
                            },
                            reminderSchedules          = vm.reminderSchedules,
                            onUpdateReminderSchedules  = { schedules ->
                                vm.updateReminderSchedules(applicationContext, schedules)
                            },
                        )
                    }
                }
            }
        }
    }
}
