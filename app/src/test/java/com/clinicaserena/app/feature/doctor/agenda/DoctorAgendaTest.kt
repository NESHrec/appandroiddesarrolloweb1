package com.clinicaserena.app.feature.doctor.agenda

import com.clinicaserena.app.core.network.ApiResult
import com.clinicaserena.app.core.network.ApiTestRig
import com.clinicaserena.app.core.time.ClinicTime
import com.clinicaserena.app.core.ui.LoadState
import com.clinicaserena.app.data.doctor.DateRange
import com.clinicaserena.app.data.doctor.DoctorApi
import com.clinicaserena.app.data.doctor.DoctorRepository
import com.clinicaserena.app.data.doctor.RemoteDoctorRepository
import com.clinicaserena.app.domain.model.AppointmentStatus
import com.clinicaserena.app.domain.model.AttentionBlocker
import com.clinicaserena.app.domain.model.DoctorAppointment
import com.clinicaserena.app.domain.model.DoctorAppointmentDetail
import com.clinicaserena.app.testing.MainDispatcherRule
import com.clinicaserena.app.testing.SessionFixture
import com.clinicaserena.app.testing.TEST_CLOCK
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

private fun range(from: String, to: String) = DateRange(ClinicTime.parse(from), ClinicTime.parse(to))

class AgendaPeriodsTest {

    // Viernes 9 de octubre de 2026, 10:52 en Guatemala.
    private val friday = Instant.parse("2026-10-09T16:52:00Z")

    @Test
    fun `hoy va de medianoche a medianoche en Guatemala`() {
        assertEquals(range("2026-10-09T00:00:00-06:00", "2026-10-10T00:00:00-06:00"), AgendaPeriods.home(HomePeriod.TODAY, friday))
    }

    @Test
    fun `de madrugada en UTC sigue siendo el dia anterior en Guatemala`() {
        // 05:30 UTC del 10/10 = 23:30 del 9/10 en Guatemala.
        assertEquals(
            range("2026-10-09T00:00:00-06:00", "2026-10-10T00:00:00-06:00"),
            AgendaPeriods.home(HomePeriod.TODAY, Instant.parse("2026-10-10T05:30:00Z")),
        )
    }

    @Test
    fun `esta semana va de lunes 00 00 a lunes siguiente 00 00`() {
        assertEquals(range("2026-10-05T00:00:00-06:00", "2026-10-12T00:00:00-06:00"), AgendaPeriods.home(HomePeriod.WEEK, friday))
    }

    @Test
    fun `el lunes empieza su propia semana y el domingo pertenece a la anterior`() {
        val monday = Instant.parse("2026-10-12T06:00:00Z") // lunes 12, 00:00 en Guatemala
        val sunday = Instant.parse("2026-10-12T05:59:00Z") // domingo 11, 23:59 en Guatemala
        assertEquals(range("2026-10-12T00:00:00-06:00", "2026-10-19T00:00:00-06:00"), AgendaPeriods.home(HomePeriod.WEEK, monday))
        assertEquals(range("2026-10-05T00:00:00-06:00", "2026-10-12T00:00:00-06:00"), AgendaPeriods.home(HomePeriod.WEEK, sunday))
    }

    @Test
    fun `una semana puede cruzar el cambio de mes`() {
        val thursday = Instant.parse("2026-10-01T18:00:00Z") // jueves 1 de octubre
        assertEquals(range("2026-09-28T00:00:00-06:00", "2026-10-05T00:00:00-06:00"), AgendaPeriods.home(HomePeriod.WEEK, thursday))
    }

    @Test
    fun `este mes es el mes calendario`() {
        assertEquals(range("2026-10-01T00:00:00-06:00", "2026-11-01T00:00:00-06:00"), AgendaPeriods.home(HomePeriod.MONTH, friday))
    }

