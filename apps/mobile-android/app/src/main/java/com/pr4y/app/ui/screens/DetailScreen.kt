package com.pr4y.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
fun DetailScreen(navController: NavController, id: String) {
    var request by remember { mutableStateOf<RequestEntity?>(null) }

    LaunchedEffect(id) {
        if (id.isNotEmpty()) {
            request = withContext(Dispatchers.IO) {
                val entity = AppContainer.db.requestDao().getById(id)
                val dek = DekManager.getDek()
                if (entity?.encryptedPayloadB64 != null && dek != null) {
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
        }
    }

    Scaffold(
        topBar = {
            Pr4yTopAppBar(
                title = "Detalle",
                onBackClick = { navController.navigateUp() },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            request?.let { req ->
                Text(
                    text = req.title.ifBlank { "Sin título" },
                    style = MaterialTheme.typography.titleLarge
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = req.body ?: "",
                    style = MaterialTheme.typography.bodyLarge
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(vertical = 16.dp))
                TextButton(
                    onClick = { navController.navigate(Routes.newEdit(req.id)) },
                ) {
                    Text("Editar")
                }
            } ?: Text("Cargando…")
        }
    }
}
