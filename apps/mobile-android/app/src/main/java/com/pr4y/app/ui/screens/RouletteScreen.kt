package com.pr4y.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.pr4y.app.data.remote.PublicRequestDto
import com.pr4y.app.ui.components.EmptyStatePlaceholder
import com.pr4y.app.ui.components.Pr4yTopAppBar
import com.pr4y.app.ui.viewmodel.RouletteUiState
import com.pr4y.app.ui.viewmodel.RouletteViewModel
import kotlin.math.absoluteValue

/**
 * Spec: Pr4y Roulette (Intercesión Anónima).
 * UI de "Paz Mental": Transiciones suaves, sin distracciones.
 */
@Composable
fun RouletteScreen(
    navController: NavController,
    viewModel: RouletteViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            Pr4yTopAppBar(
                title = "Orar por un extraño",
                onNavIconClick = { navController.popBackStack() }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val state = uiState) {
                is RouletteUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                is RouletteUiState.Empty -> {
                    EmptyStatePlaceholder(
                        icon = Icons.Default.Info,
                        title = "Silencio en el búnker",
                        description = "No hay peticiones públicas en este momento. Vuelve más tarde.",
                        actionText = "Reintentar",
                        onAction = { viewModel.loadRequests() }
                    )
                }
                is RouletteUiState.Error -> {
                    EmptyStatePlaceholder(
                        icon = Icons.Default.Refresh,
                        title = "Error de conexión",
                        description = state.message,
                        actionText = "Reintentar",
                        onAction = { viewModel.loadRequests() }
                    )
                }
                is RouletteUiState.Success -> {
                    RouletteCarousel(
                        requests = state.requests,
                        onPrayClick = { viewModel.prayForRequest(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RouletteCarousel(
    requests: List<PublicRequestDto>,
    onPrayClick: (String) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { requests.size })

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center
    ) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 32.dp),
            modifier = Modifier.weight(1f)
        ) { page ->
            val request = requests[page]
            
            // Animación suave de escala basada en la posición
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp, horizontal = 16.dp)
                    .graphicsLayer {
                        val pageOffset = (
                            (pagerState.currentPage - page) + pagerState
                                .currentPageOffsetFraction
                            ).absoluteValue
                        
                        alpha = lerp(
                            start = 0.5f,
                            stop = 1f,
                            fraction = 1f - pageOffset.coerceIn(0f, 1f)
                        )
                        scaleY = lerp(
                            start = 0.8f,
                            stop = 1f,
                            fraction = 1f - pageOffset.coerceIn(0f, 1f)
                        )
                    },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = request.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Text(
                        text = request.body ?: "",
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp), // Accesibilidad para lectura
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontStyle = FontStyle.Italic
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    var prayed by remember { mutableStateOf(false) }
                    
                    Button(
                        onClick = { 
                            if (!prayed) {
                                onPrayClick(request.id)
                                prayed = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (prayed) 
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f) 
                            else 
                                MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.Favorite, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (prayed) "Orando..." else "Me uno en oración")
                    }
                    
                    if (request.prayerCount > 0) {
                        Text(
                            text = "${request.prayerCount} personas ya han orado",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                }
            }
        }
        
        Text(
            text = "Desliza para encontrar a alguien más por quien orar",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 48.dp),
            textAlign = TextAlign.Center
        )
    }
}

// Extension to use sp in typography copy if needed (assuming imports are correct)
private val Int.sp get() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
