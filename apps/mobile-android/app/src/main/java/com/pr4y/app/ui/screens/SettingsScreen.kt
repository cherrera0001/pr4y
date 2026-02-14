package com.pr4y.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pr4y.app.data.auth.AuthRepository
import com.pr4y.app.data.sync.SyncRepository
import com.pr4y.app.data.sync.SyncResult
import com.pr4y.app.ui.components.Pr4yTopAppBar
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    navController: NavController,
    authRepository: AuthRepository,
    onLogout: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val syncRepository = remember { SyncRepository(authRepository) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Pr4yTopAppBar(
                title = "Ajustes",
                onBackClick = { navController.navigateUp() },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Sincronización")
            TextButton(
                onClick = {
                    scope.launch {
                        when (val r = syncRepository.sync()) {
                            is SyncResult.Success -> snackbar.showSnackbar("Sincronizado")
                            is SyncResult.Error -> snackbar.showSnackbar(r.message)
                        }
                    }
                },
            ) {
                Text("Sincronizar ahora")
            }
            Text("Recordatorios")
            TextButton(
                onClick = {
                    ReminderScheduler.scheduleDaily(context)
                    scope.launch { snackbar.showSnackbar("Recordatorio programado") }
                },
            ) {
                Text("Configurar recordatorio diario")
            }
            Text("Cuenta")
            TextButton(
                onClick = {
                    scope.launch {
                        authRepository.logoutRemote()
                        onLogout()
                    }
                },
            ) {
                Text("Cerrar sesión")
            }
        }
    }
}
