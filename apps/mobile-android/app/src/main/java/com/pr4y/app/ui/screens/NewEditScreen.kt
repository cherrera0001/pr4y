package com.pr4y.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pr4y.app.crypto.DekManager
import com.pr4y.app.crypto.LocalCrypto
import com.pr4y.app.data.local.entity.OutboxEntity
import com.pr4y.app.data.local.entity.RequestEntity
import com.pr4y.app.data.sync.SyncRepository
import com.pr4y.app.di.AppContainer
import com.pr4y.app.ui.components.Pr4yTopAppBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

@Composable
fun NewEditScreen(
    navController: NavController,
    requestId: String?,
) {
    var title by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf("") }
    var body by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(requestId) {
        if (requestId != null) {
            val req = withContext(Dispatchers.IO) { db.requestDao().getById(requestId) }
            req?.let {
                title = it.title
                body = it.body ?: ""
            }
        }
    }
    val snackbar = remember { SnackbarHostState() }
    val db = AppContainer.db

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
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Título (opcional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("Pedido de oración") },
                modifier = Modifier.fillMaxWidth().weight(1f),
                minLines = 4,
            )
            TextButton(
                onClick = {
                    if (body.isBlank()) return@TextButton
                    scope.launch {
                        val id = requestId ?: UUID.randomUUID().toString()
                        val now = System.currentTimeMillis()
                        val dek = DekManager.getDek()
                        withContext(Dispatchers.IO) {
                            db.requestDao().insert(
                                RequestEntity(
                                    id = id,
                                    title = title,
                                    body = body,
                                    createdAt = now,
                                    updatedAt = now,
                                    synced = false,
                                ),
                            )
                            if (dek != null) {
                                val payload = JSONObject().apply {
                                    put("title", title)
                                    put("body", body)
                                }.toString().toByteArray(Charsets.UTF_8)
                                val encrypted = LocalCrypto.encrypt(payload, dek)
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
