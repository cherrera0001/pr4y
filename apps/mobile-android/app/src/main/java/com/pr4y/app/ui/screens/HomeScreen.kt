package com.pr4y.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pr4y.app.data.local.entity.RequestEntity
import com.pr4y.app.di.AppContainer
import com.pr4y.app.ui.Routes
import com.pr4y.app.ui.components.Pr4yTopAppBar

@Composable
fun HomeScreen(navController: NavController) {
    val requests by AppContainer.db.requestDao().getAll().collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            Pr4yTopAppBar(
                title = "Pedidos",
                onNavIconClick = { navController.navigate(Routes.SETTINGS) },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate(Routes.NEW_EDIT) }) {
                Icon(Icons.Default.Add, contentDescription = "Nuevo")
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                androidx.compose.material3.TextButton(
                    onClick = { navController.navigate(Routes.JOURNAL) },
                ) {
                    Icon(Icons.Default.MenuBook, contentDescription = null)
                    Text("Diario")
                }
                androidx.compose.material3.TextButton(
                    onClick = { navController.navigate(Routes.SEARCH) },
                ) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Text("Buscar")
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(
                    items = requests,
                    key = { it.id },
                ) { req ->
                    RequestItem(
                        request = req,
                        onClick = { navController.navigate(Routes.detail(req.id)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RequestItem(
    request: RequestEntity,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = request.title.ifBlank { "Sin título" },
                style = MaterialTheme.typography.titleSmall,
            )
            if (request.body?.isNotBlank() == true) {
                Text(
                    text = request.body.take(80) + if (request.body.length > 80) "…" else "",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                )
            }
        }
    }
}
