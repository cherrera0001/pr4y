package com.pr4y.app.ui.screens

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.pr4y.app.crypto.DekManager
import com.pr4y.app.data.auth.AuthRepository
import com.pr4y.app.data.remote.KdfDto
import com.pr4y.app.data.remote.RetrofitClient
import com.pr4y.app.data.remote.WrappedDekBody
import com.pr4y.app.data.remote.WrappedDekResponse
import com.pr4y.app.data.sync.SyncRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun UnlockScreen(
    authRepository: AuthRepository,
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val bearer = authRepository.getBearer() ?: ""
    var passphrase by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var actionTrigger by remember { mutableStateOf(0) }
    var hasWrappedDekOnServer by remember { mutableStateOf<Boolean?>(null) }
    var rememberWithBiometrics by remember { mutableStateOf(false) }
    var biometricAutoShown by remember { mutableStateOf(false) }
    var showPassphraseField by remember { mutableStateOf(false) }
    var forgotPhraseStartFresh by remember { mutableStateOf(false) }
    var showForgotPhraseConfirm by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val api = remember { RetrofitClient.create(context) }
    val scope = rememberCoroutineScope()
    val syncRepository = remember { SyncRepository(authRepository, context) }

    val biometricManager = remember { BiometricManager.from(context) }
    val canAuthenticate = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    val showBiometricPrompt = {
        val executor = ContextCompat.getMainExecutor(context)
        val biometricPrompt = BiometricPrompt(
            context as FragmentActivity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val savedPass = authRepository.getPassphrase()
                    if (savedPass != null) {
                        passphrase = savedPass
                        actionTrigger++
                    }
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) showPassphraseField = true
                }
            }
        )
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Desbloquear PR4Y")
            .setSubtitle("Usa tu huella o rostro para entrar")
            .setNegativeButtonText("Usar clave")
            .build()
        biometricPrompt.authenticate(promptInfo)
    }

    LaunchedEffect(Unit) {
        if (hasWrappedDekOnServer != null) return@LaunchedEffect
        val res = api.getWrappedDek(bearer)
        hasWrappedDekOnServer = res.isSuccessful && res.body() != null

        if (hasWrappedDekOnServer == true && authRepository.isBiometricEnabled() && canAuthenticate && !biometricAutoShown) {
            biometricAutoShown = true
            delay(400) // Dejar que se pinte la pantalla antes del diálogo
            showBiometricPrompt()
        }
    }

    LaunchedEffect(actionTrigger) {
        if (actionTrigger == 0) return@LaunchedEffect
        val hasWrapped = hasWrappedDekOnServer ?: return@LaunchedEffect
        if (passphrase.length < 6) {
            snackbar.showSnackbar("La clave debe tener al menos 6 caracteres")
            return@LaunchedEffect
        }
        loading = true
        try {
            if (hasWrapped && !forgotPhraseStartFresh) {
                val res = api.getWrappedDek(bearer)
                if (!res.isSuccessful || res.body() == null) {
                    snackbar.showSnackbar("No pudimos cargar tu clave. Revisa tu conexión.")
                    loading = false
                    return@LaunchedEffect
                }
                val body: WrappedDekResponse = res.body()!!
                val kek = DekManager.deriveKek(passphrase.toCharArray(), body.kdf.saltB64)
                val dek = DekManager.unwrapDek(body.wrappedDekB64, kek)
                DekManager.setDek(dek)
                
                if (rememberWithBiometrics) {
                    authRepository.savePassphrase(passphrase)
                }
            } else {
                // Primera vez o "olvidé mi frase": crear nueva DEK y subir al servidor
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
                    snackbar.showSnackbar("No pudimos guardar tu clave de seguridad. Revisa tu conexión.")
                    loading = false
                    return@LaunchedEffect
                }
                DekManager.setDek(dek)
                if (rememberWithBiometrics) {
                    authRepository.savePassphrase(passphrase)
                }
            }
            
            // Procesar borradores tras desbloqueo exitoso
            val processed = syncRepository.processJournalDraft(context)
            if (processed) {
                scope.launch {
                    snackbar.showSnackbar("Tus notas pendientes han sido protegidas y están listas para sincronizar.")
                }
            }
            onUnlocked()
        } catch (e: android.security.keystore.KeyPermanentlyInvalidatedException) {
            snackbar.showSnackbar("La seguridad de tu dispositivo ha cambiado. Introduce tu clave de privacidad manualmente para re-activar el acceso rápido.")
        } catch (e: Exception) {
            snackbar.showSnackbar("No pudimos entrar. Revisa tu clave de privacidad.")
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
                    val biometricPreferred = hasWrappedDekOnServer == true && canAuthenticate && authRepository.isBiometricEnabled()
                    val useBiometricFlow = biometricPreferred && !showPassphraseField

                    Text(
                        text = when {
                            forgotPhraseStartFresh -> "Nueva frase de recuperación"
                            hasWrappedDekOnServer == true -> "Desbloquear"
                            else -> "Protege tus datos"
                        },
                        style = MaterialTheme.typography.headlineSmall
                    )
                    if (useBiometricFlow) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Toca el botón para usar tu huella o rostro.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (forgotPhraseStartFresh) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Crea una nueva frase. Los datos protegidos con la anterior no se podrán leer.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (hasWrappedDekOnServer == true && canAuthenticate && authRepository.isBiometricEnabled()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Escribe tu clave de privacidad si no puedes usar la huella.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (hasWrappedDekOnServer != true && !forgotPhraseStartFresh) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Crea una frase de recuperación (solo la necesitas una vez y para otro dispositivo). Aquí podrás usar solo la huella después.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(32.dp))

                    if (useBiometricFlow) {
                        Button(
                            onClick = { showBiometricPrompt() },
                            modifier = Modifier.fillMaxWidth().height(56.dp)
                        ) {
                            Icon(Icons.Default.Fingerprint, contentDescription = null, Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("Desbloquear con huella")
                        }
                        Spacer(Modifier.height(16.dp))
                        TextButton(onClick = { showPassphraseField = true }) {
                            Text("¿No puedes usar huella? Usar clave de privacidad")
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = passphrase,
                                onValueChange = { passphrase = it },
                                label = {
                                    Text(
                                        when {
                                            forgotPhraseStartFresh -> "Nueva frase de recuperación (mín. 6 caracteres)"
                                            hasWrappedDekOnServer == true -> "Clave de privacidad"
                                            else -> "Frase de recuperación (mín. 6 caracteres)"
                                        }
                                    )
                                },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                modifier = Modifier.weight(1f),
                                enabled = !loading,
                            )
                            if (biometricPreferred) {
                                Spacer(Modifier.width(8.dp))
                                IconButton(onClick = { showBiometricPrompt() }) {
                                    Icon(Icons.Default.Fingerprint, contentDescription = "Usar huella o rostro")
                                }
                            }
                        }

                        if (!authRepository.isBiometricEnabled() && canAuthenticate) {
                            Row(
                                Modifier.fillMaxWidth().padding(top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = rememberWithBiometrics,
                                    onCheckedChange = { rememberWithBiometrics = it }
                                )
                                Text("Usar seguridad biométrica para entrar más rápido", style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        if (hasWrappedDekOnServer == true && !forgotPhraseStartFresh) {
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = { showForgotPhraseConfirm = true }) {
                                Text("¿Olvidaste tu frase? Empezar de cero con una nueva")
                            }
                        }

                        Spacer(Modifier.height(32.dp))

                        if (loading) {
                            CircularProgressIndicator()
                        } else {
                            Button(
                                onClick = { actionTrigger++ },
                                modifier = Modifier.fillMaxWidth().height(56.dp)
                            ) {
                                Text(
                                    when {
                                        forgotPhraseStartFresh -> "Crear nueva frase y entrar"
                                        hasWrappedDekOnServer == true -> "Entrar"
                                        else -> "Continuar"
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showForgotPhraseConfirm) {
        AlertDialog(
            onDismissRequest = { showForgotPhraseConfirm = false },
            title = { Text("¿Empezar de cero?") },
            text = {
                Text(
                    "No podemos recuperar la frase anterior. Si creas una nueva frase, podrás entrar de nuevo y usar la huella en este dispositivo, pero los datos que tenías protegidos con la frase antigua no se podrán leer."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showForgotPhraseConfirm = false
                        forgotPhraseStartFresh = true
                        showPassphraseField = true
                        authRepository.clearPassphrase()
                    }
                ) {
                    Text("Sí, empezar de cero")
                }
            },
            dismissButton = {
                TextButton(onClick = { showForgotPhraseConfirm = false }) {
                    Text("Cancelar")
                }
            },
        )
    }
}
