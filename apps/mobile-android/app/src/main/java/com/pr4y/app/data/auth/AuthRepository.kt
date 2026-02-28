package com.pr4y.app.data.auth

import com.pr4y.app.data.remote.ApiService
import com.pr4y.app.data.remote.AuthResponse
import com.pr4y.app.data.remote.GoogleLoginBody
import com.pr4y.app.data.remote.PublicConfigResponse
import com.pr4y.app.data.remote.RefreshBody
import com.pr4y.app.util.Pr4yLog
import com.pr4y.app.di.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Tech Lead Note: Managed authentication repository.
 * Standards: Separation of concerns, clear error handling, and security-first IDs.
 * Fixed: Added userId persistence and database cleanup on logout.
 */
class AuthRepository(
    private val api: ApiService,
    private val tokenStore: AuthTokenStore,
) {
    fun hasToken(): Boolean = tokenStore.getAccessToken() != null
    fun getUserId(): String? = tokenStore.getUserId()

    suspend fun getPublicConfig(): Result<PublicConfigResponse> {
        return runCatching {
            val res = api.getPublicConfig()
            if (!res.isSuccessful) {
                Pr4yLog.e("AuthRepository: getPublicConfig failed with code ${res.code()}")
                throw AuthError(res.code(), "Failed to fetch remote config")
            }
            res.body() ?: throw Exception("Configuration body is null")
        }.recoverCatching { e ->
            throw when (e) {
                is UnknownHostException, is ConnectException, is SocketTimeoutException -> {
                    Pr4yLog.e("AuthRepository: getPublicConfig network/DNS error", e)
                    Exception("Comprueba tu conexión a internet e inténtalo de nuevo.")
                }
                else -> e
            }
        }
    }

    suspend fun register(email: String, password: String): Result<AuthResponse> {
        return runCatching {
            val res = api.register(com.pr4y.app.data.remote.RegisterBody(email, password))
            if (!res.isSuccessful) {
                val body = res.errorBody()?.string() ?: ""
                throw AuthError(res.code(), body)
            }
            val auth = res.body() ?: throw AuthError(res.code(), "Respuesta vacía del servidor")
            tokenStore.setTokens(auth.accessToken, auth.refreshToken, auth.user.id)
            auth
        }
    }

    suspend fun login(email: String, password: String): Result<AuthResponse> {
        return runCatching {
            val res = api.login(com.pr4y.app.data.remote.LoginBody(email, password))
            if (!res.isSuccessful) {
                val body = res.errorBody()?.string() ?: ""
                throw AuthError(res.code(), body)
            }
            val auth = res.body() ?: throw AuthError(res.code(), "Respuesta vacía del servidor")
            tokenStore.setTokens(auth.accessToken, auth.refreshToken, auth.user.id)
            auth
        }
    }

    suspend fun googleLogin(idToken: String): Result<AuthResponse> {
        Pr4yLog.i("AuthRepository: Initiating server-side Google validation")
        return runCatching {
            val res = api.googleLogin(GoogleLoginBody(idToken))
            if (!res.isSuccessful) {
                val body = res.errorBody()?.string() ?: ""
                throw AuthError(res.code(), body)
            }
            val auth = res.body() ?: throw AuthError(res.code(), "Respuesta vacía del servidor")
            tokenStore.setTokens(auth.accessToken, auth.refreshToken, auth.user.id)
            auth
        }.recoverCatching { e ->
            throw when (e) {
                is UnknownHostException, is ConnectException, is SocketTimeoutException -> {
                    Pr4yLog.net("AuthRepository: googleLogin network/DNS error")
                    Exception("Comprueba tu conexión a internet e inténtalo de nuevo.")
                }
                else -> e
            }
        }
    }

    suspend fun logout() {
        withContext(Dispatchers.IO) {
            Pr4yLog.i("AuthRepository: Performing full logout and búnker cleanup")
            tokenStore.clear()
            try {
                AppContainer.db.clearAllTables()
            } catch (e: Exception) {
                Pr4yLog.e("AuthRepository: Error clearing tables on logout", e)
            }
        }
    }

    suspend fun logoutRemote() {
        val refresh = tokenStore.getRefreshToken() ?: return
        try {
            api.logout(RefreshBody(refresh))
        } catch (e: Exception) {
            Pr4yLog.e("AuthRepository: Remote logout failed", e)
        }
        logout()
    }

    fun getAccessToken(): String? = tokenStore.getAccessToken()

    fun getBearer(): String? = tokenStore.getAccessToken()?.let { "Bearer $it" }

    suspend fun refreshToken(): Boolean {
        val refresh = tokenStore.getRefreshToken() ?: return false
        val res = api.refresh(RefreshBody(refresh))
        if (!res.isSuccessful) return false
        val auth = res.body() ?: run {
            Pr4yLog.e("AuthRepository: refreshToken body null con código ${res.code()}")
            return false
        }
        tokenStore.setTokens(auth.accessToken, auth.refreshToken, auth.user.id)
        return true
    }

    fun savePassphrase(passphrase: String) = tokenStore.savePassphrase(passphrase)
    fun getPassphrase(): String? = tokenStore.getPassphrase()
    fun clearPassphrase() = tokenStore.clearPassphrase()
    fun isBiometricEnabled(): Boolean = tokenStore.isBiometricEnabled()
}

class AuthError(val code: Int, override val message: String) : Exception(message)
