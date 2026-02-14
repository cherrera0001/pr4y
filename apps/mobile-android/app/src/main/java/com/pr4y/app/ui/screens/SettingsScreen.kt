package com.pr4y.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.pr4y.app.BuildConfig
import com.pr4y.app.data.auth.AuthRepository
import com.pr4y.app.data.remote.EndpointProvider
import com.pr4y.app.data.sync.SyncRepository
import com.pr4y.app.data.sync.SyncResult
import com.pr4y.app.ui.components.Pr4yTopAppBar
import com.pr4y.app.work.ReminderScheduler
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
    val syncRepository = remember { SyncRepository(authRepository, context) }

    var apiEndpoint by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        apiEndpoint = EndpointProvider.getBaseUrl(context)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            ReminderScheduler.scheduleDaily(context)
            scope.launch { snackbar.showSnackbar("Recordatorio diario programado (9:00)") }
        } else {
            scope.launch { snackbar.showSnackbar("Sin permiso de notificaciones no se pueden mostrar recordatorios") }
        }
    }

    fun scheduleReminder() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)) {
                PackageManager.PERMISSION_GRANTED -> {
                    ReminderScheduler.scheduleDaily(context)
                    scope.launch { snackbar.showSnackbar("Recordatorio diario programado (9:00)") }
                }
                else -> notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            ReminderScheduler.scheduleDaily(context)
            scope.launch { snackbar.showSnackbar("Recordatorio diario programado (9:00)") }
        }
    }

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
            if (BuildConfig.DEBUG) {
                Text("API Debug Settings", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = apiEndpoint,
                    onValueChange = { apiEndpoint = it },
                    label = { Text("API Endpoint URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                TextButton(
                    onClick = {
                        scope.launch {
                            EndpointProvider.updateEndpoint(context, apiEndpoint)
                            snackbar.showSnackbar("Endpoint actualizado. Reinicia la app para aplicar.")
                        }
                    },
                ) {
                    Text("Guardar Endpoint")
                }
                HorizontalDivider(Modifier.padding(vertical = 16.dp))
            }

            Text("Sincronización", style = MaterialTheme.typography.titleMedium)
            TextButton(
                onClick = {
                    scope.launch {
                        when (val r = syncRepository.sync()) {
                            is SyncResult.Success -> snackbar.showSnackbar("Sincronizado correctamente")
                            is SyncResult.Error -> snackbar.showSnackbar("Error: ${r.message}")
                        }
                    }
                },
            ) {
                Text("Sincronizar ahora")
            }
            Spacer(Modifier.height(16.dp))
            Text("Recordatorios", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { scheduleReminder() }) {
                Text("Configurar recordatorio diario (9:00)")
            }
            Spacer(Modifier.height(16.dp))
            Text("Cuenta", style = MaterialTheme.typography.titleMedium)
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
