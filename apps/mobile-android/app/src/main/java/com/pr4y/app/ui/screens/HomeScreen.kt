package com.pr4y.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pr4y.app.data.auth.AuthTokenStore
import com.pr4y.app.data.local.entity.RequestEntity
import com.pr4y.app.di.AppContainer
import com.pr4y.app.ui.Routes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import com.pr4y.app.ui.components.Pr4yTopAppBar
import com.pr4y.app.ui.viewmodel.HomeUiState
import com.pr4y.app.ui.viewmodel.HomeViewModel

@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()
    val syncMessage by viewModel.syncMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(syncMessage) {
        syncMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSyncMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Pr4yTopAppBar(
                title = "Mis Pedidos",
                onNavIconClick = { navController.navigate(Routes.SETTINGS) },
            )
        },
        floatingActionButton = {
            HomeFABs(uiState, navController)
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (val state = uiState) {
                is HomeUiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                is HomeUiState.Success -> {
                    SyncStatusHeader(state, onRetry = { viewModel.runSync() })
                    FidelidadCard()
                    QuickActionsRow(navController)

                    if (state.requests.isEmpty()) {
                        EmptyRequestsState { navController.navigate(Routes.NEW_EDIT) }
                    } else {
                        RequestsList(state.requests, navController)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeFABs(uiState: HomeUiState, navController: NavController) {
    Column(horizontalAlignment = Alignment.End) {
        if (uiState is HomeUiState.Success && uiState.requests.isNotEmpty()) {
            SmallFloatingActionButton(
                onClick = { navController.navigate(Routes.FOCUS_MODE) },
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(bottom = 16.dp).sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = "Modo Enfoque")
            }
        }
        FloatingActionButton(
            onClick = { navController.navigate(Routes.NEW_EDIT) },
            modifier = Modifier.sizeIn(minWidth = 56.dp, minHeight = 56.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.Black
        ) {
            Icon(Icons.Default.Add, contentDescription = "Nuevo Pedido")
        }
    }
}

@Composable
private fun SyncStatusHeader(state: HomeUiState.Success, onRetry: () -> Unit) {
    val showSyncPaused = state.lastSyncStatus?.lastOk == false
    
    AnimatedVisibility(visible = showSyncPaused) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f)
        ) {
            Row(
                Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Sincronización pausada", style = MaterialTheme.typography.labelMedium)
                }
                TextButton(onClick = onRetry) {
                    Text("Reintentar", color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
    }

    if (state.outboxCount > 0) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "${state.outboxCount} cambios pendientes",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onRetry) { Text("Sincronizar ahora") }
            }
        }
    }
}

private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

@Composable
private fun FidelidadCard() {
    val context = LocalContext.current
    val userId = remember(context) { AuthTokenStore(context.applicationContext).getUserId() ?: "" }
    var weekCounts by remember { mutableStateOf<List<Int>>(emptyList()) }
    LaunchedEffect(userId) {
        if (userId.isEmpty()) return@LaunchedEffect
        weekCounts = withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val requests = AppContainer.db.requestDao().getAll(userId).first()
            val journal = AppContainer.db.journalDao().getAll(userId).first()
            val timestamps = requests.map { it.createdAt } + journal.map { it.createdAt }
            val counts = (0 until 4).map { weekIndex ->
                val weekStart = now - (weekIndex + 1) * WEEK_MS
                val weekEnd = now - weekIndex * WEEK_MS
                timestamps.count { it in weekStart until weekEnd }
            }.reversed()
            counts
        }
    }
    if (weekCounts.isEmpty() || weekCounts.all { it == 0 }) return
    val labels = listOf("Esta semana", "Semana anterior", "Hace 2 sem.", "Hace 3 sem.")
    val maxCount = (weekCounts.maxOrNull() ?: 1).coerceAtLeast(1)
    val barColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Mi constancia",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(12.dp))
            weekCounts.take(4).forEachIndexed { index, count ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        labels.getOrElse(index) { "" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(100.dp),
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(20.dp)
                            .padding(vertical = 2.dp)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height
                            val barW = (w * (count.toFloat() / maxCount)).coerceIn(0f, w)
                            drawRect(
                                color = barColor,
                                topLeft = Offset(0f, 0f),
                                size = Size(barW, h),
                            )
                        }
                    }
                }
            }
            if (weekCounts.any { it > 0 }) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "En los momentos más difíciles, fue cuando más busqué a Dios.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    fontStyle = FontStyle.Italic,
                )
            }
        }
    }
}

@Composable
private fun QuickActionsRow(navController: NavController) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AssistChip(
            onClick = { navController.navigate(Routes.JOURNAL) },
            label = { Text("Diario") },
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.MenuBook, null, Modifier.size(18.dp)) }
        )
        AssistChip(
            onClick = { navController.navigate(Routes.ROULETTE) },
            label = { Text("Ruleta") },
            leadingIcon = { Icon(Icons.Default.Casino, null, Modifier.size(18.dp)) }
        )
        AssistChip(
            onClick = { navController.navigate(Routes.VICTORIAS) },
            label = { Text("Victorias") },
            leadingIcon = { Icon(Icons.Default.Star, null, Modifier.size(18.dp)) }
        )
    }
}

@Composable
private fun RequestsList(requests: List<RequestEntity>, navController: NavController) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 88.dp) 
    ) {
        items(items = requests, key = { it.id }) { req ->
            RequestItem(
                request = req,
                onClick = { navController.navigate(Routes.detail(req.id)) }
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
            )
        }
    }
}

@Composable
private fun RequestItem(request: RequestEntity, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = request.title.ifBlank { "Sin título" },
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                if (!request.body.isNullOrBlank()) {
                    Text(
                        text = request.body,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyRequestsState(onCreateClick: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Default.Security,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            )
            Text(
                "Tu búnker está listo",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White
            )
            Text(
                "Aún no tienes pedidos de oración registrados.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onCreateClick) {
                Text("Crear mi primer pedido")
            }
        }
    }
}
