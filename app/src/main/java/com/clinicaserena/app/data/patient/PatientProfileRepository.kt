package com.clinicaserena.app.data.patient

import com.clinicaserena.app.core.network.ApiCaller
import com.clinicaserena.app.core.network.ApiResult
import com.clinicaserena.app.core.time.ClinicTime
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import java.time.OffsetDateTime

/** `GET /pacientes/me/perfil` (`PatientProfileController`). La identidad sale de la sesión. */
@Serializable
data class PatientProfileResponse(
    val patientId: String,
    val fullName: String,
    val email: String,
    val accountStatus: String,
    val registeredAt: String,
)

interface PatientProfileApi {
    @GET("pacientes/me/perfil")
    suspend fun getOwn(): Response<PatientProfileResponse>
}

data class PatientProfile(
    val fullName: String,
    val email: String,
    val accountStatus: String,
    val registeredAt: OffsetDateTime?,
)

interface PatientProfileRepository {
    suspend fun getOwn(): ApiResult<PatientProfile>
}

class RemotePatientProfileRepository(
    private val api: PatientProfileApi,
    private val apiCaller: ApiCaller,
) : PatientProfileRepository {

    override suspend fun getOwn(): ApiResult<PatientProfile> =
        when (val result = apiCaller.call(authenticated = true) { api.getOwn() }) {
            is ApiResult.Success -> ApiResult.Success(result.data.toDomain())
            is ApiResult.Failure -> result
        }

    private fun PatientProfileResponse.toDomain() = PatientProfile(
        fullName = fullName,
        email = email,
        accountStatus = accountStatus,
        registeredAt = runCatching { ClinicTime.parse(registeredAt) }.getOrNull(),
    )
}
