package com.pr4y.app.data.auth

import com.pr4y.app.data.remote.ApiService
import com.pr4y.app.data.remote.AuthResponse
import com.pr4y.app.data.remote.RefreshBody
import retrofit2.Response

class AuthRepository(
    private val api: ApiService,
    private val tokenStore: AuthTokenStore,
) {
    fun hasToken(): Boolean = tokenStore.getAccessToken() != null

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

    // --- Soporte Biometría ---
    fun savePassphrase(passphrase: String) = tokenStore.savePassphrase(passphrase)
    fun getPassphrase(): String? = tokenStore.getPassphrase()
    fun isBiometricEnabled(): Boolean = tokenStore.isBiometricEnabled()
}

class AuthError(val code: Int, override val message: String) : Exception(message)
