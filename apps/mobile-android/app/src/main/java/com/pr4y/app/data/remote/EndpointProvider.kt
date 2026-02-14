package com.pr4y.app.data.remote

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pr4y.app.BuildConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.dataStore by preferencesDataStore(name = "api_settings")

object EndpointProvider {
    private val API_ENDPOINT_KEY = stringPreferencesKey("api_endpoint")

    fun getBaseUrl(context: Context): String {
        return runBlocking {
            context.dataStore.data.map { preferences ->
                val savedEndpoint = preferences[API_ENDPOINT_KEY]
                if (!savedEndpoint.isNullOrBlank()) {
                    savedEndpoint.ensureTrailingSlash()
                } else {
                    BuildConfig.API_BASE_URL.ensureTrailingSlash()
                }
            }.first()
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
