package com.clinicaserena.app.core.network

import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {

    /**
     * Spring rechaza campos desconocidos (`fail-on-unknown-properties`), así que los DTO de petición
     * no deben llevar campos extra. Las respuestas toleran campos nuevos del backend.
     */
    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private const val TIMEOUT_SECONDS = 15L

    fun okHttpClient(
        tokenProvider: () -> String?,
        extraInterceptors: List<Interceptor> = emptyList(),
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .addInterceptor(AuthInterceptor(tokenProvider))
        .apply { extraInterceptors.forEach(::addInterceptor) }
        .build()

    fun retrofit(baseUrl: String, client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
}
