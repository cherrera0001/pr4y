package com.pr4y.app.ui.screens

import android.Manifest
import android.app.TimePickerDialog
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import com.pr4y.app.data.prefs.DisplayPrefs
import com.pr4y.app.data.remote.ApiService
import com.pr4y.app.data.remote.EndpointProvider
import com.pr4y.app.data.remote.ReminderScheduleDto
import com.pr4y.app.data.sync.LastSyncStatus
import com.pr4y.app.data.sync.SyncRepository
import com.pr4y.app.data.sync.SyncResult
import com.pr4y.app.di.AppContainer
import com.pr4y.app.ui.Routes
import com.pr4y.app.ui.components.Pr4yTopAppBar
import com.pr4y.app.work.SyncScheduler
import kotlinx.coroutines.launch

private val DAY_LABELS = listOf("L", "M", "X", "J", "V", "S", "D")
// 0=Dom en la API → mostramos desde Lunes en UI; mapeamos DAY_LABELS a [1..6, 0]
private val DAY_OF_WEEK_ORDER = listOf(1, 2, 3, 4, 5, 6, 0) // Lun..Dom

@Composable
fun SettingsScreen(
    navController: NavController,
    authRepository: AuthRepository,
    api: ApiService,
    onLogout: () -> Unit,
    displayPrefs: DisplayPrefs = DisplayPrefs(),
    onUpdateDisplayPrefs: (DisplayPrefs) -> Unit = {},
    reminderSchedules: List<ReminderScheduleDto> = emptyList(),
    onUpdateReminderSchedules: (List<ReminderScheduleDto>) -> Unit = {},
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

    // Estado local de schedules — se inicializa desde las props y el usuario edita libremente
    var localSchedules by remember(reminderSchedules) { mutableStateOf(reminderSchedules) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            onUpdateReminderSchedules(localSchedules)
            scope.launch { snackbar.showSnackbar("Recordatorios programados") }
        } else {
            scope.launch { snackbar.showSnackbar("Sin permiso de notificaciones") }
        }
    }

    fun saveSchedules(schedules: List<ReminderScheduleDto>) {
        scope.launch {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                when (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)) {
                    PackageManager.PERMISSION_GRANTED -> {
                        onUpdateReminderSchedules(schedules)
                        snackbar.showSnackbar("Recordatorios guardados")
                    }
                    else -> notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            } else {
                onUpdateReminderSchedules(schedules)
                snackbar.showSnackbar("Recordatorios guardados")
            }
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
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp)
        ) {
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
            RemindersSection(
                schedules = localSchedules,
                onSchedulesChange = { localSchedules = it },
                onSave = { saveSchedules(localSchedules) },
            )
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            AppearanceSection(prefs = displayPrefs, onUpdate = onUpdateDisplayPrefs, navController = navController)
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Spacer(Modifier.height(8.dp))
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

// ─── Sección Apariencia ──────────────────────────────────────────────────────

private data class ChipOption<T>(val value: T, val label: String)

@Composable
private fun <T> ChipRow(
    title: String,
    options: List<ChipOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Text(title, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 12.dp))
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { opt ->
            FilterChip(
                selected = selected == opt.value,
                onClick  = { onSelect(opt.value) },
                label    = { Text(opt.label, style = MaterialTheme.typography.labelSmall) },
            )
        }
    }
}

