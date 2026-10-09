package com.clinicaserena.app.feature.patient.booking

import com.clinicaserena.app.R
import com.clinicaserena.app.core.network.ApiResult
import com.clinicaserena.app.core.network.FieldError
import com.clinicaserena.app.core.time.ClinicTime
import com.clinicaserena.app.core.ui.UserMessage
import com.clinicaserena.app.domain.model.Appointment
import com.clinicaserena.app.domain.model.AppointmentStatus
import com.clinicaserena.app.domain.model.Slot
import com.clinicaserena.app.testing.FakeAppointmentsRepository
import com.clinicaserena.app.testing.FakeCatalogRepository
import com.clinicaserena.app.testing.MainDispatcherRule
import com.clinicaserena.app.testing.SessionFixture
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BookingViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    // startAt tal como lo envía Spring (UTC con Z): debe reenviarse sin reformatear.
    private val slotRaw = "2026-10-09T15:00:00Z"
    private val slot = Slot("bloque-1", "med-1", ClinicTime.parse(slotRaw), slotRaw, ClinicTime.parse("2026-10-09T15:30:00Z"))
    private val otherSlot = Slot("bloque-2", "med-1", ClinicTime.parse("2026-10-09T16:00:00Z"), "2026-10-09T16:00:00Z", ClinicTime.parse("2026-10-09T16:30:00Z"))
    private fun appointment(scheduledAt: String = slotRaw, status: AppointmentStatus = AppointmentStatus.PENDIENTE) =
        Appointment("cita-nueva", "med-1", "esp-1", ClinicTime.parse(scheduledAt), status, status.name, "Dolor de muela", null)

    private class Rig(val catalog: FakeCatalogRepository, val appointments: FakeAppointmentsRepository, val session: SessionFixture, val vm: BookingViewModel)

    private fun TestScope.rig(): Rig {
        val catalog = FakeCatalogRepository().apply { slotsResult = ApiResult.Success(listOf(slot, otherSlot)) }
        val appointments = FakeAppointmentsRepository()
        val session = SessionFixture(backgroundScope)
        return Rig(catalog, appointments, session, BookingViewModel(catalog, appointments, session.manager))
    }

    /** Lleva el flujo hasta la confirmación con notas escritas. */
    private fun Rig.toConfirm() {
        val specialty = (vm.state.value.specialties as com.clinicaserena.app.core.ui.LoadState.Loaded).data.first()
        vm.selectSpecialty(specialty)
        val practitioner = (vm.state.value.practitioners as com.clinicaserena.app.core.ui.LoadState.Loaded).data.first()
        vm.selectPractitioner(practitioner)
        vm.selectSlot(slot)
        vm.onNotesChange("Dolor de muela")
    }

    @Test
    fun `recorre especialidad, medico, horario y confirma reenviando startAt tal cual`() = runTest {
        val r = rig()
        r.toConfirm()
        assertEquals(BookingStep.CONFIRM, r.vm.state.value.step)
        r.appointments.bookResults += ApiResult.Success(appointment())

        r.vm.submit()

        assertEquals(
            listOf(FakeAppointmentsRepository.Booking("med-1", "esp-1", slotRaw, "Dolor de muela")),
            r.appointments.bookings,
        )
        assertEquals(BookingStep.DONE, r.vm.state.value.step)
        assertEquals("cita-nueva", r.vm.state.value.booked?.id)
    }

    @Test
    fun `notas vacias no se envian`() = runTest {
        val r = rig()
        r.toConfirm()
        r.vm.onNotesChange("   ")
        r.appointments.bookResults += ApiResult.Success(appointment())

        r.vm.submit()

        assertNull(r.appointments.bookings.single().notes)
    }

    @Test
    fun `notas de mas de 1000 caracteres no se envian`() = runTest {
        val r = rig()
        r.toConfirm()
        r.vm.onNotesChange("x".repeat(1001))

        r.vm.submit()

        assertTrue(r.appointments.bookings.isEmpty())
        assertEquals(UserMessage.Local(R.string.reserva_notas_largas), r.vm.state.value.notesError)
    }

    // --- Horario que ya no sirve: refrescar y volver a horarios ---

    private fun assertSlotGone(failure: ApiResult.Failure) = runTest {
        val r = rig()
        r.toConfirm()
        val callsBefore = r.catalog.availabilityCalls
        r.appointments.bookResults += failure

        r.vm.submit()

        val state = r.vm.state.value
        assertEquals(BookingStep.SLOT, state.step)
        assertTrue(state.slotUnavailable)
        assertNull(state.slot)
        assertEquals(callsBefore + 1, r.catalog.availabilityCalls)
        assertNull(state.booked)
    }

    @Test
    fun `409 SLOT_NOT_AVAILABLE refresca los bloques y vuelve a horarios`() =
        assertSlotGone(ApiResult.Conflict("SLOT_NOT_AVAILABLE", "El bloque ya no está disponible"))

    @Test
    fun `400 SLOT_IN_PAST refresca los bloques y vuelve a horarios`() =
        assertSlotGone(ApiResult.BadRequest(400, "SLOT_IN_PAST", null))

    @Test
    fun `404 SLOT_NOT_FOUND refresca los bloques y vuelve a horarios`() =
        assertSlotGone(ApiResult.NotFound("SLOT_NOT_FOUND", null))

    // --- Otros errores: se explican sin refrescar ni volver a horarios ---

    private fun assertStaysOnConfirm(failure: ApiResult.Failure, expected: UserMessage?, notesError: UserMessage? = null) = runTest {
        val r = rig()
        r.toConfirm()
        val callsBefore = r.catalog.availabilityCalls
        r.appointments.bookResults += failure

        r.vm.submit()

        val state = r.vm.state.value
        assertEquals(BookingStep.CONFIRM, state.step)
        assertFalse(state.slotUnavailable)
        assertEquals(slot, state.slot)
        assertEquals("Dolor de muela", state.notes)
        assertEquals("no debe refrescar los bloques", callsBefore, r.catalog.availabilityCalls)
        assertEquals(expected, state.error)
        assertEquals(notesError, state.notesError)
        assertFalse(state.submitting)
    }

    @Test
    fun `400 SPECIALTY_PRACTITIONER_MISMATCH no refresca ni vuelve a horarios`() =
        assertStaysOnConfirm(
            ApiResult.BadRequest(400, "SPECIALTY_PRACTITIONER_MISMATCH", null),
            UserMessage.Local(R.string.reserva_datos_no_coinciden),
        )

    @Test
    fun `400 SLOT_PRACTITIONER_MISMATCH no refresca ni vuelve a horarios`() =
        assertStaysOnConfirm(
            ApiResult.BadRequest(400, "SLOT_PRACTITIONER_MISMATCH", null),
            UserMessage.Local(R.string.reserva_datos_no_coinciden),
        )

    @Test
    fun `400 SCHEDULED_AT_INVALID no refresca ni vuelve a horarios`() =
        assertStaysOnConfirm(
            ApiResult.BadRequest(400, "SCHEDULED_AT_INVALID", null),
            UserMessage.Local(R.string.reserva_fecha_invalida),
        )

    @Test
    fun `400 NOTES_TOO_LONG marca las notas sin refrescar`() =
        assertStaysOnConfirm(
            ApiResult.BadRequest(400, "NOTES_TOO_LONG", null),
            expected = null,
            notesError = UserMessage.Local(R.string.reserva_notas_largas),
        )

    @Test
    fun `400 VALIDATION_ERROR sin campo de notas da mensaje generico sin refrescar`() =
        assertStaysOnConfirm(
            ApiResult.Validation("Datos inválidos", listOf(FieldError("scheduledAt", "obligatorio"))),
            UserMessage.Local(R.string.reserva_error_generico),
        )

    @Test
    fun `400 VALIDATION_ERROR en notas marca el campo sin refrescar`() =
        assertStaysOnConfirm(
            ApiResult.Validation("Datos inválidos", listOf(FieldError("notes", "tamaño inválido"))),
            expected = null,
            notesError = UserMessage.Server("tamaño inválido"),
        )

    @Test
    fun `404 PRACTITIONER_NOT_FOUND no refresca ni vuelve a horarios`() =
        assertStaysOnConfirm(
            ApiResult.NotFound("PRACTITIONER_NOT_FOUND", null),
            UserMessage.Local(R.string.reserva_medico_no_disponible),
        )

    @Test
    fun `404 SPECIALTY_NOT_FOUND no refresca ni vuelve a horarios`() =
        assertStaysOnConfirm(
            ApiResult.NotFound("SPECIALTY_NOT_FOUND", null),
            UserMessage.Local(R.string.reserva_medico_no_disponible),
        )

    // --- Resultado incierto: comprobar antes de reintentar ---

    @Test
    fun `red tras enviar y la cita existe se toma como reservada sin reenviar`() = runTest {
        val r = rig()
        r.toConfirm()
        r.appointments.bookResults += ApiResult.NetworkError
        r.appointments.listResult = ApiResult.Success(listOf(appointment()))

        r.vm.submit()

        assertEquals(1, r.appointments.bookings.size)
        assertEquals(BookingStep.DONE, r.vm.state.value.step)
    }

    @Test
    fun `red tras enviar y la cita no existe permite reintentar`() = runTest {
        val r = rig()
        r.toConfirm()
        r.appointments.bookResults += ApiResult.ServerError(503)

        r.vm.submit()

        assertEquals(UserMessage.Local(R.string.reserva_no_confirmada), r.vm.state.value.error)
        assertEquals(BookingStep.CONFIRM, r.vm.state.value.step)

        r.appointments.bookResults += ApiResult.Success(appointment())
        r.vm.submit()

        assertEquals(2, r.appointments.bookings.size)
        assertEquals(BookingStep.DONE, r.vm.state.value.step)
    }

    @Test
    fun `si no se puede comprobar no se reenvia a ciegas`() = runTest {
        val r = rig()
        r.toConfirm()
        r.appointments.bookResults += ApiResult.NetworkError
        r.appointments.listResult = ApiResult.NetworkError

        r.vm.submit()
        assertEquals(UserMessage.Local(R.string.reserva_incierta), r.vm.state.value.error)

        r.vm.submit()

        assertEquals("el segundo intento solo consulta, no reenvía", 1, r.appointments.bookings.size)
        assertEquals(UserMessage.Local(R.string.reserva_incierta), r.vm.state.value.error)
    }

    @Test
    fun `409 tras un intento incierto que si se creo no se informa como ocupado`() = runTest {
        val r = rig()
        r.toConfirm()
        r.appointments.bookResults += ApiResult.NetworkError
        r.appointments.listResult = ApiResult.NetworkError
        r.vm.submit()

        // Vuelve la red: la comprobación previa ve la cita y no se reenvía.
        r.appointments.listResult = ApiResult.Success(listOf(appointment()))
        r.vm.submit()

        assertEquals(1, r.appointments.bookings.size)
        assertEquals(BookingStep.DONE, r.vm.state.value.step)
    }

    // --- Sesión vencida al confirmar ---

    @Test
    fun `401 conserva el borrador y tras re-login se reserva una sola vez`() = runTest {
        val r = rig()
        r.session.manager.login("paciente@ejemplo.invalid", "clave")
        r.toConfirm()
        r.appointments.bookResults += ApiResult.Unauthenticated("UNAUTHENTICATED", null)

        r.vm.submit()

        assertTrue(r.vm.state.value.awaitingReauth)
        assertEquals("Dolor de muela", r.vm.state.value.notes)
        assertEquals(slot, r.vm.state.value.slot)

        r.session.manager.expire()
        r.appointments.bookResults += ApiResult.Success(appointment())
        r.session.manager.login("paciente@ejemplo.invalid", "clave")

        assertEquals(2, r.appointments.bookings.size)
        assertEquals(r.appointments.bookings[0], r.appointments.bookings[1])
        assertEquals(BookingStep.DONE, r.vm.state.value.step)
        assertFalse(r.vm.state.value.awaitingReauth)

        // Otro re-login posterior no vuelve a enviar.
        r.session.manager.expire()
        r.session.manager.login("paciente@ejemplo.invalid", "clave")
        assertEquals(2, r.appointments.bookings.size)
    }

    @Test
    fun `atras recorre los pasos y en el primero sale`() = runTest {
        val r = rig()
        r.toConfirm()

        assertTrue(r.vm.back())
        assertEquals(BookingStep.SLOT, r.vm.state.value.step)
        assertTrue(r.vm.back())
        assertTrue(r.vm.back())
        assertEquals(BookingStep.SPECIALTY, r.vm.state.value.step)
        assertFalse(r.vm.back())
    }
}
