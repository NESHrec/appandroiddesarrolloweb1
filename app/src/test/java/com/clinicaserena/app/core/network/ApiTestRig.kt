package com.clinicaserena.app.core.network

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import retrofit2.Retrofit

/** Servidor simulado + el mismo cliente HTTP de la app (interceptores, logging de debug y ApiCaller). */
class ApiTestRig : AutoCloseable {
    val server = MockWebServer().apply { start() }
    var token: String? = TOKEN
    var expiredCount = 0
        private set
    val logLines = mutableListOf<String>()

    val retrofit: Retrofit = NetworkModule.retrofit(
        baseUrl = server.url("/api/v1/").toString(),
        client = NetworkModule.okHttpClient(
            tokenProvider = { token },
            extraInterceptors = listOf(createLoggingInterceptor { logLines += it }),
        ),
    )

    val apiCaller = ApiCaller(ErrorMapper(NetworkModule.json)) { expiredCount++ }

    fun enqueue(code: Int, body: String = "") {
        server.enqueue(
            MockResponse.Builder()
                .code(code)
                .addHeader("Content-Type", "application/json")
                .body(body)
                .build(),
        )
    }

    override fun close() {
        server.close()
    }

    companion object {
        const val TOKEN = "token-secreto-de-prueba"

        fun error(status: Int, code: String) =
            """{"status":$status,"code":"$code","message":"Mensaje","fieldErrors":[]}"""
    }
}
