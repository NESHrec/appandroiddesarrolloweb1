package com.clinicaserena.app.data.doctor

import com.clinicaserena.app.core.network.ApiCaller
import com.clinicaserena.app.core.network.ApiResult
import com.clinicaserena.app.core.time.ClinicTime
import com.clinicaserena.app.data.patient.AttentionResponse
import com.clinicaserena.app.data.patient.ClinicalProfileResponse
import com.clinicaserena.app.data.patient.ClinicalRecordResponse
import com.clinicaserena.app.data.patient.toDomain
import com.clinicaserena.app.domain.model.AppointmentStatus
import com.clinicaserena.app.domain.model.Attention
import com.clinicaserena.app.domain.model.AttentionBlocker
import com.clinicaserena.app.domain.model.ClinicalProfile
import com.clinicaserena.app.domain.model.ClinicalRecord
import com.clinicaserena.app.domain.model.DoctorAppointment
import com.clinicaserena.app.domain.model.DoctorAppointmentDetail
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/** `MedicalAppointmentResponse` de Spring. */
@Serializable
data class MedicalAppointmentResponse(
    val id: String,
    val patientId: String,
    val patientName: String? = null,
    val practitionerId: String,
    val specialtyId: String,
    val specialtyName: String? = null,
    val scheduledAt: String,
    val status: String,
    val notes: String? = null,
    val arrivalAt: String? = null,
    val attentionRecorded: Boolean,
    val canRecordAttention: Boolean,
    val attentionBlockers: List<String> = emptyList(),
)

/** `MedicalAppointmentDetailResponse`: la cita y su atención, si existe. */
@Serializable
data class MedicalAppointmentDetailResponse(
    val appointment: MedicalAppointmentResponse,
    val attention: AttentionResponse? = null,
)

/** Ítem de receta de `RecordAttentionRequest`. */
@Serializable
data class PrescriptionItemRequest(
    val medicine: String,
    val dose: String,
    val frequency: String,
    val duration: String,
    val instructions: String?,
)

/**
 * `RecordAttentionRequest`: cita, paciente y profesional nunca van aquí (salen de la ruta y del token).
 * `prescription` no tiene valor por defecto para que se envíe siempre, aunque esté vacía: es obligatoria.
 */
@Serializable
data class RecordAttentionRequest(
    val reason: String,
    val findings: String?,
    val diagnosis: String,
    val treatmentPlan: String?,
    val prescription: List<PrescriptionItemRequest>,
)

/** `UpdateClinicalProfileRequest`: al menos un campo con texto. */
@Serializable
data class ClinicalProfileRequest(
    val allergies: String?,
    val relevantConditions: String?,
    val currentMedications: String?,
    val dentalHistory: String?,
)

/** `MedicalCareController`: el profesional sale de la cuenta autenticada; nunca se envía su ID. */
interface DoctorApi {
    @GET("medico/citas")
    suspend fun agenda(
        @Query("from") from: String,
        @Query("to") to: String,
        @Query("status") status: String?,
        @Query("limit") limit: Int,
    ): Response<List<MedicalAppointmentResponse>>

    @GET("medico/citas/{appointmentId}")
    suspend fun detail(@Path("appointmentId") appointmentId: String): Response<MedicalAppointmentDetailResponse>

    @GET("medico/citas/{appointmentId}/expediente")
    suspend fun record(@Path("appointmentId") appointmentId: String): Response<ClinicalRecordResponse>

    @POST("medico/citas/{appointmentId}/atencion")
    suspend fun recordAttention(
        @Path("appointmentId") appointmentId: String,
        @Body body: RecordAttentionRequest,
    ): Response<AttentionResponse>

    @POST("medico/citas/{appointmentId}/expediente/perfil")
    suspend fun addClinicalProfile(
        @Path("appointmentId") appointmentId: String,
        @Body body: ClinicalProfileRequest,
    ): Response<ClinicalProfileResponse>
}

