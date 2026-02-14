package com.pr4y.app.data.remote

import android.content.Context
import com.pr4y.app.BuildConfig
import com.pr4y.app.data.auth.AuthTokenStore
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    fun create(context: Context): ApiService {
        val baseUrl = EndpointProvider.getBaseUrl(context)
        val tokenStore = AuthTokenStore(context)
        
        val authInterceptor = Interceptor { chain ->
            val original = chain.request()
            val requestBuilder = original.newBuilder()
            
            // Si el request no tiene ya un Header de Authorization, intentamos poner el token
            if (original.header("Authorization") == null) {
                tokenStore.getAccessToken()?.let { token ->
                    requestBuilder.header("Authorization", "Bearer $token")
                }
            }
            
            chain.proceed(requestBuilder.build())
        }

        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
            if (BuildConfig.DEBUG) {
                redactHeader("Authorization")
                redactHeader("Proxy-Authorization")
            }
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
