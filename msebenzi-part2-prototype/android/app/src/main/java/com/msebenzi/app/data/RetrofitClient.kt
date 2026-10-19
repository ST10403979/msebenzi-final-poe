package com.msebenzi.app.data

import com.msebenzi.app.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Single shared Retrofit instance for the whole app. The base URL comes from
 * BuildConfig.API_BASE_URL (set in app/build.gradle.kts).
 *
 * The hosted API runs on a free Render instance that "sleeps" when idle, so the first
 * request after a while can take up to a minute; the timeouts below allow for that.
 */
object RetrofitClient {

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        // Bodies contain tokens/passwords, so only log them in debug builds.
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BASIC
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    /** Shared OkHttp client (also reused by Coil for authenticated image downloads). */
    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val api: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    /** Full URL of a stored image BLOB. */
    fun mediaUrl(mediaId: Int): String = "${BuildConfig.API_BASE_URL}api/media/$mediaId"
}
