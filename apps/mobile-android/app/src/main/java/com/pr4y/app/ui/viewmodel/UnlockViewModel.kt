package com.pr4y.app.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pr4y.app.crypto.DekManager
import com.pr4y.app.data.auth.AuthRepository
import com.pr4y.app.data.remote.ApiService
import com.pr4y.app.data.remote.KdfDto
import com.pr4y.app.data.remote.WrappedDekBody
import com.pr4y.app.data.remote.WrappedDekResponse
import com.pr4y.app.data.sync.SyncRepository
import com.pr4y.app.util.Pr4yLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Tech Lead Note: Managed UI State for Unlock.
 * Standard: Decoupled crypto logic, explicit states for security flows.
 */
sealed interface UnlockUiState {
    data object Loading : UnlockUiState
    data class SetupRequired(val canUseBiometrics: Boolean) : UnlockUiState
    data class Locked(val canUseBiometrics: Boolean, val biometricEnabled: Boolean) : UnlockUiState
    data object Unlocked : UnlockUiState
    data class Error(val message: String) : UnlockUiState
}

class UnlockViewModel(
    private val authRepository: AuthRepository,
    private val syncRepository: SyncRepository,
    private val api: ApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow<UnlockUiState>(UnlockUiState.Loading)
    val uiState: StateFlow<UnlockUiState> = _uiState.asStateFlow()

    private var isForgottenState = false

    fun checkStatus(canAuthenticate: Boolean) {
        viewModelScope.launch {
            _uiState.value = UnlockUiState.Loading
            val bearer = authRepository.getBearer() ?: ""
            try {
                val res = api.getWrappedDek(bearer)
                val hasWrapped = res.isSuccessful && res.body() != null
                
                if (hasWrapped && !isForgottenState) {
                    _uiState.value = UnlockUiState.Locked(
                        canUseBiometrics = canAuthenticate,
                        biometricEnabled = authRepository.isBiometricEnabled()
                    )
                } else {
                    _uiState.value = UnlockUiState.SetupRequired(canAuthenticate)
                }
            } catch (e: Exception) {
                _uiState.value = UnlockUiState.Error("Error de conexión con el búnker")
            }
        }
    }

    fun unlockWithPassphrase(passphrase: String, useBiometrics: Boolean, context: Context) {
        if (passphrase.length < 6) {
            _uiState.value = UnlockUiState.Error("La clave debe tener al menos 6 caracteres")
            return
        }

        viewModelScope.launch {
            _uiState.value = UnlockUiState.Loading
            try {
                val bearer = authRepository.getBearer() ?: ""
                
                if (isForgottenState || _uiState.value is UnlockUiState.SetupRequired) {
                    setupNewBunker(passphrase, useBiometrics, bearer, context)
                } else {
                    val res = api.getWrappedDek(bearer)
                    if (res.isSuccessful && res.body() != null) {
                        val body: WrappedDekResponse = res.body()!!
                        val kek = DekManager.deriveKek(passphrase.toCharArray(), body.kdf.saltB64)
                        val dek = DekManager.unwrapDek(body.wrappedDekB64, kek)
                        DekManager.setDek(dek)
                        
                        if (useBiometrics || authRepository.isBiometricEnabled()) {
                            authRepository.savePassphrase(passphrase)
                        }
                        finalizeUnlock(context)
                    } else {
                        _uiState.value = UnlockUiState.Error("No se pudo cargar la clave del servidor")
                    }
                }
            } catch (e: javax.crypto.AEADBadTagException) {
                _uiState.value = UnlockUiState.Error("La clave de privacidad es incorrecta")
            } catch (e: Exception) {
                Pr4yLog.e("UnlockViewModel: Decryption failed", e)
                _uiState.value = UnlockUiState.Error("Fallo al descifrar el búnker")
            }
        }
    }

    private suspend fun setupNewBunker(passphrase: String, useBiometrics: Boolean, bearer: String, context: Context) {
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
        
        if (putRes.isSuccessful) {
            DekManager.setDek(dek)
            if (useBiometrics) {
                authRepository.savePassphrase(passphrase)
            }
            isForgottenState = false
            finalizeUnlock(context)
        } else {
            _uiState.value = UnlockUiState.Error("No se pudo guardar la nueva clave")
        }
    }

    fun startFresh() {
        isForgottenState = true
        authRepository.clearPassphrase()
        _uiState.value = UnlockUiState.SetupRequired(true)
    }

    fun unlockWithBiometrics(context: Context) {
        val savedPass = authRepository.getPassphrase()
        if (savedPass != null) {
            unlockWithPassphrase(savedPass, true, context)
        } else {
            _uiState.value = UnlockUiState.Error("Biometría no configurada correctamente")
        }
    }

    private suspend fun finalizeUnlock(context: Context) {
        syncRepository.processJournalDraft(context)
        _uiState.value = UnlockUiState.Unlocked
    }

    fun resetError(canAuthenticate: Boolean) {
        checkStatus(canAuthenticate)
    }
}
