package com.pr4y.app.data.sync

import android.content.Context
import com.pr4y.app.crypto.DekManager
import com.pr4y.app.crypto.LocalCrypto
import com.pr4y.app.data.auth.AuthRepository
import com.pr4y.app.data.local.JournalDraftStore
import com.pr4y.app.data.local.entity.JournalEntity
import com.pr4y.app.data.local.entity.OutboxEntity
import com.pr4y.app.data.local.entity.RequestEntity
import com.pr4y.app.data.local.entity.SyncStateEntity
import com.pr4y.app.data.remote.parseApiErrorMessage
import com.pr4y.app.data.remote.PushBody
import com.pr4y.app.data.remote.PushRecordDto
import com.pr4y.app.data.remote.RetrofitClient
import com.pr4y.app.data.remote.SyncRecordDto
import com.pr4y.app.di.AppContainer
import com.pr4y.app.util.Pr4yLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

/**
 * Repositorio de sincronización.
 * Refactorizado para usar acceso perezoso (lazy) a la base de datos para evitar crashes en init.
 */
class SyncRepository(
    private val authRepository: AuthRepository,
    context: Context,
) {
    private val api = RetrofitClient.create(context)
    
    // Acceso perezoso a los DAOs para evitar IllegalStateException si la DB no está lista al instanciar
    private val requestDao by lazy { AppContainer.db.requestDao() }
    private val journalDao by lazy { AppContainer.db.journalDao() }
    private val outboxDao by lazy { AppContainer.db.outboxDao() }
    private val syncStateDao by lazy { AppContainer.db.syncStateDao() }

    suspend fun sync(): SyncResult = withContext(Dispatchers.IO) {
        if (!AppContainer.isInitialized()) return@withContext SyncResult.Error("Bóveda no disponible")
        
        Pr4yLog.i("Iniciando proceso de sincronización...")
        val bearer = authRepository.getBearer() ?: return@withContext SyncResult.Error("No autenticado")
        val dek = DekManager.getDek() ?: return@withContext SyncResult.Error("Llave de privacidad no disponible")
        val userId = authRepository.getUserId() ?: return@withContext SyncResult.Error("Sesión no identificada")

        try {
            pull(bearer, dek, userId)

            var outbox = outboxDao.getAll()
            var pushRound = 0
            val maxPushRounds = 2
            while (outbox.isNotEmpty() && pushRound < maxPushRounds) {
                val records = outbox.map { o ->
                    PushRecordDto(
                        recordId = o.recordId,
                        type = o.type,
                        version = o.version,
                        encryptedPayloadB64 = o.encryptedPayloadB64,
                        clientUpdatedAt = Instant.ofEpochMilli(o.clientUpdatedAt).toString(),
                        deleted = false,
                    )
                }
                val pushRes = api.push(bearer, PushBody(records))
                if (!pushRes.isSuccessful) break
                val pushBody = pushRes.body() ?: break
                
                pushBody.accepted.forEach { recordId ->
                    outboxDao.deleteByRecordId(recordId)
                }
                
                val rejected = pushBody.rejected
                for (r in rejected) {
                    if (r.reason == "version conflict" && r.serverVersion != null) {
                        val updated = outbox.find { it.recordId == r.recordId } ?: continue
                        outboxDao.insert(
                            OutboxEntity(
                                recordId = updated.recordId,
                                type = updated.type,
                                version = r.serverVersion + 1,
                                encryptedPayloadB64 = updated.encryptedPayloadB64,
                                clientUpdatedAt = updated.clientUpdatedAt,
                                createdAt = updated.createdAt,
                            )
                        )
                    }
                }
                outbox = outboxDao.getAll()
                pushRound++
            }

            pull(bearer, dek, userId)

            persistLastSyncStatus("ok", null)
            SyncResult.Success
        } catch (e: Exception) {
            Pr4yLog.e("Sync: Error crítico durante la sincronización", e)
            persistLastSyncStatus("error", System.currentTimeMillis())
            SyncResult.Error(e.message ?: "Error de red")
        }
    }

    private suspend fun pull(bearer: String, dek: javax.crypto.SecretKey, userId: String) {
        var cursor: String? = syncStateDao.get(SYNC_CURSOR_KEY)?.value
        do {
            val pullRes = api.pull(bearer, cursor, 100)
            if (!pullRes.isSuccessful) break
            val body = pullRes.body() ?: break
            if (body.records.isNotEmpty()) {
                AppContainer.db.runInTransaction {
                    runBlocking {
                        for (rec in body.records) {
                            processRemoteRecord(rec, dek, userId)
                        }
                    }
                }
            }
            cursor = if (body.nextCursor.isEmpty()) null else body.nextCursor
            if (!cursor.isNullOrEmpty()) {
                syncStateDao.insert(
                    SyncStateEntity(
                        key = SYNC_CURSOR_KEY,
                        value = cursor,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            }
        } while (!cursor.isNullOrEmpty())
    }

    suspend fun processJournalDraft(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (!AppContainer.isInitialized()) return@withContext false
        val draft = JournalDraftStore.getDraft(context) ?: return@withContext false
        val dek = DekManager.getDek() ?: return@withContext false
        val userId = authRepository.getUserId() ?: return@withContext false

        try {
            val now = System.currentTimeMillis()
            val id = UUID.randomUUID().toString()
            val payload = JSONObject().apply {
                put("content", draft)
                put("createdAt", now)
                put("updatedAt", now)
            }.toString().toByteArray(Charsets.UTF_8)
            val encrypted = LocalCrypto.encrypt(payload, dek)

            AppContainer.db.runInTransaction {
                runBlocking {
                    journalDao.insert(
                        JournalEntity(
                            id = id,
                            userId = userId,
                            content = "",
                            createdAt = now,
                            updatedAt = now,
                            synced = false,
                            encryptedPayloadB64 = encrypted,
                        ),
                    )
                    outboxDao.insert(
                        OutboxEntity(
                            recordId = id,
                            type = TYPE_JOURNAL_ENTRY,
                            version = 1,
                            encryptedPayloadB64 = encrypted,
                            clientUpdatedAt = now,
                            createdAt = now,
                        ),
                    )
                    JournalDraftStore.clearDraft(context)
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun persistLastSyncStatus(status: String, errorAt: Long?) {
        val now = System.currentTimeMillis()
        syncStateDao.insert(SyncStateEntity(KEY_LAST_SYNC_STATUS, status, now))
        syncStateDao.insert(SyncStateEntity(KEY_LAST_SYNC_AT, now.toString(), now))
        if (errorAt != null) {
            syncStateDao.insert(SyncStateEntity(KEY_LAST_SYNC_ERROR_AT, errorAt.toString(), now))
        }
    }

    suspend fun getLastSyncStatus(): LastSyncStatus? = withContext(Dispatchers.IO) {
        if (!AppContainer.isInitialized()) return@withContext null
        val status = syncStateDao.get(KEY_LAST_SYNC_STATUS)?.value ?: return@withContext null
        val errorAtStr = syncStateDao.get(KEY_LAST_SYNC_ERROR_AT)?.value
        val errorAt = errorAtStr?.toLongOrNull()
        LastSyncStatus(status == "ok", errorAt)
    }

    private suspend fun processRemoteRecord(rec: SyncRecordDto, dek: javax.crypto.SecretKey, userId: String) {
        if (rec.deleted) {
            when (rec.type) {
                TYPE_PRAYER_REQUEST -> requestDao.deleteById(rec.recordId, userId)
                TYPE_JOURNAL_ENTRY -> journalDao.deleteById(rec.recordId, userId)
            }
            return
        }

        val updatedAt = Instant.parse(rec.serverUpdatedAt).toEpochMilli()

        when (rec.type) {
            TYPE_PRAYER_REQUEST -> {
                try {
                    val plain = LocalCrypto.decrypt(rec.encryptedPayloadB64, dek)
                    val json = JSONObject(String(plain))
                    requestDao.insert(
                        RequestEntity(
                            id = rec.recordId,
                            userId = userId,
                            title = json.optString("title", ""),
                            body = json.optString("body", ""),
                            createdAt = updatedAt,
                            updatedAt = updatedAt,
                            synced = true,
                            status = rec.status,
                        ),
                    )
                } catch (e: Exception) {
                    Pr4yLog.e("Sync: Error al descifrar registro remoto ${rec.recordId}", e)
                }
            }
            TYPE_JOURNAL_ENTRY -> {
                journalDao.insert(
                    JournalEntity(
                        id = rec.recordId,
                        userId = userId,
                        content = "",
                        createdAt = updatedAt,
                        updatedAt = updatedAt,
                        synced = true,
                        encryptedPayloadB64 = rec.encryptedPayloadB64,
                    ),
                )
            }
        }
    }

    companion object {
        const val TYPE_PRAYER_REQUEST = "prayer_request"
        const val TYPE_JOURNAL_ENTRY = "journal_entry"
        private const val SYNC_CURSOR_KEY = "sync_cursor"
        const val KEY_LAST_SYNC_STATUS = "last_sync_status"
        const val KEY_LAST_SYNC_AT = "last_sync_at"
        const val KEY_LAST_SYNC_ERROR_AT = "last_sync_error_at"
    }
}

sealed class SyncResult {
    object Success : SyncResult()
    data class Error(val message: String) : SyncResult()
}

data class LastSyncStatus(val lastOk: Boolean, val lastErrorAt: Long?)
