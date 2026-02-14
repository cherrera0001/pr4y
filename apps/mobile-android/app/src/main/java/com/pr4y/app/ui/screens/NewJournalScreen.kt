package com.pr4y.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pr4y.app.crypto.DekManager
import com.pr4y.app.crypto.LocalCrypto
import com.pr4y.app.data.local.JournalDraftStore
import com.pr4y.app.data.local.entity.JournalEntity
import com.pr4y.app.data.local.entity.OutboxEntity
import com.pr4y.app.data.sync.SyncRepository
import com.pr4y.app.di.AppContainer
import com.pr4y.app.ui.components.Pr4yTopAppBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

@Composable
fun NewJournalScreen(navController: NavController) {
    val context = LocalContext.current
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
        topBar = {
            Pr4yTopAppBar(
                title = "Nueva entrada",
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
            if (!dekAvailable) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    tonalElevation = 0.dp,
                ) {
                    Text(
                        text = "Se guardará como borrador. Desbloquea la app para proteger y sincronizar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp),
                    )
                }
                Spacer(Modifier.padding(vertical = 8.dp))
            }
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("Entrada del diario") },
                modifier = Modifier.fillMaxWidth().weight(1f),
                minLines = 5,
            )
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
                        runBlocking {
                            val encrypted = withContext(Dispatchers.Default) {
                                val payload = JSONObject().apply {
                                    put("content", trimmed)
                                    put("createdAt", now)
                                    put("updatedAt", now)
                                }.toString().toByteArray(Charsets.UTF_8)
                                LocalCrypto.encrypt(payload, dek)
                            }
                            withContext(Dispatchers.IO) {
                                AppContainer.db.journalDao().insert(
                                    JournalEntity(
                                        id = id,
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
                        }
                        navController.navigateUp()
                    }
                },
            ) {
                Text("Guardar")
            }
        }
    }
}
