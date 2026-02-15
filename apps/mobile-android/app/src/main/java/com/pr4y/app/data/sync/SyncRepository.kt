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
        Pr4yLog.i("Iniciando proceso de sincronización...")
        val bearer = authRepository.getBearer() ?: return@withContext SyncResult.Error("No autenticado")
        val dek = DekManager.getDek() ?: return@withContext SyncResult.Error("Llave de privacidad no disponible")

        try {
            // 0. Pull-before-push
            Pr4yLog.d("Sync: Ejecutando Pull inicial...")
            pull(bearer, dek)

            // 1. Push outbox
            var outbox = outboxDao.getAll()
            var pushRound = 0
            val maxPushRounds = 2
            while (outbox.isNotEmpty() && pushRound < maxPushRounds) {
                Pr4yLog.d("Sync: Ejecutando Push (Ronda ${pushRound + 1}). Elementos en outbox: ${outbox.size}")
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
                if (!pushRes.isSuccessful) {
                    Pr4yLog.e("Sync: Push fallido con código ${pushRes.code()}")
                    break
                }
                val pushBody = pushRes.body() ?: break
                
                Pr4yLog.i("Sync: Push exitoso. Aceptados: ${pushBody.accepted.size}, Rechazados: ${pushBody.rejected.size}")
                
                pushBody.accepted.forEach { recordId ->
                    outboxDao.deleteByRecordId(recordId)
                }
                
                val rejected = pushBody.rejected
                for (r in rejected) {
                    if (r.reason == "version conflict" && r.serverVersion != null) {
                        Pr4yLog.w("Sync: Conflicto de versión en ${r.recordId}. Actualizando a serverVersion + 1 (${r.serverVersion + 1})")
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
                    } else {
                        Pr4yLog.e("Sync: Registro ${r.recordId} rechazado por: ${r.reason}")
                    }
                }
                outbox = outboxDao.getAll()
                pushRound++
            }

            // 2. Pull final
            Pr4yLog.d("Sync: Ejecutando Pull final...")
            pull(bearer, dek)

            persistLastSyncStatus("ok", null)
            Pr4yLog.i("Sync: Sincronización finalizada correctamente.")
            SyncResult.Success
        } catch (e: Exception) {
            Pr4yLog.e("Sync: Error crítico durante la sincronización", e)
            persistLastSyncStatus("error", System.currentTimeMillis())
            SyncResult.Error(e.message ?: "Error de red o seguridad")
        }
    }

    private suspend fun pull(bearer: String, dek: javax.crypto.SecretKey) {
        var cursor: String? = syncStateDao.get(SYNC_CURSOR_KEY)?.value
        do {
            val pullRes = api.pull(bearer, cursor, 100)
            if (!pullRes.isSuccessful) {
                Pr4yLog.e("Sync: Pull fallido con código ${pullRes.code()}")
                break
            }
            val body = pullRes.body() ?: break
            if (body.records.isNotEmpty()) {
                Pr4yLog.i("Sync: Pull recibió ${body.records.size} registros nuevos.")
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
    }

    /**
     * Procesa borradores del diario guardados sin conexión/llave.
     * @return true si se procesó un borrador con éxito.
     */
    suspend fun processJournalDraft(context: Context): Boolean = withContext(Dispatchers.IO) {
        val draft = JournalDraftStore.getDraft(context) ?: return@withContext false
        val dek = DekManager.getDek() ?: return@withContext false
        
        Pr4yLog.i("Sync: Procesando borrador de diario pendiente...")
        try {
            val now = System.currentTimeMillis()
            val id = UUID.randomUUID().toString()
            val payload = JSONObject().apply {
                put("content", draft)
                put("createdAt", now)
                put("updatedAt", now)
            }.toString().toByteArray(Charsets.UTF_8)
            val encrypted = LocalCrypto.encrypt(payload, dek)

            db.runInTransaction {
                runBlocking {
                    journalDao.insert(
                        JournalEntity(
                            id = id,
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
            Pr4yLog.i("Sync: Borrador procesado y protegido con éxito.")
            true
        } catch (e: Exception) {
            Pr4yLog.e("Sync: Error al procesar borrador", e)
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
        val status = syncStateDao.get(KEY_LAST_SYNC_STATUS)?.value ?: return@withContext null
        val errorAtStr = syncStateDao.get(KEY_LAST_SYNC_ERROR_AT)?.value
        val errorAt = errorAtStr?.toLongOrNull()
        LastSyncStatus(status == "ok", errorAt)
    }

    private suspend fun processRemoteRecord(rec: SyncRecordDto, dek: javax.crypto.SecretKey) {
        if (rec.deleted) {
            when (rec.type) {
                TYPE_PRAYER_REQUEST -> requestDao.deleteById(rec.recordId)
                TYPE_JOURNAL_ENTRY -> journalDao.deleteById(rec.recordId)
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
                            title = json.optString("title", ""),
                            body = json.optString("body", ""),
                            createdAt = updatedAt,
                            updatedAt = updatedAt,
                            synced = true,
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
