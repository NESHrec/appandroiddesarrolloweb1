package com.clinicaserena.app.core.security

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.clinicaserena.app.core.network.ApiResult
import com.clinicaserena.app.data.auth.AuthRepository
import com.clinicaserena.app.data.auth.UnifiedLogin
import com.clinicaserena.app.domain.model.AccountType
import com.clinicaserena.app.domain.model.Role
import com.clinicaserena.app.domain.model.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SessionManagerTest {

    @get:Rule
    val folder = TemporaryFolder()

    private class FakeAuthRepository : AuthRepository {
        var roleResult: ApiResult<Role> = ApiResult.Success(Role.PACIENTE)
        val roleChecks = mutableListOf<AccountType>()
        val logouts = mutableListOf<AccountType>()

        override suspend fun loginUnified(email: String, password: String): ApiResult<UnifiedLogin> =
            error("no se usa")

        override suspend fun currentRole(accountType: AccountType): ApiResult<Role> {
            roleChecks += accountType
            return roleResult
        }

        override suspend fun logout(accountType: AccountType): ApiResult<Unit> {
            logouts += accountType
            return ApiResult.Success(Unit)
        }
    }

    private val now = Instant.parse("2026-10-07T06:00:00Z")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cipher = FakeTokenCipher()
    private val store = SessionStore(
        PreferenceDataStoreFactory.create(scope = scope) { folder.root.resolve("session.preferences_pb") },
        cipher,
    )
    private val auth = FakeAuthRepository()
    private val manager = SessionManager(store, { auth }, Clock.fixed(now, ZoneOffset.UTC), scope)

    private fun session(accountType: AccountType, role: Role, expiresAt: Instant = now.plusSeconds(600)) =
        Session("token-guardado", accountType, role, expiresAt)

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `sin sesion guardada muestra el acceso`() = runBlocking {
        manager.restore()

        assertEquals(SessionState.LoggedOut(LogoutReason.NONE), manager.state.value)
        assertTrue(auth.roleChecks.isEmpty())
    }

    @Test
    fun `token vencido localmente se borra sin consultar al servidor`() = runBlocking {
        store.save(session(AccountType.PACIENTE, Role.PACIENTE, expiresAt = now.minusSeconds(1)))

        manager.restore()

        assertEquals(SessionState.LoggedOut(LogoutReason.EXPIRED), manager.state.value)
        assertTrue(auth.roleChecks.isEmpty())
        assertNull(store.load())
    }

    @Test
    fun `sesion de paciente vigente se valida con auth me`() = runBlocking {
        store.save(session(AccountType.PACIENTE, Role.PACIENTE))

        manager.restore()

        assertEquals(listOf(AccountType.PACIENTE), auth.roleChecks)
        assertEquals(SessionState.Active(session(AccountType.PACIENTE, Role.PACIENTE)), manager.state.value)
        assertEquals("token-guardado", manager.currentToken())
    }

    @Test
    fun `sesion de medico vigente se valida con staff auth me`() = runBlocking {
        store.save(session(AccountType.PERSONAL, Role.MEDICO))
        auth.roleResult = ApiResult.Success(Role.MEDICO)

        manager.restore()

        assertEquals(listOf(AccountType.PERSONAL), auth.roleChecks)
        assertEquals(SessionState.Active(session(AccountType.PERSONAL, Role.MEDICO)), manager.state.value)
    }

    @Test
    fun `401 al validar borra la sesion y pide login`() = runBlocking {
        store.save(session(AccountType.PACIENTE, Role.PACIENTE))
        auth.roleResult = ApiResult.Unauthenticated("UNAUTHENTICATED", null)

        manager.restore()

        assertEquals(SessionState.LoggedOut(LogoutReason.EXPIRED), manager.state.value)
        assertNull(manager.currentToken())
        assertNull(store.load())
    }

    @Test
    fun `error de red al validar es recuperable y conserva la sesion guardada`() = runBlocking {
        store.save(session(AccountType.PACIENTE, Role.PACIENTE))
        auth.roleResult = ApiResult.NetworkError

        manager.restore()

        assertEquals(SessionState.RestoreFailed(ApiResult.NetworkError), manager.state.value)
        assertNull(manager.currentToken())
        assertNotNull(store.load())
    }

    @Test
    fun `cuenta ADMIN guardada se revoca y se rechaza`() = runBlocking {
        store.save(session(AccountType.PERSONAL, Role.MEDICO))
        auth.roleResult = ApiResult.Success(Role.ADMIN)

        manager.restore()

        assertEquals(listOf(AccountType.PERSONAL), auth.logouts)
        assertEquals(SessionState.LoggedOut(LogoutReason.UNSUPPORTED_ROLE), manager.state.value)
        assertNull(store.load())
    }

    @Test
    fun `sesion ilegible se descarta sin fallar`() = runBlocking {
        store.save(session(AccountType.PACIENTE, Role.PACIENTE))
        cipher.failDecrypt = true

        manager.restore()

        assertEquals(SessionState.LoggedOut(LogoutReason.NONE), manager.state.value)
        assertEquals(1, cipher.deletedKeys)
        assertTrue(auth.roleChecks.isEmpty())
    }

    @Test
    fun `login de RECEPCION se revoca y no se guarda`() = runBlocking {
        manager.startSession(UnifiedLogin("token-recepcion", AccountType.PERSONAL, Role.RECEPCION, 1800))

        assertEquals(listOf(AccountType.PERSONAL), auth.logouts)
        assertEquals(SessionState.LoggedOut(LogoutReason.UNSUPPORTED_ROLE), manager.state.value)
        assertNull(manager.currentToken())
        assertNull(store.load())
    }

    @Test
    fun `login de paciente guarda la sesion con margen de vencimiento`() = runBlocking {
        manager.startSession(UnifiedLogin("token-nuevo", AccountType.PACIENTE, Role.PACIENTE, 1800))

        val expected = Session("token-nuevo", AccountType.PACIENTE, Role.PACIENTE, now.plusSeconds(1770))
        assertEquals(SessionState.Active(expected), manager.state.value)
        assertEquals(expected, store.load())
        assertEquals("token-nuevo", manager.currentToken())
    }

    @Test
    fun `expire limpia memoria y almacen`() = runBlocking {
        manager.startSession(UnifiedLogin("token-nuevo", AccountType.PACIENTE, Role.PACIENTE, 1800))

        manager.expire()

        assertEquals(SessionState.LoggedOut(LogoutReason.EXPIRED), manager.state.value)
        assertNull(manager.currentToken())
        withTimeout(5_000) { while (store.load() != null) delay(10) }
    }
}
