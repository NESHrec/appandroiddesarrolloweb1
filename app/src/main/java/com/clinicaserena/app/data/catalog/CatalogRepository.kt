package com.clinicaserena.app.data.catalog

import com.clinicaserena.app.core.network.ApiCaller
import com.clinicaserena.app.core.network.ApiResult
import com.clinicaserena.app.core.network.PublicEndpoint
import com.clinicaserena.app.core.time.ClinicTime
import com.clinicaserena.app.domain.model.Practitioner
import com.clinicaserena.app.domain.model.Slot
import com.clinicaserena.app.domain.model.Specialty
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** `SpecialtyDto` de Spring. */
@Serializable
data class SpecialtyResponse(val id: String, val name: String, val description: String? = null)

/** `PractitionerDto` de Spring. */
@Serializable
data class PractitionerResponse(
    val id: String,
    val fullName: String,
    val specialtyId: String,
    val specialtyName: String,
    val licenseNumber: String? = null,
)

/** `AvailabilitySlotDto` de Spring. Las fechas se guardan como texto para reenviar `startAt` tal cual. */
@Serializable
data class AvailabilitySlotResponse(
    val id: String,
    val practitionerId: String,
    val startAt: String,
    val endAt: String,
)

/** Catálogo público (`CatalogoController`): no lleva Bearer. */
interface CatalogApi {
    @PublicEndpoint
    @GET("especialidades")
    suspend fun specialties(): Response<List<SpecialtyResponse>>

    @PublicEndpoint
    @GET("medicos")
    suspend fun practitioners(@Query("specialtyId") specialtyId: String? = null): Response<List<PractitionerResponse>>

    /** Sin `desde`/`hasta`, Spring devuelve desde hoy (Guatemala) y 30 días, sin bloques ya iniciados ni ocupados. */
    @PublicEndpoint
    @GET("medicos/{medicoId}/disponibilidad")
    suspend fun availability(@Path("medicoId") practitionerId: String): Response<List<AvailabilitySlotResponse>>
}

interface CatalogRepository {
    suspend fun specialties(): ApiResult<List<Specialty>>
    suspend fun practitioners(specialtyId: String): ApiResult<List<Practitioner>>

    /** Todos los profesionales (en caché) para mostrar nombres en las citas, que solo traen IDs. */
    suspend fun allPractitioners(): ApiResult<List<Practitioner>>
    suspend fun availability(practitionerId: String): ApiResult<List<Slot>>
}

/**
 * Catálogo público. Especialidades y profesionales se guardan en memoria (no son datos clínicos) para
 * resolver nombres; la disponibilidad nunca se guarda porque cambia con cada reserva.
 */
class RemoteCatalogRepository(
    private val api: CatalogApi,
    private val apiCaller: ApiCaller,
) : CatalogRepository {

    private val mutex = Mutex()
    private var specialtiesCache: List<Specialty>? = null
    private var practitionersCache: List<Practitioner>? = null

    override suspend fun specialties(): ApiResult<List<Specialty>> = mutex.withLock {
        specialtiesCache?.let { return ApiResult.Success(it) }
        when (val result = apiCaller.call(authenticated = false) { api.specialties() }) {
            is ApiResult.Success -> result.data.map { Specialty(it.id, it.name, it.description) }
                .sortedBy { it.name }
                .also { specialtiesCache = it }
                .let { ApiResult.Success(it) }
            is ApiResult.Failure -> result
        }
    }

    override suspend fun practitioners(specialtyId: String): ApiResult<List<Practitioner>> =
        when (val result = apiCaller.call(authenticated = false) { api.practitioners(specialtyId) }) {
            is ApiResult.Success -> ApiResult.Success(result.data.map { it.toDomain() }.sortedBy { it.fullName })
            is ApiResult.Failure -> result
        }

    override suspend fun allPractitioners(): ApiResult<List<Practitioner>> = mutex.withLock {
        practitionersCache?.let { return ApiResult.Success(it) }
        when (val result = apiCaller.call(authenticated = false) { api.practitioners(null) }) {
            is ApiResult.Success -> result.data.map { it.toDomain() }
                .also { practitionersCache = it }
                .let { ApiResult.Success(it) }
            is ApiResult.Failure -> result
        }
    }

    override suspend fun availability(practitionerId: String): ApiResult<List<Slot>> =
        when (val result = apiCaller.call(authenticated = false) { api.availability(practitionerId) }) {
            is ApiResult.Success -> try {
                ApiResult.Success(result.data.map { it.toDomain() }.sortedBy { it.startAt.toInstant() })
            } catch (_: Exception) {
                ApiResult.Unexpected(null)
            }
            is ApiResult.Failure -> result
        }

    private fun PractitionerResponse.toDomain() = Practitioner(id, fullName, specialtyId, specialtyName)

    private fun AvailabilitySlotResponse.toDomain() = Slot(
        id = id,
        practitionerId = practitionerId,
        startAt = ClinicTime.parse(startAt),
        startAtRaw = startAt,
        endAt = ClinicTime.parse(endAt),
    )
}
