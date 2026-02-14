package com.pr4y.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.pr4y.app.BuildConfig
import com.pr4y.app.data.auth.AuthRepository
import com.pr4y.app.data.remote.EndpointProvider
import com.pr4y.app.data.sync.LastSyncStatus
import com.pr4y.app.data.sync.SyncRepository
import com.pr4y.app.data.sync.SyncResult
import com.pr4y.app.di.AppContainer
import com.pr4y.app.ui.components.Pr4yTopAppBar
import com.pr4y.app.work.ReminderScheduler
import com.pr4y.app.work.SyncScheduler
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
    val outbox by AppContainer.db.outboxDao().getAllFlow().collectAsState(initial = emptyList())
    var lastSyncStatus by remember { mutableStateOf<LastSyncStatus?>(null) }

    var apiEndpoint by remember { mutableStateOf("") }
    LaunchedEffect(Unit, outbox) {
        apiEndpoint = EndpointProvider.getBaseUrl(context)
        lastSyncStatus = syncRepository.getLastSyncStatus()
    }

    fun runSyncAndRefresh() {
        scope.launch {
            when (val r = syncRepository.sync()) {
                is SyncResult.Success -> {
                    snackbar.showSnackbar("Sincronizado correctamente")
                    lastSyncStatus = syncRepository.getLastSyncStatus()
                }
                is SyncResult.Error -> {
                    snackbar.showSnackbar("Error: ${r.message}")
                    lastSyncStatus = syncRepository.getLastSyncStatus()
                }
            }
        }
    }

    val showProtectedSyncIndicator = outbox.isEmpty() && lastSyncStatus?.lastOk == true
    val recentErrorAt = lastSyncStatus?.lastErrorAt
    val showSyncPausedBanner = recentErrorAt != null &&
        (System.currentTimeMillis() - recentErrorAt) < 24 * 60 * 60 * 1000L

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
            if (showProtectedSyncIndicator) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "Protegido y sincronizado",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (showSyncPausedBanner) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = "Sincronización pausada. Comprueba tu conexión.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { runSyncAndRefresh() }) {
                        Text("Reintentar")
                    }
                }
            }
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
            TextButton(onClick = {
                SyncScheduler.scheduleOnce(context)
                runSyncAndRefresh()
            }) {
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
