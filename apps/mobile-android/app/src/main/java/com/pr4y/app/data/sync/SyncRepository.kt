package com.pr4y.app.data.sync

import com.pr4y.app.crypto.DekManager
import com.pr4y.app.crypto.LocalCrypto
import com.pr4y.app.data.auth.AuthRepository
import com.pr4y.app.data.local.entity.OutboxEntity
import com.pr4y.app.data.local.entity.RequestEntity
import com.pr4y.app.data.remote.PushBody
import com.pr4y.app.data.remote.PushRecordDto
import com.pr4y.app.data.remote.RetrofitClient
import com.pr4y.app.di.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class SyncRepository(
    private val authRepository: AuthRepository,
) {
    private val api = RetrofitClient.create()
    private val db = AppContainer.db
    private val requestDao = db.requestDao()
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
                        clientUpdatedAt = java.time.Instant.ofEpochMilli(o.clientUpdatedAt).toString(),
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
            var cursor: String? = null
            val syncCursorKey = "sync_cursor"
            cursor = syncStateDao.get(syncCursorKey)?.value

            do {
                val pullRes = api.pull(bearer, cursor, 100)
                if (!pullRes.isSuccessful) break
                val body = pullRes.body() ?: break
                for (rec in body.records) {
                    if (rec.deleted) {
                        requestDao.deleteById(rec.recordId)
                    } else if (rec.type == TYPE_PRAYER_REQUEST) {
                        try {
                            val plain = LocalCrypto.decrypt(rec.encryptedPayloadB64, dek)
                            val json = JSONObject(String(plain))
                            val title = json.optString("title", "")
                            val bodyText = json.optString("body", "")
                            val updatedAt = java.time.Instant.parse(rec.serverUpdatedAt).toEpochMilli()
                            requestDao.insert(
                                RequestEntity(
                                    id = rec.recordId,
                                    title = title,
                                    body = bodyText,
                                    createdAt = updatedAt,
                                    updatedAt = updatedAt,
                                    synced = true,
                                ),
                            )
                        } catch (_: Exception) { }
                    }
                }
                cursor = if (body.nextCursor.isEmpty()) null else body.nextCursor
                if (!cursor.isNullOrEmpty()) {
                    syncStateDao.insert(
                        com.pr4y.app.data.local.entity.SyncStateEntity(
                            key = syncCursorKey,
                            value = cursor,
                            updatedAt = System.currentTimeMillis(),
                        ),
                    )
                }
            } while (!cursor.isNullOrEmpty())

            SyncResult.Success
        } catch (e: Exception) {
            SyncResult.Error(e.message ?: "Error de red")
        }
    }

    companion object {
        const val TYPE_PRAYER_REQUEST = "prayer_request"
    }
}

sealed class SyncResult {
    object Success : SyncResult()
    data class Error(val message: String) : SyncResult()
}
