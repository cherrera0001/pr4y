package com.pr4y.app.data.remote

import org.json.JSONObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

data class RegisterBody(val email: String, val password: String)
data class LoginBody(val email: String, val password: String)
data class GoogleLoginBody(val idToken: String)
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
    val status: String = "PENDING",
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
data class RejectedDto(
    val recordId: String,
    val reason: String,
    val serverVersion: Int? = null,
    val serverUpdatedAt: String? = null,
)

data class AnswersStatsResponse(val answeredCount: Int)

data class AnswerDto(
    val id: String,
    val recordId: String,
    val answeredAt: String,
    val testimony: String?,
    val record: AnswerRecordDto?,
)
data class AnswerRecordDto(val id: String, val type: String, val clientUpdatedAt: String)
data class AnswersListResponse(val answers: List<AnswerDto>)

data class AnswerBody(val recordId: String, val testimony: String? = null)

data class PublicConfigResponse(
    val googleWebClientId: String,
    val googleAndroidClientId: String = "",
)

data class ReminderPreferencesResponse(
    val time: String,
    val daysOfWeek: List<Int>,
    val enabled: Boolean,
)

data class ReminderScheduleDto(
    val id: String? = null,
    val time: String,
    val daysOfWeek: List<Int>,
    val enabled: Boolean,
)

data class ReminderSchedulesResponse(
    val schedules: List<ReminderScheduleDto>,
)

data class PublicRequestDto(
    val id: String,
    val title: String,
    val body: String?,
    val prayerCount: Int,
    val createdAt: String
)

data class PublicRequestsResponse(val requests: List<PublicRequestDto>)

data class DisplayPreferencesDto(
    val theme: String,
    val fontSize: String,
    val fontFamily: String,
    val lineSpacing: String,
    val contemplativeMode: Boolean,
)

data class GlobalContentItemDto(
    val id: String,
    val type: String,
    val title: String,
    val body: String,
    val published: Boolean,
    val sortOrder: Int,
    val createdAt: String,
    val updatedAt: String,
)
data class GlobalContentResponse(val items: List<GlobalContentItemDto>)

interface ApiService {
    @GET("health")
    suspend fun health(): Response<Map<String, String>>

    @GET("config")
    suspend fun getPublicConfig(): Response<PublicConfigResponse>

    @POST("auth/register")
    suspend fun register(@Body body: RegisterBody): Response<AuthResponse>

    @POST("auth/login")
    suspend fun login(@Body body: LoginBody): Response<AuthResponse>

    @POST("auth/google")
    suspend fun googleLogin(@Body body: GoogleLoginBody): Response<AuthResponse>

    @POST("auth/refresh")
    suspend fun refresh(@Body body: RefreshBody): Response<AuthResponse>

    @POST("auth/logout")
    suspend fun logout(@Body body: RefreshBody): Response<Map<String, Boolean>>

    @GET("crypto/wrapped-dek")
    suspend fun getWrappedDek(@Header("Authorization") bearer: String): Response<WrappedDekResponse>

    @PUT("crypto/wrapped-dek")
    suspend fun putWrappedDek(
        @Header("Authorization") bearer: String,
        @Body body: WrappedDekBody,
    ): Response<Map<String, Boolean>>

    @GET("sync/pull")
    suspend fun pull(
        @Header("Authorization") bearer: String,
        @Query("cursor") cursor: String?,
        @Query("limit") limit: Int?,
    ): Response<PullResponse>

    @POST("sync/push")
    suspend fun push(
        @Header("Authorization") bearer: String,
        @Body body: PushBody,
    ): Response<PushResponse>

    @GET("answers/stats")
    suspend fun getAnswersStats(@Header("Authorization") bearer: String): Response<AnswersStatsResponse>

    @GET("answers")
    suspend fun getAnswers(@Header("Authorization") bearer: String): Response<AnswersListResponse>

    @POST("answers")
    suspend fun createAnswer(
        @Header("Authorization") bearer: String,
        @Body body: AnswerBody,
    ): Response<Map<String, Any>>

    // --- Preferencias de Usuario (rutas deben coincidir con API: user/display-preferences, user/reminder-schedules) ---

    @GET("user/reminder-schedules")
    suspend fun getReminderSchedules(@Header("Authorization") bearer: String): Response<ReminderSchedulesResponse>

    @PUT("user/reminder-schedules")
    suspend fun putReminderSchedules(
        @Header("Authorization") bearer: String,
        @Body body: ReminderSchedulesResponse,
    ): Response<ReminderSchedulesResponse>

    @GET("user/display-preferences")
    suspend fun getDisplayPreferences(@Header("Authorization") bearer: String): Response<DisplayPreferencesDto>

    @PUT("user/display-preferences")
    suspend fun putDisplayPreferences(
        @Header("Authorization") bearer: String,
        @Body body: DisplayPreferencesDto,
    ): Response<DisplayPreferencesDto>

    // --- Roulette (Anonymous) ---
    
    @GET("public/requests")
    suspend fun getPublicRequests(@Header("X-Anonymous") anon: String = "true"): Response<PublicRequestsResponse>

    @POST("public/requests/{id}/pray")
    suspend fun prayForPublicRequest(
        @Path("id") id: String,
        @Header("X-Anonymous") anon: String = "true"
    ): Response<Map<String, Any>>

    /** GET /v1/public/content?type=... — contenido global publicado por admin. */
    @GET("public/content")
    suspend fun getGlobalContent(@Query("type") type: String? = null): Response<GlobalContentResponse>
}

fun parseApiErrorMessage(jsonBody: String?): String? {
    if (jsonBody.isNullOrBlank()) return null
    return try {
        val obj = JSONObject(jsonBody)
        val err = obj.optJSONObject("error") ?: return null
        err.optString("message").takeIf { it.isNotEmpty() }
    } catch (_: Exception) {
        null
    }
}
