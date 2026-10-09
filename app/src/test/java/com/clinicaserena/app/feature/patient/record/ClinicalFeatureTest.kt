package com.clinicaserena.app.feature.patient.record

import com.clinicaserena.app.R
import com.clinicaserena.app.core.network.ApiResult
import com.clinicaserena.app.core.network.ApiTestRig
import com.clinicaserena.app.core.network.FieldError
import com.clinicaserena.app.core.time.ClinicTime
import com.clinicaserena.app.core.ui.LoadState
import com.clinicaserena.app.core.ui.UserMessage
import com.clinicaserena.app.data.patient.PatientClinicalApi
import com.clinicaserena.app.data.patient.PatientClinicalRepository
import com.clinicaserena.app.data.patient.PatientProfile
import com.clinicaserena.app.data.patient.PatientProfileApi
import com.clinicaserena.app.data.patient.PatientProfileRepository
import com.clinicaserena.app.data.patient.RemotePatientClinicalRepository
import com.clinicaserena.app.data.patient.RemotePatientProfileRepository
import com.clinicaserena.app.domain.model.Checkup
import com.clinicaserena.app.domain.model.ClinicalRecord
import com.clinicaserena.app.domain.model.Prescription
import com.clinicaserena.app.feature.patient.profile.PatientProfileViewModel
import com.clinicaserena.app.testing.MainDispatcherRule
import com.clinicaserena.app.testing.SessionFixture
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PatientClinicalRepositoryTest {

    private val rig = ApiTestRig()
    private val repo = RemotePatientClinicalRepository(rig.retrofit.create(PatientClinicalApi::class.java), rig.apiCaller)
    private val profiles = RemotePatientProfileRepository(rig.retrofit.create(PatientProfileApi::class.java), rig.apiCaller)

    @After
    fun tearDown() = rig.close()

    @Test
    fun `recetas usan el Bearer, se ordenan por fecha y sus items por order`() = runTest {
        rig.enqueue(
            200,
            """[{"id":"r1","appointmentId":"a1","appointmentScheduledAt":"2026-10-01T15:00:00Z","issuedAt":"2026-10-01T15:20:00Z","practitionerId":"m","practitionerName":"Dra. Elena Morales",
                 "items":[{"id":"i2","order":2,"medicine":"B","dose":"d","frequency":"f","duration":"u","instructions":null},
                          {"id":"i1","order":1,"medicine":"A","dose":"d","frequency":"f","duration":"u","instructions":"x"}]},
                {"id":"r2","appointmentId":"a2","appointmentScheduledAt":null,"issuedAt":"2026-10-09T16:30:00Z","practitionerId":"m","practitionerName":null,"items":[]}]""",
        )

        val result = (repo.prescriptions() as ApiResult.Success).data

        val request = rig.server.takeRequest()
        assertEquals("/api/v1/pacientes/me/recetas", request.url.encodedPath)
        assertEquals("Bearer ${ApiTestRig.TOKEN}", request.headers["Authorization"])
        assertEquals(listOf("r2", "r1"), result.map { it.id })
        assertEquals(listOf("A", "B"), result[1].items.map { it.medicine })
        assertNull(result[0].practitionerName)
        assertNull(result[0].appointmentScheduledAt)
    }

    @Test
    fun `expediente sin atenciones llega con recordId nulo`() = runTest {
        rig.enqueue(
            200,
            """{"patient":{"id":"p","fullName":"Ana","status":"ACTIVO","registeredAt":"2026-10-07T06:07:25Z"},"recordId":null,"recordCreatedAt":null,"clinicalProfile":null,"clinicalProfileHistory":[],"attentions":[]}""",
        )

        val record = (repo.record() as ApiResult.Success).data

        assertEquals("/api/v1/pacientes/me/expediente", rig.server.takeRequest().url.encodedPath)
        assertEquals(ClinicalRecord(null, null, emptyList(), emptyList()), record)
    }

    @Test
    fun `expediente con atencion, receta y adendas en orden`() = runTest {
        rig.enqueue(
            200,
            """{"patient":{"id":"p","fullName":"Ana","status":"ACTIVO","registeredAt":"2026-10-07T06:07:25Z"},"recordId":"e1","recordCreatedAt":"2026-10-09T16:30:00Z",
                "clinicalProfile":{"id":"v2","allergies":"Penicilina","relevantConditions":null,"currentMedications":null,"dentalHistory":null,"authorAccountId":"c","authorName":"Medico","recordedAt":"2026-10-09T16:31:00Z"},
                "clinicalProfileHistory":[{"id":"v1","allergies":null,"recordedAt":"2026-10-01T10:00:00Z"},{"id":"v2","allergies":"Penicilina","recordedAt":"2026-10-09T16:31:00Z"}],
                "attentions":[{"id":"t1","appointmentId":"a","appointmentScheduledAt":"2026-10-09T16:28:00Z","practitionerId":"m","practitionerName":"Dra. Elena Morales","authorAccountId":"c","authorName":"Medico",
                  "reason":"Control","findings":null,"diagnosis":"Gingivitis","treatmentPlan":null,"recordedAt":"2026-10-09T16:30:00Z",
                  "prescription":[{"order":1,"medicine":"A","dose":"d","frequency":"f","duration":"u","instructions":null}],
                  "addenda":[{"id":"d2","text":"segunda","reason":"r","recordedAt":"2026-10-09T18:00:00Z"},{"id":"d1","text":"primera","reason":"r","recordedAt":"2026-10-09T17:00:00Z"}]}]}""",
        )

        val record = (repo.record() as ApiResult.Success).data

        assertEquals("Penicilina", record.profile?.allergies)
        assertEquals(listOf("2026-10-09T16:31Z", "2026-10-01T10:00Z"), record.profileHistory.map { it.recordedAt.toString() })
        assertEquals("Gingivitis", record.attentions.single().diagnosis)
        assertEquals(listOf("primera", "segunda"), record.attentions.single().addenda.map { it.text })
    }

    @Test
    fun `chequeos y fecha ilegible`() = runTest {
        rig.enqueue(200, """[{"id":"c1","appointmentId":"a","recordedAt":"2026-10-09T16:30:00Z","reason":"Control","diagnosis":"Gingivitis"}]""")
        rig.enqueue(200, """[{"id":"r","appointmentId":"a","issuedAt":"ayer","items":[]}]""")

        assertEquals("c1", (repo.checkups() as ApiResult.Success).data.single().id)
        assertEquals("/api/v1/pacientes/me/chequeos", rig.server.takeRequest().url.encodedPath)
        assertTrue(repo.prescriptions() is ApiResult.Unexpected)
    }

    @Test
    fun `cambiar nombre envia PATCH solo con fullName`() = runTest {
        rig.enqueue(200, """{"patientId":"p","fullName":"Ana María","email":"ana@ejemplo.invalid","accountStatus":"ACTIVA","registeredAt":"2026-10-07T06:07:25Z"}""")

        val result = profiles.updateName("Ana María") as ApiResult.Success

        val request = rig.server.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("/api/v1/pacientes/me/perfil", request.url.encodedPath)
        assertEquals("""{"fullName":"Ana María"}""", request.body?.utf8())
        assertEquals("Ana María", result.data.fullName)
    }

    @Test
    fun `el log HTTP no contiene datos clinicos`() = runTest {
        rig.enqueue(200, """[{"id":"r","appointmentId":"a","issuedAt":"2026-10-09T16:30:00Z","items":[{"order":1,"medicine":"Clorhexidina-secreta","dose":"d","frequency":"f","duration":"u"}]}]""")

        repo.prescriptions()

        assertFalse(rig.logLines.joinToString("\n").contains("Clorhexidina-secreta"))
    }
}

class ClinicalViewModelsTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private class FakeClinicalRepository : PatientClinicalRepository {
        var prescriptions: ApiResult<List<Prescription>> = ApiResult.Success(emptyList())
        var checkups: ApiResult<List<Checkup>> = ApiResult.Success(emptyList())
        var record: ApiResult<ClinicalRecord> = ApiResult.Success(ClinicalRecord(null, null, emptyList(), emptyList()))
        var prescriptionCalls = 0
        override suspend fun prescriptions(): ApiResult<List<Prescription>> { prescriptionCalls++; return prescriptions }
        override suspend fun checkups() = checkups
        override suspend fun record() = record
    }

    @Test
    fun `recetas vacias es un estado vacio, no un error`() = runTest {
        val vm = PrescriptionsViewModel(FakeClinicalRepository(), SessionFixture(backgroundScope).manager)

        assertEquals(LoadState.Loaded(emptyList<Prescription>()), vm.state.value)
    }

    @Test
    fun `recetas se recargan tras re-login si fallaron por 401`() = runTest {
        val session = SessionFixture(backgroundScope)
        session.manager.login("paciente@ejemplo.invalid", "clave")
        val repo = FakeClinicalRepository().apply { prescriptions = ApiResult.Unauthenticated("UNAUTHENTICATED", null) }
        val vm = PrescriptionsViewModel(repo, session.manager)

        repo.prescriptions = ApiResult.Success(emptyList())
        session.manager.expire()
        session.manager.login("paciente@ejemplo.invalid", "clave")

        assertEquals(2, repo.prescriptionCalls)
        assertTrue(vm.state.value is LoadState.Loaded)
    }

    @Test
    fun `expediente y seguimientos fallan por separado`() = runTest {
        val repo = FakeClinicalRepository().apply { checkups = ApiResult.ServerError(500) }
        val vm = MyRecordViewModel(repo, SessionFixture(backgroundScope).manager)

        assertTrue(vm.state.value.record is LoadState.Loaded)
        assertEquals(LoadState.Failed(ApiResult.ServerError(500)), vm.state.value.checkups)
    }

    @Test
    fun `403 se conserva como permiso denegado`() = runTest {
        val repo = FakeClinicalRepository().apply { record = ApiResult.Forbidden("FORBIDDEN", "No tienes permiso") }
        val vm = MyRecordViewModel(repo, SessionFixture(backgroundScope).manager)

        assertEquals(LoadState.Failed(ApiResult.Forbidden("FORBIDDEN", "No tienes permiso")), vm.state.value.record)
    }
}

class PatientProfileEditTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val profile = PatientProfile("Paciente Prueba", "paciente@ejemplo.invalid", "ACTIVA", ClinicTime.parse("2026-10-07T06:07:25Z"))

    private class FakeProfileRepository(var current: PatientProfile) : PatientProfileRepository {
        val updates = ArrayDeque<ApiResult<PatientProfile>>()
        val sent = mutableListOf<String>()
        override suspend fun getOwn(): ApiResult<PatientProfile> = ApiResult.Success(current)
        override suspend fun updateName(fullName: String): ApiResult<PatientProfile> {
            sent += fullName
            return updates.removeFirst()
        }
    }

    @Test
    fun `valida de 2 a 160 caracteres sin llamar a Spring`() = runTest {
        val repo = FakeProfileRepository(profile)
        val vm = PatientProfileViewModel(repo, SessionFixture(backgroundScope).manager)
        vm.startEditing()

        vm.onNameChange(" A ")
        vm.save()
        assertEquals(UserMessage.Local(R.string.perfil_nombre_corto), vm.state.value.nameError)

        vm.onNameChange("x".repeat(161))
        vm.save()
        assertEquals(UserMessage.Local(R.string.validacion_nombre_largo), vm.state.value.nameError)
        assertTrue(repo.sent.isEmpty())
    }

    @Test
    fun `exito solo tras el 200 y con el nombre que devuelve Spring`() = runTest {
        val repo = FakeProfileRepository(profile)
        val vm = PatientProfileViewModel(repo, SessionFixture(backgroundScope).manager)
        vm.startEditing()
        vm.onNameChange("  Ana María Prueba ")
        repo.updates += ApiResult.Success(profile.copy(fullName = "Ana María Prueba"))

        vm.save()

        assertEquals(listOf("Ana María Prueba"), repo.sent)
        assertTrue(vm.state.value.saved)
        assertFalse(vm.state.value.editing)
        assertEquals("Ana María Prueba", (vm.state.value.load as LoadState.Loaded).data.fullName)
    }

    @Test
    fun `error de Spring no marca exito ni pierde el borrador`() = runTest {
        val repo = FakeProfileRepository(profile)
        val vm = PatientProfileViewModel(repo, SessionFixture(backgroundScope).manager)
        vm.startEditing()
        vm.onNameChange("Ana")
        repo.updates += ApiResult.Validation("Datos inválidos", listOf(FieldError("fullName", "tamaño inválido")))

        vm.save()

        assertFalse(vm.state.value.saved)
        assertTrue(vm.state.value.editing)
        assertEquals("Ana", vm.state.value.nameDraft)
        assertEquals(UserMessage.Server("tamaño inválido"), vm.state.value.nameError)
        assertEquals("Paciente Prueba", (vm.state.value.load as LoadState.Loaded).data.fullName)
    }

    @Test
    fun `401 al guardar conserva el borrador y se guarda una vez tras re-login`() = runTest {
        val session = SessionFixture(backgroundScope)
        session.manager.login("paciente@ejemplo.invalid", "clave")
        val repo = FakeProfileRepository(profile)
        val vm = PatientProfileViewModel(repo, session.manager)
        vm.startEditing()
        vm.onNameChange("Nombre Nuevo")
        repo.updates += ApiResult.Unauthenticated("UNAUTHENTICATED", null)

        vm.save()
        assertTrue(vm.state.value.awaitingReauth)
        assertEquals("Nombre Nuevo", vm.state.value.nameDraft)

        repo.updates += ApiResult.Success(profile.copy(fullName = "Nombre Nuevo"))
        session.manager.expire()
        session.manager.login("paciente@ejemplo.invalid", "clave")

        assertEquals(listOf("Nombre Nuevo", "Nombre Nuevo"), repo.sent)
        assertTrue(vm.state.value.saved)

        session.manager.expire()
        session.manager.login("paciente@ejemplo.invalid", "clave")
        assertEquals(2, repo.sent.size)
    }
}
