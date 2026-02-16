package com.pr4y.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.CustomCredential
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.pr4y.app.BuildConfig
import com.pr4y.app.data.auth.AuthRepository
import com.pr4y.app.util.Pr4yLog
import androidx.credentials.exceptions.NoCredentialException
import kotlinx.coroutines.launch
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.content.Context

@Composable
fun LoginScreen(
    authRepository: AuthRepository,
    onSuccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var loading by remember { mutableStateOf(false) }
    
    val credentialManager = remember { CredentialManager.create(context) }
    val prefs = remember { context.getSharedPreferences("pr4y_login_cache", Context.MODE_PRIVATE) }
    
    // El backend REQUIERE el Web Client ID para validar tokens desde Android.
    val webId = BuildConfig.GOOGLE_WEB_CLIENT_ID
    val cachedWebId = prefs.getString("google_web_id", "") ?: ""
    
    val initialId = webId.ifBlank { cachedWebId }
    var serverClientId by remember { mutableStateOf(initialId) }
    var configLoading by remember { mutableStateOf(initialId.isBlank()) }

    LaunchedEffect(Unit) {
        Pr4yLog.i("LoginScreen: Iniciando flujo con ID: ${serverClientId.take(10)}...")
        
        scope.launch {
            authRepository.getPublicConfig()
                .onSuccess { config ->
                    val newWebId = config.googleWebClientId
                    if (newWebId.isNotBlank()) {
                        prefs.edit().putString("google_web_id", newWebId).apply()
                        serverClientId = newWebId
                        Pr4yLog.i("Login: Web ID actualizado desde API.")
                    }
                }
                .onFailure { e ->
                    Pr4yLog.e("Login: Error al refrescar config", e)
                }
            configLoading = false
        }
    }

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = Color(0xFF0A0A0A)
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Brush.verticalGradient(listOf(Color(0xFF1A1A1A), Color(0xFF0A0A0A)))),
            contentAlignment = Alignment.Center
        ) {
            if (loading) {
                ShimmerLoading()
            }

            AnimatedVisibility(
                visible = visible && !loading,
                enter = fadeIn(tween(1000)) + slideInVertically(initialOffsetY = { it / 2 }, animationSpec = tween(1000)),
                exit = fadeOut(tween(500))
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f),
                        modifier = Modifier.size(120.dp),
                        tonalElevation = 8.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Security, null, Modifier.size(60.dp), MaterialTheme.colorScheme.primary)
                        }
                    }

                    Spacer(Modifier.height(32.dp))

                    Text(
                        text = "PR4Y",
                        style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 4.sp),
                        color = Color.White
                    )

                    Text(text = "Tu búnker de oración privado", style = MaterialTheme.typography.bodyLarge, color = Color.Gray)

                    Spacer(Modifier.height(64.dp))

                    if (configLoading) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    } else {
                        Button(
                            onClick = {
                                if (serverClientId.isBlank()) {
                                    scope.launch { snackbar.showSnackbar("Configuración no disponible.") }
                                    return@Button
                                }
                                scope.launch {
                                    loading = true
                                    try {
                                        Pr4yLog.i("Login: Solicitando credencial a Google...")
                                        val googleIdOption = GetGoogleIdOption.Builder()
                                            .setFilterByAuthorizedAccounts(false)
                                            .setServerClientId(serverClientId)
                                            .setAutoSelectEnabled(false)
                                            .build()

                                        val request = GetCredentialRequest.Builder()
                                            .addCredentialOption(googleIdOption)
                                            .build()

                                        val result = credentialManager.getCredential(context, request)
                                        handleSignIn(result, authRepository, onSuccess, snackbar)
                                    } catch (e: NoCredentialException) {
                                        Pr4yLog.e("NoCredentialException: Usuario canceló o no hay cuentas.")
                                        snackbar.showSnackbar("Selecciona una cuenta de Google para continuar.")
                                    } catch (e: Exception) {
                                        Pr4yLog.e("Error en login: ${e.message}", e)
                                        snackbar.showSnackbar("Error al conectar con Google.")
                                    } finally {
                                        loading = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(28.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                        ) {
                            Text("Continuar con Google", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        }
                        
                        if (Build.MANUFACTURER.contains("Xiaomi", true)) {
                            TextButton(onClick = {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            }) {
                                Text("Ajustes de la app (Xiaomi fix)", color = Color.Gray, fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(Modifier.height(32.dp))
                    Text(
                        text = "Tus datos serán cifrados de extremo a extremo.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.DarkGray,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

private suspend fun handleSignIn(
    result: GetCredentialResponse,
    authRepository: AuthRepository,
    onSuccess: () -> Unit,
    snackbar: SnackbarHostState
) {
    val credential = result.credential
    val typeGoogleId = GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL

    // Reconocer por tipo (algunos dispositivos/Play Services devuelven un wrapper que no es CustomCredential).
    if (credential.type != typeGoogleId) {
        Pr4yLog.w("Tipo de credencial no soportado: ${credential.type}")
        snackbar.showSnackbar("El sistema de Google devolvió un formato no reconocido.")
        return
    }

    val idToken: String? = when (credential) {
        is GoogleIdTokenCredential -> credential.idToken
        else -> {
            val data = (credential as? CustomCredential)?.data
                ?: getCredentialDataViaReflection(credential)
            if (data != null) {
                try {
                    GoogleIdTokenCredential.createFrom(data).idToken
                } catch (e: GoogleIdTokenParsingException) {
                    Pr4yLog.e("Error al parsear el token de Google", e)
                    null
                }
            } else null
        }
    }

    if (idToken == null) {
        snackbar.showSnackbar("Error al procesar la cuenta de Google.")
        return
    }

    try {
        Pr4yLog.net("Token obtenido. Validando con búnker remoto...")
        val loginResult = authRepository.googleLogin(idToken)
        loginResult.fold(
            onSuccess = {
                Pr4yLog.i("¡Búnker desbloqueado con éxito!")
                onSuccess()
            },
            onFailure = { error ->
                Pr4yLog.e("El búnker rechazó el token: ${error.message}")
                snackbar.showSnackbar("Error de validación: ${error.message}")
            }
        )
    } catch (e: Exception) {
        Pr4yLog.e("Error en login con Google", e)
        snackbar.showSnackbar("Error al conectar con el servidor.")
    }
}

@Suppress("UNCHECKED_CAST")
private fun getCredentialDataViaReflection(credential: Any): android.os.Bundle? {
    return try {
        val field = credential.javaClass.getDeclaredField("data")
        field.isAccessible = true
        field.get(credential) as? android.os.Bundle
    } catch (e: Exception) {
        Pr4yLog.e("Reflection para data de credencial: ${e.message}")
        null
    }
}

@Composable
fun ShimmerLoading() {
    val shimmerColors = listOf(Color.DarkGray.copy(0.6f), Color.LightGray.copy(0.2f), Color.DarkGray.copy(0.6f))
    val transition = rememberInfiniteTransition(label = "")
    val translateAnim = transition.animateFloat(0f, 1000f, infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing)), label = "")
    val brush = Brush.linearGradient(shimmerColors, Offset.Zero, Offset(translateAnim.value, translateAnim.value))
    Column(
        modifier = Modifier.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(120.dp).clip(CircleShape).background(brush))
        Spacer(Modifier.height(32.dp))
        Box(Modifier.fillMaxWidth(0.5f).height(40.dp).clip(RoundedCornerShape(8.dp)).background(brush))
    }
}
