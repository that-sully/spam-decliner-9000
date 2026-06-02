package com.example.spam_decliner_9000.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// ---------------------------------------------------------------------------
// FTC Socrata response model
// ---------------------------------------------------------------------------

/**
 * A single record from the FTC Do Not Call complaint dataset.
 * Fetched via the Socrata open data API:
 *   https://data.ftc.gov/resource/dumd-b9yd.json
 *
 * This dataset contains phone numbers reported by consumers for making
 * illegal robocalls or violating the Do Not Call Registry, published and
 * regularly updated by the Federal Trade Commission.
 */
@JsonClass(generateAdapter = true)
data class FtcComplaintRecord(
    @Json(name = "phone_number_calling_you") val phoneNumber: String?
)

// ---------------------------------------------------------------------------
// FTC Socrata service interface
// ---------------------------------------------------------------------------

/**
 * Fetches pages of DNC complaint records from the FTC's Socrata API.
 * Used exclusively by [SpamSyncWorker] for weekly bulk DB seeding.
 *
 * Docs: https://dev.socrata.com/foundry/data.ftc.gov/dumd-b9yd
 */
interface FtcApiService {
    @GET("dumd-b9yd.json?\$select=phone_number_calling_you")
    suspend fun getComplaints(
        @Query("\$limit")  limit: Int = 50_000,
        @Query("\$offset") offset: Int = 0
    ): List<FtcComplaintRecord>
}

// ---------------------------------------------------------------------------
// Client singleton
// ---------------------------------------------------------------------------

object SpamApiClient {

    private const val FTC_BASE_URL = "https://data.ftc.gov/resource/"

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS) // FTC bulk pages can be large
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
        )
        .build()

    val ftcService: FtcApiService by lazy {
        Retrofit.Builder()
            .baseUrl(FTC_BASE_URL)
            .client(okHttp)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(FtcApiService::class.java)
    }
}
