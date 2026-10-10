package com.clinicaserena.app.feature.doctor.odontogram

import com.clinicaserena.app.R
import com.clinicaserena.app.core.network.ApiResult
import com.clinicaserena.app.core.network.ApiTestRig
import com.clinicaserena.app.core.network.FieldError
import com.clinicaserena.app.core.time.ClinicTime
import com.clinicaserena.app.core.ui.LoadState
import com.clinicaserena.app.core.ui.UserMessage
import com.clinicaserena.app.data.doctor.AddendumRequest
import com.clinicaserena.app.data.doctor.ClinicalProfileRequest
import com.clinicaserena.app.data.doctor.DateRange
import com.clinicaserena.app.data.doctor.DentalObservationRequest
import com.clinicaserena.app.data.doctor.DoctorApi
import com.clinicaserena.app.data.doctor.DoctorRepository
import com.clinicaserena.app.data.doctor.RecordAttentionRequest
import com.clinicaserena.app.data.doctor.RemoteDoctorRepository
import com.clinicaserena.app.domain.model.Addendum
import com.clinicaserena.app.domain.model.AppointmentStatus
import com.clinicaserena.app.domain.model.Attention
import com.clinicaserena.app.domain.model.AttentionBlocker
import com.clinicaserena.app.domain.model.ClinicalProfile
import com.clinicaserena.app.domain.model.ClinicalRecord
import com.clinicaserena.app.domain.model.DentalObservation
import com.clinicaserena.app.domain.model.Dentition
import com.clinicaserena.app.domain.model.DoctorAppointment
import com.clinicaserena.app.domain.model.DoctorAppointmentDetail
import com.clinicaserena.app.domain.model.FdiTeeth
import com.clinicaserena.app.domain.model.Odontogram
import com.clinicaserena.app.domain.model.ToothSurface
import com.clinicaserena.app.domain.model.groupObservations
import com.clinicaserena.app.feature.doctor.care.AddendumField
import com.clinicaserena.app.feature.doctor.care.AddendumFormViewModel
import com.clinicaserena.app.feature.doctor.care.AttentionFormUiState
import com.clinicaserena.app.feature.doctor.care.AttentionFormViewModel
import com.clinicaserena.app.feature.doctor.care.ClinicalProfileFormUiState
import com.clinicaserena.app.feature.doctor.care.ClinicalProfileFormViewModel
import com.clinicaserena.app.feature.doctor.care.FormBack
import com.clinicaserena.app.feature.doctor.care.FormStep
import com.clinicaserena.app.feature.doctor.care.ItemField
import com.clinicaserena.app.feature.doctor.care.ProfileField
import com.clinicaserena.app.feature.doctor.care.formBack
import com.clinicaserena.app.testing.MainDispatcherRule
import com.clinicaserena.app.testing.SessionFixture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private fun at(value: String) = ClinicTime.parse(value)

private val ADDENDUM = Addendum("Se corrige la dosis", "Error de transcripcion", "Medico", at("2026-10-09T18:00:00Z"))

private fun attention(addenda: List<Addendum> = emptyList()) = Attention(
    "att-a", at("2026-10-07T06:11:00Z"), "Dra. Elena Morales", "Medico", "Control", null,
    "Gingivitis", null, at("2026-10-09T17:30:00Z"), emptyList(), addenda,
)

private fun appointment(id: String = "cita-d", canRecord: Boolean = true, blockers: List<String> = emptyList()) = DoctorAppointment(
    id, "p1", "Paciente Uno", "Odontología general", at("2026-10-09T16:00:00Z"),
    AppointmentStatus.CONFIRMADA, "CONFIRMADA", null, at("2026-10-09T15:55:00Z"),
    !canRecord, canRecord, blockers.map { AttentionBlocker.from(it) }, blockers,
)

private fun observation(
    id: String,
    tooth: Int,
    surface: String?,
    recordedAt: String,
    appointmentId: String = "cita-d",
    text: String = "Obs $id",
) = DentalObservation(id, appointmentId, tooth, surface?.let { ToothSurface.from(it) }, surface, text, at(recordedAt))

