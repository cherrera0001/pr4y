package com.pr4y.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pr4y.app.crypto.DekManager
import com.pr4y.app.crypto.LocalCrypto
import com.pr4y.app.data.auth.AuthRepository
import com.pr4y.app.data.local.entity.RequestEntity
import com.pr4y.app.data.sync.LastSyncStatus
import com.pr4y.app.data.sync.SyncRepository
import com.pr4y.app.data.sync.SyncResult
import com.pr4y.app.di.AppContainer
import com.pr4y.app.ui.Routes
import com.pr4y.app.ui.components.Pr4yTopAppBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val SYNC_ERROR_RECENT_MS = 24 * 60 * 60 * 1000L // 24h

@Composable
fun HomeScreen(
    navController: NavController,
    authRepository: AuthRepository,
) {
    val context = LocalContext.current
    val entities by AppContainer.db.requestDao().getAll().collectAsState(initial = emptyList())
    var requests by remember { mutableStateOf<List<RequestEntity>>(emptyList()) }
    val outbox by AppContainer.db.outboxDao().getAllFlow().collectAsState(initial = emptyList())
    val snackbar = remember { SnackbarHostState() }
    val syncRepository = remember {
        SyncRepository(authRepository = authRepository, context = context)
    }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var lastSyncStatus by remember { mutableStateOf<LastSyncStatus?>(null) }

    LaunchedEffect(entities) {
        requests = withContext(Dispatchers.Default) {
            val dek = DekManager.getDek()
            entities.map { entity ->
                if (entity.encryptedPayloadB64 != null && dek != null) {
                    try {
                        val plain = LocalCrypto.decrypt(entity.encryptedPayloadB64, dek)
                        val json = JSONObject(String(plain))
                        entity.copy(
                            title = json.optString("title", ""),
                            body = json.optString("body", "")
                        )
                    } catch (e: Exception) { entity }
                } else {
                    entity
                }
            }
        }
    }

    LaunchedEffect(Unit, outbox) {
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
        (System.currentTimeMillis() - recentErrorAt) < SYNC_ERROR_RECENT_MS

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Pr4yTopAppBar(
                title = "Pedidos",
                onNavIconClick = { navController.navigate(Routes.SETTINGS) },
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                if (requests.isNotEmpty()) {
                    SmallFloatingActionButton(
                        onClick = { navController.navigate(Routes.FOCUS_MODE) },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "Modo Enfoque")
                    }
                }
                FloatingActionButton(onClick = { navController.navigate(Routes.NEW_EDIT) }) {
                    Icon(Icons.Default.Add, contentDescription = "Nuevo")
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (showProtectedSyncIndicator) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    tonalElevation = 0.dp,
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
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
            }
            if (showSyncPausedBanner) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                    tonalElevation = 0.dp,
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Text(
                                text = "Sincronización pausada. Comprueba tu conexión.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                        TextButton(onClick = { runSyncAndRefresh() }) {
                            Text("Reintentar", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            if (outbox.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "${outbox.size} cambio(s) sin sincronizar",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(onClick = { runSyncAndRefresh() }) {
                            Text("Sincronizar")
                        }
                    }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = { navController.navigate(Routes.JOURNAL) }) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Diario", style = MaterialTheme.typography.labelLarge)
                }
                TextButton(onClick = { navController.navigate(Routes.SEARCH) }) {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Buscar", style = MaterialTheme.typography.labelLarge)
                }
            }
            if (requests.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            text = "Aún no hay pedidos",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "Añade el primero y compártelo con quien quieras que ore por ti.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = { navController.navigate(Routes.NEW_EDIT) }) {
                            Text("Crear pedido")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    items(
                        items = requests,
                        key = { it.id },
                    ) { req ->
                        RequestItem(
                            request = req,
                            onClick = { navController.navigate(Routes.detail(req.id)) },
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RequestItem(
    request: RequestEntity,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            Modifier
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = request.title.ifBlank { "Sin título" },
                    style = MaterialTheme.typography.titleMedium,
                )
                if (request.body?.isNotBlank() == true) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = request.body.take(80) + if (request.body.length > 80) "…" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
