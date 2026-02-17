package com.pr4y.app.data.auth

import com.pr4y.app.data.remote.ApiService
import com.pr4y.app.data.remote.AuthResponse
import com.pr4y.app.data.remote.GoogleLoginBody
import com.pr4y.app.data.remote.PublicConfigResponse
import com.pr4y.app.data.remote.RefreshBody
import com.pr4y.app.util.Pr4yLog
import retrofit2.Response
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Tech Lead Note: Managed authentication repository.
 * Standards: Separation of concerns, clear error handling, and security-first IDs.
 */
class AuthRepository(
    private val api: ApiService,
    private val tokenStore: AuthTokenStore,
) {
    fun hasToken(): Boolean = tokenStore.getAccessToken() != null

    /** 
     * Gets public configuration. 
     * Security Standard: Credential Manager REQUIRES the Web Client ID for cross-platform token validation.
     */
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
            val auth = res.body()!!
            tokenStore.setTokens(auth.accessToken, auth.refreshToken)
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
            val auth = res.body()!!
            tokenStore.setTokens(auth.accessToken, auth.refreshToken)
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
            val auth = res.body()!!
            tokenStore.setTokens(auth.accessToken, auth.refreshToken)
            auth
        }.recoverCatching { e ->
            throw when (e) {
                is UnknownHostException, is ConnectException, is SocketTimeoutException -> {
                    Pr4yLog.e("AuthRepository: googleLogin network/DNS error", e)
                    Exception("Comprueba tu conexión a internet e inténtalo de nuevo.")
                }
                else -> e
            }
        }
    }

    fun logout() {
        tokenStore.clear()
    }

    suspend fun logoutRemote() {
        val refresh = tokenStore.getRefreshToken() ?: return
        api.logout(RefreshBody(refresh))
        tokenStore.clear()
    }

    fun getAccessToken(): String? = tokenStore.getAccessToken()

    fun getBearer(): String? = tokenStore.getAccessToken()?.let { "Bearer $it" }

    suspend fun refreshToken(): Boolean {
        val refresh = tokenStore.getRefreshToken() ?: return false
        val res = api.refresh(RefreshBody(refresh))
        if (!res.isSuccessful) return false
        val auth = res.body()!!
        tokenStore.setTokens(auth.accessToken, auth.refreshToken)
        return true
    }

    fun savePassphrase(passphrase: String) = tokenStore.savePassphrase(passphrase)
    fun getPassphrase(): String? = tokenStore.getPassphrase()
    fun clearPassphrase() = tokenStore.clearPassphrase()
    fun isBiometricEnabled(): Boolean = tokenStore.isBiometricEnabled()
}

class AuthError(val code: Int, override val message: String) : Exception(message)
