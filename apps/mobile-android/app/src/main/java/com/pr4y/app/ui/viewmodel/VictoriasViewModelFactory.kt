package com.pr4y.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.pr4y.app.data.auth.AuthRepository
import com.pr4y.app.data.remote.ApiService

class VictoriasViewModelFactory(
    private val authRepository: AuthRepository,
    private val api: ApiService,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(VictoriasViewModel::class.java)) {
            return VictoriasViewModel(authRepository, api) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