    @Test
    fun `cambio de mes en Guatemala aunque en UTC ya sea el mes siguiente`() {
        // 31/10 a las 21:00 en Guatemala = 1/11 03:00 UTC: sigue siendo octubre.
        val lastNight = Instant.parse("2026-11-01T03:00:00Z")
        assertEquals(range("2026-10-01T00:00:00-06:00", "2026-11-01T00:00:00-06:00"), AgendaPeriods.home(HomePeriod.MONTH, lastNight))
        // 1/11 00:00 en Guatemala ya es noviembre.
        val firstMinute = Instant.parse("2026-11-01T06:00:00Z")
        assertEquals(range("2026-11-01T00:00:00-06:00", "2026-12-01T00:00:00-06:00"), AgendaPeriods.home(HomePeriod.MONTH, firstMinute))
    }

    @Test
    fun `cambio de anio y febrero`() {
        assertEquals(
            range("2026-12-01T00:00:00-06:00", "2027-01-01T00:00:00-06:00"),
            AgendaPeriods.home(HomePeriod.MONTH, Instant.parse("2026-12-31T20:00:00Z")),
        )
        assertEquals(
            range("2027-02-01T00:00:00-06:00", "2027-03-01T00:00:00-06:00"),
            AgendaPeriods.home(HomePeriod.MONTH, Instant.parse("2027-02-15T12:00:00Z")),
        )
    }

    @Test
    fun `rangos de la agenda`() {
        assertEquals(range("2026-10-09T00:00:00-06:00", "2026-10-16T00:00:00-06:00"), AgendaPeriods.agenda(AgendaRange.NEXT_7, friday))
        assertEquals(range("2026-10-02T00:00:00-06:00", "2026-10-10T00:00:00-06:00"), AgendaPeriods.agenda(AgendaRange.LAST_7, friday))
        assertEquals(range("2026-10-09T00:00:00-06:00", "2026-11-08T00:00:00-06:00"), AgendaPeriods.agenda(AgendaRange.NEXT_30, friday))
    }
}

private fun appointment(
    id: String,
    patientId: String,
    status: AppointmentStatus = AppointmentStatus.PENDIENTE,
    at: String = "2026-10-10T14:00:00Z",
) = DoctorAppointment(
    id, patientId, "Paciente $patientId", "Odontología general", ClinicTime.parse(at), status, status.name,
    null, null, false, false, emptyList(), emptyList(),
)

class DoctorStatsTest {

    @Test
    fun `cuenta pacientes distintos y no filas, sin canceladas`() {
        val list = listOf(
            appointment("1", "p1"),
            appointment("2", "p1", AppointmentStatus.COMPLETADA),
            appointment("3", "p2"),
            appointment("4", "p3", AppointmentStatus.CANCELADA),
        )
        assertEquals(2, DoctorStats.distinctPatients(list))
    }

    @Test
    fun `un paciente con cita cancelada y otra activa cuenta una vez`() {
        val list = listOf(appointment("1", "p1", AppointmentStatus.CANCELADA), appointment("2", "p1"))
        assertEquals(1, DoctorStats.distinctPatients(list))
    }

    @Test
    fun `con 100 citas el resultado puede estar incompleto`() {
        assertTrue(DoctorStats.mayBeIncomplete(List(100) { appointment("$it", "p$it") }))
        assertFalse(DoctorStats.mayBeIncomplete(List(99) { appointment("$it", "p$it") }))
    }
}

class DoctorRepositoryTest {

    private val rig = ApiTestRig()
    private val repo = RemoteDoctorRepository(rig.retrofit.create(DoctorApi::class.java), rig.apiCaller)

    @After
    fun tearDown() = rig.close()

    @Test
    fun `agenda envia from y to con offset, status y limit 100`() = runTest {
        rig.enqueue(200, "[]")

        repo.agenda(range("2026-10-05T00:00:00-06:00", "2026-10-12T00:00:00-06:00"), AppointmentStatus.PENDIENTE)

        val url = rig.server.takeRequest().url
        assertEquals("/api/v1/medico/citas", url.encodedPath)
        assertEquals("2026-10-05T00:00:00-06:00", url.queryParameter("from"))
        assertEquals("2026-10-12T00:00:00-06:00", url.queryParameter("to"))
        assertEquals("PENDIENTE", url.queryParameter("status"))
        assertEquals("100", url.queryParameter("limit"))
    }

