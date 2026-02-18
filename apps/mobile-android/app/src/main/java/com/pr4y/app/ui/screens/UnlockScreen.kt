package com.pr4y.app.ui.screens

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.pr4y.app.ui.viewmodel.UnlockUiState
import com.pr4y.app.ui.viewmodel.UnlockViewModel
import kotlinx.coroutines.delay

/**
 * Tech Lead Review: UnlockScreen Production Version.
 * Standards: Midnight Blue identity, Reactive state, Biometric first.
 */
@Composable
fun UnlockScreen(
    viewModel: UnlockViewModel,
    onUnlocked: () -> Unit,
    onSessionExpired: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var passphrase by rememberSaveable { mutableStateOf("") }
    var rememberWithBiometrics by remember { mutableStateOf(false) }
    var showPassphraseField by remember { mutableStateOf(false) }
    var showForgotConfirm by remember { mutableStateOf(false) }

    val biometricManager = remember { BiometricManager.from(context) }
    val canAuthenticate = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    val launchBiometrics = {
        val executor = ContextCompat.getMainExecutor(context)
        val biometricPrompt = BiometricPrompt(
            context as FragmentActivity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    viewModel.unlockWithBiometrics(context)
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        showPassphraseField = true
                    } else {
                        val humanMessage = when (errorCode) {
                            BiometricPrompt.ERROR_LOCKOUT,
                            BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> "Demasiados intentos. Usa tu clave o espera un momento."
                            BiometricPrompt.ERROR_HW_UNAVAILABLE,
                            BiometricPrompt.ERROR_NO_BIOMETRICS,
                            BiometricPrompt.ERROR_HW_NOT_PRESENT -> "El sensor no está disponible. Usa tu clave."
                            else -> "El búnker está ocupado protegiendo tus datos. Reintenta en un segundo."
                        }
                        snackbarHostState.showSnackbar(humanMessage)
                    }
                }
            }
        )
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Desbloquear Búnker")
            .setSubtitle("Toca el sensor para entrar")
            .setNegativeButtonText("Usar clave")
            .build()
        biometricPrompt.authenticate(promptInfo)
    }

    LaunchedEffect(Unit) {
        viewModel.checkStatus(canAuthenticate)
    }

    var showOfferBiometricDialog by remember { mutableStateOf(false) }
    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is UnlockUiState.Unlocked -> {
                if (state.offerBiometric) showOfferBiometricDialog = true
                else onUnlocked()
            }
            is UnlockUiState.SessionExpired -> onSessionExpired()
            is UnlockUiState.Error -> {
                snackbarHostState.showSnackbar(state.message)
            }
            is UnlockUiState.Locked -> {
                if (state.biometricEnabled && state.canUseBiometrics && !showPassphraseField) {
                    delay(300)
                    launchBiometrics()
                }
            }
            else -> {}
        }
    }

    if (showOfferBiometricDialog) {
        AlertDialog(
            onDismissRequest = { showOfferBiometricDialog = false; onUnlocked() },
            title = { Text("¿Prefieres entrar con tu huella la próxima vez?") },
            text = { Text("Es más rápido y igual de seguro.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.savePassphraseForBiometric()
                    showOfferBiometricDialog = false
                    onUnlocked()
                }) { Text("Sí, usar huella") }
            },
            dismissButton = {
                TextButton(onClick = { showOfferBiometricDialog = false; onUnlocked() }) { Text("No") }
            }
        )
    }

    if (showForgotConfirm) {
        AlertDialog(
            onDismissRequest = { showForgotConfirm = false },
            title = { Text("¿Restablecer Búnker?") },
            text = { Text("Perderás el acceso a los datos protegidos con la frase actual. ¿Deseas continuar?") },
            confirmButton = {
                TextButton(onClick = { 
                    showForgotConfirm = false
                    viewModel.startFresh()
                    showPassphraseField = true
                }) { Text("Empezar de cero", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showForgotConfirm = false }) { Text("Cancelar") }
            }
        )
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.background)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.padding(32.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Security Icon
                Box(
                    modifier = Modifier.size(100.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(Modifier.height(32.dp))

                Text(
                    text = when (uiState) {
                        is UnlockUiState.SetupRequired -> "Protege tu Búnker"
                        else -> "Acceso Privado"
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(16.dp))

                when (val state = uiState) {
                    is UnlockUiState.Loading -> CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    is UnlockUiState.SessionExpired -> {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Sesión expirada. Redirigiendo al inicio de sesión…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    else -> {
                        val isLocked = state is UnlockUiState.Locked
                        val isSetup = state is UnlockUiState.SetupRequired
                        
                        Text(
                            text = if (isSetup) "Crea una frase de seguridad para cifrar tus oraciones." 
                                   else "Introduce tu clave para entrar.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(Modifier.height(48.dp))

                        if (isLocked && state.biometricEnabled && !showPassphraseField) {
                            Button(
                                onClick = { launchBiometrics() },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = RoundedCornerShape(28.dp)
                            ) {
                                Icon(Icons.Default.Fingerprint, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Usar Biometría")
                            }
                            TextButton(onClick = { showPassphraseField = true }) {
                                Text("Prefiero usar mi clave", color = MaterialTheme.colorScheme.primary)
                            }
                        } else {
                            OutlinedTextField(
                                value = passphrase,
                                onValueChange = { passphrase = it },
                                label = { Text("Clave de Privacidad") },
                                modifier = Modifier.fillMaxWidth(),
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp)
                            )

                            if (isSetup && canAuthenticate) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                ) {
                                    Checkbox(checked = rememberWithBiometrics, onCheckedChange = { rememberWithBiometrics = it })
                                    Text("Activar acceso rápido con huella", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                                }
                            }

                            Spacer(Modifier.height(24.dp))

                            Button(
                                onClick = { viewModel.unlockWithPassphrase(passphrase, rememberWithBiometrics, context, canUseBiometrics = canAuthenticate) },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = RoundedCornerShape(28.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                            ) {
                                Text(if (isSetup) "Configurar y Entrar" else "Desbloquear", fontWeight = FontWeight.Bold)
                            }

                            if (isLocked) {
                                TextButton(onClick = { showForgotConfirm = true }) {
                                    Text("¿Olvidaste tu clave?", color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
