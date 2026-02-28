package com.pr4y.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pr4y.app.crypto.DekManager
import com.pr4y.app.crypto.LocalCrypto
import com.pr4y.app.data.auth.AuthRepository
import com.pr4y.app.data.auth.AuthTokenStore
import com.pr4y.app.data.local.entity.RequestEntity
import com.pr4y.app.data.remote.AnswerBody
import com.pr4y.app.data.remote.ApiService
import com.pr4y.app.di.AppContainer
import com.pr4y.app.ui.Routes
import com.pr4y.app.ui.components.Pr4yTopAppBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private val HopeGreen = Color(0xFF81C784)

@Composable
fun DetailScreen(
    navController: NavController,
    id: String,
    authRepository: AuthRepository,
    api: ApiService,
) {
    val context = LocalContext.current
    val userId = remember(context) { AuthTokenStore(context.applicationContext).getUserId() ?: "" }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var request by remember { mutableStateOf<RequestEntity?>(null) }
    var showAnsweredDialog by remember { mutableStateOf(false) }
    var testimony by remember { mutableStateOf("") }
    var markingAnswered by remember { mutableStateOf(false) }
    var alreadyAnswered by remember { mutableStateOf(false) }

    LaunchedEffect(id, userId) {
        if (id.isNotEmpty() && userId.isNotEmpty()) {
            request = withContext(Dispatchers.IO) {
                val entity = AppContainer.db.requestDao().getById(id, userId)
                val dek = DekManager.getDek()
                if (entity?.encryptedPayloadB64 != null && dek != null) {
                    try {
                        val plain = LocalCrypto.decrypt(entity.encryptedPayloadB64, dek)
                        val json = JSONObject(String(plain))
                        entity.copy(
                            title = json.optString("title", ""),
                            body = json.optString("body", ""),
                        )
                    } catch (e: Exception) {
                        entity
                    }
                } else {
                    entity
                }
            }
        }
    }

    if (showAnsweredDialog) {
        AlertDialog(
            onDismissRequest = { if (!markingAnswered) showAnsweredDialog = false },
            title = { Text("¡Oración respondida!") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "¿Quieres compartir cómo fue respondida? (opcional)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = testimony,
                        onValueChange = { if (it.length <= 2000) testimony = it },
                        placeholder = { Text("Testimonio (opcional)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 6,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                        ),
                        supportingText = { Text("${testimony.length}/2000") },
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            markingAnswered = true
                            try {
                                val bearer = authRepository.getBearer() ?: run {
                                    snackbar.showSnackbar("Sesión no disponible")
                                    return@launch
                                }
                                val res = api.createAnswer(
                                    bearer = bearer,
                                    recordId = id,
                                    body = AnswerBody(testimony = testimony.trim().ifBlank { null }),
                                )
                                if (res.isSuccessful) {
                                    alreadyAnswered = true
                                    showAnsweredDialog = false
                                    snackbar.showSnackbar("¡Victoria registrada!")
                                } else {
                                    snackbar.showSnackbar("No se pudo registrar. Intenta de nuevo.")
                                }
                            } catch (e: Exception) {
                                snackbar.showSnackbar("Error de conexión")
                            } finally {
                                markingAnswered = false
                            }
                        }
                    },
                    enabled = !markingAnswered,
                    colors = ButtonDefaults.buttonColors(containerColor = HopeGreen),
                ) {
                    if (markingAnswered) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text("Confirmar")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAnsweredDialog = false },
                    enabled = !markingAnswered,
                ) {
                    Text("Cancelar")
                }
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Pr4yTopAppBar(
                title = "Detalle",
                onBackClick = { navController.navigateUp() },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            request?.let { req ->
                Text(
                    text = req.title.ifBlank { "Sin título" },
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = req.body ?: "",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))

                TextButton(
                    onClick = { navController.navigate(Routes.newEdit(req.id)) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Editar")
                }

                Button(
                    onClick = { showAnsweredDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !alreadyAnswered,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (alreadyAnswered) MaterialTheme.colorScheme.surfaceVariant
                        else HopeGreen,
                    ),
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Text(if (alreadyAnswered) "Respondida ✓" else "Marcar como respondida")
                }
            } ?: Text("Cargando…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
