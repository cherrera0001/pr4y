package com.pr4y.app.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.pr4y.app.crypto.DekManager
import com.pr4y.app.crypto.LocalCrypto
import com.pr4y.app.data.auth.AuthTokenStore
import com.pr4y.app.data.local.entity.RequestEntity
import com.pr4y.app.di.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private val TIMER_OPTIONS = listOf(1, 3, 5)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusModeScreen(navController: NavController) {
    val context = LocalContext.current
    val userId = remember(context) { AuthTokenStore(context.applicationContext).getUserId() ?: "" }
    val entities by AppContainer.db.requestDao().getAll(userId).collectAsState(initial = emptyList())
    var requests by remember { mutableStateOf<List<RequestEntity>>(emptyList()) }

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
                    } catch (_: Exception) { entity }
                } else {
                    entity
                }
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Momento de Oración", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        if (requests.isEmpty()) {
            EmptyFocusState(Modifier.padding(padding))
        } else {
            FocusCarousel(
                requests = requests,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun EmptyFocusState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                "No hay pedidos para orar.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Text(
                "Añade pedidos desde Inicio y vuelve aquí para tu momento de oración.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FocusCarousel(
    requests: List<RequestEntity>,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { requests.size })

    // Per-page prayer state
    var activePrayerPage by remember { mutableIntStateOf(-1) }
    var selectedMinutes by remember { mutableIntStateOf(0) }
    var timerSecondsLeft by remember { mutableIntStateOf(0) }
    var timerRunning by remember { mutableStateOf(false) }
    var showCompletion by remember { mutableStateOf(false) }

    // Timer countdown
    LaunchedEffect(timerRunning, timerSecondsLeft) {
        if (timerRunning && timerSecondsLeft > 0) {
            delay(1000L)
            timerSecondsLeft--
        } else if (timerRunning && timerSecondsLeft == 0) {
            timerRunning = false
            showCompletion = true
        }
    }

    Column(modifier.fillMaxSize()) {
        // Pager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 32.dp),
            pageSpacing = 16.dp,
            userScrollEnabled = !timerRunning && !showCompletion,
            key = { requests.getOrNull(it)?.id ?: it }
        ) { page ->
            val request = requests.getOrNull(page) ?: return@HorizontalPager
            val isPrayingThis = activePrayerPage == page && (timerRunning || showCompletion)

            PrayerCard(
                request = request,
                isPraying = isPrayingThis && timerRunning,
                showCompletion = isPrayingThis && showCompletion,
                timerSecondsLeft = if (isPrayingThis) timerSecondsLeft else 0,
                totalSeconds = selectedMinutes * 60,
                onStartPrayer = { minutes ->
                    activePrayerPage = page
                    selectedMinutes = minutes
                    timerSecondsLeft = minutes * 60
                    timerRunning = true
                    showCompletion = false
                },
                onStopPrayer = {
                    timerRunning = false
                    activePrayerPage = -1
                    timerSecondsLeft = 0
                },
                onNextAfterCompletion = {
                    showCompletion = false
                    activePrayerPage = -1
                    if (page < requests.size - 1) {
                        scope.launch { pagerState.animateScrollToPage(page + 1) }
                    }
                },
            )
        }

        // Dots indicator
        DotsIndicator(
            totalDots = requests.size,
            currentPage = pagerState.currentPage,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
        )

        // Footer
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Pedido ${pagerState.currentPage + 1} de ${requests.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PrayerCard(
    request: RequestEntity,
    isPraying: Boolean,
    showCompletion: Boolean,
    timerSecondsLeft: Int,
    totalSeconds: Int,
    onStartPrayer: (minutes: Int) -> Unit,
    onStopPrayer: () -> Unit,
    onNextAfterCompletion: () -> Unit,
) {
    val view = LocalView.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f)
            .padding(vertical = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Box(Modifier.fillMaxSize()) {
            androidx.compose.animation.AnimatedVisibility(
                visible = showCompletion,
                enter = fadeIn(tween(400)),
                exit = fadeOut(tween(200)),
            ) {
                CompletionContent(onNextAfterCompletion)
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = !showCompletion,
                enter = fadeIn(),
                exit = fadeOut(tween(200)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Content area (scrollable)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        )
                        Spacer(Modifier.height(20.dp))
                        Text(
                            text = request.title.ifBlank { "Sin título" },
                            style = MaterialTheme.typography.headlineMedium,
                            textAlign = TextAlign.Center,
                            lineHeight = 36.sp
                        )
                        if (!request.body.isNullOrBlank()) {
                            Spacer(Modifier.height(20.dp))
                            Text(
                                text = request.body!!,
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Action area
                    AnimatedContent(
                        targetState = isPraying,
                        transitionSpec = {
                            fadeIn(tween(300)) + slideInVertically { it / 4 } togetherWith
                                fadeOut(tween(200)) + slideOutVertically { -it / 4 }
                        },
                        label = "prayer-action"
                    ) { praying ->
                        if (praying) {
                            TimerDisplay(
                                secondsLeft = timerSecondsLeft,
                                totalSeconds = totalSeconds,
                                onStop = onStopPrayer,
                            )
                        } else {
                            TimerSelector(
                                onSelect = { minutes ->
                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                    onStartPrayer(minutes)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimerSelector(onSelect: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Orar por esto",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(12.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TIMER_OPTIONS.forEach { minutes ->
                FilledTonalButton(
                    onClick = { onSelect(minutes) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.sizeIn(minWidth = 72.dp, minHeight = 48.dp),
                ) {
                    Text("${minutes} min")
                }
            }
        }
    }
}

@Composable
private fun TimerDisplay(
    secondsLeft: Int,
    totalSeconds: Int,
    onStop: () -> Unit,
) {
    val mins = secondsLeft / 60
    val secs = secondsLeft % 60
    val progress = if (totalSeconds > 0) (totalSeconds - secondsLeft).toFloat() / totalSeconds else 0f

    // Pulse animation for the timer text
    val pulseScale by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse-scale"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "%d:%02d".format(mins, secs),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Light,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.scale(pulseScale),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Orando en silencio…",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            fontStyle = FontStyle.Italic,
        )
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onStop) {
            Text("Terminar antes")
        }
    }
}

@Composable
private fun CompletionContent(onNext: () -> Unit) {
    val view = LocalView.current
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "completion-scale"
    )

    LaunchedEffect(Unit) {
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Default.Favorite,
            contentDescription = null,
            modifier = Modifier
                .size(64.dp)
                .scale(scale),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "Gracias por orar",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Tu oración fue escuchada.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onNext,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.sizeIn(minHeight = 48.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Orar por otro")
        }
    }
}

@Composable
private fun DotsIndicator(
    totalDots: Int,
    currentPage: Int,
    modifier: Modifier = Modifier,
) {
    if (totalDots <= 1) return

    val maxVisibleDots = 7
    val showDots = totalDots <= maxVisibleDots

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showDots) {
            repeat(totalDots) { index ->
                val isSelected = index == currentPage
                val size by animateDpAsState(
                    targetValue = if (isSelected) 10.dp else 6.dp,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "dot-size"
                )
                val color by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    animationSpec = tween(200),
                    label = "dot-color"
                )
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(size)
                        .clip(CircleShape)
                        .background(color)
                )
            }
        } else {
            // Fallback: text indicator for many items
            Text(
                "${currentPage + 1} / $totalDots",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
