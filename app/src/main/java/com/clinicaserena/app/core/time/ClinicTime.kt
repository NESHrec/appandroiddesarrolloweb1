package com.clinicaserena.app.core.time

import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Fechas de Spring: ISO 8601 con offset; hoy llegan en UTC con `Z` (por ejemplo `2026-10-07T06:11:00Z`).
 * Se muestran en America/Guatemala. Para reservar, se reenvía el `startAt` original como texto, sin
 * reconvertirlo, para no alterar el instante.
 */
object ClinicTime {
    val ZONE: ZoneId = ZoneId.of("America/Guatemala")

    private val locale: Locale = Locale.forLanguageTag("es-GT")
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy, HH:mm", locale)
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", locale)
    private val dayFormatter = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", locale)

    /** Día en Guatemala, para agrupar horarios: "jueves 8 de octubre". */
    fun formatDay(value: OffsetDateTime): String = value.atZoneSameInstant(ZONE).format(dayFormatter)

    /** Fecha de calendario en Guatemala (para agrupar bloques por día). */
    fun localDate(value: OffsetDateTime): java.time.LocalDate = value.atZoneSameInstant(ZONE).toLocalDate()

    /** Acepta `Z` y offsets explícitos (`-06:00`, `+00:00`). */
    fun parse(value: String): OffsetDateTime = OffsetDateTime.parse(value)

    fun formatDateTime(value: OffsetDateTime): String =
        value.atZoneSameInstant(ZONE).format(dateTimeFormatter)

    fun formatTime(value: OffsetDateTime): String =
        value.atZoneSameInstant(ZONE).format(timeFormatter)
}
