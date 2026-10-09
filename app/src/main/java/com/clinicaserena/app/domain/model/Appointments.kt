package com.clinicaserena.app.domain.model

import java.time.OffsetDateTime

data class Specialty(val id: String, val name: String, val description: String?)

data class Practitioner(
    val id: String,
    val fullName: String,
    val specialtyId: String,
    val specialtyName: String,
)

/**
 * Bloque de disponibilidad. [startAtRaw] es el texto exacto que envió Spring: para reservar se
 * reenvía tal cual, sin reformatear, y así coincide con el `startAt` del bloque.
 */
data class Slot(
    val id: String,
    val practitionerId: String,
    val startAt: OffsetDateTime,
    val startAtRaw: String,
    val endAt: OffsetDateTime,
)

/** Estados de cita de Spring (`EstadoCita`). [DESCONOCIDO] cubre un valor nuevo sin romper la lista. */
enum class AppointmentStatus { PENDIENTE, CONFIRMADA, CANCELADA, COMPLETADA, DESCONOCIDO;

    /** Puede ocurrir todavía: cuenta como próxima si su hora no pasó. */
    val isActive: Boolean get() = this == PENDIENTE || this == CONFIRMADA

    companion object {
        fun from(value: String): AppointmentStatus = entries.firstOrNull { it.name == value } ?: DESCONOCIDO
    }
}

data class Appointment(
    val id: String,
    val practitionerId: String,
    val specialtyId: String,
    val scheduledAt: OffsetDateTime,
    val status: AppointmentStatus,
    val statusRaw: String,
    val notes: String?,
    val amountCents: Long?,
)
