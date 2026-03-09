package com.pr4y.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pr4y.app.crypto.DekManager
import com.pr4y.app.crypto.LocalCrypto
import com.pr4y.app.data.local.AppDatabase
import com.pr4y.app.data.local.entity.RequestEntity
import com.pr4y.app.data.sync.LastSyncStatus
import com.pr4y.app.data.sync.SyncRepository
import com.pr4y.app.data.sync.SyncResult
import com.pr4y.app.util.Pr4yLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Tech Lead Note: UI State for Home.
 * Standard: Decoupled logic from UI, background decryption.
 */
sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Success(
        val requests: List<RequestEntity>,
        val outboxCount: Int,
        val lastSyncStatus: LastSyncStatus?
    ) : HomeUiState
}

class HomeViewModel(
    private val db: AppDatabase,
    private val syncRepository: SyncRepository,
    private val userId: String,
) : ViewModel() {

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage = _syncMessage.asStateFlow()

    val uiState: StateFlow<HomeUiState> = combine(
        db.requestDao().getAll(userId),
        db.outboxDao().getAllFlow()
    ) { entities, outbox ->
        val decryptedRequests = withContext(Dispatchers.Default) {
            val dek = DekManager.getDek()
            entities.map { entity ->
                if (entity.encryptedPayloadB64 != null && dek != null) {
                    try {
                        val plain = LocalCrypto.decrypt(entity.encryptedPayloadB64, dek)
                        val json = JSONObject(String(plain))
                        entity.copy(
                            title = json.optString("title", ""),
                            body = json.optString("body", "")
                        )
                    } catch (e: Exception) {
                        Pr4yLog.e("HomeViewModel: Decryption failed for ${entity.id}", e)
                        entity
                    }
                } else {
                    entity
                }
            }
        }
        HomeUiState.Success(
            requests = decryptedRequests,
            outboxCount = outbox.size,
            lastSyncStatus = syncRepository.getLastSyncStatus()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState.Loading
    )

    fun runSync() {
        viewModelScope.launch {
            when (val r = syncRepository.sync()) {
                is SyncResult.Success -> _syncMessage.value = "Sincronizado correctamente"
                is SyncResult.Error -> _syncMessage.value = "Error: ${r.message}"
            }
        }
    }

    fun clearSyncMessage() {
        _syncMessage.value = null
    }
}
