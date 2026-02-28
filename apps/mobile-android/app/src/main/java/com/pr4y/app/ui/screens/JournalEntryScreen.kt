package com.pr4y.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pr4y.app.crypto.DekManager
import com.pr4y.app.data.auth.AuthTokenStore
import com.pr4y.app.crypto.LocalCrypto
import com.pr4y.app.data.local.entity.JournalEntity
import com.pr4y.app.di.AppContainer
import com.pr4y.app.ui.components.Pr4yTopAppBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun JournalEntryScreen(navController: NavController, id: String) {
    val context = LocalContext.current
    val userId = remember(context) { AuthTokenStore(context.applicationContext).getUserId() ?: "" }
    var entry by remember { mutableStateOf<JournalEntity?>(null) }
    var decryptedContent by remember { mutableStateOf("") }
    val db = AppContainer.db

    LaunchedEffect(id, userId) {
        if (userId.isEmpty()) return@LaunchedEffect
        val loaded = withContext(Dispatchers.IO) { db.journalDao().getById(id, userId) }
        entry = loaded
        loaded?.let {
            val content = withContext(Dispatchers.Default) {
                val dek = DekManager.getDek()
                if (it.encryptedPayloadB64 != null && dek != null) {
                    try {
                        val plain = LocalCrypto.decrypt(it.encryptedPayloadB64, dek)
                        JSONObject(String(plain)).optString("content", "")
                    } catch (e: Exception) {
                        it.content
                    }
                } else {
                    it.content
                }
            }
            decryptedContent = content
        }
    }

    Scaffold(
        topBar = {
            Pr4yTopAppBar(
                title = "Entrada del diario",
                onBackClick = { navController.navigateUp() }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            entry?.let { e ->
                val dateStr = SimpleDateFormat("d 'de' MMMM, yyyy - HH:mm", Locale.getDefault())
                    .format(Date(e.updatedAt))
                
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = decryptedContent,
                    style = MaterialTheme.typography.bodyLarge
                )
            } ?: CircularProgressIndicator()
        }
    }
}
