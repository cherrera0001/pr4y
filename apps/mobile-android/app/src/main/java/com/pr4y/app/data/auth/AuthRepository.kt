package com.pr4y.app.data.auth

import com.pr4y.app.data.remote.ApiService
import com.pr4y.app.data.remote.AuthResponse
import com.pr4y.app.data.remote.GoogleLoginBody
import com.pr4y.app.data.remote.RefreshBody
import com.pr4y.app.util.Pr4yLog
import retrofit2.Response

class AuthRepository(
    private val api: ApiService,
    private val tokenStore: AuthTokenStore,
) {
    fun hasToken(): Boolean = tokenStore.getAccessToken() != null

    suspend fun register(email: String, password: String): Result<AuthResponse> {
        Pr4yLog.i("Iniciando registro para: $email")
        return runCatching {
            val res = api.register(com.pr4y.app.data.remote.RegisterBody(email, password))
            if (!res.isSuccessful) {
                val body = res.errorBody()?.string() ?: ""
                Pr4yLog.e("Error en registro [${res.code()}]: $body")
                throw AuthError(res.code(), body)
            }
            val auth = res.body()!!
            tokenStore.setTokens(auth.accessToken, auth.refreshToken)
            Pr4yLog.i("Registro exitoso para: $email")
            auth
        }
    }

    suspend fun login(email: String, password: String): Result<AuthResponse> {
        Pr4yLog.i("Iniciando login para: $email")
        return runCatching {
            val res = api.login(com.pr4y.app.data.remote.LoginBody(email, password))
            if (!res.isSuccessful) {
                val body = res.errorBody()?.string() ?: ""
                Pr4yLog.e("Error en login [${res.code()}]: $body")
                throw AuthError(res.code(), body)
            }
            val auth = res.body()!!
            tokenStore.setTokens(auth.accessToken, auth.refreshToken)
            Pr4yLog.i("Login exitoso para: $email")
            auth
        }
    }

    suspend fun googleLogin(idToken: String): Result<AuthResponse> {
        Pr4yLog.i("Iniciando Google Login...")
        return runCatching {
            val res = api.googleLogin(GoogleLoginBody(idToken))
            if (!res.isSuccessful) {
                val body = res.errorBody()?.string() ?: ""
                Pr4yLog.e("Error en Google Login [${res.code()}]: $body")
                throw AuthError(res.code(), body)
            }
            val auth = res.body()!!
            tokenStore.setTokens(auth.accessToken, auth.refreshToken)
            Pr4yLog.i("Google Login exitoso")
            auth
        }
    }

    fun logout() {
        Pr4yLog.i("Cerrando sesión local")
        tokenStore.clear()
    }

    suspend fun logoutRemote() {
        Pr4yLog.i("Cerrando sesión remota")
        val refresh = tokenStore.getRefreshToken() ?: return
        api.logout(RefreshBody(refresh))
        tokenStore.clear()
    }

    fun getAccessToken(): String? = tokenStore.getAccessToken()

    fun getBearer(): String? = tokenStore.getAccessToken()?.let { "Bearer $it" }

    suspend fun refreshToken(): Boolean {
        Pr4yLog.i("Refrescando token")
        val refresh = tokenStore.getRefreshToken() ?: return false
        val res = api.refresh(RefreshBody(refresh))
        if (!res.isSuccessful) {
            Pr4yLog.e("Error al refrescar token: ${res.code()}")
            return false
        }
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
