package com.clinicaserena.app.core.push

import com.clinicaserena.app.core.network.ApiResult
import com.clinicaserena.app.core.security.LogoutReason
import com.clinicaserena.app.core.security.SessionState
import com.clinicaserena.app.data.push.PushDeviceRepository
import com.clinicaserena.app.domain.model.AccountType
import com.clinicaserena.app.domain.model.Role
import com.clinicaserena.app.domain.model.Session
import com.clinicaserena.app.testing.TEST_NOW
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FakePushTokenSource : PushTokenSource {
    var current = "fcm-token-1"
    var failToken = false
    var failDelete = false
    var autoInitEnabled = false
    var tokenRequests = 0
    var deletes = 0

    override suspend fun token(): String {
        tokenRequests++
        if (failToken) throw IllegalStateException("SERVICE_NOT_AVAILABLE")
        return current
    }

    override suspend fun delete() {
        yield() // como la llamada de red real: punto de suspensión donde llega una cancelación
        if (failDelete) throw IllegalStateException("sin red")
        deletes++
    }

    override fun setAutoInit(enabled: Boolean) {
        autoInitEnabled = enabled
    }
}

class FakePushDeviceRepository : PushDeviceRepository {
    val registerResults = ArrayDeque<ApiResult<Unit>>()
    val registered = mutableListOf<String>()
    val removed = mutableListOf<String>()

    override suspend fun register(token: String): ApiResult<Unit> {
        registered += token
        return registerResults.removeFirstOrNull() ?: ApiResult.Success(Unit)
    }

    override suspend fun remove(token: String): ApiResult<Unit> {
        removed += token
        return ApiResult.Success(Unit)
    }
}

class InMemoryPushTokenFlag : PushTokenFlag {
    override var issued: Boolean = false
}

@OptIn(ExperimentalCoroutinesApi::class)
class PushRegistrationManagerTest {

    private class Fixture(scope: CoroutineScope) {
        val state = MutableStateFlow<SessionState>(SessionState.Checking)
        val repository = FakePushDeviceRepository()
        val tokens = FakePushTokenSource()
        var permissionGranted = true
        val flag = InMemoryPushTokenFlag()
        val manager = PushRegistrationManager(state, repository, tokens, { permissionGranted }, flag, scope).also { it.start() }
    }

    private fun TestScope.fixture() = Fixture(backgroundScope).also { runCurrent() }

    private fun session(role: Role = Role.PACIENTE, id: String = "paciente-1") = Session(
        token = "bearer-$id",
        accountType = if (role == Role.PACIENTE) AccountType.PACIENTE else AccountType.PERSONAL,
        role = role,
        expiresAt = TEST_NOW.plusSeconds(600),
        subjectId = id,
        email = "$id@ejemplo.invalid",
    )

    @Test
    fun `paciente con permiso registra una vez y muestra exito solo tras el 204`() = runTest {
        val f = fixture()
        f.repository.registerResults += ApiResult.NetworkError

        f.state.value = SessionState.Active(session())
        runCurrent()
        assertTrue(f.manager.status.value is PushStatus.Failed)

        f.manager.retry()
        runCurrent()
        assertEquals(PushStatus.Registered, f.manager.status.value)
        assertEquals(listOf("fcm-token-1", "fcm-token-1"), f.repository.registered)
        assertTrue(f.tokens.autoInitEnabled)
        assertTrue(f.flag.issued)

        f.manager.refresh()
        runCurrent()
        assertEquals("sin repetir el PUT en cada regreso a la app", 2, f.repository.registered.size)
    }

    @Test
    fun `medico nunca pide token ni registra`() = runTest {
        val f = fixture()

        f.state.value = SessionState.Active(session(Role.MEDICO, "medico-1"))
        runCurrent()
        f.manager.refresh()
        f.manager.onNewToken()
        runCurrent()

        assertEquals(0, f.tokens.tokenRequests)
        assertTrue(f.repository.registered.isEmpty())
        assertEquals(PushStatus.Inactive, f.manager.status.value)
    }

    @Test
    fun `medico en un telefono que uso un paciente borra el token`() = runTest {
        val f = fixture()
        f.flag.issued = true

        f.state.value = SessionState.Active(session(Role.MEDICO, "medico-1"))
        runCurrent()

        assertEquals(1, f.tokens.deletes)
        assertFalse(f.flag.issued)
        assertFalse(f.tokens.autoInitEnabled)
    }

    @Test
    fun `sin permiso no se genera token y se pide el permiso`() = runTest {
        val f = fixture()
        f.permissionGranted = false

        f.state.value = SessionState.Active(session())
        runCurrent()

        assertEquals(PushStatus.PermissionRequired, f.manager.status.value)
        assertEquals(0, f.tokens.tokenRequests)

        f.permissionGranted = true
        f.manager.refresh()
        runCurrent()
        assertEquals(PushStatus.Registered, f.manager.status.value)
    }

    @Test
    fun `si se revoca el permiso se retira el token una sola vez`() = runTest {
        val f = fixture()
        f.state.value = SessionState.Active(session())
        runCurrent()

        f.permissionGranted = false
        f.manager.refresh()
        runCurrent()
        f.manager.refresh()
        runCurrent()

        assertEquals(PushStatus.PermissionRequired, f.manager.status.value)
        assertEquals(listOf("fcm-token-1"), f.repository.removed)
    }

