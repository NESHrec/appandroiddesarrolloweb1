package com.clinicaserena.app.domain.model

import java.time.OffsetDateTime

/** Superficies que acepta `OdontogramaService` (Spring rechaza cualquier otra con `SURFACE_INVALID`). */
enum class ToothSurface {
    MESIAL, DISTAL, VESTIBULAR, LINGUAL, PALATINA, OCLUSAL, INCISAL;

    companion object {
        /** `null` si Spring envía una superficie que la app no conoce: se muestra el texto original. */
        fun from(value: String): ToothSurface? = entries.firstOrNull { it.name == value.trim().uppercase() }
    }
}

enum class Dentition { PERMANENT, TEMPORARY }

/** Piezas FDI válidas, por dentición y cuadrante, en el orden en que se muestran. */
object FdiTeeth {
    val permanentQuadrants: List<List<Int>> = listOf(11..18, 21..28, 31..38, 41..48).map { it.toList() }
    val temporaryQuadrants: List<List<Int>> = listOf(51..55, 61..65, 71..75, 81..85).map { it.toList() }
    private val order: List<Int> = permanentQuadrants.flatten() + temporaryQuadrants.flatten()
    private val valid: Set<Int> = order.toSet()

    fun isValid(tooth: Int): Boolean = tooth in valid
    fun dentition(tooth: Int): Dentition = if (tooth >= 51) Dentition.TEMPORARY else Dentition.PERMANENT

    /** Posición en el orden FDI; las piezas desconocidas van al final. */
    fun position(tooth: Int): Int = order.indexOf(tooth).let { if (it < 0) Int.MAX_VALUE else it }
}

/**
 * Observación del odontograma. Es append-only: nunca representa el "estado actual" de la pieza. Los
 * registros antiguos pueden no tener superficie ([surfaceRaw] `null`).
 */
data class DentalObservation(
    val id: String,
    val appointmentId: String,
    val toothNumber: Int,
    val surface: ToothSurface?,
    val surfaceRaw: String?,
    val observation: String,
    val recordedAt: OffsetDateTime,
)

data class Odontogram(val patientName: String?, val observations: List<DentalObservation>)

data class SurfaceHistory(val surface: ToothSurface?, val surfaceRaw: String?, val observations: List<DentalObservation>)

data class ToothHistory(val toothNumber: Int, val dentition: Dentition, val surfaces: List<SurfaceHistory>)

/**
 * Historial agrupado por pieza (orden FDI) y superficie (orden de [ToothSurface]; "sin superficie" al
 * final). Dentro de cada superficie, de la observación más reciente a la más antigua.
 */
fun groupObservations(observations: List<DentalObservation>): List<ToothHistory> =
    observations
        .groupBy { it.toothNumber }
        .toSortedMap(compareBy<Int> { FdiTeeth.position(it) }.thenBy { it })
        .map { (tooth, list) ->
            val surfaces = list
                .groupBy { it.surface to (if (it.surface == null) it.surfaceRaw?.trim()?.uppercase() else null) }
                .map { (key, items) ->
                    SurfaceHistory(key.first, items.first().surfaceRaw, items.sortedByDescending { it.recordedAt.toInstant() })
                }
                .sortedWith(compareBy<SurfaceHistory> { it.surface?.ordinal ?: (ToothSurface.entries.size + if (it.surfaceRaw == null) 1 else 0) })
            ToothHistory(tooth, FdiTeeth.dentition(tooth), surfaces)
        }