/** Doble completo del repositorio del médico; los envíos pueden quedar "colgados" con [gate]. */
private class FakeRepository : DoctorRepository {
    var detailResult: ApiResult<DoctorAppointmentDetail> = ApiResult.Success(DoctorAppointmentDetail(appointment(), null))
    var odontogramResult: ApiResult<Odontogram> = ApiResult.Success(Odontogram("Paciente Uno", emptyList()))
    val addendumResults = ArrayDeque<ApiResult<Addendum>>()
    val observationResults = ArrayDeque<ApiResult<DentalObservation>>()
    val addendumCalls = mutableListOf<Pair<String, AddendumRequest>>()
    val observationCalls = mutableListOf<DentalObservationRequest>()
    var detailCalls = 0
    var odontogramCalls = 0
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun agenda(range: DateRange, status: AppointmentStatus?) = ApiResult.Success(emptyList<DoctorAppointment>())
    override suspend fun appointment(appointmentId: String): ApiResult<DoctorAppointmentDetail> {
        detailCalls++
        return detailResult
    }
    override suspend fun record(appointmentId: String): ApiResult<ClinicalRecord> =
        ApiResult.Success(ClinicalRecord(null, null, emptyList(), emptyList()))
    override suspend fun recordAttention(appointmentId: String, request: RecordAttentionRequest): ApiResult<Attention> = error("no se usa")
    override suspend fun addClinicalProfile(appointmentId: String, request: ClinicalProfileRequest): ApiResult<ClinicalProfile> = error("no se usa")
    override suspend fun addAddendum(appointmentId: String, attentionId: String, request: AddendumRequest): ApiResult<Addendum> {
        addendumCalls += attentionId to request
        gate?.await()
        return addendumResults.removeFirst()
    }
    override suspend fun odontogram(patientId: String): ApiResult<Odontogram> {
        odontogramCalls++
        return odontogramResult
    }
    override suspend fun addObservation(appointmentId: String, request: DentalObservationRequest): ApiResult<DentalObservation> {
        observationCalls += request
        gate?.await()
        return observationResults.removeFirst()
    }
}

class AddendumFormViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private fun AddendumFormViewModel.fillValid() {
        onChange(AddendumField.TEXT, "  Se corrige la dosis  ")
        onChange(AddendumField.REASON, "Error de transcripcion")
    }

    @Test
    fun `valida limites antes de confirmar`() = runTest {
        val repo = FakeRepository()
        val vm = AddendumFormViewModel(repo, SessionFixture(backgroundScope).manager, "cita-a", "att-a")
        vm.onChange(AddendumField.TEXT, " ab ")
        vm.onChange(AddendumField.REASON, "x".repeat(501))

        vm.review()

        assertEquals(FormStep.FORM, vm.state.value.step)
        assertEquals(UserMessage.Local(R.string.adenda_validacion_texto), vm.state.value.fieldErrors[AddendumField.TEXT])
        assertEquals(UserMessage.Local(R.string.adenda_validacion_motivo), vm.state.value.fieldErrors[AddendumField.REASON])
        assertTrue(repo.addendumCalls.isEmpty())
    }

    @Test
    fun `doble toque registra una sola adenda con texto recortado`() = runTest {
        val repo = FakeRepository().apply { gate = CompletableDeferred(); addendumResults += ApiResult.Success(ADDENDUM) }
        val vm = AddendumFormViewModel(repo, SessionFixture(backgroundScope).manager, "cita-a", "att-a")
        vm.fillValid()
        vm.review()
        assertEquals(FormStep.CONFIRM, vm.state.value.step)

        vm.submit()
        assertTrue(vm.state.value.submitting)
        vm.submit()
        repo.gate!!.complete(Unit)

        assertEquals(listOf("att-a" to AddendumRequest("Se corrige la dosis", "Error de transcripcion")), repo.addendumCalls)
        assertEquals(FormStep.DONE, vm.state.value.step)
    }

    @Test
    fun `404 ATTENTION_NOT_FOUND muestra su mensaje y conserva el borrador`() = runTest {
        val repo = FakeRepository().apply { addendumResults += ApiResult.NotFound("ATTENTION_NOT_FOUND", null) }
        val vm = AddendumFormViewModel(repo, SessionFixture(backgroundScope).manager, "cita-a", "att-x")
        vm.fillValid()
        vm.review()

        vm.submit()

        assertEquals(UserMessage.Local(R.string.adenda_404), vm.state.value.error)
        assertEquals("Error de transcripcion", vm.state.value.reason)
    }

    @Test
    fun `errores por campo de Spring vuelven al formulario`() = runTest {
        val repo = FakeRepository().apply {
            addendumResults += ApiResult.Validation(null, listOf(FieldError("reason", "tamaño inválido")))
        }
        val vm = AddendumFormViewModel(repo, SessionFixture(backgroundScope).manager, "cita-a", "att-a")
        vm.fillValid()
        vm.review()

        vm.submit()

        assertEquals(FormStep.FORM, vm.state.value.step)
        assertEquals(UserMessage.Server("tamaño inválido"), vm.state.value.fieldErrors[AddendumField.REASON])
    }

    @Test
    fun `red tras enviar y la adenda ya aparece en la cita se toma como registrada`() = runTest {
        val repo = FakeRepository().apply {
            addendumResults += ApiResult.NetworkError
            detailResult = ApiResult.Success(DoctorAppointmentDetail(appointment("cita-a", false), attention(listOf(ADDENDUM))))
        }
        val vm = AddendumFormViewModel(repo, SessionFixture(backgroundScope).manager, "cita-a", "att-a")
        vm.fillValid()
        vm.review()

        vm.submit()

        assertEquals(1, repo.addendumCalls.size)
        assertEquals(FormStep.DONE, vm.state.value.step)
        assertEquals(ADDENDUM, vm.state.value.saved)
    }

    @Test
    fun `red tras enviar sin poder consultar la cita no reenvia`() = runTest {
        val repo = FakeRepository().apply {
            addendumResults += ApiResult.ServerError(503)
            detailResult = ApiResult.NetworkError
        }
        val vm = AddendumFormViewModel(repo, SessionFixture(backgroundScope).manager, "cita-a", "att-a")
        vm.fillValid()
        vm.review()

        vm.submit()
        vm.submit()

        assertEquals(1, repo.addendumCalls.size)
        assertEquals(UserMessage.Local(R.string.adenda_incierta), vm.state.value.error)
    }

    @Test
    fun `401 conserva el borrador y tras re-login registra una sola vez`() = runTest {
        val session = SessionFixture(backgroundScope)
        session.manager.login("medico@ejemplo.invalid", "clave")
        val repo = FakeRepository().apply { addendumResults += ApiResult.Unauthenticated("UNAUTHENTICATED", null) }
        val vm = AddendumFormViewModel(repo, session.manager, "cita-a", "att-a")
        vm.fillValid()
        vm.review()

        vm.submit()
        assertTrue(vm.state.value.awaitingReauth)
        repo.addendumResults += ApiResult.Success(ADDENDUM)
        session.manager.expire()
        session.manager.login("medico@ejemplo.invalid", "clave")
        session.manager.expire()
        session.manager.login("medico@ejemplo.invalid", "clave")

        assertEquals(2, repo.addendumCalls.size)
        assertEquals(repo.addendumCalls[0], repo.addendumCalls[1])
        assertEquals(FormStep.DONE, vm.state.value.step)
    }
}

class ObservationFormViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val saved = observation("o1", 16, "OCLUSAL", "2026-10-09T18:10:00Z", text = "Caries incipiente")

    private fun ObservationFormViewModel.fillValid() {
        selectTooth(16)
        selectSurface(ToothSurface.OCLUSAL)
        onObservationChange("  Caries incipiente ")
    }

    @Test
    fun `catalogo FDI rechaza piezas fuera de rango sin enviarlas`() = runTest {
        val valid = (11..18) + (21..28) + (31..38) + (41..48) + (51..55) + (61..65) + (71..75) + (81..85)
        assertEquals(52, valid.size)
        valid.forEach { assertTrue("$it", FdiTeeth.isValid(it)) }
        listOf(10, 19, 20, 29, 39, 49, 50, 56, 66, 76, 86, 99).forEach { assertFalse("$it", FdiTeeth.isValid(it)) }
        assertEquals(Dentition.TEMPORARY, FdiTeeth.dentition(55))
        assertEquals(Dentition.PERMANENT, FdiTeeth.dentition(48))

        val repo = FakeRepository()
        val vm = ObservationFormViewModel(repo, SessionFixture(backgroundScope).manager, "cita-d", "p1")
        listOf(19, 56, 86).forEach { tooth ->
            vm.selectTooth(tooth)
            assertNull(vm.state.value.tooth)
            assertEquals(UserMessage.Local(R.string.odontograma_validacion_pieza), vm.state.value.toothError)
        }
        vm.selectSurface(ToothSurface.MESIAL)
        vm.onObservationChange("x")
        vm.review()
        vm.submit()

        assertEquals(FormStep.FORM, vm.state.value.step)
        assertTrue(repo.observationCalls.isEmpty())
    }

    @Test
    fun `exige pieza, superficie y observacion de 1 a 500`() = runTest {
        val vm = ObservationFormViewModel(FakeRepository(), SessionFixture(backgroundScope).manager, "cita-d", "p1")
        vm.onObservationChange("x".repeat(501))

        vm.review()

        val state = vm.state.value
        assertEquals(FormStep.FORM, state.step)
        assertEquals(UserMessage.Local(R.string.odontograma_validacion_pieza), state.toothError)
        assertEquals(UserMessage.Local(R.string.odontograma_validacion_superficie), state.surfaceError)
        assertEquals(UserMessage.Local(R.string.odontograma_validacion_observacion), state.observationError)
    }

    @Test
    fun `confirmacion y doble toque envian una sola observacion`() = runTest {
        val repo = FakeRepository().apply { gate = CompletableDeferred(); observationResults += ApiResult.Success(saved) }
        val vm = ObservationFormViewModel(repo, SessionFixture(backgroundScope).manager, "cita-d", "p1")
        vm.fillValid()
        vm.review()
        assertEquals(FormStep.CONFIRM, vm.state.value.step)

        vm.submit()
        assertTrue(vm.state.value.submitting)
        vm.submit()
        repo.gate!!.complete(Unit)

        assertEquals(listOf(DentalObservationRequest(16, "OCLUSAL", "Caries incipiente")), repo.observationCalls)
        assertEquals(FormStep.DONE, vm.state.value.step)
    }

    @Test
    fun `cada 409 tiene su mensaje y no se reintenta solo`() = runTest {
        mapOf(
            "APPOINTMENT_NOT_DOCUMENTABLE" to R.string.odontograma_409_no_documentable,
            "APPOINTMENT_NOT_STARTED" to R.string.odontograma_409_no_iniciada,
            "ARRIVAL_NOT_REGISTERED" to R.string.odontograma_409_sin_llegada,
            "PATIENT_RECORD_UNAVAILABLE" to R.string.atencion_409_sin_paciente,
            "OTRO" to R.string.odontograma_409_generico,
        ).forEach { (code, message) ->
            val repo = FakeRepository().apply { observationResults += ApiResult.Conflict(code, null) }
            val vm = ObservationFormViewModel(repo, SessionFixture(backgroundScope).manager, "cita-a", "p1")
            vm.fillValid()
            vm.review()

            vm.submit()

            assertEquals(code, UserMessage.Local(message), vm.state.value.error)
            assertEquals(1, repo.observationCalls.size)
            assertEquals(16, vm.state.value.tooth)
        }
    }

    @Test
    fun `400 de dominio marca el campo y 404 tiene su mensaje`() = runTest {
        val repo = FakeRepository().apply {
            observationResults += ApiResult.BadRequest(400, "SURFACE_INVALID", null)
            observationResults += ApiResult.NotFound("PATIENT_NOT_FOUND", null)
        }
        val vm = ObservationFormViewModel(repo, SessionFixture(backgroundScope).manager, "cita-x", "p1")
        vm.fillValid()
        vm.review()

        vm.submit()
        assertEquals(FormStep.FORM, vm.state.value.step)
        assertEquals(UserMessage.Local(R.string.odontograma_validacion_superficie), vm.state.value.surfaceError)

        vm.review()
        vm.submit()
        assertEquals(UserMessage.Local(R.string.odontograma_404), vm.state.value.error)
    }

    @Test
    fun `red tras enviar consulta el historial antes de reintentar`() = runTest {
        val repo = FakeRepository().apply {
            observationResults += ApiResult.NetworkError
            odontogramResult = ApiResult.Success(Odontogram("Paciente Uno", listOf(saved.copy(appointmentId = "otra-cita"))))
        }
        val vm = ObservationFormViewModel(repo, SessionFixture(backgroundScope).manager, "cita-d", "p1")
        vm.fillValid()
        vm.review()

        vm.submit()
        assertEquals("la misma observación de otra cita no cuenta", UserMessage.Local(R.string.odontograma_no_guardada), vm.state.value.error)

        repo.odontogramResult = ApiResult.Success(Odontogram("Paciente Uno", listOf(saved)))
        vm.submit()

        assertEquals("encontrada en el historial: no se reenvía", 1, repo.observationCalls.size)
        assertEquals(2, repo.odontogramCalls)
        assertEquals(FormStep.DONE, vm.state.value.step)
    }

    @Test
    fun `401 tras re-login registra una sola vez`() = runTest {
        val session = SessionFixture(backgroundScope)
        session.manager.login("medico@ejemplo.invalid", "clave")
        val repo = FakeRepository().apply { observationResults += ApiResult.Unauthenticated("UNAUTHENTICATED", null) }
        val vm = ObservationFormViewModel(repo, session.manager, "cita-d", "p1")
        vm.fillValid()
        vm.review()

        vm.submit()
        repo.observationResults += ApiResult.Success(saved)
        session.manager.expire()
        session.manager.login("medico@ejemplo.invalid", "clave")
        session.manager.expire()
        session.manager.login("medico@ejemplo.invalid", "clave")

        assertEquals(2, repo.observationCalls.size)
        assertEquals(FormStep.DONE, vm.state.value.step)
    }
}

class OdontogramHistoryTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @Test
    fun `agrupa por pieza en orden FDI y por superficie, de lo mas reciente a lo mas antiguo`() {
        val history = groupObservations(
            listOf(
                observation("a", 55, "DISTAL", "2026-10-09T18:00:00Z"),
                observation("b", 16, "OCLUSAL", "2026-10-01T10:00:00Z"),
                observation("c", 16, null, "2026-09-01T10:00:00Z"),
                observation("d", 16, "OCLUSAL", "2026-10-09T18:05:00Z"),
                observation("e", 16, "MESIAL", "2026-10-02T10:00:00Z"),
                observation("f", 21, "VESTIBULAR", "2026-10-03T10:00:00Z"),
            ),
        )

        assertEquals(listOf(16, 21, 55), history.map { it.toothNumber })
        assertEquals(listOf(Dentition.PERMANENT, Dentition.PERMANENT, Dentition.TEMPORARY), history.map { it.dentition })
        val tooth16 = history.first().surfaces
        assertEquals(listOf(ToothSurface.MESIAL, ToothSurface.OCLUSAL, null), tooth16.map { it.surface })
        assertEquals(listOf("d", "b"), tooth16[1].observations.map { it.id })
        assertNull("sin superficie: registro antiguo", tooth16[2].surfaceRaw)
    }

    @Test
    fun `superficie desconocida se conserva con su texto original`() {
        val history = groupObservations(listOf(observation("x", 11, "RARA", "2026-10-01T10:00:00Z")))

        val surface = history.single().surfaces.single()
        assertNull(surface.surface)
        assertEquals("RARA", surface.surfaceRaw)
    }

    @Test
    fun `carga historial y cita, y 404 PATIENT_NOT_FOUND queda tipado`() = runTest {
        val repo = FakeRepository().apply {
            odontogramResult = ApiResult.Success(Odontogram("Paciente Uno", listOf(observation("a", 16, "OCLUSAL", "2026-10-09T18:00:00Z"))))
            detailResult = ApiResult.Success(DoctorAppointmentDetail(appointment("cita-a", false, listOf("ATTENTION_ALREADY_RECORDED")), null))
        }
        val vm = OdontogramViewModel(repo, SessionFixture(backgroundScope).manager, "cita-a", "p1")

        val loaded = (vm.state.value as LoadState.Loaded).data
        assertEquals(listOf(16), loaded.history.map { it.toothNumber })
        assertFalse(loaded.appointment.canRecordAttention)

        repo.odontogramResult = ApiResult.NotFound("PATIENT_NOT_FOUND", null)
        vm.load()
        assertEquals(ApiResult.NotFound("PATIENT_NOT_FOUND", null), (vm.state.value as LoadState.Failed).failure)
    }
}

