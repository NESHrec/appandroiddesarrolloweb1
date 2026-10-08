package com.clinicaserena.app.core.security

import com.clinicaserena.app.core.network.ApiResult
import com.clinicaserena.app.data.auth.UnifiedLogin
import com.clinicaserena.app.domain.model.AccountType
import com.clinicaserena.app.domain.model.Identity
import com.clinicaserena.app.domain.model.PractitionerLinkStatus
import com.clinicaserena.app.domain.model.Role
import com.clinicaserena.app.domain.model.Session
import com.clinicaserena.app.testing.SessionFixture
import com.clinicaserena.app.testing.TEST_NOW
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionManagerTest {

    private fun TestScope.fixture() = SessionFixture(backgroundScope)

    private val patient = Session(
        token = "token-guardado",
        accountType = AccountType.PACIENTE,
        role = Role.PACIENTE,
        expiresAt = TEST_NOW.plusSeconds(600),
        subjectId = "paciente-1",
        email = "paciente@ejemplo.invalid",
    )
    private val doctorIdentity = Identity(
        subjectId = "cuenta-medico",
        email = "medico@ejemplo.invalid",
        role = Role.MEDICO,
        fullName = "Medico Prueba",
        practitionerLinkStatus = PractitionerLinkStatus.VINCULADA,
    )

    // --- Arranque con sesión guardada ---

    @Test
    fun `sin sesion guardada muestra el acceso`() = runTest {
        val f = fixture()
        f.manager.restore()

        assertEquals(SessionState.LoggedOut(LogoutReason.NONE), f.manager.state.value)
        assertTrue(f.auth.identityChecks.isEmpty())
    }

    @Test
    fun `token vencido localmente se borra sin consultar al servidor`() = runTest {
        val f = fixture()
        f.store.save(patient.copy(expiresAt = TEST_NOW.minusSeconds(1)))

        f.manager.restore()

        assertEquals(SessionState.LoggedOut(LogoutReason.EXPIRED), f.manager.state.value)
        assertTrue(f.auth.identityChecks.isEmpty())
        assertNull(f.store.load())
    }

    @Test
    fun `sesion vigente se valida con me segun accountType`() = runTest {
        val f = fixture()
        f.store.save(patient)

        f.manager.restore()

        assertEquals(listOf(AccountType.PACIENTE), f.auth.identityChecks)
        assertEquals(SessionState.Active(patient), f.manager.state.value)
        assertEquals("token-guardado", f.manager.currentToken())
    }

    @Test
    fun `401 al validar borra la sesion y pide login`() = runTest {
        val f = fixture()
        f.store.save(patient)
        f.auth.identityResult = ApiResult.Unauthenticated("UNAUTHENTICATED", null)

        f.manager.restore()

        assertEquals(SessionState.LoggedOut(LogoutReason.EXPIRED), f.manager.state.value)
        assertNull(f.manager.currentToken())
        assertNull(f.store.load())
    }

    @Test
    fun `error de red al validar es recuperable y conserva la sesion guardada`() = runTest {
        val f = fixture()
        f.store.save(patient)
        f.auth.identityResult = ApiResult.NetworkError

        f.manager.restore()

        assertEquals(SessionState.RestoreFailed(ApiResult.NetworkError), f.manager.state.value)
        assertNull(f.manager.currentToken())
        assertNotNull(f.store.load())
    }

    @Test
    fun `cuenta guardada que ahora es ADMIN se revoca y se rechaza`() = runTest {
        val f = fixture()
        f.store.save(patient.copy(accountType = AccountType.PERSONAL, role = Role.MEDICO))
        f.auth.identityResult = ApiResult.Success(doctorIdentity.copy(subjectId = "paciente-1", role = Role.ADMIN))

        f.manager.restore()

        assertEquals(listOf(AccountType.PERSONAL), f.auth.logouts)
        assertEquals(SessionState.LoggedOut(LogoutReason.UNSUPPORTED_ROLE), f.manager.state.value)
        assertNull(f.store.load())
    }

    @Test
    fun `sesion ilegible se descarta sin fallar`() = runTest {
        val f = fixture()
        f.store.save(patient)
        f.cipher.failDecrypt = true

        f.manager.restore()

        assertEquals(SessionState.LoggedOut(LogoutReason.NONE), f.manager.state.value)
        assertEquals(1, f.cipher.deletedKeys)
    }

    // --- Login ---

    @Test
    fun `login de paciente hace login-unified, valida con me y guarda la sesion`() = runTest {
        val f = fixture()

        val outcome = f.manager.login("  paciente@ejemplo.invalid ", "clave")

        assertEquals(LoginOutcome.Success, outcome)
        assertEquals(listOf("paciente@ejemplo.invalid"), f.auth.logins)
        assertEquals(listOf(AccountType.PACIENTE), f.auth.identityChecks)
        val expected = Session(
            "token-paciente", AccountType.PACIENTE, Role.PACIENTE, TEST_NOW.plusSeconds(1770),
            "paciente-1", "paciente@ejemplo.invalid",
        )
        assertEquals(SessionState.Active(expected), f.manager.state.value)
        assertEquals(expected, f.store.load())
    }

    @Test
    fun `login de medico usa staff me`() = runTest {
        val f = fixture()
        f.auth.loginResult = ApiResult.Success(UnifiedLogin("token-medico", AccountType.PERSONAL, Role.MEDICO, 1800))
        f.auth.identityResult = ApiResult.Success(doctorIdentity)

        assertEquals(LoginOutcome.Success, f.manager.login("medico@ejemplo.invalid", "clave"))

        assertEquals(listOf(AccountType.PERSONAL), f.auth.identityChecks)
        assertEquals(Role.MEDICO, (f.manager.state.value as SessionState.Active).session.role)
    }

    @Test
    fun `credenciales incorrectas no cambian el estado`() = runTest {
        val f = fixture()
        f.manager.restore()
        f.auth.loginResult = ApiResult.InvalidCredentials("Credenciales inválidas")

        val outcome = f.manager.login("paciente@ejemplo.invalid", "mala")

        assertEquals(LoginOutcome.Failed(ApiResult.InvalidCredentials("Credenciales inválidas")), outcome)
        assertEquals(SessionState.LoggedOut(LogoutReason.NONE), f.manager.state.value)
        assertNull(f.manager.currentToken())
    }

    @Test
    fun `login de RECEPCION se revoca en Spring y no se guarda`() = runTest {
        val f = fixture()
        f.auth.loginResult = ApiResult.Success(UnifiedLogin("token-recepcion", AccountType.PERSONAL, Role.RECEPCION, 1800))

        assertEquals(LoginOutcome.UnsupportedRole, f.manager.login("recepcion@ejemplo.invalid", "clave"))

        assertEquals(listOf(AccountType.PERSONAL), f.auth.logouts)
        assertTrue(f.auth.identityChecks.isEmpty())
        assertEquals(SessionState.LoggedOut(LogoutReason.UNSUPPORTED_ROLE), f.manager.state.value)
        assertNull(f.manager.currentToken())
        assertNull(f.store.load())
    }

    @Test
    fun `si me falla tras el login no queda sesion`() = runTest {
        val f = fixture()
        f.manager.restore()
        f.auth.identityResult = ApiResult.ServerError(503)

        assertEquals(LoginOutcome.Failed(ApiResult.ServerError(503)), f.manager.login("a@ejemplo.invalid", "x"))

        assertNull(f.manager.currentToken())
        assertEquals(SessionState.LoggedOut(LogoutReason.NONE), f.manager.state.value)
    }

    // --- Sesión vencida en uso y re-login ---

    @Test
    fun `401 en uso pasa a ReauthRequired, conserva la persona y borra el token`() = runTest {
        val f = fixture()
        f.manager.login("paciente@ejemplo.invalid", "clave")
        val active = (f.manager.state.value as SessionState.Active).session

        f.manager.expire()
        runCurrent()

        assertEquals(SessionState.ReauthRequired(active), f.manager.state.value)
        assertNull(f.manager.currentToken())
        assertNull(f.store.load())
    }

    @Test
    fun `re-login de la misma persona vuelve a Active y avisa para reintentar`() = runTest(UnconfinedTestDispatcher()) {
        val f = fixture()
        val retries = mutableListOf<Unit>()
        backgroundScope.launch { f.manager.reauthenticated.collect { retries += it } }
        f.manager.login("paciente@ejemplo.invalid", "clave")
        f.manager.expire()

        assertEquals(LoginOutcome.Success, f.manager.login("paciente@ejemplo.invalid", "clave"))

        assertEquals(1, retries.size)
        assertEquals("paciente-1", (f.manager.state.value as SessionState.Active).session.subjectId)
    }

    @Test
    fun `re-login de otra persona no emite reintento`() = runTest(UnconfinedTestDispatcher()) {
        val f = fixture()
        val retries = mutableListOf<Unit>()
        backgroundScope.launch { f.manager.reauthenticated.collect { retries += it } }
        f.manager.login("paciente@ejemplo.invalid", "clave")
        f.manager.expire()
        f.auth.identityResult = ApiResult.Success(Identity("paciente-2", "otro@ejemplo.invalid", Role.PACIENTE))

        f.manager.login("otro@ejemplo.invalid", "clave")

        assertEquals("paciente-2", (f.manager.state.value as SessionState.Active).session.subjectId)
        assertTrue(retries.isEmpty())
    }

    @Test
    fun `expire sin sesion activa no hace nada`() = runTest {
        val f = fixture()
        f.manager.restore()

        f.manager.expire()

        assertEquals(SessionState.LoggedOut(LogoutReason.NONE), f.manager.state.value)
    }

    @Test
    fun `abandonar la sesion vencida lleva al acceso`() = runTest {
        val f = fixture()
        f.manager.login("paciente@ejemplo.invalid", "clave")
        f.manager.expire()

        f.manager.abandonExpiredSession()

        assertEquals(SessionState.LoggedOut(LogoutReason.NONE), f.manager.state.value)
    }

    // --- Logout ---

    @Test
    fun `logout 204 limpia todo`() = runTest {
        val f = fixture()
        f.manager.login("paciente@ejemplo.invalid", "clave")

        assertEquals(LogoutOutcome.Done, f.manager.logout())

        assertEquals(listOf(AccountType.PACIENTE), f.auth.logouts)
        assertEquals(SessionState.LoggedOut(LogoutReason.NONE), f.manager.state.value)
        assertNull(f.manager.currentToken())
        assertNull(f.store.load())
    }

    @Test
    fun `logout con 401 tambien cierra porque la sesion ya no existia`() = runTest {
        val f = fixture()
        f.manager.login("paciente@ejemplo.invalid", "clave")
        f.auth.logoutResult = ApiResult.Unauthenticated("UNAUTHENTICATED", null)

        assertEquals(LogoutOutcome.Done, f.manager.logout())
        assertEquals(SessionState.LoggedOut(LogoutReason.NONE), f.manager.state.value)
    }

    @Test
    fun `logout sin red no simula exito y conserva la sesion`() = runTest {
        val f = fixture()
        f.manager.login("paciente@ejemplo.invalid", "clave")
        val before = f.manager.state.value
        f.auth.logoutResult = ApiResult.NetworkError

        assertEquals(LogoutOutcome.Failed(ApiResult.NetworkError), f.manager.logout())

        assertEquals(before, f.manager.state.value)
        assertEquals("token-paciente", f.manager.currentToken())
        assertNotNull(f.store.load())
    }

    @Test
    fun `cerrar solo en este dispositivo limpia y lo indica`() = runTest {
        val f = fixture()
        f.manager.login("paciente@ejemplo.invalid", "clave")

        f.manager.logoutLocalOnly()

        assertEquals(SessionState.LoggedOut(LogoutReason.LOCAL_ONLY), f.manager.state.value)
        assertNull(f.manager.currentToken())
        assertNull(f.store.load())
    }

}
