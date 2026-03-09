package com.pr4y.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pr4y.app.data.remote.ApiService
import com.pr4y.app.data.remote.PublicRequestDto
import com.pr4y.app.util.Pr4yLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class RouletteUiState {
    object Loading : RouletteUiState()
    data class Success(val requests: List<PublicRequestDto>) : RouletteUiState()
    object Empty : RouletteUiState()
    data class Error(val message: String) : RouletteUiState()
}

@HiltViewModel
class RouletteViewModel @Inject constructor(
    private val api: ApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow<RouletteUiState>(RouletteUiState.Loading)
    val uiState: StateFlow<RouletteUiState> = _uiState

    init {
        loadRequests()
    }

    fun loadRequests() {
        viewModelScope.launch {
            _uiState.value = RouletteUiState.Loading
            try {
                // Spec: Llamada anónima para intercesión aleatoria
                val response = api.getPublicRequests()
                if (response.isSuccessful) {
                    val requests = response.body()?.requests ?: emptyList()
                    if (requests.isEmpty()) {
                        _uiState.value = RouletteUiState.Empty
                    } else {
                        _uiState.value = RouletteUiState.Success(requests)
                    }
                } else {
                    _uiState.value = RouletteUiState.Error("Error al cargar peticiones: ${response.code()}")
                }
            } catch (e: Exception) {
                Pr4yLog.e("RouletteViewModel: Error de red", e)
                _uiState.value = RouletteUiState.Error(e.message ?: "Error desconocido")
            }
        }
    }

    fun prayForRequest(id: String) {
        viewModelScope.launch {
            try {
                // Registro anónimo de la oración
                api.prayForPublicRequest(id)
                Pr4yLog.i("Roulette: Me he unido en oración para $id")
            } catch (e: Exception) {
                Pr4yLog.e("RouletteViewModel: Error al registrar oración", e)
            }
        }
    }
}
