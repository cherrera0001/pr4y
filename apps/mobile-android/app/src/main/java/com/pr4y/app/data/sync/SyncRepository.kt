package com.pr4y.app.data.sync

import android.content.Context
import com.pr4y.app.crypto.DekManager
import com.pr4y.app.crypto.LocalCrypto
import com.pr4y.app.data.auth.AuthRepository
import com.pr4y.app.data.local.entity.JournalEntity
import com.pr4y.app.data.local.entity.RequestEntity
import com.pr4y.app.data.local.entity.SyncStateEntity
import com.pr4y.app.data.remote.PushBody
import com.pr4y.app.data.remote.PushRecordDto
import com.pr4y.app.data.remote.RetrofitClient
import com.pr4y.app.data.remote.SyncRecordDto
import com.pr4y.app.di.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.Instant

class SyncRepository(
    private val authRepository: AuthRepository,
    context: Context,
) {
    private val api = RetrofitClient.create(context)
    private val db = AppContainer.db
    private val requestDao = db.requestDao()
    private val journalDao = db.journalDao()
    private val outboxDao = db.outboxDao()
    private val syncStateDao = db.syncStateDao()

    suspend fun sync(): SyncResult = withContext(Dispatchers.IO) {
        val bearer = authRepository.getBearer() ?: return@withContext SyncResult.Error("No autenticado")
        val dek = DekManager.getDek() ?: return@withContext SyncResult.Error("DEK no disponible")

        try {
            // 1. Push outbox
            val outbox = outboxDao.getAll()
            if (outbox.isNotEmpty()) {
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
                if (pushRes.isSuccessful) {
                    pushRes.body()?.accepted?.forEach { recordId ->
                        outboxDao.deleteByRecordId(recordId)
                    }
                }
            }

            // 2. Pull
            var cursor: String? = syncStateDao.get(SYNC_CURSOR_KEY)?.value

            do {
                val pullRes = api.pull(bearer, cursor, 100)
                if (!pullRes.isSuccessful) break
                val body = pullRes.body() ?: break
                
                if (body.records.isNotEmpty()) {
                    db.runInTransaction {
                        runBlocking {
                            for (rec in body.records) {
                                processRemoteRecord(rec, dek)
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

            SyncResult.Success
        } catch (e: Exception) {
            SyncResult.Error(e.message ?: "Error de red o descifrado")
        }
    }

    private suspend fun processRemoteRecord(rec: SyncRecordDto, dek: javax.crypto.SecretKey) {
        if (rec.deleted) {
            when (rec.type) {
                TYPE_PRAYER_REQUEST -> requestDao.deleteById(rec.recordId)
                // En un futuro, añadir deleteById para journal
            }
            return
        }

        try {
            val plain = LocalCrypto.decrypt(rec.encryptedPayloadB64, dek)
            val json = JSONObject(String(plain))
            val updatedAt = Instant.parse(rec.serverUpdatedAt).toEpochMilli()

            when (rec.type) {
                TYPE_PRAYER_REQUEST -> {
                    requestDao.insert(
                        RequestEntity(
                            id = rec.recordId,
                            title = json.optString("title", ""),
                            body = json.optString("body", ""),
                            createdAt = updatedAt,
                            updatedAt = updatedAt,
                            synced = true,
                        ),
                    )
                }
                TYPE_JOURNAL_ENTRY -> {
                    journalDao.insert(
                        JournalEntity(
                            id = rec.recordId,
                            content = json.optString("content", ""),
                            createdAt = updatedAt,
                            updatedAt = updatedAt,
                            synced = true,
                        ),
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        const val TYPE_PRAYER_REQUEST = "prayer_request"
        const val TYPE_JOURNAL_ENTRY = "journal_entry"
        private const val SYNC_CURSOR_KEY = "sync_cursor"
    }
}

sealed class SyncResult {
    object Success : SyncResult()
    data class Error(val message: String) : SyncResult()
}
