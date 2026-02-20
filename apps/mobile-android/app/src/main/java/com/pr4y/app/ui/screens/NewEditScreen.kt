package com.pr4y.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Brush
import com.pr4y.app.ui.theme.ElectricCyan
import com.pr4y.app.ui.theme.MidnightBlue
import androidx.navigation.NavController
import com.pr4y.app.crypto.DekManager
import com.pr4y.app.crypto.LocalCrypto
import com.pr4y.app.data.auth.AuthTokenStore
import com.pr4y.app.data.local.entity.OutboxEntity
import com.pr4y.app.data.local.entity.RequestEntity
import com.pr4y.app.data.sync.SyncRepository
import com.pr4y.app.di.AppContainer
import com.pr4y.app.ui.components.Pr4yTopAppBar
import com.pr4y.app.util.InputSanitizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID
import kotlin.math.roundToInt

private const val SWIPE_THRESHOLD_FRACTION = 0.7f

@Composable
fun NewEditScreen(
    navController: NavController,
    requestId: String?,
) {
    val context = LocalContext.current
    val userId = remember(context) { AuthTokenStore(context.applicationContext).getUserId() ?: "" }
    var title by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var body by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var isDelivered by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val db = AppContainer.db
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(requestId, userId) {
        if (requestId != null && userId.isNotEmpty()) {
            val req = withContext(Dispatchers.IO) { db.requestDao().getById(requestId, userId) }
            req?.let {
                val dek = DekManager.getDek()
                if (it.encryptedPayloadB64 != null && dek != null) {
                    try {
                        val plain = LocalCrypto.decrypt(it.encryptedPayloadB64, dek)
                        val json = JSONObject(String(plain))
                        title = json.optString("title", "")
                        body = json.optString("body", "")
                    } catch (e: Exception) {
                        title = it.title
                        body = it.body ?: ""
                    }
                } else {
                    title = it.title
                    body = it.body ?: ""
                }
            }
        }
    }

    fun performSave() {
        if (body.isBlank()) return
        scope.launch {
            val id = requestId ?: UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val dek = DekManager.getDek()
            if (dek == null) {
                snackbar.showSnackbar("Desbloquea la app para guardar con cifrado.")
                return@launch
            }
            val (cleanTitle, titleHadDangerous) = InputSanitizer.sanitizeTitleWithDetection(title)
            val (cleanBody, bodyHadDangerous) = InputSanitizer.sanitizeBodyWithDetection(body)
            if (titleHadDangerous || bodyHadDangerous) {
                snackbar.showSnackbar("Mantenemos tu búnker limpio de cualquier código externo para que solo tus palabras existan aquí.")
            }
            withContext(Dispatchers.IO) {
                val payload = JSONObject().apply {
                    put("title", cleanTitle)
                    put("body", cleanBody)
                }.toString().toByteArray(Charsets.UTF_8)
                val encrypted = LocalCrypto.encrypt(payload, dek)
                db.requestDao().insert(
                    RequestEntity(
                        id = id,
                        userId = userId,
                        title = "",
                        body = "",
                        createdAt = now,
                        updatedAt = now,
                        synced = false,
                        encryptedPayloadB64 = encrypted
                    ),
                )
                db.outboxDao().insert(
                    OutboxEntity(
                        recordId = id,
                        type = SyncRepository.TYPE_PRAYER_REQUEST,
                        version = 1,
                        encryptedPayloadB64 = encrypted,
                        clientUpdatedAt = now,
                        createdAt = now,
                    ),
                )
            }
            isDelivered = true
            delay(400)
            navController.navigateUp()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Pr4yTopAppBar(
                title = if (requestId == null) "Nuevo pedido" else "Editar pedido",
                onBackClick = { navController.navigateUp() },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            AnimatedVisibility(
                visible = !isDelivered,
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(animationSpec = tween(300)),
            ) {
                Column(Modifier.fillMaxSize()) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Título (opcional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = body,
                        onValueChange = { body = it },
                        label = { Text("Pedido de oración") },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        minLines = 4,
                    )
                    Spacer(Modifier.height(24.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(28.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        MidnightBlue,
                                        ElectricCyan.copy(alpha = 0.12f),
                                    )
                                )
                            )
                            .padding(2.dp),
                    ) {
                            SwipeToDeliver(
                            onTrigger = { performSave() },
                            enabled = body.isNotBlank(),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    TextButton(
                        onClick = { performSave() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Entregar")
                    }
                }
            }
        }
    }
}

@Composable
private fun SwipeToDeliver(
    onTrigger: () -> Unit,
    enabled: Boolean,
) {
    var maxWidthPx by remember { mutableFloatStateOf(0f) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var hasTriggered by remember { mutableStateOf(false) }
    val animatedOffset by animateFloatAsState(targetValue = dragOffset, label = "swipe")
    val thresholdPx = maxWidthPx * SWIPE_THRESHOLD_FRACTION
    if (thresholdPx > 0f && dragOffset >= thresholdPx && enabled && !hasTriggered) {
        hasTriggered = true
        LaunchedEffect(Unit) { onTrigger() }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragEnd = { if (!hasTriggered) dragOffset = 0f },
                    onHorizontalDrag = { _, dragAmount ->
                        if (!hasTriggered) dragOffset = (dragOffset + dragAmount).coerceIn(0f, maxWidthPx)
                    },
                )
            }
            .onGloballyPositioned { maxWidthPx = it.size.width.toFloat() },
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = "Desliza para entregar",
            modifier = Modifier.padding(horizontal = 20.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        )
        Box(
            modifier = Modifier
                .offset { IntOffset(animatedOffset.roundToInt(), 0) }
                .size(48.dp)
                .padding(4.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Deslizar para entregar",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
