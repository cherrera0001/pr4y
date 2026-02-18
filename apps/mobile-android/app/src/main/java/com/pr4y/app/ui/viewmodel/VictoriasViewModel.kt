package com.pr4y.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pr4y.app.data.auth.AuthRepository
import com.pr4y.app.data.remote.AnswerDto
import com.pr4y.app.data.remote.ApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface VictoriasUiState {
    data object Loading : VictoriasUiState
    data class Success(val answeredCount: Int, val answers: List<AnswerDto>) : VictoriasUiState
    data class Error(val message: String) : VictoriasUiState
}

class VictoriasViewModel(
    private val authRepository: AuthRepository,
    private val api: ApiService,
) : ViewModel() {

    private val _uiState = MutableStateFlow<VictoriasUiState>(VictoriasUiState.Loading)
    val uiState: StateFlow<VictoriasUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = VictoriasUiState.Loading
            val bearer = authRepository.getBearer() ?: run {
                _uiState.value = VictoriasUiState.Error("Sesión no disponible")
                return@launch
            }
            try {
                val statsRes = api.getAnswersStats(bearer)
                val listRes = api.getAnswers(bearer)
                if (statsRes.isSuccessful && listRes.isSuccessful) {
                    val count = statsRes.body()?.answeredCount ?: 0
                    val list = listRes.body()?.answers ?: emptyList()
                    _uiState.value = VictoriasUiState.Success(answeredCount = count, answers = list)
                } else {
                    _uiState.value = VictoriasUiState.Error("No se pudo cargar Mis Victorias")
                }
            } catch (e: Exception) {
                _uiState.value = VictoriasUiState.Error("Error: ${e.message}")
            }
        }
    }
}
