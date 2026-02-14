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
import com.pr4y.app.data.auth.AuthRepository

@Composable
fun LoginScreen(
    authRepository: AuthRepository,
    onSuccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var isRegister by rememberSaveable { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var actionTrigger by remember { mutableStateOf(0) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(actionTrigger) {
        if (actionTrigger == 0) return@LaunchedEffect
        loading = true
        val result = if (isRegister) {
            authRepository.register(email.trim(), password)
        } else {
            authRepository.login(email.trim(), password)
        }
        loading = false
        result.fold(
            onSuccess = { onSuccess() },
            onFailure = {
                snackbar.showSnackbar("No pudimos entrar. Revisa tu conexión o tus datos.")
            },
        )
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
            Text(
                text = "PR4Y",
                style = androidx.compose.material3.MaterialTheme.typography.headlineLarge,
            )
            Spacer(Modifier.height(32.dp))
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Contraseña (mín. 8)") },
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
                    onClick = {
                        if (email.isNotBlank() && password.length >= 8) actionTrigger++
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (isRegister) "Registrarse" else "Entrar")
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { isRegister = !isRegister },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (isRegister) "Ya tengo cuenta" else "Crear cuenta")
                }
            }
        }
    }
}
