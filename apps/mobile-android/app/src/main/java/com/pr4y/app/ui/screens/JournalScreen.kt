package com.pr4y.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pr4y.app.data.local.entity.JournalEntity
import com.pr4y.app.di.AppContainer
import com.pr4y.app.ui.Routes
import com.pr4y.app.ui.components.Pr4yTopAppBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun JournalScreen(navController: NavController) {
    val entries by AppContainer.db.journalDao().getAll().collectAsState(initial = emptyList())

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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items = entries, key = { it.id }) { entry ->
                JournalItem(
                    entry = entry,
                    onClick = { navController.navigate(Routes.journalEntry(entry.id)) },
                )
            }
        }
    }
}

@Composable
private fun JournalItem(
    entry: JournalEntity,
    onClick: () -> Unit,
) {
    val dateStr = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())
        .format(Date(entry.updatedAt))
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
            text = entry.content.take(120) + if (entry.content.length > 120) "…" else "",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 3,
        )
    }
}
