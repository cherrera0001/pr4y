package com.pr4y.app.data.auth

import com.pr4y.app.data.remote.ApiService
import com.pr4y.app.data.remote.AuthResponse
import com.pr4y.app.data.remote.GoogleLoginBody
import com.pr4y.app.data.remote.LoginBody
import com.pr4y.app.data.remote.RefreshBody
import com.pr4y.app.data.remote.RegisterBody
import com.pr4y.app.data.remote.UserDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.net.UnknownHostException

class AuthRepositoryTest {

    private lateinit var api: ApiService
    private lateinit var tokenStore: AuthTokenStore
    private lateinit var repo: AuthRepository

    @Before
    fun setUp() {
        api = mockk()
        tokenStore = mockk(relaxed = true)
        repo = AuthRepository(api, tokenStore)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun fakeAuthResponse() = AuthResponse(
        accessToken = "access_abc",
        refreshToken = "refresh_xyz",
        expiresIn = 900,
        user = UserDto(id = "user-1", email = "test@pr4y.cl", createdAt = "2024-01-01"),
    )

    private fun errorBody(json: String = "{}") =
        json.toResponseBody("application/json".toMediaType())

    private fun nullBodySuccessResponse(): Response<AuthResponse> = mockk {
        every { isSuccessful } returns true
        every { body() } returns null
        every { code() } returns 200
    }

    // ── register() ────────────────────────────────────────────────────────────

    @Test
    fun `register - success returns AuthResponse and stores tokens`() = runTest {
        val auth = fakeAuthResponse()
        coEvery { api.register(any()) } returns Response.success(auth)

        val result = repo.register("test@pr4y.cl", "password123")

        assertTrue(result.isSuccess)
        assertEquals(auth, result.getOrThrow())
        verify { tokenStore.setTokens("access_abc", "refresh_xyz", "user-1") }
    }

    @Test
    fun `register - 409 conflict throws AuthError with correct code`() = runTest {
        coEvery { api.register(any()) } returns Response.error(409, errorBody())

        val result = repo.register("dup@pr4y.cl", "password123")

        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue("Expected AuthError", ex is AuthError)
        assertEquals(409, (ex as AuthError).code)
    }

    @Test
    fun `register - null body no longer crashes, returns AuthError`() = runTest {
        coEvery { api.register(any()) } returns nullBodySuccessResponse()

        val result = repo.register("test@pr4y.cl", "password123")

        // Antes del fix: NullPointerException. Ahora: fallo controlado.
        assertTrue("Debe fallar con AuthError, no con NPE", result.isFailure)
        assertTrue(result.exceptionOrNull() is AuthError)
    }

    @Test
    fun `register - tokens NOT stored when body is null`() = runTest {
        coEvery { api.register(any()) } returns nullBodySuccessResponse()

        repo.register("test@pr4y.cl", "password123")

        verify(exactly = 0) { tokenStore.setTokens(any(), any(), any()) }
    }

    // ── login() ───────────────────────────────────────────────────────────────

    @Test
    fun `login - success stores tokens`() = runTest {
        val auth = fakeAuthResponse()
        coEvery { api.login(any()) } returns Response.success(auth)

        val result = repo.login("test@pr4y.cl", "password123")

        assertTrue(result.isSuccess)
        verify { tokenStore.setTokens(any(), any(), any()) }
    }

    @Test
    fun `login - 401 throws AuthError`() = runTest {
        coEvery { api.login(any()) } returns Response.error(401, errorBody())

        val result = repo.login("test@pr4y.cl", "wrong_password")

        assertTrue(result.isFailure)
        assertEquals(401, (result.exceptionOrNull() as AuthError).code)
    }

    @Test
    fun `login - null body no longer crashes`() = runTest {
        coEvery { api.login(any()) } returns nullBodySuccessResponse()

        val result = repo.login("test@pr4y.cl", "password123")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AuthError)
    }

