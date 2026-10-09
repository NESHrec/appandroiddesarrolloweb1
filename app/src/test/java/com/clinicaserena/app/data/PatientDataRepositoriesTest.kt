package com.clinicaserena.app.data

import com.clinicaserena.app.core.network.ApiResult
import com.clinicaserena.app.core.network.ApiTestRig
import com.clinicaserena.app.data.catalog.CatalogApi
import com.clinicaserena.app.data.catalog.RemoteCatalogRepository
import com.clinicaserena.app.data.patient.AppointmentsApi
import com.clinicaserena.app.data.patient.RemoteAppointmentsRepository
import com.clinicaserena.app.domain.model.AppointmentStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PatientDataRepositoriesTest {

    private val rig = ApiTestRig()
    private val appointments = RemoteAppointmentsRepository(rig.retrofit.create(AppointmentsApi::class.java), rig.apiCaller)
    private val catalog = RemoteCatalogRepository(rig.retrofit.create(CatalogApi::class.java), rig.apiCaller)

    @After
    fun tearDown() = rig.close()

    @Test
    fun `mis citas usa el Bearer y conserva fechas Z y con offset`() = runTest {
        rig.enqueue(
            200,
            """[{"id":"a","patientId":"p","practitionerId":"m","specialtyId":"e","scheduledAt":"2026-10-07T06:11:00Z","status":"PENDIENTE","notes":null,"amountCents":null,"createdAt":"2026-10-07T06:08:57.999662Z","updatedAt":"2026-10-07T06:08:57.999662Z"},
               {"id":"b","patientId":"p","practitionerId":"m","specialtyId":"e","scheduledAt":"2026-10-08T09:00:00-06:00","status":"COMPLETADA","amountCents":15000}]""",
        )

        val result = appointments.listOwn() as ApiResult.Success

        val request = rig.server.takeRequest()
        assertEquals("/api/v1/pacientes/me/citas", request.url.encodedPath)
        assertNull("sin filtro de estado: se filtra en el cliente", request.url.query)
        assertEquals("Bearer ${ApiTestRig.TOKEN}", request.headers["Authorization"])
        assertEquals(AppointmentStatus.PENDIENTE, result.data[0].status)
        assertEquals(result.data[1].scheduledAt.toInstant(), java.time.Instant.parse("2026-10-08T15:00:00Z"))
        assertEquals(15000L, result.data[1].amountCents)
    }

    @Test
    fun `reservar envia solo los campos del contrato y el startAt original`() = runTest {
        rig.enqueue(
            201,
            """{"id":"n","patientId":"p","practitionerId":"m","specialtyId":"e","scheduledAt":"2026-10-09T15:00:00Z","status":"PENDIENTE","notes":"Hola"}""",
        )
        rig.enqueue(201, """{"id":"n2","patientId":"p","practitionerId":"m","specialtyId":"e","scheduledAt":"2026-10-09T16:00:00Z","status":"PENDIENTE"}""")

        appointments.book("m", "e", "2026-10-09T15:00:00Z", "Hola")
        appointments.book("m", "e", "2026-10-09T16:00:00Z", null)

        val first = rig.server.takeRequest()
        assertEquals("/api/v1/citas", first.url.encodedPath)
        assertEquals(
            """{"practitionerId":"m","specialtyId":"e","scheduledAt":"2026-10-09T15:00:00Z","notes":"Hola"}""",
            first.body?.utf8(),
        )
        assertEquals(
            """{"practitionerId":"m","specialtyId":"e","scheduledAt":"2026-10-09T16:00:00Z"}""",
            rig.server.takeRequest().body?.utf8(),
        )
    }

    @Test
    fun `409 de reserva conserva su code`() = runTest {
        rig.enqueue(409, ApiTestRig.error(409, "SLOT_NOT_AVAILABLE"))

        assertEquals(ApiResult.Conflict("SLOT_NOT_AVAILABLE", "Mensaje"), appointments.book("m", "e", "x", null))
    }

    @Test
    fun `catalogo es publico, filtra medicos por especialidad y guarda especialidades en memoria`() = runTest {
        rig.enqueue(200, """[{"id":"e2","name":"Ortodoncia","description":"d"},{"id":"e1","name":"Medicina general","description":"d"}]""")
        rig.enqueue(200, """[{"id":"m","fullName":"Dra. Elena Morales","specialtyId":"e1","specialtyName":"Odontología general","licenseNumber":"X"}]""")
        rig.enqueue(200, """[{"id":"s","practitionerId":"m","startAt":"2026-10-09T15:00:00Z","endAt":"2026-10-09T15:30:00Z"}]""")

        val specialties = catalog.specialties() as ApiResult.Success
        catalog.specialties()
        catalog.practitioners("e1")
        val slots = catalog.availability("m") as ApiResult.Success

        assertEquals(listOf("Medicina general", "Ortodoncia"), specialties.data.map { it.name })
        assertEquals(3, rig.server.requestCount)
        val r1 = rig.server.takeRequest()
        assertEquals("/api/v1/especialidades", r1.url.encodedPath)
        assertNull(r1.headers["Authorization"])
        val r2 = rig.server.takeRequest()
        assertEquals("specialtyId=e1", r2.url.query)
        assertNull(r2.headers["Authorization"])
        assertEquals("/api/v1/medicos/m/disponibilidad", rig.server.takeRequest().url.encodedPath)
        assertEquals("2026-10-09T15:00:00Z", slots.data.single().startAtRaw)
    }

    @Test
    fun `una fecha ilegible en la respuesta es Unexpected`() = runTest {
        rig.enqueue(200, """[{"id":"s","practitionerId":"m","startAt":"mañana","endAt":"x"}]""")

        assertTrue(catalog.availability("m") is ApiResult.Unexpected)
    }
}
