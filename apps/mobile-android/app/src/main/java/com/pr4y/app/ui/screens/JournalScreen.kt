package com.pr4y.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pr4y.app.ui.components.Pr4yTopAppBar

@Composable
fun JournalScreen(navController: NavController) {
    Scaffold(
        topBar = {
            Pr4yTopAppBar(
                title = "Diario",
                onBackClick = { navController.navigateUp() },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Journal (diario de oración)")
            // [PENDIENTE] Lista desde Room (journal)
        }
    }
}
