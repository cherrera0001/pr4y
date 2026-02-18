package com.pr4y.app.ui.viewmodel

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.pr4y.app.BuildConfig
import com.pr4y.app.data.auth.AuthRepository
import com.pr4y.app.util.Pr4yLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Tech Lead Note: Production-ready UI State for Login.
 * Standard: Minimal technical exposure. No SHA-1 or internal IDs in UI.
 */
sealed interface LoginUiState {
    data object Idle : LoginUiState
    data object Loading : LoginUiState
    data object Success : LoginUiState
    data class Error(val message: String) : LoginUiState
}

class LoginViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onGoogleSignIn(context: Context) {
        val serverClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID

        if (!isNetworkAvailable(context)) {
            _uiState.value = LoginUiState.Error("Sin conexión a internet. Revisa tu red.")
            return
        }

        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            // Primero sin nonce (mejor compatibilidad con MIUI/Xiaomi y otros); luego con nonce si falla
            if (!performAuth(context, serverClientId, useNonce = false)) {
                Pr4yLog.i("Login: Intento sin nonce falló, intentando con nonce")
                kotlinx.coroutines.delay(500)
                performAuth(context, serverClientId, useNonce = true)
            }
        }
    }

    private suspend fun performAuth(context: Context, serverClientId: String, useNonce: Boolean): Boolean {
        val credentialManager = CredentialManager.create(context)
        try {
            val builder = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(serverClientId)
                .setAutoSelectEnabled(false)

            if (useNonce) builder.setNonce(UUID.randomUUID().toString())

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(builder.build())
                .build()

            val result = credentialManager.getCredential(context, request)
            val credential = result.credential

            val idToken: String? = when (credential) {
                is GoogleIdTokenCredential -> credential.idToken
                is CustomCredential -> {
                    if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL ||
                        credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_SIWG_CREDENTIAL) {
                        try {
                            GoogleIdTokenCredential.createFrom(credential.data).idToken
                        } catch (e: Exception) {
                            Pr4yLog.e("Login: CustomCredential unwrap failed", e)
                            null
                        }
                    } else null
                }
                else -> null
            }
            if (idToken != null) {
                return authRepository.googleLogin(idToken).fold(
                    onSuccess = {
                        _uiState.value = LoginUiState.Success
                        true
                    },
                    onFailure = {
                        _uiState.value = LoginUiState.Error("Acceso denegado por el búnker.")
                        false
                    }
                )
            }
            return false
        } catch (e: NoCredentialException) {
            Pr4yLog.e("Login: NoCredentialException (Google no reconoce la app). Revisa SHA-1 en GCP.", e)
            if (useNonce) {
                _uiState.value = LoginUiState.Error(
                    "No se pudo iniciar sesión con Google. Comprueba que tengas una cuenta de Google en Ajustes del dispositivo. " +
                    "Si ya la tienes, puede que esta versión de la app no esté autorizada; pide una actualización al que te compartió la app."
                )
                return true
            }
            return false
        } catch (e: Exception) {
            Pr4yLog.e("Auth error", e)
            if (useNonce) {
                _uiState.value = LoginUiState.Error("Error al conectar con los servicios de Google.")
                return true
            }
            return false
        }
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun resetState() { _uiState.value = LoginUiState.Idle }
}
