package com.pr4y.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pr4y.app.data.auth.AuthRepository
import com.pr4y.app.data.auth.AuthTokenStore
import com.pr4y.app.ui.screens.*

@Composable
fun Pr4yNavHost(
    authRepository: AuthRepository,
    navController: NavHostController = rememberNavController(),
    loggedIn: Boolean,
    onLoginSuccess: () -> Unit,
    onLogout: () -> Unit,
    unlocked: Boolean,
    onUnlocked: () -> Unit,
) {
    val context = LocalContext.current
    val tokenStore = remember { AuthTokenStore(context) }
    
    val startDestination = when {
        !loggedIn -> Routes.LOGIN
        !unlocked -> Routes.UNLOCK
        !tokenStore.hasSeenWelcome() -> Routes.WELCOME
        else -> Routes.MAIN
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        composable(Routes.LOGIN) {
            LoginScreen(
                authRepository = authRepository,
                onSuccess = {
                    onLoginSuccess()
                    navController.navigate(Routes.UNLOCK) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.UNLOCK) {
            UnlockScreen(
                authRepository = authRepository,
                onUnlocked = {
                    onUnlocked()
                    val nextRoute = if (!tokenStore.hasSeenWelcome()) Routes.WELCOME else Routes.MAIN
                    navController.navigate(nextRoute) {
                        popUpTo(Routes.UNLOCK) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.WELCOME) {
            WelcomeScreen(
                onStartClick = {
                    tokenStore.setHasSeenWelcome(true)
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.WELCOME) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.MAIN) {
            InnerNavHost(authRepository = authRepository, onLogout = onLogout)
        }
    }
}

@Composable
private fun InnerNavHost(
    authRepository: AuthRepository,
    onLogout: () -> Unit,
) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
    ) {
        composable(Routes.HOME) { HomeScreen(navController = navController, authRepository = authRepository) }
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
