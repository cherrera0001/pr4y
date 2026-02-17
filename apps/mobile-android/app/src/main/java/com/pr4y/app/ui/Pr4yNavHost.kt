package com.pr4y.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pr4y.app.data.auth.AuthRepository
import com.pr4y.app.data.remote.RetrofitClient
import com.pr4y.app.data.sync.SyncRepository
import com.pr4y.app.di.AppContainer
import com.pr4y.app.ui.screens.*
import com.pr4y.app.ui.viewmodel.*

/**
 * Tech Lead Review: Navigation Integration Final.
 * Standards: Full ViewModel injection, consistent M3 identity.
 * Status: Refactored Login, Unlock, and Home.
 */
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
        !loggedIn -> Routes.LOGIN
        !unlocked -> Routes.UNLOCK
        !hasSeenWelcome -> Routes.WELCOME
        else -> Routes.MAIN
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
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
            InnerNavHost(
                authRepository = authRepository, 
                syncRepository = syncRepository,
                onLogout = onLogout
            )
        }
    }
}

@Composable
private fun InnerNavHost(
    authRepository: AuthRepository,
    syncRepository: SyncRepository,
    onLogout: () -> Unit,
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
    ) {
        composable(Routes.HOME) { 
            val homeViewModel: HomeViewModel = viewModel(
                factory = HomeViewModelFactory(AppContainer.db, syncRepository)
            )
            HomeScreen(navController = navController, viewModel = homeViewModel) 
        }

        composable(Routes.NEW_EDIT) { NewEditScreen(navController = navController, requestId = null) }
        composable(Routes.NEW_EDIT_ID) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id")
            NewEditScreen(navController = navController, requestId = id)
        }
        composable(Routes.DETAIL) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id") ?: ""
            DetailScreen(navController = navController, id = id)
        }
        composable(Routes.JOURNAL) { JournalScreen(navController = navController) }
        composable(Routes.NEW_JOURNAL) { NewJournalScreen(navController = navController) }
        composable(Routes.JOURNAL_ENTRY) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id") ?: ""
            JournalEntryScreen(navController = navController, id = id)
        }
        composable(Routes.SEARCH) { SearchScreen(navController = navController) }
        composable(Routes.FOCUS_MODE) { FocusModeScreen(navController = navController) }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                navController = navController,
                authRepository = authRepository,
                onLogout = onLogout,
            )
        }
    }
}
