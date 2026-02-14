package com.pr4y.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pr4y.app.crypto.DekManager
import com.pr4y.app.crypto.LocalCrypto
import com.pr4y.app.di.AppContainer
import com.pr4y.app.ui.Routes
import com.pr4y.app.ui.components.Pr4yTopAppBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Modelo para mostrar en lista: contenido ya descifrado. */
private data class JournalDisplay(val id: String, val content: String, val updatedAt: Long)

@Composable
fun JournalScreen(navController: NavController) {
    val entities by AppContainer.db.journalDao().getAll().collectAsState(initial = emptyList())
    var entries by remember { mutableStateOf<List<JournalDisplay>>(emptyList()) }

    LaunchedEffect(entities) {
        entries = withContext(Dispatchers.Default) {
            val dek = DekManager.getDek()
            entities.map { entity ->
                val content = when {
                    entity.encryptedPayloadB64 != null && dek != null -> {
                        try {
                            val plain = LocalCrypto.decrypt(entity.encryptedPayloadB64, dek)
                            JSONObject(String(plain)).optString("content", "")
                        } catch (_: Exception) { "" }
                    }
                    else -> entity.content
                }
                JournalDisplay(entity.id, content, entity.updatedAt)
            }
        }
    }

    Scaffold(
        topBar = {
            Pr4yTopAppBar(
                title = "Diario",
                onBackClick = { navController.navigateUp() },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate(Routes.NEW_JOURNAL) }) {
                Icon(Icons.Default.Add, contentDescription = "Nueva entrada")
            }
        },
    ) { padding ->
        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = "Tu diario está vacío",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Escribe tu primera reflexión o gratitud.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = { navController.navigate(Routes.NEW_JOURNAL) }) {
                        Text("Nueva entrada")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items = entries, key = { it.id }) { display ->
                    JournalItem(
                        id = display.id,
                        content = display.content,
                        updatedAt = display.updatedAt,
                        onClick = { navController.navigate(Routes.journalEntry(display.id)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun JournalItem(
    id: String,
    content: String,
    updatedAt: Long,
    onClick: () -> Unit,
) {
    val dateStr = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())
        .format(Date(updatedAt))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Text(
            text = dateStr,
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            text = content.take(120) + if (content.length > 120) "…" else "",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 3,
        )
    }
}
