package com.clinicaserena.app.data.patient

import com.clinicaserena.app.core.network.ApiCaller
import com.clinicaserena.app.core.network.ApiResult
import com.clinicaserena.app.core.time.ClinicTime
import com.clinicaserena.app.domain.model.Addendum
import com.clinicaserena.app.domain.model.Attention
import com.clinicaserena.app.domain.model.Checkup
import com.clinicaserena.app.domain.model.ClinicalProfile
import com.clinicaserena.app.domain.model.ClinicalRecord
import com.clinicaserena.app.domain.model.Prescription
import com.clinicaserena.app.domain.model.PrescriptionItem
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET

// DTO de Spring (`PatientPrescriptionResponse`, `PatientCheckupResponse`, `ClinicalRecordResponse`).
// Los nombres pueden faltar (`null`) si el profesional o el autor no se encuentran.

@Serializable
data class PrescriptionItemResponse(
    val id: String? = null,
    val order: Int,
    val medicine: String,
    val dose: String,
    val frequency: String,
    val duration: String,
    val instructions: String? = null,
)

@Serializable
data class PrescriptionResponse(
    val id: String,
    val appointmentId: String,
    val appointmentScheduledAt: String? = null,
    val issuedAt: String,
    val practitionerId: String? = null,
    val practitionerName: String? = null,
    val items: List<PrescriptionItemResponse> = emptyList(),
)

@Serializable
data class CheckupResponse(
    val id: String,
    val appointmentId: String,
    val appointmentScheduledAt: String? = null,
    val recordedAt: String,
    val practitionerId: String? = null,
    val practitionerName: String? = null,
    val reason: String,
    val findings: String? = null,
    val diagnosis: String,
    val treatmentPlan: String? = null,
    val prescription: List<PrescriptionItemResponse> = emptyList(),
)

@Serializable
data class PatientSummaryResponse(
    val id: String,
    val fullName: String? = null,
    val status: String,
    val registeredAt: String? = null,
)

@Serializable
data class ClinicalProfileResponse(
    val id: String,
    val allergies: String? = null,
    val relevantConditions: String? = null,
    val currentMedications: String? = null,
    val dentalHistory: String? = null,
    val authorAccountId: String? = null,
    val authorName: String? = null,
    val recordedAt: String,
)

@Serializable
data class AddendumResponse(
    val id: String,
    val text: String,
    val reason: String,
    val authorAccountId: String? = null,
    val authorName: String? = null,
    val recordedAt: String,
)

@Serializable
data class AttentionResponse(
    val id: String,
    val appointmentId: String,
    val appointmentScheduledAt: String? = null,
    val practitionerId: String? = null,
    val practitionerName: String? = null,
    val authorAccountId: String? = null,
    val authorName: String? = null,
    val reason: String,
    val findings: String? = null,
    val diagnosis: String,
    val treatmentPlan: String? = null,
    val recordedAt: String,
    val prescription: List<PrescriptionItemResponse> = emptyList(),
    val addenda: List<AddendumResponse> = emptyList(),
)

@Serializable
data class ClinicalRecordResponse(
    val patient: PatientSummaryResponse,
    val recordId: String? = null,
    val recordCreatedAt: String? = null,
    val clinicalProfile: ClinicalProfileResponse? = null,
    val clinicalProfileHistory: List<ClinicalProfileResponse> = emptyList(),
    val attentions: List<AttentionResponse> = emptyList(),
)

/** Endpoints propios del paciente (la identidad sale del token; no se envía ningún ID). */
interface PatientClinicalApi {
    @GET("pacientes/me/recetas")
    suspend fun prescriptions(): Response<List<PrescriptionResponse>>

    @GET("pacientes/me/chequeos")
    suspend fun checkups(): Response<List<CheckupResponse>>

    @GET("pacientes/me/expediente")
    suspend fun record(): Response<ClinicalRecordResponse>
}

