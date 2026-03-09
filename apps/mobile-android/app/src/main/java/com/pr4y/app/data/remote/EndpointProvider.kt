package com.pr4y.app.data.remote

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pr4y.app.BuildConfig
import com.pr4y.app.util.Pr4yLog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "api_settings")

object EndpointProvider {
    private val API_ENDPOINT_KEY = stringPreferencesKey("api_endpoint")
    private const val TARGET_DOMAIN = "pr4yapi-production.up.railway.app"

    suspend fun getBaseUrl(context: Context): String {
        val current = context.dataStore.data.map { it[API_ENDPOINT_KEY] }.first()

        // Si el endpoint guardado no es el actual (Railway), lo limpiamos para forzar la migración
        if (current != null && !current.contains(TARGET_DOMAIN)) {
            Pr4yLog.w("Endpoint antiguo detectado ($current). Limpiando para usar el actual.")
            context.dataStore.edit { it.remove(API_ENDPOINT_KEY) }
            return BuildConfig.API_BASE_URL.ensureTrailingSlash()
        }
        return if (!current.isNullOrBlank()) {
            current.ensureTrailingSlash()
        } else {
            BuildConfig.API_BASE_URL.ensureTrailingSlash()
        }
    }

    suspend fun updateEndpoint(context: Context, newEndpoint: String) {
        context.dataStore.edit { settings ->
            settings[API_ENDPOINT_KEY] = newEndpoint
        }
    }

    private fun String.ensureTrailingSlash(): String {
        return if (endsWith("/")) this else "$this/"
    }
}