    // ── googleLogin() ─────────────────────────────────────────────────────────

    @Test
    fun `googleLogin - success returns AuthResponse`() = runTest {
        val auth = fakeAuthResponse()
        coEvery { api.googleLogin(any()) } returns Response.success(auth)

        val result = repo.googleLogin("google-id-token")

        assertTrue(result.isSuccess)
        assertEquals(auth, result.getOrThrow())
    }

    @Test
    fun `googleLogin - null body no longer crashes`() = runTest {
        coEvery { api.googleLogin(any()) } returns nullBodySuccessResponse()

        val result = repo.googleLogin("google-id-token")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AuthError)
    }

    @Test
    fun `googleLogin - UnknownHostException retorna mensaje legible en español`() = runTest {
        coEvery { api.googleLogin(any()) } throws UnknownHostException("Unable to resolve host")

        val result = repo.googleLogin("google-id-token")

        assertTrue(result.isFailure)
        val msg = result.exceptionOrNull()?.message ?: ""
        assertTrue("El mensaje debe mencionar conexión", msg.contains("conexión"))
    }

    @Test
    fun `googleLogin - server error 500 throws AuthError`() = runTest {
        coEvery { api.googleLogin(any()) } returns Response.error(500, errorBody())

        val result = repo.googleLogin("google-id-token")

        assertTrue(result.isFailure)
        assertEquals(500, (result.exceptionOrNull() as AuthError).code)
    }

    // ── refreshToken() ────────────────────────────────────────────────────────

    @Test
    fun `refreshToken - no stored refresh token returns false without API call`() = runTest {
        every { tokenStore.getRefreshToken() } returns null

        val result = repo.refreshToken()

        assertFalse(result)
        coVerify(exactly = 0) { api.refresh(any()) }
    }

    @Test
    fun `refreshToken - server 401 returns false`() = runTest {
        every { tokenStore.getRefreshToken() } returns "old-refresh"
        coEvery { api.refresh(any()) } returns Response.error(401, errorBody())

        val result = repo.refreshToken()

        assertFalse(result)
    }

    @Test
    fun `refreshToken - null body returns false instead of crashing`() = runTest {
        every { tokenStore.getRefreshToken() } returns "old-refresh"
        coEvery { api.refresh(any()) } returns nullBodySuccessResponse()

        // Antes del fix: NullPointerException propagado al caller.
        // Ahora: false + log, sin crash.
        val result = repo.refreshToken()

        assertFalse(result)
        verify(exactly = 0) { tokenStore.setTokens(any(), any(), any()) }
    }

    @Test
    fun `refreshToken - success updates tokens and returns true`() = runTest {
        val auth = fakeAuthResponse()
        every { tokenStore.getRefreshToken() } returns "old-refresh"
        coEvery { api.refresh(RefreshBody("old-refresh")) } returns Response.success(auth)

        val result = repo.refreshToken()

        assertTrue(result)
        verify { tokenStore.setTokens("access_abc", "refresh_xyz", "user-1") }
    }

    // ── hasToken() / getUserId() ──────────────────────────────────────────────

    @Test
    fun `hasToken returns false when no access token stored`() {
        every { tokenStore.getAccessToken() } returns null

        assertFalse(repo.hasToken())
    }

    @Test
    fun `hasToken returns true when access token present`() {
        every { tokenStore.getAccessToken() } returns "bearer-token"

        assertTrue(repo.hasToken())
    }

    @Test
    fun `getBearer returns null when no token`() {
        every { tokenStore.getAccessToken() } returns null

        val bearer = repo.getBearer()

        assertNotNull(bearer).let { /* noop */ }
        assertEquals(null, bearer)
    }

    @Test
    fun `getBearer returns Bearer prefixed token`() {
        every { tokenStore.getAccessToken() } returns "my-token"

        assertEquals("Bearer my-token", repo.getBearer())
    }
}
