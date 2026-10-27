package com.clinicaserena.app.data.push

import com.clinicaserena.app.core.network.ApiCaller
import com.clinicaserena.app.core.network.ApiResult
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.PUT

/**
 * `RegisterPushDeviceRequest` de Spring: `token` (20–4096, `[A-Za-z0-9:_-]`) y `platform` (solo
 * `ANDROID`). Spring rechaza campos extra. `toString` oculta el token.
 */
@Serializable
data class RegisterPushDeviceRequest(val token: String, val platform: String) {
    override fun toString(): String = "RegisterPushDeviceRequest(token=<oculto>, platform=$platform)"
}

/** `RemovePushDeviceRequest` de Spring: solo `token`. */
@Serializable
data class RemovePushDeviceRequest(val token: String) {
    override fun toString(): String = "RemovePushDeviceRequest(token=<oculto>)"
}

/**
 * `PatientPushDeviceController` (rol PACIENTE). Ambos responden 204. El retiro usa POST con cuerpo para
 * que el token nunca viaje en la URL.
 */
interface PushDeviceApi {
    @PUT("pacientes/me/dispositivos-push")
    suspend fun register(@Body body: RegisterPushDeviceRequest): Response<Unit>

    @POST("pacientes/me/dispositivos-push/retiro")
    suspend fun remove(@Body body: RemovePushDeviceRequest): Response<Unit>
}

interface PushDeviceRepository {
    /** Alta idempotente del token de este teléfono para el paciente de la sesión. */
    suspend fun register(token: String): ApiResult<Unit>

    /** Retiro idempotente: 204 aunque el token no exista o sea de otra cuenta. */
    suspend fun remove(token: String): ApiResult<Unit>
}

class RemotePushDeviceRepository(
    private val api: PushDeviceApi,
    private val apiCaller: ApiCaller,
) : PushDeviceRepository {

    override suspend fun register(token: String): ApiResult<Unit> =
        apiCaller.callNoContent(authenticated = true) { api.register(RegisterPushDeviceRequest(token, PLATFORM_ANDROID)) }

    // Se usa justo antes del logout: `authenticated = false` evita que un 401 aquí abra el re-login
    // (la sesión se está cerrando de todos modos).
    override suspend fun remove(token: String): ApiResult<Unit> =
        apiCaller.callNoContent(authenticated = false) { api.remove(RemovePushDeviceRequest(token)) }

    private companion object {
        const val PLATFORM_ANDROID = "ANDROID"
    }
}
