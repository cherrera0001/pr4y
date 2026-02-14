package com.pr4y.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pr4y.app.crypto.DekManager
import com.pr4y.app.crypto.LocalCrypto
import com.pr4y.app.data.local.entity.RequestEntity
import com.pr4y.app.di.AppContainer
import com.pr4y.app.ui.Routes
import com.pr4y.app.ui.components.Pr4yTopAppBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
fun SearchScreen(navController: NavController) {
    var query by rememberSaveable { mutableStateOf("") }
    val allEntities by AppContainer.db.requestDao().getAll().collectAsState(initial = emptyList())
    var filteredRequests by remember { mutableStateOf<List<RequestEntity>>(emptyList()) }

    // Búsqueda Híbrida (E2EE Client-Side)
    LaunchedEffect(query, allEntities) {
        filteredRequests = withContext(Dispatchers.Default) {
            val dek = DekManager.getDek()
            val lowerQuery = query.trim().lowercase()
            
            val decrypted = allEntities.map { entity ->
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

            if (lowerQuery.isBlank()) {
                decrypted
            } else {
                decrypted.filter { 
                    it.title.lowercase().contains(lowerQuery) || 
                    (it.body?.lowercase()?.contains(lowerQuery) == true)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            Pr4yTopAppBar(
                title = "Buscar",
                onBackClick = { navController.navigateUp() },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Buscar oraciones...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Ej. Gratitud, Familia...") }
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            if (filteredRequests.isEmpty() && query.isNotBlank()) {
                Text(
                    text = "No se encontraron resultados para \"$query\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(
                    items = filteredRequests,
                    key = { it.id }
                ) { req ->
                    SearchRequestItem(
                        request = req,
                        onClick = { navController.navigate(Routes.detail(req.id)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchRequestItem(
    request: RequestEntity,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = request.title.ifBlank { "Sin título" },
                style = MaterialTheme.typography.titleMedium
            )
            if (!request.body.isNullOrBlank()) {
                Text(
                    text = request.body.take(100) + if (request.body.length > 100) "..." else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
