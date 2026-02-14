package com.pr4y.app.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Query

data class RegisterBody(val email: String, val password: String)
data class LoginBody(val email: String, val password: String)
data class RefreshBody(val refreshToken: String)
data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Int,
    val user: UserDto,
)
data class UserDto(val id: String, val email: String, val createdAt: String)

data class WrappedDekBody(
    val kdf: KdfDto,
    val wrappedDekB64: String,
)
data class KdfDto(val name: String, val params: Map<String, Any>, val saltB64: String)
data class WrappedDekResponse(val kdf: KdfDto, val wrappedDekB64: String)

data class PullResponse(val nextCursor: String, val records: List<SyncRecordDto>)
data class SyncRecordDto(
    val recordId: String,
    val type: String,
    val version: Int,
    val encryptedPayloadB64: String,
    val clientUpdatedAt: String,
    val serverUpdatedAt: String,
    val deleted: Boolean,
)

data class PushBody(val records: List<PushRecordDto>)
data class PushRecordDto(
    val recordId: String,
    val type: String,
    val version: Int,
    val encryptedPayloadB64: String,
    val clientUpdatedAt: String,
    val deleted: Boolean,
)
data class PushResponse(
    val accepted: List<String>,
    val rejected: List<RejectedDto>,
    val serverTime: String,
)
data class RejectedDto(val recordId: String, val reason: String)

interface ApiService {
    @GET("v1/health")
    suspend fun health(): Response<Map<String, String>>

    @POST("v1/auth/register")
    suspend fun register(@Body body: RegisterBody): Response<AuthResponse>

    @POST("v1/auth/login")
    suspend fun login(@Body body: LoginBody): Response<AuthResponse>

    @POST("v1/auth/refresh")
    suspend fun refresh(@Body body: RefreshBody): Response<AuthResponse>

    @POST("v1/auth/logout")
    suspend fun logout(@Body body: RefreshBody): Response<Map<String, Boolean>>

    @GET("v1/crypto/wrapped-dek")
    suspend fun getWrappedDek(@Header("Authorization") bearer: String): Response<WrappedDekResponse>

    @PUT("v1/crypto/wrapped-dek")
    suspend fun putWrappedDek(
        @Header("Authorization") bearer: String,
        @Body body: WrappedDekBody,
    ): Response<Map<String, Boolean>>

    @GET("v1/sync/pull")
    suspend fun pull(
        @Header("Authorization") bearer: String,
        @Query("cursor") cursor: String?,
        @Query("limit") limit: Int?,
    ): Response<PullResponse>

    @POST("v1/sync/push")
    suspend fun push(
        @Header("Authorization") bearer: String,
        @Body body: PushBody,
    ): Response<PushResponse>
}