/** "¿Descartar borrador?" en los 4 formularios inmutables: con flecha, botón Atrás o gesto (todos llaman a onBack). */
class DiscardDraftTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @Test
    fun `regla comun de Atras`() {
        assertEquals(FormBack.EXIT, formBack(FormStep.FORM, hasChanges = false))
        assertEquals(FormBack.STAY, formBack(FormStep.FORM, hasChanges = true))
        assertEquals(FormBack.STAY, formBack(FormStep.CONFIRM, hasChanges = true))
        assertEquals(FormBack.FINISHED, formBack(FormStep.DONE, hasChanges = true))
    }

    @Test
    fun `atencion sin cambios sale y con cambios pide confirmar`() = runTest {
        val vm = AttentionFormViewModel(FakeRepository(), SessionFixture(backgroundScope).manager, "cita-a")
        assertEquals(FormBack.EXIT, vm.onBack())
        assertFalse(vm.state.value.confirmDiscard)

        vm.addItem()
        assertEquals("un medicamento vacío también es un cambio", FormBack.STAY, vm.onBack())
        assertTrue(vm.state.value.confirmDiscard)

        vm.keepEditing()
        assertFalse(vm.state.value.confirmDiscard)
        vm.onItemChange(0, ItemField.MEDICINE, "Ibuprofeno")
        vm.onReasonChange("Control")
        assertEquals(FormBack.STAY, vm.onBack())

        vm.discardDraft()
        assertEquals(AttentionFormUiState(), vm.state.value)
        assertEquals(FormBack.EXIT, vm.onBack())
    }

    @Test
    fun `atencion desde la confirmacion vuelve al formulario sin dialogo`() = runTest {
        val vm = AttentionFormViewModel(FakeRepository(), SessionFixture(backgroundScope).manager, "cita-a")
        vm.onReasonChange("Control odontologico")
        vm.onDiagnosisChange("Gingivitis")
        vm.review()
        assertEquals(FormStep.CONFIRM, vm.state.value.step)

        assertEquals(FormBack.STAY, vm.onBack())

        assertEquals(FormStep.FORM, vm.state.value.step)
        assertFalse(vm.state.value.confirmDiscard)
        assertEquals("Control odontologico", vm.state.value.reason)
    }

    @Test
    fun `perfil clinico`() = runTest {
        val vm = ClinicalProfileFormViewModel(FakeRepository(), SessionFixture(backgroundScope).manager, "cita-a")
        assertEquals(FormBack.EXIT, vm.onBack())
        vm.onChange(ProfileField.ALLERGIES, "   ")
        assertEquals("solo espacios no es un cambio", FormBack.EXIT, vm.onBack())

        vm.onChange(ProfileField.DENTAL_HISTORY, "Ortodoncia previa")
        assertEquals(FormBack.STAY, vm.onBack())
        assertTrue(vm.state.value.confirmDiscard)

        vm.discardDraft()
        assertEquals(ClinicalProfileFormUiState(), vm.state.value)
    }

    @Test
    fun `adenda`() = runTest {
        val vm = AddendumFormViewModel(FakeRepository(), SessionFixture(backgroundScope).manager, "cita-a", "att-a")
        assertEquals(FormBack.EXIT, vm.onBack())
        vm.onChange(AddendumField.REASON, "Correccion")
        assertEquals(FormBack.STAY, vm.onBack())
        assertTrue(vm.state.value.confirmDiscard)

        vm.keepEditing()
        assertEquals("Correccion", vm.state.value.reason)
        vm.discardDraft()
        assertEquals("", vm.state.value.reason)
        assertEquals(FormBack.EXIT, vm.onBack())
    }

    @Test
    fun `observacion, incluso solo con la pieza elegida`() = runTest {
        val repo = FakeRepository().apply { observationResults += ApiResult.Success(observation("o", 16, "OCLUSAL", "2026-10-09T18:00:00Z")) }
        val vm = ObservationFormViewModel(repo, SessionFixture(backgroundScope).manager, "cita-d", "p1")
        assertEquals(FormBack.EXIT, vm.onBack())
        vm.selectTooth(16)
        assertEquals(FormBack.STAY, vm.onBack())
        assertTrue(vm.state.value.confirmDiscard)
        vm.keepEditing()

        vm.selectSurface(ToothSurface.OCLUSAL)
        vm.onObservationChange("Caries")
        vm.review()
        vm.submit()
        assertEquals("tras registrar, Atrás sale y recarga el historial", FormBack.FINISHED, vm.onBack())
    }
}

