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
import androidx.compose.material3.Text
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
            }
        )
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Desbloquear PR4Y")
            .setSubtitle("Usa tu seguridad biométrica para entrar rápido")
            .setNegativeButtonText("Usar clave")
            .build()
        biometricPrompt.authenticate(promptInfo)
    }

    LaunchedEffect(Unit) {
        if (hasWrappedDekOnServer != null) return@LaunchedEffect
        val res = api.getWrappedDek(bearer)
        hasWrappedDekOnServer = res.isSuccessful && res.body() != null
        
        if (hasWrappedDekOnServer == true && authRepository.isBiometricEnabled() && canAuthenticate) {
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
            if (hasWrapped) {
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
                    Text(
                        text = if (hasWrappedDekOnServer == true) "Desbloquear" else "Crea tu clave de privacidad",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    if (hasWrappedDekOnServer != true) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Esta clave protege tus datos con seguridad de grado bancario para que nadie más pueda leerlos.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(32.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = passphrase,
                            onValueChange = { passphrase = it },
                            label = { Text("Clave de privacidad") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.weight(1f),
                            enabled = !loading,
                        )
                        if (hasWrappedDekOnServer == true && canAuthenticate && authRepository.isBiometricEnabled()) {
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

                    Spacer(Modifier.height(32.dp))
                    
                    if (loading) {
                        CircularProgressIndicator()
                    } else {
                        Button(
                            onClick = { actionTrigger++ },
                            modifier = Modifier.fillMaxWidth().height(56.dp)
                        ) {
                            Text(if (hasWrappedDekOnServer == true) "Entrar" else "Crear clave y entrar")
                        }
                    }
                }
            }
        }
    }
}
