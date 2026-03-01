package com.pr4y.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pr4y.app.data.remote.ApiService
import com.pr4y.app.data.remote.PublicRequestDto
import com.pr4y.app.util.Pr4yLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class RouletteUiState {
    object Loading : RouletteUiState()
    data class Success(val requests: List<PublicRequestDto>) : RouletteUiState()
    data class Error(val message: String) : RouletteUiState()
    object Empty : RouletteUiState()
}

@HiltViewModel
class RouletteViewModel @Inject constructor(
    private val api: ApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow<RouletteUiState>(RouletteUiState.Loading)
    val uiState: StateFlow<RouletteUiState> = _uiState.asStateFlow()

    init {
        loadRequests()
    }

    fun loadRequests() {
        viewModelScope.launch {
            _uiState.value = RouletteUiState.Loading
            try {
                val response = api.getPublicRequests()
                when {
                    response.isSuccessful -> {
                        val requests = response.body()?.requests ?: emptyList()
                        if (requests.isEmpty()) {
                            _uiState.value = RouletteUiState.Empty
                        } else {
                            _uiState.value = RouletteUiState.Success(requests)
                        }
                    }
                    response.code() == 404 -> {
                        // Backend puede no exponer aún GET /public/requests; mostrar estado vacío
                        _uiState.value = RouletteUiState.Empty
                    }
                    else -> _uiState.value = RouletteUiState.Error("Error al conectar con el búnker público")
                }
            } catch (e: Exception) {
                Pr4yLog.e("RouletteViewModel: Error loading requests", e)
                _uiState.value = RouletteUiState.Error("Sin conexión")
            }
        }
    }

    fun prayForRequest(requestId: String) {
        viewModelScope.launch {
            try {
                // El interceptor TelemetryInterceptor registrará el evento roulette_intercession_success
                api.prayForPublicRequest(requestId)
            } catch (e: Exception) {
                Pr4yLog.e("RouletteViewModel: Error posting prayer", e)
            }
        }
    }
}
