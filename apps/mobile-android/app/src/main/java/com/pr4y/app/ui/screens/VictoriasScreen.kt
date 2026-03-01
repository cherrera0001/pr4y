package com.pr4y.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pr4y.app.data.remote.AnswerDto
import com.pr4y.app.ui.components.Pr4yTopAppBar
import com.pr4y.app.ui.viewmodel.VictoriasUiState
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

private val HopeGreen = Color(0xFF81C784)
private val SoftGold = Color(0xFFD4A574)

private val dateFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

private fun formatIsoDate(iso: String): String = try {
    val instant = Instant.parse(iso)
    dateFormat.format(instant.atZone(java.time.ZoneId.systemDefault()))
} catch (_: Exception) {
    iso.take(10)
}

@Composable
fun VictoriasScreen(navController: NavController, viewModel: com.pr4y.app.ui.viewmodel.VictoriasViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            Pr4yTopAppBar(
                title = "Mis Victorias",
                onBackClick = { navController.navigateUp() },
            )
        },
    ) { padding ->
        when (val state = uiState) {
            is VictoriasUiState.Loading -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            is VictoriasUiState.Error -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
            is VictoriasUiState.Success -> {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                ) {
                    Text(
                        text = "${state.answeredCount} oración(es) respondida(s)",
                        style = MaterialTheme.typography.titleMedium,
                        color = HopeGreen,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                    if (state.answers.isEmpty()) {
                        Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Cuando marques un pedido como respondido, aparecerá aquí.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(24.dp),
                            )
                        }
                    } else {
                        val sortedAnswers = state.answers.sortedByDescending { it.answeredAt }
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(sortedAnswers, key = { it.id }) { answer ->
                                VictoriaCard(answer = answer)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VictoriaCard(answer: AnswerDto) {
    val requestedStr = answer.record?.clientUpdatedAt?.let { formatIsoDate(it) } ?: "—"
    val answeredStr = formatIsoDate(answer.answeredAt)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors().copy(
            containerColor = SoftGold.copy(alpha = 0.15f),
        ),
        shape = CardDefaults.outlinedShape,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Pedido $requestedStr",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
                )
                Icon(
                    Icons.Default.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = HopeGreen,
                )
                Text(
                    text = "Respondido $answeredStr",
                    style = MaterialTheme.typography.labelMedium,
                    color = SoftGold,
                )
            }
            if (!answer.testimony.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = answer.testimony,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