    @Test
    fun `servidor sin envio habilitado no es exito ni error generico`() = runTest {
        val f = fixture()
        f.repository.registerResults += ApiResult.Conflict("PUSH_NOT_ENABLED", "No disponible")
        f.repository.registerResults += ApiResult.BadRequest(400, "PUSH_TOKEN_NOT_SIMULATED", "Solo simulados")

        f.state.value = SessionState.Active(session())
        runCurrent()
        assertEquals(PushStatus.ServerDisabled, f.manager.status.value)

        f.manager.retry()
        runCurrent()
        assertEquals(PushStatus.ServerDisabled, f.manager.status.value)
    }

    @Test
    fun `sin token de FCM se informa y se puede reintentar`() = runTest {
        val f = fixture()
        f.tokens.failToken = true

        f.state.value = SessionState.Active(session())
        runCurrent()
        assertEquals(PushStatus.TokenUnavailable, f.manager.status.value)
        assertTrue(f.repository.registered.isEmpty())

        f.tokens.failToken = false
        f.manager.retry()
        runCurrent()
        assertEquals(PushStatus.Registered, f.manager.status.value)
    }

    @Test
    fun `401 espera el re-login y la sesion nueva vuelve a registrar`() = runTest {
        val f = fixture()
        f.repository.registerResults += ApiResult.Unauthenticated("UNAUTHENTICATED", null)
        val first = session()

        f.state.value = SessionState.Active(first)
        runCurrent()
        assertEquals(PushStatus.AwaitingReauth, f.manager.status.value)

        f.state.value = SessionState.ReauthRequired(first)
        runCurrent()
        f.state.value = SessionState.Active(first.copy(token = "bearer-nuevo"))
        runCurrent()

        assertEquals(PushStatus.Registered, f.manager.status.value)
        assertEquals(2, f.repository.registered.size)
    }

    @Test
    fun `token renovado se registra de nuevo`() = runTest {
        val f = fixture()
        f.state.value = SessionState.Active(session())
        runCurrent()

        f.tokens.current = "fcm-token-2"
        f.manager.onNewToken()
        runCurrent()

        assertEquals(listOf("fcm-token-1", "fcm-token-2"), f.repository.registered)
    }

    @Test
    fun `aviso de FCM con el mismo token no repite el PUT`() = runTest {
        val f = fixture()
        f.state.value = SessionState.Active(session())
        runCurrent()

        f.manager.onNewToken()
        runCurrent()

        assertEquals(listOf("fcm-token-1"), f.repository.registered)
        assertEquals(PushStatus.Registered, f.manager.status.value)
    }

    @Test
    fun `logout retira el token y luego lo borra en FCM`() = runTest {
        val f = fixture()
        val active = session()
        f.state.value = SessionState.Active(active)
        runCurrent()

        f.manager.beforeLogout(active)
        f.state.value = SessionState.LoggedOut(LogoutReason.NONE)
        f.manager.signedOut()
        runCurrent()

        assertEquals(listOf("fcm-token-1"), f.repository.removed)
        assertEquals(1, f.tokens.deletes)
        assertFalse(f.flag.issued)
        assertFalse(f.tokens.autoInitEnabled)
        assertFalse(f.manager.remindersExpected())
        assertEquals(PushStatus.Inactive, f.manager.status.value)
    }

    @Test
    fun `logout sin red conserva la marca para borrar el token despues`() = runTest {
        val f = fixture()
        f.state.value = SessionState.Active(session())
        runCurrent()
        f.tokens.failDelete = true

        f.manager.signedOut()
        runCurrent()
        assertTrue(f.flag.issued)

        f.tokens.failDelete = false
        f.manager.signedOut()
        runCurrent()
        assertFalse(f.flag.issued)
    }

    @Test
    fun `el borrado del token sobrevive a la cancelacion de quien cierra sesion`() = runTest {
        val f = fixture()
        f.state.value = SessionState.Active(session())
        runCurrent()

        // Igual que el viewModelScope del cierre de sesión, que se cancela al volver al acceso.
        val caller = launch(start = CoroutineStart.UNDISPATCHED) { f.manager.signedOut() }
        caller.cancel()
        runCurrent()

        assertEquals(1, f.tokens.deletes)
        assertFalse(f.flag.issued)
    }

    @Test
    fun `el vencimiento de la sesion no borra el token`() = runTest {
        val f = fixture()
        f.state.value = SessionState.Active(session())
        runCurrent()

        f.state.value = SessionState.LoggedOut(LogoutReason.EXPIRED)
        runCurrent()

        assertEquals(0, f.tokens.deletes)
        assertTrue(f.flag.issued)
        assertTrue("los recordatorios siguen llegando aunque la sesión venza", f.manager.remindersExpected())
    }

    @Test
    fun `logout de un medico no toca push`() = runTest {
        val f = fixture()

        f.manager.beforeLogout(session(Role.MEDICO, "medico-1"))

        assertEquals(0, f.tokens.tokenRequests)
        assertTrue(f.repository.removed.isEmpty())
    }
}
