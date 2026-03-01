package com.pr4y.app.ui

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pr4y.app.crypto.DekManager
import com.pr4y.app.data.auth.AuthRepository
import com.pr4y.app.data.prefs.DisplayPrefs
import com.pr4y.app.data.remote.ReminderScheduleDto
import com.pr4y.app.data.remote.RetrofitClient
import com.pr4y.app.data.sync.SyncRepository
import com.pr4y.app.di.AppContainer
import com.pr4y.app.ui.screens.*
import com.pr4y.app.ui.viewmodel.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun Pr4yNavHost(
    authRepository: AuthRepository,
    navController: NavHostController = rememberNavController(),
    loggedIn: Boolean,
    onLoginSuccess: () -> Unit,
    onLogout: () -> Unit,
    unlocked: Boolean,
    onUnlocked: () -> Unit,
    hasSeenWelcome: Boolean,
    onSetHasSeenWelcome: () -> Unit,
    displayPrefs: DisplayPrefs,
    onUpdateDisplayPrefs: (DisplayPrefs) -> Unit,
    reminderSchedules: List<ReminderScheduleDto>,
    onUpdateReminderSchedules: (List<ReminderScheduleDto>) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val api = remember { RetrofitClient.create(context) }
    val syncRepository = remember { SyncRepository(authRepository, context) }

    LaunchedEffect(loggedIn) {
        if (!loggedIn && navController.currentDestination?.route != Routes.LOGIN) {
            navController.navigate(Routes.LOGIN) {
                popUpTo(navController.graph.startDestinationId) { inclusive = true }
            }
        }
    }

    val startDestination = when {
        !loggedIn      -> Routes.LOGIN
        !unlocked      -> Routes.UNLOCK
        !hasSeenWelcome-> Routes.WELCOME
        else           -> Routes.MAIN
    }

    // Rastrea si el usuario estuvo desbloqueado al menos una vez en esta sesión.
    // El overlay solo aparece cuando la DEK se limpia MID-SESSION (no en la apertura inicial).
    var wasEverUnlocked by remember { mutableStateOf(unlocked) }
    if (unlocked) wasEverUnlocked = true

    val showReauthOverlay = loggedIn && !unlocked && wasEverUnlocked

    Box(Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = startDestination) {
            composable(Routes.LOGIN) {
                val loginViewModel: LoginViewModel = viewModel(
                    factory = LoginViewModelFactory(authRepository)
                )
                LoginScreen(
                    viewModel = loginViewModel,
                    onSuccess = {
                        onLoginSuccess()
                        navController.navigate(Routes.UNLOCK) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    }
                )
            }

            composable(Routes.UNLOCK) {
                val unlockViewModel: UnlockViewModel = viewModel(
                    factory = UnlockViewModelFactory(authRepository, syncRepository, api)
                )
                UnlockScreen(
                    viewModel = unlockViewModel,
                    onUnlocked = {
                        onUnlocked()
                        val nextRoute = if (!hasSeenWelcome) Routes.WELCOME else Routes.MAIN
                        navController.navigate(nextRoute) {
                            popUpTo(Routes.UNLOCK) { inclusive = true }
                        }
                    },
                    onSessionExpired = onLogout
                )
            }

            composable(Routes.WELCOME) {
                WelcomeScreen(
                    onStartClick = {
                        onSetHasSeenWelcome()
                        navController.navigate(Routes.MAIN) {
                            popUpTo(Routes.WELCOME) { inclusive = true }
                        }
                    }
                )
            }

            composable(Routes.MAIN) {
                val mainContext = androidx.compose.ui.platform.LocalContext.current
                val mainUserId = remember(mainContext) {
                    com.pr4y.app.data.auth.AuthTokenStore(mainContext.applicationContext).getUserId() ?: ""
                }
                InnerNavHost(
                    authRepository             = authRepository,
                    syncRepository             = syncRepository,
                    api                        = api,
                    onLogout                   = onLogout,
                    userId                     = mainUserId,
                    displayPrefs               = displayPrefs,
                    onUpdateDisplayPrefs       = onUpdateDisplayPrefs,
                    reminderSchedules          = reminderSchedules,
                    onUpdateReminderSchedules  = onUpdateReminderSchedules,
                )
            }
        }

        // Overlay de re-autenticación biométrica — aparece cuando la DEK expira mid-session.
        // El NavHost subyacente permanece en composición, preservando todo el estado (rememberSaveable).
        if (showReauthOverlay) {
            ReauthOverlay(
                onUnlocked = onUnlocked,
                onFallbackToPassphrase = {
                    // Navegar a UnlockScreen para autenticación completa con clave.
                    // El estado de la pantalla actual se pierde (caso poco frecuente).
                    navController.navigate(Routes.UNLOCK) {
                        popUpTo(Routes.MAIN) { inclusive = true }
                    }
                }
            )
        }
    }
}

