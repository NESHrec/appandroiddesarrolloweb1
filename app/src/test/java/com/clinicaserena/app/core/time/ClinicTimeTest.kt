package com.clinicaserena.app.core.time

import org.junit.Assert.assertEquals
import org.junit.Test

class ClinicTimeTest {

    @Test
    fun `una fecha UTC de Spring se muestra en America Guatemala`() {
        val value = ClinicTime.parse("2026-10-07T06:11:00Z")

        assertEquals("miércoles 7 de octubre de 2026, 00:11", ClinicTime.formatDateTime(value))
        assertEquals("00:11", ClinicTime.formatTime(value))
    }

    @Test
    fun `Z y offset de Guatemala representan el mismo instante`() {
        val utc = ClinicTime.parse("2026-10-08T15:00:00Z")
        val local = ClinicTime.parse("2026-10-08T09:00:00-06:00")

        assertEquals(utc.toInstant(), local.toInstant())
        assertEquals(ClinicTime.formatDateTime(utc), ClinicTime.formatDateTime(local))
    }
}
