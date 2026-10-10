package com.clinicaserena.app.feature.doctor.care

import com.clinicaserena.app.R
import com.clinicaserena.app.core.network.ApiResult
import com.clinicaserena.app.core.network.ApiTestRig
import com.clinicaserena.app.core.network.FieldError
import com.clinicaserena.app.core.time.ClinicTime
import com.clinicaserena.app.core.ui.UserMessage
import com.clinicaserena.app.data.doctor.AddendumRequest
import com.clinicaserena.app.data.doctor.ClinicalProfileRequest
import com.clinicaserena.app.data.doctor.DentalObservationRequest
import com.clinicaserena.app.data.doctor.DateRange
import com.clinicaserena.app.data.doctor.DoctorApi
import com.clinicaserena.app.data.doctor.DoctorRepository
import com.clinicaserena.app.data.doctor.RecordAttentionRequest
import com.clinicaserena.app.data.doctor.RemoteDoctorRepository
import com.clinicaserena.app.domain.model.Addendum
import com.clinicaserena.app.domain.model.AppointmentStatus
import com.clinicaserena.app.domain.model.DentalObservation
import com.clinicaserena.app.domain.model.Odontogram
import com.clinicaserena.app.domain.model.Attention
import com.clinicaserena.app.domain.model.ClinicalProfile
import com.clinicaserena.app.domain.model.ClinicalRecord
import com.clinicaserena.app.domain.model.DoctorAppointment
import com.clinicaserena.app.domain.model.DoctorAppointmentDetail
import com.clinicaserena.app.testing.MainDispatcherRule
import com.clinicaserena.app.testing.SessionFixture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private val ATTENTION = Attention(
    "att-1", ClinicTime.parse("2026-10-07T06:11:00Z"), "Dra. Elena Morales", "Medico", "Control", null,
    "Gingivitis", null, ClinicTime.parse("2026-10-09T17:30:00Z"), emptyList(), emptyList(),
)

private fun detail(attention: Attention?) = DoctorAppointmentDetail(
    DoctorAppointment(
        "cita-a", "p1", "Paciente", "Odontología general", ClinicTime.parse("2026-10-07T06:11:00Z"),
        AppointmentStatus.PENDIENTE, "PENDIENTE", null, ClinicTime.parse("2026-10-07T06:09:10Z"),
        attention != null, attention == null, emptyList(), emptyList(),
    ),
    attention,
)

/** Repositorio configurable; un envío puede quedar "colgado" para probar el doble toque. */
private class FakeCareRepository : DoctorRepository {
    val attentionResults = ArrayDeque<ApiResult<Attention>>()
    val profileResults = ArrayDeque<ApiResult<ClinicalProfile>>()
    var detailResult: ApiResult<DoctorAppointmentDetail> = ApiResult.Success(detail(null))
    var recordResult: ApiResult<ClinicalRecord> = ApiResult.Success(ClinicalRecord(null, null, emptyList(), emptyList()))
    var gate: CompletableDeferred<Unit>? = null
    val attentionCalls = mutableListOf<RecordAttentionRequest>()
    val profileCalls = mutableListOf<ClinicalProfileRequest>()
    var detailCalls = 0

    override suspend fun agenda(range: DateRange, status: AppointmentStatus?) = ApiResult.Success(emptyList<DoctorAppointment>())
    override suspend fun appointment(appointmentId: String): ApiResult<DoctorAppointmentDetail> {
        detailCalls++
        return detailResult
    }
    override suspend fun record(appointmentId: String) = recordResult
    override suspend fun recordAttention(appointmentId: String, request: RecordAttentionRequest): ApiResult<Attention> {
        attentionCalls += request
        gate?.await()
        return attentionResults.removeFirst()
    }
    override suspend fun addClinicalProfile(appointmentId: String, request: ClinicalProfileRequest): ApiResult<ClinicalProfile> {
        profileCalls += request
        gate?.await()
        return profileResults.removeFirst()
    }
    override suspend fun addAddendum(appointmentId: String, attentionId: String, request: AddendumRequest): ApiResult<Addendum> =
        error("no se usa")
    override suspend fun odontogram(patientId: String): ApiResult<Odontogram> = error("no se usa")
    override suspend fun addObservation(appointmentId: String, request: DentalObservationRequest): ApiResult<DentalObservation> =
        error("no se usa")
}

class AttentionFormViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private class Rig(val repo: FakeCareRepository, val session: SessionFixture, val vm: AttentionFormViewModel)

    private fun TestScope.rig(): Rig {
        val repo = FakeCareRepository()
        val session = SessionFixture(backgroundScope)
        return Rig(repo, session, AttentionFormViewModel(repo, session.manager, "cita-a"))
    }

    private fun AttentionFormViewModel.fillValid(items: Int = 2) {
        onReasonChange("  Control odontologico  ")
        onDiagnosisChange("Gingivitis leve")
        onFindingsChange("   ")
        repeat(items) { i ->
            addItem()
            onItemChange(i, ItemField.MEDICINE, "Medicamento $i")
            onItemChange(i, ItemField.DOSE, "1 tableta")
            onItemChange(i, ItemField.FREQUENCY, "Cada 8 horas")
            onItemChange(i, ItemField.DURATION, "3 dias")
        }
    }

    @Test
    fun `valida limites antes de confirmar`() = runTest {
        val r = rig()
        r.vm.onReasonChange("ab")
        r.vm.addItem()
        r.vm.onItemChange(0, ItemField.MEDICINE, "A")

        r.vm.review()

        val errors = r.vm.state.value.fieldErrors
        assertEquals(FormStep.FORM, r.vm.state.value.step)
        assertEquals(UserMessage.Local(R.string.atencion_validacion_motivo), errors["reason"])
        assertEquals(UserMessage.Local(R.string.atencion_validacion_diagnostico), errors["diagnosis"])
        assertEquals(UserMessage.Local(R.string.atencion_validacion_medicamento), errors["prescription[0].medicine"])
        assertEquals(UserMessage.Local(R.string.atencion_validacion_80), errors["prescription[0].dose"])
        assertTrue(r.repo.attentionCalls.isEmpty())
    }

    @Test
    fun `maximo 10 medicamentos`() = runTest {
        val r = rig()
        repeat(12) { r.vm.addItem() }

        assertEquals(10, r.vm.state.value.items.size)
    }

    @Test
    fun `confirmacion y envio con texto recortado y opcionales nulos`() = runTest {
        val r = rig()
        r.vm.fillValid()
        r.vm.review()
        assertEquals(FormStep.CONFIRM, r.vm.state.value.step)
        r.repo.attentionResults += ApiResult.Success(ATTENTION)

        r.vm.submit()

        val sent = r.repo.attentionCalls.single()
        assertEquals("Control odontologico", sent.reason)
        assertNull(sent.findings)
        assertNull(sent.prescription[0].instructions)
        assertEquals(2, sent.prescription.size)
        assertEquals(FormStep.DONE, r.vm.state.value.step)
    }

    @Test
    fun `doble toque en confirmar envia una sola vez`() = runTest {
        val r = rig()
        r.vm.fillValid()
        r.vm.review()
        r.repo.gate = CompletableDeferred()
        r.repo.attentionResults += ApiResult.Success(ATTENTION)

        r.vm.submit()
        assertTrue(r.vm.state.value.submitting)
        r.vm.submit()
        r.vm.submit()
        r.repo.gate!!.complete(Unit)

        assertEquals(1, r.repo.attentionCalls.size)
        assertEquals(FormStep.DONE, r.vm.state.value.step)
    }

    @Test
    fun `cada 409 tiene su mensaje y conserva el borrador`() = runTest {
        val codes = mapOf(
            "ATTENTION_ALREADY_RECORDED" to R.string.atencion_409_ya_registrada,
            "APPOINTMENT_NOT_DOCUMENTABLE" to R.string.atencion_409_no_documentable,
            "APPOINTMENT_NOT_STARTED" to R.string.atencion_409_no_iniciada,
            "ARRIVAL_NOT_REGISTERED" to R.string.atencion_409_sin_llegada,
            "PATIENT_RECORD_UNAVAILABLE" to R.string.atencion_409_sin_paciente,
        )
        codes.forEach { (code, message) ->
            val r = rig()
            r.vm.fillValid()
            r.vm.review()
            r.repo.attentionResults += ApiResult.Conflict(code, "msg")

            r.vm.submit()

            val state = r.vm.state.value
            assertEquals(code, UserMessage.Local(message), state.error)
            assertEquals(code == "ATTENTION_ALREADY_RECORDED", state.alreadyRecorded)
            assertEquals("Control odontologico", state.reason.trim())
            assertEquals(2, state.items.size)
            assertFalse(state.submitting)
            assertEquals("un 409 no se reintenta solo", 1, r.repo.attentionCalls.size)
        }
    }

    @Test
    fun `errores por campo de Spring vuelven al formulario`() = runTest {
        val r = rig()
        r.vm.fillValid()
        r.vm.review()
        r.repo.attentionResults += ApiResult.Validation("Datos inválidos", listOf(FieldError("prescription[1].dose", "tamaño inválido")))

        r.vm.submit()

        assertEquals(FormStep.FORM, r.vm.state.value.step)
        assertEquals(UserMessage.Server("tamaño inválido"), r.vm.state.value.fieldErrors["prescription[1].dose"])
    }

    @Test
    fun `red tras enviar y la atencion existe se toma como registrada sin reenviar`() = runTest {
        val r = rig()
        r.vm.fillValid()
        r.vm.review()
        r.repo.attentionResults += ApiResult.NetworkError
        r.repo.detailResult = ApiResult.Success(detail(ATTENTION))

        r.vm.submit()

        assertEquals(1, r.repo.attentionCalls.size)
        assertEquals(1, r.repo.detailCalls)
        assertEquals(FormStep.DONE, r.vm.state.value.step)
        assertEquals(ATTENTION, r.vm.state.value.recorded)
    }

    @Test
    fun `red tras enviar sin atencion permite reintentar tras comprobar de nuevo`() = runTest {
        val r = rig()
        r.vm.fillValid()
        r.vm.review()
        r.repo.attentionResults += ApiResult.ServerError(503)

        r.vm.submit()

        assertEquals(UserMessage.Local(R.string.atencion_no_registrada_reintentar), r.vm.state.value.error)
        assertEquals(FormStep.CONFIRM, r.vm.state.value.step)

        r.repo.attentionResults += ApiResult.Success(ATTENTION)
        r.vm.submit()

        assertEquals("antes de reenviar se vuelve a consultar el detalle", 2, r.repo.detailCalls)
        assertEquals(2, r.repo.attentionCalls.size)
        assertEquals(FormStep.DONE, r.vm.state.value.step)
    }

    @Test
    fun `si no se puede consultar el detalle no se reenvia`() = runTest {
        val r = rig()
        r.vm.fillValid()
        r.vm.review()
        r.repo.attentionResults += ApiResult.NetworkError
        r.repo.detailResult = ApiResult.NetworkError

        r.vm.submit()
        r.vm.submit()

        assertEquals(1, r.repo.attentionCalls.size)
        assertEquals(UserMessage.Local(R.string.atencion_incierta), r.vm.state.value.error)
    }

    @Test
    fun `401 conserva el borrador y tras re-login se registra una sola vez`() = runTest {
        val r = rig()
        r.session.manager.login("medico@ejemplo.invalid", "clave")
        r.vm.fillValid()
        r.vm.review()
        r.repo.attentionResults += ApiResult.Unauthenticated("UNAUTHENTICATED", null)

        r.vm.submit()
        assertTrue(r.vm.state.value.awaitingReauth)

        r.repo.attentionResults += ApiResult.Success(ATTENTION)
        r.session.manager.expire()
        r.session.manager.login("medico@ejemplo.invalid", "clave")

        assertEquals(2, r.repo.attentionCalls.size)
        assertEquals(r.repo.attentionCalls[0], r.repo.attentionCalls[1])
        assertEquals(FormStep.DONE, r.vm.state.value.step)

        r.session.manager.expire()
        r.session.manager.login("medico@ejemplo.invalid", "clave")
        assertEquals(2, r.repo.attentionCalls.size)
    }
}

class ClinicalProfileFormViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val saved = ClinicalProfile("Penicilina", null, null, null, "Medico", ClinicTime.parse("2026-10-09T17:40:00Z"))

    @Test
    fun `sin ningun dato no pasa a la confirmacion`() = runTest {
        val vm = ClinicalProfileFormViewModel(FakeCareRepository(), SessionFixture(backgroundScope).manager, "cita-a")
        vm.onChange(ProfileField.ALLERGIES, "   ")

        vm.review()

        assertEquals(FormStep.FORM, vm.state.value.step)
        assertEquals(UserMessage.Local(R.string.perfil_clinico_vacio), vm.state.value.error)
    }

    @Test
    fun `doble toque guarda una sola version y envia nulos los vacios`() = runTest {
        val repo = FakeCareRepository().apply { gate = CompletableDeferred(); profileResults += ApiResult.Success(saved) }
        val vm = ClinicalProfileFormViewModel(repo, SessionFixture(backgroundScope).manager, "cita-a")
        vm.onChange(ProfileField.ALLERGIES, " Penicilina ")
        vm.review()

        vm.submit()
        vm.submit()
        repo.gate!!.complete(Unit)

        assertEquals(listOf(ClinicalProfileRequest("Penicilina", null, null, null)), repo.profileCalls)
        assertEquals(FormStep.DONE, vm.state.value.step)
    }

    @Test
    fun `CLINICAL_PROFILE_EMPTY de Spring vuelve al formulario`() = runTest {
        val repo = FakeCareRepository().apply { profileResults += ApiResult.BadRequest(400, "CLINICAL_PROFILE_EMPTY", null) }
        val vm = ClinicalProfileFormViewModel(repo, SessionFixture(backgroundScope).manager, "cita-a")
        vm.onChange(ProfileField.CONDITIONS, "x")
        vm.review()

        vm.submit()

        assertEquals(FormStep.FORM, vm.state.value.step)
        assertEquals(UserMessage.Local(R.string.perfil_clinico_vacio), vm.state.value.error)
    }

    @Test
    fun `red tras enviar consulta el expediente antes de reintentar`() = runTest {
        val repo = FakeCareRepository().apply {
            profileResults += ApiResult.NetworkError
            recordResult = ApiResult.Success(ClinicalRecord("e", saved, listOf(saved), emptyList()))
        }
        val vm = ClinicalProfileFormViewModel(repo, SessionFixture(backgroundScope).manager, "cita-a")
        vm.onChange(ProfileField.ALLERGIES, "Penicilina")
        vm.review()

        vm.submit()

        assertEquals(1, repo.profileCalls.size)
        assertEquals(FormStep.DONE, vm.state.value.step)
    }
}

