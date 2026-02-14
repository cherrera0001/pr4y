package com.pr4y.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pr4y.app.data.local.entity.RequestEntity
import com.pr4y.app.di.AppContainer
import com.pr4y.app.ui.components.Pr4yTopAppBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DetailScreen(navController: NavController, id: String) {
    var request by remember { mutableStateOf<RequestEntity?>(null) }

    LaunchedEffect(id) {
        if (id.isNotEmpty()) {
            request = withContext(Dispatchers.IO) {
                AppContainer.db.requestDao().getById(id)
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
                Text(text = req.title.ifBlank { "Sin título" }, style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
                Text(text = req.body ?: "", style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                androidx.compose.material3.TextButton(
                    onClick = { navController.navigate(com.pr4y.app.ui.Routes.newEdit(req.id)) },
                ) { androidx.compose.material3.Text("Editar") }
            } ?: Text("Cargando…")
        }
    }
}
