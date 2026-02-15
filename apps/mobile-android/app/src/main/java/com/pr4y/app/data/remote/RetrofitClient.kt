package com.pr4y.app.data.remote

import android.content.Context
import com.pr4y.app.BuildConfig
import com.pr4y.app.data.auth.AuthTokenStore
import com.pr4y.app.util.Pr4yLog
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    /** Usa un AuthTokenStore existente (p. ej. creado en IO) para no bloquear el main thread. */
    fun create(context: Context, tokenStore: AuthTokenStore): ApiService =
        buildApiService(context, tokenStore)

    /** Crea un AuthTokenStore internamente; puede bloquear el main thread (evitar en arranque). */
    fun create(context: Context): ApiService =
        buildApiService(context, AuthTokenStore(context))

    private fun buildApiService(context: Context, tokenStore: AuthTokenStore): ApiService {
        val baseUrl = BuildConfig.API_BASE_URL
        
        val authInterceptor = Interceptor { chain ->
            val original = chain.request()
            val requestBuilder = original.newBuilder()
            
            if (original.header("Authorization") == null) {
                tokenStore.getAccessToken()?.let { token ->
                    requestBuilder.header("Authorization", "Bearer $token")
                }
            }
            
            val response = chain.proceed(requestBuilder.build())
            
            if (!response.isSuccessful) {
                val errorBody = response.peekBody(1024 * 1024).string()
                Pr4yLog.e("HTTP Error ${response.code} [${original.url}]: $errorBody")
            } else {
                Pr4yLog.net("HTTP SUCCESS ${response.code}: ${original.url}")
            }
            
            response
        }

        val logging = HttpLoggingInterceptor { message ->
            Pr4yLog.net(message)
        }.apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(ApiService::class.java)
    }
}
