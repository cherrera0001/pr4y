package com.pr4y.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pr4y.app.data.auth.AuthRepository
import com.pr4y.app.ui.screens.DetailScreen
import com.pr4y.app.ui.screens.HomeScreen
import com.pr4y.app.ui.screens.JournalScreen
import com.pr4y.app.ui.screens.LoginScreen
import com.pr4y.app.ui.screens.NewEditScreen
import com.pr4y.app.ui.screens.SearchScreen
import com.pr4y.app.ui.screens.SettingsScreen
import com.pr4y.app.ui.screens.UnlockScreen

object Routes {
    const val LOGIN = "login"
    const val UNLOCK = "unlock"
    const val MAIN = "main"
    const val HOME = "home"
    const val NEW_EDIT = "new_edit"
    const val NEW_EDIT_ID = "new_edit/{id}"
    const val DETAIL = "detail/{id}"
    const val JOURNAL = "journal"
    const val SEARCH = "search"
    const val SETTINGS = "settings"

    fun detail(id: String) = "detail/$id"
    fun newEdit(id: String?) = if (id != null) "new_edit/$id" else NEW_EDIT
}

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
    val startDestination = when {
        !loggedIn -> Routes.LOGIN
        !unlocked -> Routes.UNLOCK
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
            val bearer = authRepository.getBearer() ?: ""
            UnlockScreen(
                bearer = bearer,
                onUnlocked = {
                    onUnlocked()
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.UNLOCK) { inclusive = true }
                    }
                },
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
        composable(Routes.HOME) { HomeScreen(navController = navController) }
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
        composable(Routes.SEARCH) { SearchScreen(navController = navController) }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                navController = navController,
                authRepository = authRepository,
                onLogout = onLogout,
            )
        }
    }
}