interface PatientClinicalRepository {
    suspend fun prescriptions(): ApiResult<List<Prescription>>
    suspend fun checkups(): ApiResult<List<Checkup>>
    suspend fun record(): ApiResult<ClinicalRecord>
}

/** Sin caché: cada pantalla consulta a Spring y los datos solo viven en su ViewModel. */
class RemotePatientClinicalRepository(
    private val api: PatientClinicalApi,
    private val apiCaller: ApiCaller,
) : PatientClinicalRepository {

    override suspend fun prescriptions(): ApiResult<List<Prescription>> =
        apiCaller.call(authenticated = true) { api.prescriptions() }.mapSafely { list ->
            list.map { it.toDomain() }.sortedByDescending { it.issuedAt.toInstant() }
        }

    override suspend fun checkups(): ApiResult<List<Checkup>> =
        apiCaller.call(authenticated = true) { api.checkups() }.mapSafely { list ->
            list.map { it.toDomain() }.sortedByDescending { it.recordedAt.toInstant() }
        }

    override suspend fun record(): ApiResult<ClinicalRecord> =
        apiCaller.call(authenticated = true) { api.record() }.mapSafely { it.toDomain() }

    /** Una fecha ilegible no rompe la app: la respuesta se trata como inesperada. */
    private inline fun <T, R> ApiResult<T>.mapSafely(transform: (T) -> R): ApiResult<R> = when (this) {
        is ApiResult.Success -> try {
            ApiResult.Success(transform(data))
        } catch (_: Exception) {
            ApiResult.Unexpected(null)
        }
        is ApiResult.Failure -> this
    }
}

private fun String?.toDateOrNull() = this?.let { ClinicTime.parse(it) }

private fun List<PrescriptionItemResponse>.toItems() = map {
    PrescriptionItem(it.order, it.medicine, it.dose, it.frequency, it.duration, it.instructions)
}.sortedBy { it.order }

internal fun PrescriptionResponse.toDomain() = Prescription(
    id = id,
    appointmentScheduledAt = appointmentScheduledAt.toDateOrNull(),
    issuedAt = ClinicTime.parse(issuedAt),
    practitionerName = practitionerName,
    items = items.toItems(),
)

internal fun CheckupResponse.toDomain() = Checkup(
    id = id,
    appointmentScheduledAt = appointmentScheduledAt.toDateOrNull(),
    recordedAt = ClinicTime.parse(recordedAt),
    practitionerName = practitionerName,
    reason = reason,
    findings = findings,
    diagnosis = diagnosis,
    treatmentPlan = treatmentPlan,
    prescription = prescription.toItems(),
)

internal fun ClinicalProfileResponse.toDomain() = ClinicalProfile(
    allergies = allergies,
    relevantConditions = relevantConditions,
    currentMedications = currentMedications,
    dentalHistory = dentalHistory,
    authorName = authorName,
    recordedAt = ClinicTime.parse(recordedAt),
)

internal fun ClinicalRecordResponse.toDomain() = ClinicalRecord(
    recordId = recordId,
    profile = clinicalProfile?.toDomain(),
    profileHistory = clinicalProfileHistory.map { it.toDomain() }.sortedByDescending { it.recordedAt.toInstant() },
    attentions = attentions.map { it.toDomain() }.sortedByDescending { it.recordedAt.toInstant() },
    patientName = patient.fullName,
)

/** Atención de Spring (`AttentionResponse`); la usan el expediente y el detalle de cita del médico. */
internal fun AttentionResponse.toDomain() = Attention(
    id = id,
    appointmentScheduledAt = appointmentScheduledAt.toDateOrNull(),
    practitionerName = practitionerName,
    authorName = authorName,
    reason = reason,
    findings = findings,
    diagnosis = diagnosis,
    treatmentPlan = treatmentPlan,
    recordedAt = ClinicTime.parse(recordedAt),
    prescription = prescription.toItems(),
    addenda = addenda
        .map { Addendum(it.text, it.reason, it.authorName, ClinicTime.parse(it.recordedAt)) }
        .sortedBy { it.recordedAt.toInstant() },
)