    @Test
    fun `un offset positivo llega codificado`() = runTest {
        rig.enqueue(200, "[]")

        repo.agenda(range("2026-10-05T00:00:00+02:00", "2026-10-12T00:00:00+02:00"))

        val url = rig.server.takeRequest().url
        assertTrue(url.encodedQuery!!.contains("%2B02%3A00"))
        assertNull("sin filtro no se envía status", url.queryParameter("status"))
    }

    @Test
    fun `mapea llegada, bloqueos en orden y bloqueos desconocidos`() = runTest {
        rig.enqueue(
            200,
            """[{"id":"b","patientId":"p1","patientName":"Paciente Uno","practitionerId":"m","specialtyId":"e","specialtyName":"Odontología general",
                 "scheduledAt":"2026-10-07T06:22:00Z","status":"PENDIENTE","notes":"n","arrivalAt":null,"attentionRecorded":false,"canRecordAttention":false,
                 "attentionBlockers":["NOT_STARTED","ARRIVAL_NOT_REGISTERED","NUEVO_MOTIVO"]}]""",
        )

        val a = (repo.agenda(range("2026-10-05T00:00:00-06:00", "2026-10-12T00:00:00-06:00")) as ApiResult.Success).data.single()

        assertEquals(
            listOf(AttentionBlocker.NOT_STARTED, AttentionBlocker.ARRIVAL_NOT_REGISTERED, AttentionBlocker.DESCONOCIDO),
            a.blockers,
        )
        assertEquals("NUEVO_MOTIVO", a.blockersRaw[2])
        assertNull(a.arrivalAt)
    }

    @Test
    fun `detalle con atencion y errores tipados`() = runTest {
        rig.enqueue(
            200,
            """{"appointment":{"id":"c","patientId":"p1","practitionerId":"m","specialtyId":"e","scheduledAt":"2026-10-09T16:28:00Z","status":"COMPLETADA",
                 "arrivalAt":"2026-10-09T16:28:17Z","attentionRecorded":true,"canRecordAttention":false,"attentionBlockers":["ATTENTION_ALREADY_RECORDED","STATUS_NOT_DOCUMENTABLE"]},
                "attention":{"id":"t","appointmentId":"c","reason":"Control","diagnosis":"Gingivitis","recordedAt":"2026-10-09T16:28:20Z","prescription":[],"addenda":[]}}""",
        )
        rig.enqueue(404, ApiTestRig.error(404, "APPOINTMENT_NOT_FOUND"))
        rig.enqueue(403, ApiTestRig.error(403, "PRACTITIONER_LINK_REQUIRED"))

        val detail = (repo.appointment("c") as ApiResult.Success).data
        assertEquals("Gingivitis", detail.attention?.diagnosis)
        assertEquals("/api/v1/medico/citas/c", rig.server.takeRequest().url.encodedPath)
        assertEquals(ApiResult.NotFound("APPOINTMENT_NOT_FOUND", "Mensaje"), repo.appointment("ajena"))
        assertEquals(
            ApiResult.Forbidden("PRACTITIONER_LINK_REQUIRED", "Mensaje"),
            repo.agenda(range("2026-10-05T00:00:00-06:00", "2026-10-12T00:00:00-06:00")),
        )
    }
}

class DoctorAgendaViewModelsTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private class FakeDoctorRepository : DoctorRepository {
        var agendaResult: ApiResult<List<DoctorAppointment>> = ApiResult.Success(emptyList())
        var detailResult: ApiResult<DoctorAppointmentDetail> = ApiResult.NotFound("APPOINTMENT_NOT_FOUND", null)
        val agendaCalls = mutableListOf<Pair<DateRange, AppointmentStatus?>>()
        override suspend fun agenda(range: DateRange, status: AppointmentStatus?): ApiResult<List<DoctorAppointment>> {
            agendaCalls += range to status
            return agendaResult
        }
        override suspend fun appointment(appointmentId: String) = detailResult
        override suspend fun record(appointmentId: String): ApiResult<com.clinicaserena.app.domain.model.ClinicalRecord> = error("no se usa")
        override suspend fun recordAttention(
            appointmentId: String,
            request: com.clinicaserena.app.data.doctor.RecordAttentionRequest,
        ): ApiResult<com.clinicaserena.app.domain.model.Attention> = error("no se usa")
        override suspend fun addClinicalProfile(
            appointmentId: String,
            request: com.clinicaserena.app.data.doctor.ClinicalProfileRequest,
        ): ApiResult<com.clinicaserena.app.domain.model.ClinicalProfile> = error("no se usa")
        override suspend fun addAddendum(
            appointmentId: String,
            attentionId: String,
            request: com.clinicaserena.app.data.doctor.AddendumRequest,
        ): ApiResult<com.clinicaserena.app.domain.model.Addendum> = error("no se usa")
        override suspend fun odontogram(patientId: String): ApiResult<com.clinicaserena.app.domain.model.Odontogram> = error("no se usa")
        override suspend fun addObservation(
            appointmentId: String,
            request: com.clinicaserena.app.data.doctor.DentalObservationRequest,
        ): ApiResult<com.clinicaserena.app.domain.model.DentalObservation> = error("no se usa")
    }

    @Test
    fun `inicio consulta la semana por defecto y cambia de periodo`() = runTest {
        val repo = FakeDoctorRepository()
        val vm = DoctorHomeViewModel(repo, SessionFixture(backgroundScope).manager, TEST_CLOCK)

        // TEST_CLOCK = miércoles 7/10 00:00 en Guatemala → semana del lunes 5 al lunes 12.
        assertEquals(range("2026-10-05T00:00:00-06:00", "2026-10-12T00:00:00-06:00"), repo.agendaCalls.single().first)

        vm.selectPeriod(HomePeriod.MONTH)

        assertEquals(range("2026-10-01T00:00:00-06:00", "2026-11-01T00:00:00-06:00"), repo.agendaCalls.last().first)
    }

    @Test
    fun `agenda pasa el filtro de estado a Spring`() = runTest {
        val repo = FakeDoctorRepository()
        val vm = DoctorAgendaViewModel(repo, SessionFixture(backgroundScope).manager, TEST_CLOCK)

        vm.selectStatus(AppointmentStatus.COMPLETADA)
        vm.selectRange(AgendaRange.LAST_7)

        assertEquals(AppointmentStatus.COMPLETADA, repo.agendaCalls.last().second)
        assertEquals(3, repo.agendaCalls.size)
    }

    @Test
    fun `403 de vinculacion se conserva tipado`() = runTest {
        val repo = FakeDoctorRepository().apply { agendaResult = ApiResult.Forbidden("PRACTITIONER_LINK_REQUIRED", null) }
        val vm = DoctorAgendaViewModel(repo, SessionFixture(backgroundScope).manager, TEST_CLOCK)

        assertEquals(LoadState.Failed(ApiResult.Forbidden("PRACTITIONER_LINK_REQUIRED", null)), vm.state.value.load)
    }

    @Test
    fun `tras re-login la agenda vuelve a cargar`() = runTest {
        val session = SessionFixture(backgroundScope)
        session.manager.login("medico@ejemplo.invalid", "clave")
        val repo = FakeDoctorRepository().apply { agendaResult = ApiResult.Unauthenticated("UNAUTHENTICATED", null) }
        val vm = DoctorAgendaViewModel(repo, session.manager, TEST_CLOCK)

        repo.agendaResult = ApiResult.Success(listOf(appointment("1", "p1")))
        session.manager.expire()
        session.manager.login("medico@ejemplo.invalid", "clave")

        assertTrue(vm.state.value.load is LoadState.Loaded)
        assertEquals(2, repo.agendaCalls.size)
    }

    @Test
    fun `detalle de cita ajena queda como 404`() = runTest {
        val vm = DoctorAppointmentViewModel(FakeDoctorRepository(), SessionFixture(backgroundScope).manager, "ajena")

        assertEquals(LoadState.Failed(ApiResult.NotFound("APPOINTMENT_NOT_FOUND", null)), vm.state.value)
    }
}
