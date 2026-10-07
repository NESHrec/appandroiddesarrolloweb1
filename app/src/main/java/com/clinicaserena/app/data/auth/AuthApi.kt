package com.clinicaserena.app.data.auth

import com.clinicaserena.app.core.network.PublicEndpoint
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/** Endpoints de sesión verificados en el código de Spring (`AuthController`, `StaffAuthController`). */
interface AuthApi {

    @PublicEndpoint
    @POST("auth/login-unified")
    suspend fun loginUnified(@Body body: LoginRequest): Response<UnifiedLoginResponse>

    @GET("auth/me")
    suspend fun patientMe(): Response<PatientIdentityResponse>

    @POST("auth/logout")
    suspend fun patientLogout(): Response<Unit>

    @GET("staff/auth/me")
    suspend fun staffMe(): Response<StaffIdentityResponse>

    @POST("staff/auth/logout")
    suspend fun staffLogout(): Response<Unit>
}
