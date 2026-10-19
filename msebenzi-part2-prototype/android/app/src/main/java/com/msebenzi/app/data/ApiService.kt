package com.msebenzi.app.data

import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Defines every REST call the app makes against the Msebenzi API (see /backend).
 * Retrofit generates the networking code from this interface at compile time.
 *
 * Reference: Square (2024) Retrofit. Available at: https://square.github.io/retrofit/
 */
interface ApiService {

    // ---- Authentication ----

    @POST("api/auth/register")
    suspend fun register(@Body body: RegisterRequest): Response<AuthResponse>

    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): Response<AuthResponse>

    /** Single sign-on with a Google ID token (verified server-side). */
    @POST("api/auth/google")
    suspend fun googleLogin(@Body body: GoogleLoginRequest): Response<AuthResponse>

    // ---- Profile & settings ----

    @GET("api/users/me")
    suspend fun getProfile(@Header("Authorization") token: String): Response<User>

    @PUT("api/users/me")
    suspend fun updateProfile(
        @Header("Authorization") token: String,
        @Body body: UpdateProfileRequest
    ): Response<Map<String, Any>>

    @DELETE("api/users/me")
    suspend fun deleteAccount(@Header("Authorization") token: String): Response<Map<String, Any>>

    @PUT("api/users/me/fcm-token")
    suspend fun registerFcmToken(
        @Header("Authorization") token: String,
        @Body body: FcmTokenRequest
    ): Response<Map<String, Any>>

    @DELETE("api/users/me/fcm-token")
    suspend fun clearFcmToken(@Header("Authorization") token: String): Response<Map<String, Any>>

    // ---- Blob storage (images) ----

    /** Uploads raw image bytes (Content-Type image/jpeg) and returns the new media id. */
    @POST("api/media")
    suspend fun uploadMedia(
        @Header("Authorization") token: String,
        @Body body: RequestBody
    ): Response<MediaUploadResponse>

    // ---- Jobs ----

    @GET("api/jobs")
    suspend fun getJobs(
        @Header("Authorization") token: String,
        @Query("status") status: String? = null
    ): Response<List<JobDto>>

    @GET("api/jobs/{id}")
    suspend fun getJob(
        @Header("Authorization") token: String,
        @Path("id") id: Int
    ): Response<JobDto>

    @POST("api/jobs")
    suspend fun createJob(
        @Header("Authorization") token: String,
        @Body body: CreateJobRequest
    ): Response<JobDto>

    @PUT("api/jobs/{id}/accept")
    suspend fun acceptJob(
        @Header("Authorization") token: String,
        @Path("id") id: Int
    ): Response<Map<String, Any>>

    @PUT("api/jobs/{id}/complete")
    suspend fun completeJob(
        @Header("Authorization") token: String,
        @Path("id") id: Int
    ): Response<Map<String, Any>>

    // ---- Feature: Trusted ratings and reviews ----

    @POST("api/jobs/{id}/rate")
    suspend fun rateJob(
        @Header("Authorization") token: String,
        @Path("id") id: Int,
        @Body body: SubmitRatingRequest
    ): Response<Map<String, Any>>

    @GET("api/users/{id}/ratings")
    suspend fun getRatingsForUser(
        @Header("Authorization") token: String,
        @Path("id") id: Int
    ): Response<List<Rating>>

    // ---- Feature: Worker profile and verification ----

    @GET("api/users/{id}")
    suspend fun getUserProfile(
        @Header("Authorization") token: String,
        @Path("id") id: Int
    ): Response<User>

    @PUT("api/users/me/request-verification")
    suspend fun requestVerification(
        @Header("Authorization") token: String
    ): Response<Map<String, Any>>

    // ---- Feature: Targeted job alerts ----

    @GET("api/notifications")
    suspend fun getNotifications(
        @Header("Authorization") token: String
    ): Response<List<AppNotification>>

    @GET("api/notifications/unread-count")
    suspend fun getUnreadNotificationCount(
        @Header("Authorization") token: String
    ): Response<Map<String, Int>>

    @PUT("api/notifications/{id}/read")
    suspend fun markNotificationRead(
        @Header("Authorization") token: String,
        @Path("id") id: Int
    ): Response<AppNotification>
}