class DoctorCareRepositoryTest {

    private val rig = ApiTestRig()
    private val repo = RemoteDoctorRepository(rig.retrofit.create(DoctorApi::class.java), rig.apiCaller)

    @After
    fun tearDown() = rig.close()

    @Test
    fun `atencion envia solo los campos del contrato y la receta aunque este vacia`() = runTest {
        val response = """{"id":"t","appointmentId":"a","reason":"Control","diagnosis":"Dx","recordedAt":"2026-10-09T17:30:00Z","prescription":[],"addenda":[]}"""
        rig.enqueue(201, response)
        rig.enqueue(201, response)

        repo.recordAttention("a", RecordAttentionRequest("Control", null, "Dx", null, emptyList()))
        repo.recordAttention(
            "a",
            RecordAttentionRequest("Control", "Hallazgo", "Dx", "Plan", listOf(com.clinicaserena.app.data.doctor.PrescriptionItemRequest("Med", "1", "c/8h", "3 d", null))),
        )

        val first = rig.server.takeRequest()
        assertEquals("/api/v1/medico/citas/a/atencion", first.url.encodedPath)
        assertEquals("""{"reason":"Control","diagnosis":"Dx","prescription":[]}""", first.body?.utf8())
        assertEquals(
            """{"reason":"Control","findings":"Hallazgo","diagnosis":"Dx","treatmentPlan":"Plan","prescription":[{"medicine":"Med","dose":"1","frequency":"c/8h","duration":"3 d"}]}""",
            rig.server.takeRequest().body?.utf8(),
        )
    }

    @Test
    fun `perfil clinico y expediente del medico`() = runTest {
        rig.enqueue(201, """{"id":"v","allergies":"Penicilina","recordedAt":"2026-10-09T17:40:00Z"}""")
        rig.enqueue(200, """{"patient":{"id":"p1","fullName":"Paciente Uno","status":"ACTIVO"},"recordId":"e","attentions":[]}""")

        repo.addClinicalProfile("a", ClinicalProfileRequest("Penicilina", null, null, null))
        val record = (repo.record("a") as ApiResult.Success).data

        val post = rig.server.takeRequest()
        assertEquals("/api/v1/medico/citas/a/expediente/perfil", post.url.encodedPath)
        assertEquals("""{"allergies":"Penicilina"}""", post.body?.utf8())
        assertEquals("/api/v1/medico/citas/a/expediente", rig.server.takeRequest().url.encodedPath)
        assertEquals("Paciente Uno", record.patientName)
    }

    @Test
    fun `409 de atencion conserva su code`() = runTest {
        rig.enqueue(409, ApiTestRig.error(409, "ARRIVAL_NOT_REGISTERED"))

        assertEquals(
            ApiResult.Conflict("ARRIVAL_NOT_REGISTERED", "Mensaje"),
            repo.recordAttention("b", RecordAttentionRequest("Control", null, "Dx", null, emptyList())),
        )
    }
}
