package com.pr4y.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.pr4y.app.data.auth.AuthRepository
import com.pr4y.app.data.remote.ApiService
import com.pr4y.app.data.sync.SyncRepository

/**
 * Tech Lead Note: Factory for UnlockViewModel.
 * Standard: Clean dependency injection for cryptographic operations.
 */
class UnlockViewModelFactory(
    private val authRepository: AuthRepository,
    private val syncRepository: SyncRepository,
    private val api: ApiService
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(UnlockViewModel::class.java)) {
            return UnlockViewModel(authRepository, syncRepository, api) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