class DoctorC3RepositoryTest {

    private val rig = ApiTestRig()
    private val repo = RemoteDoctorRepository(rig.retrofit.create(DoctorApi::class.java), rig.apiCaller)

    @After
    fun tearDown() = rig.close()

    @Test
    fun `adenda usa la ruta de la atencion y solo texto y motivo`() = runTest {
        rig.enqueue(
            201,
            """{"id":"ad","text":"Se corrige","reason":"Error","authorAccountId":"acc","authorName":"Medico","recordedAt":"2026-10-09T18:00:00Z"}""",
        )

        val result = repo.addAddendum("a", "t", AddendumRequest("Se corrige", "Error"))

        val request = rig.server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/medico/citas/a/atenciones/t/adendas", request.url.encodedPath)
        assertEquals("""{"text":"Se corrige","reason":"Error"}""", request.body?.utf8())
        assertEquals("Medico", (result as ApiResult.Success).data.authorName)
    }

    @Test
    fun `odontograma del paciente y observacion nueva`() = runTest {
        rig.enqueue(
            200,
            """{"patientId":"p1","patientName":"Paciente Uno","observations":[
                {"id":"o2","patientId":"p1","appointmentId":"d","practitionerId":"m","recordedByAccountId":"acc","toothNumber":55,"surface":"DISTAL","observation":"Temporal","recordedAt":"2026-10-09T18:05:00Z"},
                {"id":"o1","patientId":"p1","appointmentId":"d","practitionerId":"m","recordedByAccountId":"acc","toothNumber":16,"surface":null,"observation":"Antigua","recordedAt":"2026-10-01T18:00:00Z"}]}""",
        )
        rig.enqueue(
            201,
            """{"id":"o3","patientId":"p1","appointmentId":"d","toothNumber":16,"surface":"OCLUSAL","observation":"Caries","recordedAt":"2026-10-09T18:10:00Z"}""",
        )

        val page = (repo.odontogram("p1") as ApiResult.Success).data
        val saved = (repo.addObservation("d", DentalObservationRequest(16, "OCLUSAL", "Caries")) as ApiResult.Success).data

        assertEquals("/api/v1/medico/pacientes/p1/odontograma", rig.server.takeRequest().url.encodedPath)
        val post = rig.server.takeRequest()
        assertEquals("/api/v1/medico/citas/d/odontograma", post.url.encodedPath)
        assertEquals("""{"toothNumber":16,"surface":"OCLUSAL","observation":"Caries"}""", post.body?.utf8())
        assertEquals("Paciente Uno", page.patientName)
        assertEquals(listOf(ToothSurface.DISTAL, null), page.observations.map { it.surface })
        assertEquals(ToothSurface.OCLUSAL, saved.surface)
    }

    @Test
    fun `404 y 409 del odontograma conservan su code`() = runTest {
        rig.enqueue(404, ApiTestRig.error(404, "PATIENT_NOT_FOUND"))
        rig.enqueue(409, ApiTestRig.error(409, "APPOINTMENT_NOT_DOCUMENTABLE"))
        rig.enqueue(404, ApiTestRig.error(404, "ATTENTION_NOT_FOUND"))

        assertEquals(ApiResult.NotFound("PATIENT_NOT_FOUND", "Mensaje"), repo.odontogram("ajeno"))
        assertEquals(
            ApiResult.Conflict("APPOINTMENT_NOT_DOCUMENTABLE", "Mensaje"),
            repo.addObservation("a", DentalObservationRequest(16, "OCLUSAL", "x")),
        )
        assertEquals(ApiResult.NotFound("ATTENTION_NOT_FOUND", "Mensaje"), repo.addAddendum("a", "ajena", AddendumRequest("abc", "abc")))
    }
}
