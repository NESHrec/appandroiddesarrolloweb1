package com.clinicaserena.app.data.health

import com.clinicaserena.app.core.network.ApiCaller
import com.clinicaserena.app.core.network.ApiResult
import com.clinicaserena.app.core.network.PublicEndpoint
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET

@Serializable
data class HealthResponse(
    val status: String,
    val service: String,
)

interface HealthApi {
    @PublicEndpoint
    @GET("health")
    suspend fun health(): Response<HealthResponse>
}

/** Solo para el diagnóstico de debug: comprueba que el emulador alcanza el backend. */
class HealthRepository(
    private val api: HealthApi,
    private val apiCaller: ApiCaller,
) {
    suspend fun check(): ApiResult<HealthResponse> = apiCaller.call(authenticated = false) { api.health() }
}
