package com.pr4y.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.pr4y.app.crypto.DekManager
import com.pr4y.app.ui.theme.ElectricCyan
import com.pr4y.app.crypto.LocalCrypto
import com.pr4y.app.data.auth.AuthTokenStore
import com.pr4y.app.data.local.JournalDraftStore
import com.pr4y.app.data.local.entity.JournalEntity
import com.pr4y.app.data.local.entity.OutboxEntity
import com.pr4y.app.data.sync.SyncRepository
import com.pr4y.app.di.AppContainer
import com.pr4y.app.ui.theme.MidnightBlue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

private val JournalPlaceholder = "Cuéntale a Dios cómo estuvo tu día…"

@Composable
fun NewJournalScreen(navController: NavController) {
    val context = LocalContext.current
    val userId = remember(context) { AuthTokenStore(context.applicationContext).getUserId() ?: "" }
    var content by rememberSaveable { mutableStateOf("") }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val dekAvailable = DekManager.getDek() != null

    LaunchedEffect(Unit) {
        JournalDraftStore.getDraft(context)?.let { draft ->
            content = draft
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MidnightBlue,
        topBar = {
            Surface(color = MidnightBlue) {
                IconButton(
                    onClick = { navController.navigateUp() },
                    modifier = Modifier.statusBarsPadding()
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Atrás",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .background(MidnightBlue)
                .padding(padding)
                .padding(horizontal = 20.dp),
        ) {
            if (!dekAvailable) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    tonalElevation = 0.dp,
                ) {
                    Text(
                        text = "Tus palabras se guardan aquí de forma temporal. Al desbloquear la app se protegerán y sincronizarán.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp),
                    )
                }
                Spacer(Modifier.padding(vertical = 8.dp))
            }
            Spacer(Modifier.padding(vertical = 8.dp))
            BasicTextField(
                value = content,
                onValueChange = { content = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = FontFamily.Serif,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { inner ->
                    if (content.isEmpty()) {
                        Text(
                            text = JournalPlaceholder,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontFamily = FontFamily.Serif,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            ),
                        )
                    }
                    inner()
                },
            )
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MidnightBlue,
                                ElectricCyan.copy(alpha = 0.1f),
                            )
                        )
                    )
                    .padding(8.dp),
            ) {
                TextButton(
                    onClick = {
                    if (content.isNotBlank()) {
                        val now = System.currentTimeMillis()
                        val id = UUID.randomUUID().toString()
                        val trimmed = content.trim()
                        val dek = DekManager.getDek()
                        if (dek == null) {
                            JournalDraftStore.saveDraft(context, trimmed)
                            scope.launch {
                                snackbar.showSnackbar("Guardado como borrador. Desbloquea la app para proteger y sincronizar.")
                            }
                            navController.navigateUp()
                            return@TextButton
                        }
                        val (cleanContent, hadDangerous) = com.pr4y.app.util.InputSanitizer.sanitizeBodyWithDetection(trimmed)
                        scope.launch {
                            val encrypted = withContext(Dispatchers.Default) {
                                val payload = JSONObject().apply {
                                    put("content", cleanContent)
                                    put("createdAt", now)
                                    put("updatedAt", now)
                                }.toString().toByteArray(Charsets.UTF_8)
                                LocalCrypto.encrypt(payload, dek)
                            }
                            withContext(Dispatchers.IO) {
                                AppContainer.db.journalDao().insert(
                                    JournalEntity(
                                        id = id,
                                        userId = userId,
                                        content = "",
                                        createdAt = now,
                                        updatedAt = now,
                                        synced = false,
                                        encryptedPayloadB64 = encrypted,
                                    ),
                                )
                                AppContainer.db.outboxDao().insert(
                                    OutboxEntity(
                                        recordId = id,
                                        type = SyncRepository.TYPE_JOURNAL_ENTRY,
                                        version = 1,
                                        encryptedPayloadB64 = encrypted,
                                        clientUpdatedAt = now,
                                        createdAt = now,
                                    ),
                                )
                                JournalDraftStore.clearDraft(context)
                            }
                            if (hadDangerous) {
                                snackbar.showSnackbar("Mantenemos tu búnker limpio de cualquier código externo para que solo tus palabras existan aquí.")
                            }
                            navController.navigateUp()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Guardar")
            }
            }
        }
    }
}
