package com.pr4y.app.di

import android.content.Context
import com.pr4y.app.BuildConfig
import com.pr4y.app.data.auth.AuthTokenStore
import com.pr4y.app.data.remote.ApiService
import com.pr4y.app.util.Pr4yLog
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class AuthInterceptor

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class TelemetryInterceptor

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideAuthTokenStore(@ApplicationContext context: Context): AuthTokenStore {
        return AuthTokenStore(context)
    }

    @Provides
    @Singleton
    fun provideHttpLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor { message ->
            Pr4yLog.net(message)
        }.apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.HEADERS else HttpLoggingInterceptor.Level.NONE
        }
    }

    @Provides
    @Singleton
    @AuthInterceptor
    fun provideAuthInterceptor(tokenStore: AuthTokenStore): Interceptor {
        return Interceptor { chain ->
            val original = chain.request()
            val requestBuilder = original.newBuilder()

            // Spec "Anonimato en el Tráfico (Stripping)": 
            // Si el header X-Anonymous está presente, eliminamos identificación.
            val isAnonymous = original.header("X-Anonymous") == "true"

            if (isAnonymous) {
                Pr4yLog.net("Stripping identifying headers for anonymous request: ${original.url}")
                requestBuilder.removeHeader("Authorization")
                requestBuilder.removeHeader("X-Anonymous") // Opcional: limpiar el trigger
                // Aquí se eliminarían otros metadatos si existieran (ej. Device-ID)
            } else {
                if (original.header("Authorization") == null) {
                    tokenStore.getAccessToken()?.let { token ->
                        requestBuilder.header("Authorization", "Bearer $token")
                    }
                }
            }

            chain.proceed(requestBuilder.build())
        }
    }

    @Provides
    @Singleton
    @TelemetryInterceptor
    fun provideTelemetryInterceptor(): Interceptor {
        return Interceptor { chain ->
            val start = System.currentTimeMillis()
            val request = chain.request()
            val response = chain.proceed(request)
            val duration = System.currentTimeMillis() - start

            // Registro de telemetría siguiendo estándar MCP Feb 2026
            // "Validación de Seguridad: No registrar payloads cifrados"
            if (!response.isSuccessful) {
                val url = request.url.toString()
                val getPublicRequests404 = response.code == 404 && request.method == "GET" && url.contains("public/requests")
                if (getPublicRequests404) {
                    // GET public/requests puede devolver 404 si el backend aún no expone la ruta (Roulette)
                    Pr4yLog.i("TELEMETRY: [404] GET public/requests (endpoint opcional) took ${duration}ms")
                } else {
                    Pr4yLog.e("TELEMETRY: [${response.code}] ${request.method} ${request.url} took ${duration}ms")
                }
            } else {
                Pr4yLog.net("TELEMETRY: SUCCESS [${response.code}] took ${duration}ms")
                
                // Métrica de impacto Roulette
                if (request.url.toString().contains("public/requests") && request.method == "POST") {
                    Pr4yLog.i("TELEMETRY_EVENT: roulette_intercession_success")
                }
            }
            
            response
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor,
        @AuthInterceptor authInterceptor: Interceptor,
        @TelemetryInterceptor telemetryInterceptor: Interceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(telemetryInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideApiService(okHttpClient: OkHttpClient): ApiService {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
