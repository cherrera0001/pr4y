package com.pr4y.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pr4y.app.data.local.entity.JournalEntity
import com.pr4y.app.di.AppContainer
import com.pr4y.app.ui.components.Pr4yTopAppBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.UUID

@Composable
fun NewJournalScreen(navController: NavController) {
    var content by rememberSaveable { mutableStateOf("") }

    Scaffold(
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
                        runBlocking {
                            withContext(Dispatchers.IO) {
                                AppContainer.db.journalDao().insert(
                                    JournalEntity(
                                        id = id,
                                        content = content.trim(),
                                        createdAt = now,
                                        updatedAt = now,
                                        synced = false,
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
