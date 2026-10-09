package com.clinicaserena.app.feature.doctor.agenda

import com.clinicaserena.app.core.time.ClinicTime
import com.clinicaserena.app.data.doctor.DateRange
import com.clinicaserena.app.data.doctor.DoctorRepository
import com.clinicaserena.app.domain.model.AppointmentStatus
import com.clinicaserena.app.domain.model.DoctorAppointment
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Períodos del Inicio: el conteo de pacientes se calcula sobre uno de ellos. */
enum class HomePeriod { TODAY, WEEK, MONTH }

/** Rangos de la Agenda. */
enum class AgendaRange { TODAY, NEXT_7, LAST_7, NEXT_30 }

/**
 * Rangos `[from, to)` en America/Guatemala (UTC−6, sin horario de verano). Los límites son
 * medianoches locales y `to` es exclusivo, igual que en `/medico/citas`.
 */
object AgendaPeriods {

    fun home(period: HomePeriod, now: Instant, zone: ZoneId = ClinicTime.ZONE): DateRange {
        // atZone(): LocalDate.ofInstant requiere API 34 y la app soporta desde API 26.
        val today = now.atZone(zone).toLocalDate()
        return when (period) {
            HomePeriod.TODAY -> days(today, today.plusDays(1), zone)
            // Semana de lunes 00:00 a lunes siguiente 00:00.
            HomePeriod.WEEK -> {
                val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                days(monday, monday.plusWeeks(1), zone)
            }
            // Mes calendario: día 1 00:00 a día 1 del mes siguiente 00:00.
            HomePeriod.MONTH -> {
                val first = today.withDayOfMonth(1)
                days(first, first.plusMonths(1), zone)
            }
        }
    }

    fun agenda(range: AgendaRange, now: Instant, zone: ZoneId = ClinicTime.ZONE): DateRange {
        // atZone(): LocalDate.ofInstant requiere API 34 y la app soporta desde API 26.
        val today = now.atZone(zone).toLocalDate()
        return when (range) {
            AgendaRange.TODAY -> days(today, today.plusDays(1), zone)
            AgendaRange.NEXT_7 -> days(today, today.plusDays(7), zone)
            AgendaRange.LAST_7 -> days(today.minusDays(7), today.plusDays(1), zone)
            AgendaRange.NEXT_30 -> days(today, today.plusDays(30), zone)
        }
    }

    private fun days(start: LocalDate, endExclusive: LocalDate, zone: ZoneId) = DateRange(
        from = start.atStartOfDay(zone).toOffsetDateTime(),
        to = endExclusive.atStartOfDay(zone).toOffsetDateTime(),
    )
}

object DoctorStats {
    /** Pacientes distintos con al menos una cita no cancelada (no se cuentan filas como personas). */
    fun distinctPatients(appointments: List<DoctorAppointment>): Int =
        appointments.filter { it.status != AppointmentStatus.CANCELADA }.map { it.patientId }.toSet().size

    /** Spring devuelve como máximo 100 y no pagina: si llegan 100 puede haber más. */
    fun mayBeIncomplete(appointments: List<DoctorAppointment>): Boolean =
        appointments.size >= DoctorRepository.MAX_LIMIT
}
