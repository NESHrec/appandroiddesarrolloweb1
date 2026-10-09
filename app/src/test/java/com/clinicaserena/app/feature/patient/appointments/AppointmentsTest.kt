package com.clinicaserena.app.feature.patient.appointments

import com.clinicaserena.app.core.network.ApiResult
import com.clinicaserena.app.core.time.ClinicTime
import com.clinicaserena.app.core.ui.LoadState
import com.clinicaserena.app.domain.model.Appointment
import com.clinicaserena.app.domain.model.AppointmentStatus
import com.clinicaserena.app.testing.FakeAppointmentsRepository
import com.clinicaserena.app.testing.FakeCatalogRepository
import com.clinicaserena.app.testing.MainDispatcherRule
import com.clinicaserena.app.testing.SessionFixture
import com.clinicaserena.app.testing.TEST_CLOCK
import com.clinicaserena.app.testing.TEST_NOW
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private fun appt(id: String, at: String, status: AppointmentStatus) =
    Appointment(id, "med-1", "esp-1", ClinicTime.parse(at), status, status.name, null, null)

private fun item(a: Appointment) = AppointmentItem(a, "Dra. Elena Morales", "Odontología general")

class AppointmentsLogicTest {
    // TEST_NOW = 2026-10-07T06:00:00Z
    private val futureLater = item(appt("f2", "2026-10-09T15:00:00Z", AppointmentStatus.PENDIENTE))
    private val futureSoon = item(appt("f1", "2026-10-07T07:00:00-06:00", AppointmentStatus.CONFIRMADA))
    private val pastPending = item(appt("p1", "2026-10-06T06:11:00Z", AppointmentStatus.PENDIENTE))
    private val futureCancelled = item(appt("c1", "2026-10-10T15:00:00Z", AppointmentStatus.CANCELADA))
    private val completed = item(appt("d1", "2026-10-05T15:00:00Z", AppointmentStatus.COMPLETADA))
    private val all = listOf(futureLater, pastPending, futureCancelled, futureSoon, completed)

    @Test
    fun `proximas son activas y futuras en orden ascendente`() {
        assertEquals(listOf(futureSoon, futureLater), AppointmentsLogic.forTab(all, AppointmentTab.UPCOMING, TEST_NOW))
    }

    @Test
    fun `historial incluye pasadas y canceladas o completadas en orden descendente`() {
        assertEquals(
            listOf(futureCancelled, pastPending, completed),
            AppointmentsLogic.forTab(all, AppointmentTab.HISTORY, TEST_NOW),
        )
    }

    @Test
    fun `la proxima cita es la activa mas cercana`() {
        assertEquals(futureSoon, AppointmentsLogic.next(all, TEST_NOW))
        assertNull(AppointmentsLogic.next(listOf(pastPending, completed), TEST_NOW))
    }

    @Test
    fun `filtro por estado`() {
        val history = AppointmentsLogic.forTab(all, AppointmentTab.HISTORY, TEST_NOW)
        assertEquals(listOf(pastPending), AppointmentsLogic.filterByStatus(history, AppointmentStatus.PENDIENTE))
        assertEquals(history, AppointmentsLogic.filterByStatus(history, null))
    }

    @Test
    fun `un estado nuevo del backend no rompe la lista`() {
        assertEquals(AppointmentStatus.DESCONOCIDO, AppointmentStatus.from("REPROGRAMADA"))
    }
}

class PatientAppointmentsViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @Test
    fun `une las citas con los nombres del catalogo`() = runTest {
        val repo = FakeAppointmentsRepository().apply {
            listResult = ApiResult.Success(listOf(appt("a", "2026-10-09T15:00:00Z", AppointmentStatus.PENDIENTE)))
        }
        val vm = PatientAppointmentsViewModel(repo, FakeCatalogRepository(), SessionFixture(backgroundScope).manager, TEST_CLOCK)

        val items = (vm.state.value.load as LoadState.Loaded).data
        assertEquals("Dra. Elena Morales", items.single().practitionerName)
        assertEquals("Odontología general", items.single().specialtyName)
        assertEquals("a", vm.find("a")?.appointment?.id)
        assertNull(vm.find("otra"))
    }

    @Test
    fun `si el catalogo falla las citas se muestran sin nombres`() = runTest {
        val repo = FakeAppointmentsRepository().apply {
            listResult = ApiResult.Success(listOf(appt("a", "2026-10-09T15:00:00Z", AppointmentStatus.PENDIENTE)))
        }
        val catalog = FakeCatalogRepository().apply {
            practitionersResult = ApiResult.NetworkError
            specialtiesResult = ApiResult.NetworkError
        }
        val vm = PatientAppointmentsViewModel(repo, catalog, SessionFixture(backgroundScope).manager, TEST_CLOCK)

        val item = (vm.state.value.load as LoadState.Loaded).data.single()
        assertNull(item.practitionerName)
    }

    @Test
    fun `401 al cargar se reintenta tras el re-login`() = runTest {
        val session = SessionFixture(backgroundScope)
        session.manager.login("paciente@ejemplo.invalid", "clave")
        val repo = FakeAppointmentsRepository().apply { listResult = ApiResult.Unauthenticated("UNAUTHENTICATED", null) }
        val vm = PatientAppointmentsViewModel(repo, FakeCatalogRepository(), session.manager, TEST_CLOCK)
        assertTrue(vm.state.value.load is LoadState.Failed)

        repo.listResult = ApiResult.Success(emptyList())
        session.manager.expire()
        session.manager.login("paciente@ejemplo.invalid", "clave")

        assertEquals(LoadState.Loaded(emptyList<AppointmentItem>()), vm.state.value.load)
        assertEquals(2, repo.listCalls)
    }
}
