package com.pr4y.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.pr4y.app.data.local.AppDatabase
import com.pr4y.app.data.sync.SyncRepository

/**
 * Tech Lead Note: Managed factory for HomeViewModel.
 * Ensures consistent data injection.
 */
class HomeViewModelFactory(
    private val db: AppDatabase,
    private val syncRepository: SyncRepository,
    private val userId: String,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            return HomeViewModel(db, syncRepository, userId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
