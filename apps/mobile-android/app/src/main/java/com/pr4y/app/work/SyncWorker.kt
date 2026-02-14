package com.pr4y.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pr4y.app.data.auth.AuthRepository
import com.pr4y.app.data.auth.AuthTokenStore
import com.pr4y.app.data.remote.RetrofitClient
import com.pr4y.app.data.sync.SyncRepository
import com.pr4y.app.data.sync.SyncResult

/**
 * Sincronización en segundo plano. Se programa con restricciones de red (y opcionalmente batería)
 * para no ejecutarse sin conexión ni gastar batería innecesariamente.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val authRepository = AuthRepository(
            RetrofitClient.create(ctx),
            AuthTokenStore(ctx),
        )
        val syncRepository = SyncRepository(authRepository, ctx)
        return when (syncRepository.sync()) {
            is SyncResult.Success -> Result.success()
            is SyncResult.Error -> Result.retry()
        }
    }
}
