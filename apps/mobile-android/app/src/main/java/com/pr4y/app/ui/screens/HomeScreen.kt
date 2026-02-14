package com.pr4y.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pr4y.app.data.auth.AuthRepository
import com.pr4y.app.data.local.entity.RequestEntity
import com.pr4y.app.data.sync.SyncRepository
import com.pr4y.app.data.sync.SyncResult
import com.pr4y.app.di.AppContainer
import com.pr4y.app.ui.Routes
import com.pr4y.app.ui.components.Pr4yTopAppBar
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    navController: NavController,
    authRepository: AuthRepository,
) {
    val context = LocalContext.current
    val requests by AppContainer.db.requestDao().getAll().collectAsState(initial = emptyList())
    val outbox by AppContainer.db.outboxDao().getAllFlow().collectAsState(initial = emptyList())
    val snackbar = remember { SnackbarHostState() }
    val syncRepository = remember { SyncRepository(authRepository, context) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

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
                        Button(
                            onClick = {
                                scope.launch {
                                    when (val r = syncRepository.sync()) {
                                        is SyncResult.Success ->
                                            snackbar.showSnackbar("Sincronizado correctamente")
                                        is SyncResult.Error ->
                                            snackbar.showSnackbar("Error: ${r.message}")
                                    }
                                }
                            },
                        ) {
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