/** Rango `[from, to)` con offset explícito para `/medico/citas`. */
data class DateRange(val from: OffsetDateTime, val to: OffsetDateTime)

interface DoctorRepository {
    /** Agenda propia, en orden ascendente. Spring devuelve como máximo [MAX_LIMIT] citas y no pagina. */
    suspend fun agenda(range: DateRange, status: AppointmentStatus? = null): ApiResult<List<DoctorAppointment>>
    suspend fun appointment(appointmentId: String): ApiResult<DoctorAppointmentDetail>

    /** Expediente del paciente de una cita propia. */
    suspend fun record(appointmentId: String): ApiResult<ClinicalRecord>

    /** Registra la atención (inmutable; una por cita). */
    suspend fun recordAttention(appointmentId: String, request: RecordAttentionRequest): ApiResult<Attention>

    /** Agrega una versión nueva del perfil clínico (las anteriores quedan en el historial). */
    suspend fun addClinicalProfile(appointmentId: String, request: ClinicalProfileRequest): ApiResult<ClinicalProfile>

    companion object {
        const val MAX_LIMIT = 100
    }
}

class RemoteDoctorRepository(
    private val api: DoctorApi,
    private val apiCaller: ApiCaller,
) : DoctorRepository {

    override suspend fun agenda(range: DateRange, status: AppointmentStatus?): ApiResult<List<DoctorAppointment>> =
        apiCaller.call(authenticated = true) {
            api.agenda(
                from = range.from.format(QUERY_FORMAT),
                to = range.to.format(QUERY_FORMAT),
                status = status?.name,
                limit = DoctorRepository.MAX_LIMIT,
            )
        }.mapSafely { list -> list.map { it.toDomain() } }

    override suspend fun appointment(appointmentId: String): ApiResult<DoctorAppointmentDetail> =
        apiCaller.call(authenticated = true) { api.detail(appointmentId) }.mapSafely {
            DoctorAppointmentDetail(it.appointment.toDomain(), it.attention?.toDomain())
        }

    override suspend fun record(appointmentId: String): ApiResult<ClinicalRecord> =
        apiCaller.call(authenticated = true) { api.record(appointmentId) }.mapSafely { it.toDomain() }

    override suspend fun recordAttention(appointmentId: String, request: RecordAttentionRequest): ApiResult<Attention> =
        apiCaller.call(authenticated = true) { api.recordAttention(appointmentId, request) }.mapSafely { it.toDomain() }

    override suspend fun addClinicalProfile(
        appointmentId: String,
        request: ClinicalProfileRequest,
    ): ApiResult<ClinicalProfile> =
        apiCaller.call(authenticated = true) { api.addClinicalProfile(appointmentId, request) }.mapSafely { it.toDomain() }

    private inline fun <T, R> ApiResult<T>.mapSafely(transform: (T) -> R): ApiResult<R> = when (this) {
        is ApiResult.Success -> try {
            ApiResult.Success(transform(data))
        } catch (_: Exception) {
            ApiResult.Unexpected(null)
        }
        is ApiResult.Failure -> this
    }

    companion object {
        /** ISO 8601 con segundos y offset (`2026-10-05T00:00:00-06:00`); OkHttp codifica el `+` si lo hubiera. */
        val QUERY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")
    }
}

internal fun MedicalAppointmentResponse.toDomain() = DoctorAppointment(
    id = id,
    patientId = patientId,
    patientName = patientName,
    specialtyName = specialtyName,
    scheduledAt = ClinicTime.parse(scheduledAt),
    status = AppointmentStatus.from(status),
    statusRaw = status,
    notes = notes,
    arrivalAt = arrivalAt?.let { ClinicTime.parse(it) },
    attentionRecorded = attentionRecorded,
    canRecordAttention = canRecordAttention,
    blockers = attentionBlockers.map { AttentionBlocker.from(it) },
    blockersRaw = attentionBlockers,
)
