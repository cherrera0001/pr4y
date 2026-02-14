package com.pr4y.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.pr4y.app.crypto.DekManager
import com.pr4y.app.data.remote.KdfDto
import com.pr4y.app.data.remote.RetrofitClient
import com.pr4y.app.data.remote.WrappedDekBody
import com.pr4y.app.data.remote.WrappedDekResponse

@Composable
fun UnlockScreen(
    bearer: String,
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var passphrase by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var actionTrigger by remember { mutableStateOf(0) }
    var hasWrappedDekOnServer by remember { mutableStateOf<Boolean?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val api = remember { RetrofitClient.create() }

    LaunchedEffect(Unit) {
        if (hasWrappedDekOnServer != null) return@LaunchedEffect
        val res = api.getWrappedDek(bearer)
        hasWrappedDekOnServer = res.isSuccessful && res.body() != null
    }

    LaunchedEffect(actionTrigger) {
        if (actionTrigger == 0) return@LaunchedEffect
        val hasWrapped = hasWrappedDekOnServer ?: return@LaunchedEffect
        if (passphrase.length < 6) {
            snackbar.showSnackbar("Passphrase al menos 6 caracteres")
            return@LaunchedEffect
        }
        loading = true
        try {
            if (hasWrapped) {
                val res = api.getWrappedDek(bearer)
                if (!res.isSuccessful || res.body() == null) {
                    snackbar.showSnackbar("No se pudo obtener DEK")
                    loading = false
                    return@LaunchedEffect
                }
                val body: WrappedDekResponse = res.body()!!
                val kek = DekManager.deriveKek(passphrase.toCharArray(), body.kdf.saltB64)
                val dek = DekManager.unwrapDek(body.wrappedDekB64, kek)
                DekManager.setDek(dek)
            } else {
                val saltB64 = DekManager.generateSaltB64()
                val dek = DekManager.generateDek()
                val kek = DekManager.deriveKek(passphrase.toCharArray(), saltB64)
                val wrappedB64 = DekManager.wrapDek(dek, kek)
                val putRes = api.putWrappedDek(
                    bearer,
                    WrappedDekBody(
                        kdf = KdfDto("pbkdf2", mapOf("iterations" to 120000), saltB64),
                        wrappedDekB64 = wrappedB64,
                    ),
                )
                if (!putRes.isSuccessful) {
                    snackbar.showSnackbar("No se pudo guardar DEK")
                    loading = false
                    return@LaunchedEffect
                }
                DekManager.setDek(dek)
            }
            onUnlocked()
        } catch (e: Exception) {
            snackbar.showSnackbar(e.message ?: "Error")
        } finally {
            loading = false
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(24.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (hasWrappedDekOnServer) {
                null -> CircularProgressIndicator()
                else -> {
                    Text(
                        text = if (hasWrappedDekOnServer == true) "Desbloquear" else "Crear passphrase",
                        style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                    )
                    Spacer(Modifier.height(24.dp))
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = { Text(if (hasWrappedDekOnServer == true) "Passphrase" else "Nueva passphrase (mín. 6)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !loading,
                    )
                    Spacer(Modifier.height(24.dp))
                    if (loading) {
                        CircularProgressIndicator()
                    } else {
                        Button(
                            onClick = { actionTrigger++ },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (hasWrappedDekOnServer == true) "Entrar" else "Crear y continuar")
                        }
                    }
                }
            }
        }
    }
}
