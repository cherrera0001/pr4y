@file:Suppress("DEPRECATION")

package com.pr4y.app.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.pr4y.app.BuildConfig
import com.pr4y.app.data.auth.AuthRepository
import com.pr4y.app.util.Pr4yLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.util.UUID

/**
 * Tech Lead Note: Production-ready UI State for Login.
 */
sealed interface LoginUiState {
    data object Idle : LoginUiState
    data object Loading : LoginUiState
    data object Success : LoginUiState
    data class Error(val message: String) : LoginUiState
    /** Lanzar este intent para flujo legacy cuando Credential Manager no devuelve credenciales. */
    data class LaunchLegacyGoogleSignIn(val signInIntent: Intent) : LoginUiState
}

class LoginViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onGoogleSignIn(context: Context) {
        val serverClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID

        if (serverClientId.isNullOrBlank()) {
            _uiState.value = LoginUiState.Error("Error: Client ID de Google no configurado.")
            return
        }

        if (!isNetworkAvailable(context)) {
            _uiState.value = LoginUiState.Error("Sin conexión a internet.")
            return
        }

        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            if (!performAuth(context, serverClientId, useNonce = false)) {
                performAuth(context, serverClientId, useNonce = true)
            }
        }
    }

    private suspend fun performAuth(context: Context, serverClientId: String, useNonce: Boolean): Boolean {
        if (serverClientId.isBlank()) return false 

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
                        } catch (e: Exception) { null }
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
                        _uiState.value = LoginUiState.Error(it.message ?: "Acceso denegado por el búnker.")
                        false
                    }
                )
            }
            return false
        } catch (e: NoCredentialException) {
            if (useNonce) {
                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(serverClientId)
                    .requestEmail()
                    .build()
                val client = GoogleSignIn.getClient(context, gso)
                _uiState.value = LoginUiState.LaunchLegacyGoogleSignIn(client.signInIntent)
                return true
            }
            return false
        } catch (e: Exception) {
            if (useNonce) {
                _uiState.value = LoginUiState.Error("Error al conectar con Google.")
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

    fun handleLegacySignInResult(context: Context, data: Intent?) {
        viewModelScope.launch {
            if (data == null) {
                _uiState.value = LoginUiState.Error("Inicio de sesión cancelado.")
                return@launch
            }
            try {
                val account = GoogleSignIn.getSignedInAccountFromIntent(data).await()
                val idToken = account.idToken
                if (idToken.isNullOrBlank()) {
                    _uiState.value = LoginUiState.Error("No se pudo obtener el token de Google.")
                    return@launch
                }
                authRepository.googleLogin(idToken).fold(
                    onSuccess = { _uiState.value = LoginUiState.Success },
                    onFailure = { _uiState.value = LoginUiState.Error(it.message ?: "Acceso denegado.") }
                )
            } catch (e: ApiException) {
                val code = e.status.statusCode
                if (code == 10) {
                    val currentSha1 = getCertificateSHA1(context)
                    val pkg = context.packageName
                    Pr4yLog.e("Login: legacy ApiException 10 (DEVELOPER_ERROR). Registrar SHA-1 y package en GCP. Ver COMO-RESOLVER-LOGIN.md", e)
                    Pr4yLog.e("Package: $pkg | SHA-1: $currentSha1")
                    _uiState.value = LoginUiState.Error(
                        "Google no reconoce esta instalación. Añade en Google Cloud (Credenciales → cliente Android) esta huella SHA-1: $currentSha1 — Paquete: $pkg"
                    )
                } else {
                    _uiState.value = LoginUiState.Error("Error al conectar con Google (código $code).")
                }
            } catch (e: Exception) {
                _uiState.value = LoginUiState.Error("Error al conectar con Google (Legacy).")
            }
        }
    }

    private fun getCertificateSHA1(context: Context): String {
        return try {
            val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            }

            @Suppress("DEPRECATION")
            val signatures: Array<android.content.pm.Signature>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkgInfo.signingInfo?.apkContentsSigners
            } else {
                pkgInfo.signatures
            }

            val signature = signatures?.firstOrNull()
            if (signature != null) {
                val md = MessageDigest.getInstance("SHA-1")
                val digest = md.digest(signature.toByteArray())
                digest.joinToString(":") { String.format("%02X", it) }
            } else "SHA1-NO-ENCONTRADA"
        } catch (e: Exception) {
            "SHA1-ERROR: ${e.message}"
        }
    }

    fun onLegacySignInLaunched() { _uiState.value = LoginUiState.Loading }
    fun resetState() { _uiState.value = LoginUiState.Idle }
}
