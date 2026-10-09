package com.clinicaserena.app.feature.patient.appointments

import com.clinicaserena.app.domain.model.Appointment
import com.clinicaserena.app.domain.model.AppointmentStatus
import java.time.Instant

/** Cita con los nombres resueltos desde el catálogo público. */
data class AppointmentItem(
    val appointment: Appointment,
    val practitionerName: String?,
    val specialtyName: String?,
)

enum class AppointmentTab { UPCOMING, HISTORY }

/** Reglas de la lista de citas, sin dependencias de Android para probarlas con un reloj fijo. */
object AppointmentsLogic {

    /** Próxima: pendiente o confirmada y con hora igual o posterior a [now]. */
    fun isUpcoming(appointment: Appointment, now: Instant): Boolean =
        appointment.status.isActive && !appointment.scheduledAt.toInstant().isBefore(now)

    /** Próximas en orden ascendente; historial (pasadas, completadas o canceladas) en orden descendente. */
    fun forTab(items: List<AppointmentItem>, tab: AppointmentTab, now: Instant): List<AppointmentItem> = when (tab) {
        AppointmentTab.UPCOMING -> items.filter { isUpcoming(it.appointment, now) }
            .sortedBy { it.appointment.scheduledAt.toInstant() }
        AppointmentTab.HISTORY -> items.filterNot { isUpcoming(it.appointment, now) }
            .sortedByDescending { it.appointment.scheduledAt.toInstant() }
    }

    fun filterByStatus(items: List<AppointmentItem>, status: AppointmentStatus?): List<AppointmentItem> =
        if (status == null) items else items.filter { it.appointment.status == status }

    fun next(items: List<AppointmentItem>, now: Instant): AppointmentItem? =
        forTab(items, AppointmentTab.UPCOMING, now).firstOrNull()
}