/**
 * Overlay de re-autenticación biométrica que aparece sobre la pantalla actual
 * cuando la DEK expira por timeout (30s en segundo plano).
 *
 * Ventaja clave: el contenido subyacente permanece en composición, preservando
 * todo el estado (campos de texto, posición de scroll, borradores) sin navegación.
 */
@Composable
private fun ReauthOverlay(
    onUnlocked: () -> Unit,
    onFallbackToPassphrase: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val biometricEnabled = remember { DekManager.hasPersistedDekForBiometric() }
    val canAuthenticate = remember {
        BiometricManager.from(context)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    fun launchBiometric() {
        val crypto = DekManager.getInitializedCipherForRecovery() ?: run {
            onFallbackToPassphrase()
            return
        }
        val executor = ContextCompat.getMainExecutor(context)
        val prompt = BiometricPrompt(
            context as FragmentActivity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val cipher = result.cryptoObject?.cipher
                    if (cipher != null && DekManager.recoverDekWithCipher(cipher)) {
                        onUnlocked()
                    } else {
                        scope.launch { snackbarHostState.showSnackbar("No se pudo desbloquear. Usa tu clave.") }
                        onFallbackToPassphrase()
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    when (errorCode) {
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                        BiometricPrompt.ERROR_CANCELED,
                        BiometricPrompt.ERROR_USER_CANCELED -> onFallbackToPassphrase()
                        BiometricPrompt.ERROR_LOCKOUT,
                        BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> {
                            scope.launch { snackbarHostState.showSnackbar("Demasiados intentos. Usa tu clave.") }
                            onFallbackToPassphrase()
                        }
                        else -> onFallbackToPassphrase()
                    }
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Sesión protegida")
            .setSubtitle("Toca el sensor para continuar donde lo dejaste")
            .setNegativeButtonText("Usar clave")
            .build()
        prompt.authenticate(info, crypto)
    }

    // Auto-lanzar biométrica al aparecer el overlay
    LaunchedEffect(Unit) {
        if (biometricEnabled && canAuthenticate) {
            delay(200L)
            launchBiometric()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    )
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = "Sesión protegida",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "El búnker se cerró tras un tiempo en segundo plano. Autentícate para continuar.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(40.dp))

                    if (biometricEnabled && canAuthenticate) {
                        Button(
                            onClick = { launchBiometric() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(28.dp),
                        ) {
                            Icon(Icons.Default.Fingerprint, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Desbloquear con huella", fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    TextButton(onClick = onFallbackToPassphrase) {
                        Text("Usar mi clave de privacidad")
                    }
                }
            }
        }
    }
}

@Composable
private fun InnerNavHost(
    authRepository: AuthRepository,
    syncRepository: SyncRepository,
    api: com.pr4y.app.data.remote.ApiService,
    onLogout: () -> Unit,
    userId: String = "",
    displayPrefs: DisplayPrefs,
    onUpdateDisplayPrefs: (DisplayPrefs) -> Unit,
    reminderSchedules: List<ReminderScheduleDto>,
    onUpdateReminderSchedules: (List<ReminderScheduleDto>) -> Unit,
) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            val homeViewModel: HomeViewModel = viewModel(
                factory = HomeViewModelFactory(AppContainer.db, syncRepository, userId)
            )
            HomeScreen(navController = navController, viewModel = homeViewModel, api = api)
        }

        composable(Routes.NEW_EDIT) { NewEditScreen(navController = navController, requestId = null) }
        composable(Routes.NEW_EDIT_ID) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id")
            NewEditScreen(navController = navController, requestId = id)
        }
        composable(Routes.DETAIL) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id") ?: ""
            DetailScreen(navController = navController, id = id, authRepository = authRepository, api = api)
        }
        composable(Routes.JOURNAL) { JournalScreen(navController = navController) }
        composable(Routes.NEW_JOURNAL) { NewJournalScreen(navController = navController) }
        composable(Routes.JOURNAL_ENTRY) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id") ?: ""
            JournalEntryScreen(navController = navController, id = id)
        }
        composable(Routes.SEARCH) { SearchScreen(navController = navController) }
        composable(Routes.FOCUS_MODE) { FocusModeScreen(navController = navController) }
        composable(Routes.VICTORIAS) {
            val victoriasViewModel: VictoriasViewModel = viewModel(
                factory = VictoriasViewModelFactory(authRepository, api)
            )
            VictoriasScreen(navController = navController, viewModel = victoriasViewModel)
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                navController             = navController,
                authRepository            = authRepository,
                api                       = api,
                onLogout                  = onLogout,
                displayPrefs              = displayPrefs,
                onUpdateDisplayPrefs      = onUpdateDisplayPrefs,
                reminderSchedules         = reminderSchedules,
                onUpdateReminderSchedules = onUpdateReminderSchedules,
            )
        }
        composable(Routes.ROULETTE) {
            RouletteScreen(navController = navController)
        }
    }
}
