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
import retrofit2.Response

/**
 * Tech Lead Note: Managed UI State for Unlock.
 * Standard: Decoupled crypto logic, explicit states for security flows.
 */
sealed interface UnlockUiState {
    data object Loading : UnlockUiState
    data class SetupRequired(val canUseBiometrics: Boolean) : UnlockUiState
    data class Locked(val canUseBiometrics: Boolean, val biometricEnabled: Boolean) : UnlockUiState
    /** offerBiometric: true si se acaba de desbloquear con clave y se puede ofrecer activar huella. */
    data class Unlocked(val offerBiometric: Boolean = false) : UnlockUiState
    data class Error(val message: String) : UnlockUiState
    /** Sesión expirada (401 tras fallo de refresh); la app debe llevar a Login. */
    data object SessionExpired : UnlockUiState
}

/** Resultado de obtener wrapped DEK con posible refresh ante 401. */
private sealed class WrappedDekResult {
    data class Ok(val response: Response<WrappedDekResponse>) : WrappedDekResult()
    data object SessionExpired : WrappedDekResult()
    data object Error : WrappedDekResult()
}

class UnlockViewModel(
    private val authRepository: AuthRepository,
    private val syncRepository: SyncRepository,
    private val api: ApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow<UnlockUiState>(UnlockUiState.Loading)
    val uiState: StateFlow<UnlockUiState> = _uiState.asStateFlow()

    private var isForgottenState = false
    /** Passphrase temporal para ofrecer "activar huella la próxima vez" tras desbloquear con clave. */
    private var pendingPassphraseForBiometricOffer: String? = null

    /** Obtiene wrapped DEK; ante 401 intenta refresh y un reintento. Si sigue 401 o falla refresh → SessionExpired. */
    private suspend fun getWrappedDekWithRefresh(): WrappedDekResult {
        var bearer = authRepository.getBearer() ?: return WrappedDekResult.Error
        var res = api.getWrappedDek(bearer)
        if (res.code() == 401) {
            if (!authRepository.refreshToken()) {
                authRepository.logout()
                return WrappedDekResult.SessionExpired
            }
            bearer = authRepository.getBearer() ?: return WrappedDekResult.Error
            res = api.getWrappedDek(bearer)
            if (res.code() == 401) {
                authRepository.logout()
                return WrappedDekResult.SessionExpired
            }
        }
        return WrappedDekResult.Ok(res)
    }

    fun checkStatus(canAuthenticate: Boolean) {
        viewModelScope.launch {
            _uiState.value = UnlockUiState.Loading
            try {
                when (val result = getWrappedDekWithRefresh()) {
                    is WrappedDekResult.Ok -> {
                        val hasWrapped = result.response.isSuccessful && result.response.body() != null
                        if (hasWrapped && !isForgottenState) {
                            _uiState.value = UnlockUiState.Locked(
                                canUseBiometrics = canAuthenticate,
                                biometricEnabled = authRepository.isBiometricEnabled()
                            )
                        } else {
                            _uiState.value = UnlockUiState.SetupRequired(canAuthenticate)
                        }
                    }
                    is WrappedDekResult.SessionExpired -> _uiState.value = UnlockUiState.SessionExpired
                    is WrappedDekResult.Error -> _uiState.value = UnlockUiState.Error("Error de conexión con el búnker")
                }
            } catch (e: Exception) {
                _uiState.value = UnlockUiState.Error("Error de conexión con el búnker")
            }
        }
    }

    /**
     * @param canUseBiometrics Si el dispositivo soporta biometría; usado para ofrecer "activar huella la próxima vez" tras desbloquear con clave (solo en estado Locked).
     */
    fun unlockWithPassphrase(passphrase: String, useBiometrics: Boolean, context: Context, canUseBiometrics: Boolean = false) {
        if (passphrase.length < 6) {
            _uiState.value = UnlockUiState.Error("La clave debe tener al menos 6 caracteres")
            return
        }

        viewModelScope.launch {
            _uiState.value = UnlockUiState.Loading
            try {
                if (isForgottenState || _uiState.value is UnlockUiState.SetupRequired) {
                    val bearer = authRepository.getBearer() ?: ""
                    setupNewBunker(passphrase, useBiometrics, bearer, context)
                } else {
                    when (val result = getWrappedDekWithRefresh()) {
                        is WrappedDekResult.Ok -> {
                            val res = result.response
                            if (res.isSuccessful && res.body() != null) {
                                val body: WrappedDekResponse = res.body()!!
                                val kek = DekManager.deriveKek(passphrase.toCharArray(), body.kdf.saltB64)
                                val dek = DekManager.unwrapDek(body.wrappedDekB64, kek)
                                DekManager.setDek(dek)
                                if (useBiometrics || authRepository.isBiometricEnabled()) {
                                    authRepository.savePassphrase(passphrase)
                                } else if (canUseBiometrics) {
                                    pendingPassphraseForBiometricOffer = passphrase
                                }
                                finalizeUnlock(context)
                            } else {
                                _uiState.value = UnlockUiState.Error("No se pudo cargar la clave del servidor")
                            }
                        }
                        is WrappedDekResult.SessionExpired -> _uiState.value = UnlockUiState.SessionExpired
                        is WrappedDekResult.Error -> _uiState.value = UnlockUiState.Error("No se pudo cargar la clave del servidor")
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
        val body = WrappedDekBody(
            kdf = KdfDto("pbkdf2", mapOf("iterations" to 120000), saltB64),
            wrappedDekB64 = wrappedB64,
        )

        var currentBearer = bearer
        var putRes = api.putWrappedDek(currentBearer, body)
        if (putRes.code() == 401) {
            if (authRepository.refreshToken()) {
                currentBearer = authRepository.getBearer() ?: ""
                putRes = api.putWrappedDek(currentBearer, body)
            }
            if (putRes.code() == 401) {
                authRepository.logout()
                _uiState.value = UnlockUiState.SessionExpired
                return
            }
        }

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
        _uiState.value = UnlockUiState.Unlocked(offerBiometric = pendingPassphraseForBiometricOffer != null)
    }

    /** Activa la huella para la próxima vez usando la passphrase guardada tras desbloqueo. Llamar cuando el usuario acepta el diálogo "¿Prefieres entrar con huella?". */
    fun savePassphraseForBiometric() {
        pendingPassphraseForBiometricOffer?.let {
            authRepository.savePassphrase(it)
            pendingPassphraseForBiometricOffer = null
        }
    }

    fun resetError(canAuthenticate: Boolean) {
        checkStatus(canAuthenticate)
    }
}
