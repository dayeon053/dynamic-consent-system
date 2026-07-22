package com.dynamicconsent.data.remote

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Retrofit 인스턴스 팩토리.
 * 서버 배포 후 base URL만 실제 주소로 바꾸면 된다 (예: EC2 주소).
 */
object ApiClient {

    /** TODO: 백엔드 배포 후 실제 서버 주소로 교체 */
    const val DEFAULT_BASE_URL = "http://10.0.2.2:8080/"

    private val json = Json { ignoreUnknownKeys = true }

    fun create(baseUrl: String = DEFAULT_BASE_URL): ConsentRadarApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ConsentRadarApi::class.java)
    }
}
