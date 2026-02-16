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
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.pr4y.app.BuildConfig
import com.pr4y.app.data.auth.AuthRepository
import com.pr4y.app.util.Pr4yLog
import androidx.credentials.exceptions.NoCredentialException
import kotlinx.coroutines.launch

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
    val serverClientId = remember { BuildConfig.GOOGLE_WEB_CLIENT_ID }

    // Traza de versión para depuración
    LaunchedEffect(Unit) {
        Pr4yLog.i("LoginScreen Cargada - Versión App: 1.2.4")
        Pr4yLog.d("Configured Server Client ID: ${serverClientId.take(10)}...")
    }

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = Color(0xFF0A0A0A)
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF1A1A1A), Color(0xFF0A0A0A))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            if (loading) {
                ShimmerLoading()
            }

            AnimatedVisibility(
                visible = visible && !loading,
                enter = fadeIn(tween(1000)) + slideInVertically(
                    initialOffsetY = { it / 2 },
                    animationSpec = tween(1000)
                ),
                exit = fadeOut(tween(500))
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f),
                        modifier = Modifier.size(120.dp),
                        tonalElevation = 8.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                modifier = Modifier.size(60.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(Modifier.height(32.dp))

                    Text(
                        text = "PR4Y",
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 4.sp
                        ),
                        color = Color.White
                    )

                    Text(
                        text = "Tu búnker de oración privado",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(64.dp))

                    Button(
                        onClick = {
                            if (serverClientId.isBlank()) {
                                scope.launch { snackbar.showSnackbar("Error: ID de Google no configurado.") }
                                return@Button
                            }
                            scope.launch {
                                loading = true
                                try {
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
                                    Pr4yLog.e("Google Auth: NoCredentialException | msg=${e.message} | cause=${e.cause}", e)
                                    snackbar.showSnackbar("No se encontró ninguna cuenta de Google activa.")
                                } catch (e: GetCredentialException) {
                                    Pr4yLog.e("Google Auth: GetCredentialException | type=${e.type} | msg=${e.message} | cause=${e.cause}", e)
                                    snackbar.showSnackbar("Error al conectar con Google.")
                                } catch (e: Exception) {
                                    Pr4yLog.e("Login inesperado: ${e.javaClass.simpleName} | msg=${e.message} | cause=${e.cause}", e)
                                    snackbar.showSnackbar("Ocurrió un fallo inesperado al entrar.")
                                } finally {
                                    loading = false
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        )
                    ) {
                        Text(
                            "Continuar con Google",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Spacer(Modifier.height(32.dp))

                    Text(
                        text = "Al continuar, aceptas que tus datos serán cifrados de extremo a extremo.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.DarkGray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
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
    if (credential is GoogleIdTokenCredential) {
        val idToken = credential.idToken
        Pr4yLog.net("Google Token validado localmente. Enviando a búnker remoto...")
        val loginResult = authRepository.googleLogin(idToken)
        loginResult.fold(
            onSuccess = { 
                Pr4yLog.i("Acceso al búnker verificado.")
                onSuccess() 
            },
            onFailure = { 
                Pr4yLog.e("El búnker rechazó el acceso", it)
                snackbar.showSnackbar("El servidor no pudo validar tu cuenta de Google.")
            }
        )
    }
}

@Composable
fun ShimmerLoading() {
    val shimmerColors = listOf(
        Color.DarkGray.copy(alpha = 0.6f),
        Color.LightGray.copy(alpha = 0.2f),
        Color.DarkGray.copy(alpha = 0.6f),
    )

    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslation"
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset.Zero,
        end = Offset(x = translateAnim.value, y = translateAnim.value)
    )

    Column(
        modifier = Modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(modifier = Modifier.size(120.dp).clip(CircleShape).background(brush))
        Spacer(Modifier.height(32.dp))
        Box(modifier = Modifier.fillMaxWidth(0.5f).height(40.dp).clip(RoundedCornerShape(8.dp)).background(brush))
        Spacer(Modifier.height(16.dp))
        Box(modifier = Modifier.fillMaxWidth(0.8f).height(24.dp).clip(RoundedCornerShape(8.dp)).background(brush))
    }
}