@Composable
private fun AppearanceSection(
    prefs: DisplayPrefs,
    onUpdate: (DisplayPrefs) -> Unit,
    navController: NavController,
) {
    Text("Apariencia", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))

    ChipRow(
        title    = "Tema",
        options  = listOf(
            ChipOption("system", "Auto"),
            ChipOption("light",  "Claro"),
            ChipOption("dark",   "Oscuro"),
        ),
        selected = prefs.theme,
        onSelect = { onUpdate(prefs.copy(theme = it)) },
    )

    ChipRow(
        title    = "Tamaño de texto",
        options  = listOf(
            ChipOption("sm", "Pequeño"),
            ChipOption("md", "Normal"),
            ChipOption("lg", "Grande"),
            ChipOption("xl", "Extra"),
        ),
        selected = prefs.fontSize,
        onSelect = { onUpdate(prefs.copy(fontSize = it)) },
    )

    ChipRow(
        title    = "Tipografía",
        options  = listOf(
            ChipOption("system", "Sans"),
            ChipOption("serif",  "Serif"),
            ChipOption("mono",   "Mono"),
        ),
        selected = prefs.fontFamily,
        onSelect = { onUpdate(prefs.copy(fontFamily = it)) },
    )

    ChipRow(
        title    = "Espaciado",
        options  = listOf(
            ChipOption("compact",  "Compacto"),
            ChipOption("normal",   "Normal"),
            ChipOption("relaxed",  "Amplio"),
        ),
        selected = prefs.lineSpacing,
        onSelect = { onUpdate(prefs.copy(lineSpacing = it)) },
    )

    Spacer(Modifier.height(8.dp))
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text("Modo Contemplativo", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Paleta cálida y sin distracciones",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = prefs.contemplativeMode,
            onCheckedChange = { enabled ->
                if (enabled) {
                    onUpdate(
                        prefs.copy(
                            contemplativeMode = true,
                            fontFamily = "serif",
                            fontSize = "xl",
                            lineSpacing = "relaxed",
                        )
                    )
                    navController.navigate(Routes.FOCUS_MODE)
                } else {
                    onUpdate(
                        prefs.copy(
                            contemplativeMode = false,
                            fontFamily = "system",
                            fontSize = "md",
                            lineSpacing = "normal",
                        )
                    )
                }
            },
        )
    }
}

// ─── Sección Recordatorios (multi-schedule, hora libre) ──────────────────────

@Composable
private fun RemindersSection(
    schedules: List<ReminderScheduleDto>,
    onSchedulesChange: (List<ReminderScheduleDto>) -> Unit,
    onSave: () -> Unit,
) {
    Text("Recordatorios", style = MaterialTheme.typography.titleMedium)
    Text(
        "Elige cuándo orar — agrega tantos horarios como necesites.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
    )

    schedules.forEachIndexed { index, schedule ->
        ScheduleRow(
            schedule = schedule,
            onUpdate = { updated ->
                onSchedulesChange(schedules.toMutableList().also { it[index] = updated })
            },
            onDelete = {
                onSchedulesChange(schedules.toMutableList().also { it.removeAt(index) })
            },
        )
        if (index < schedules.lastIndex) {
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
        }
    }

    if (schedules.isEmpty()) {
        Text(
            "Sin recordatorios. Añade uno para que PR4Y te avise a la hora de orar.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )
    }

    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (schedules.size < 5) {
            TextButton(
                onClick = {
                    onSchedulesChange(
                        schedules + ReminderScheduleDto(
                            time = "09:00",
                            daysOfWeek = listOf(1, 2, 3, 4, 5),
                            enabled = true,
                        )
                    )
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(4.dp))
                Text("Agregar horario")
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
        TextButton(onClick = onSave) {
            Text("Guardar")
        }
    }
}

@Composable
private fun ScheduleRow(
    schedule: ReminderScheduleDto,
    onUpdate: (ReminderScheduleDto) -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val timeParts = schedule.time.split(":")
    val hour = timeParts.getOrNull(0)?.toIntOrNull() ?: 9
    val minute = timeParts.getOrNull(1)?.toIntOrNull() ?: 0

    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Botón de hora — abre TimePickerDialog nativo
            FilterChip(
                selected = true,
                onClick = {
                    TimePickerDialog(context, { _, h, m ->
                        onUpdate(schedule.copy(time = "%02d:%02d".format(h, m)))
                    }, hour, minute, true).show()
                },
                label = {
                    Text(
                        schedule.time,
                        style = MaterialTheme.typography.labelLarge,
                    )
                },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = schedule.enabled,
                    onCheckedChange = { onUpdate(schedule.copy(enabled = it)) },
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Eliminar",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        // Días de la semana
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            DAY_LABELS.forEachIndexed { uiIndex, label ->
                val dow = DAY_OF_WEEK_ORDER[uiIndex]
                FilterChip(
                    selected = schedule.daysOfWeek.contains(dow),
                    onClick = {
                        val newDays = if (schedule.daysOfWeek.contains(dow))
                            schedule.daysOfWeek - dow
                        else
                            schedule.daysOfWeek + dow
                        onUpdate(schedule.copy(daysOfWeek = newDays))
                    },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }
    }
}
