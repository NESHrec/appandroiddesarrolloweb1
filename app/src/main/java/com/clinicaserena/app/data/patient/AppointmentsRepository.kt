package com.clinicaserena.app.data.patient

import com.clinicaserena.app.core.network.ApiCaller
import com.clinicaserena.app.core.network.ApiResult
import com.clinicaserena.app.core.time.ClinicTime
import com.clinicaserena.app.domain.model.Appointment
import com.clinicaserena.app.domain.model.AppointmentStatus
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/** `CitaDto` de Spring. No trae nombres de médico ni de especialidad: se resuelven con el catálogo. */
@Serializable
data class AppointmentResponse(
    val id: String,
    val patientId: String,
    val practitionerId: String,
    val specialtyId: String,
    val scheduledAt: String,
    val status: String,
    val notes: String? = null,
    val amountCents: Long? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

/**
 * `CrearCitaRequest` de Spring (`additionalProperties: false`): solo estos campos. El `patientId` sale
 * del token; nunca se envía. `scheduledAt` es el `startAt` del bloque tal como llegó.
 */
@Serializable
data class CreateAppointmentRequest(
    val practitionerId: String,
    val specialtyId: String,
    val scheduledAt: String,
    val notes: String? = null,
)

/** `CitaController`: lista propia y reserva. */
interface AppointmentsApi {
    @GET("pacientes/me/citas")
    suspend fun listOwn(): Response<List<AppointmentResponse>>

    @POST("citas")
    suspend fun create(@Body body: CreateAppointmentRequest): Response<AppointmentResponse>
}

interface AppointmentsRepository {
    /** Lista completa de citas propias (el backend no pagina). */
    suspend fun listOwn(): ApiResult<List<Appointment>>

    suspend fun book(practitionerId: String, specialtyId: String, scheduledAtRaw: String, notes: String?): ApiResult<Appointment>
}

class RemoteAppointmentsRepository(
    private val api: AppointmentsApi,
    private val apiCaller: ApiCaller,
) : AppointmentsRepository {

    override suspend fun listOwn(): ApiResult<List<Appointment>> =
        when (val result = apiCaller.call(authenticated = true) { api.listOwn() }) {
            is ApiResult.Success -> result.data.mapOrUnexpected()
            is ApiResult.Failure -> result
        }

    override suspend fun book(
        practitionerId: String,
        specialtyId: String,
        scheduledAtRaw: String,
        notes: String?,
    ): ApiResult<Appointment> {
        val body = CreateAppointmentRequest(practitionerId, specialtyId, scheduledAtRaw, notes?.takeIf { it.isNotBlank() })
        return when (val result = apiCaller.call(authenticated = true) { api.create(body) }) {
            is ApiResult.Success -> listOf(result.data).mapOrUnexpected().let {
                when (it) {
                    is ApiResult.Success -> ApiResult.Success(it.data.first())
                    is ApiResult.Failure -> it
                }
            }
            is ApiResult.Failure -> result
        }
    }

    private fun List<AppointmentResponse>.mapOrUnexpected(): ApiResult<List<Appointment>> = try {
        ApiResult.Success(map { it.toDomain() })
    } catch (_: Exception) {
        ApiResult.Unexpected(null)
    }
}

internal fun AppointmentResponse.toDomain() = Appointment(
    id = id,
    practitionerId = practitionerId,
    specialtyId = specialtyId,
    scheduledAt = ClinicTime.parse(scheduledAt),
    status = AppointmentStatus.from(status),
    statusRaw = status,
    notes = notes,
    amountCents = amountCents,
)
