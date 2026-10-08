package com.clinicaserena.app.testing

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.clinicaserena.app.core.network.ApiResult
import com.clinicaserena.app.core.security.FakeTokenCipher
import com.clinicaserena.app.core.security.SessionManager
import com.clinicaserena.app.core.security.SessionStore
import com.clinicaserena.app.data.auth.AuthRepository
import com.clinicaserena.app.data.auth.UnifiedLogin
import com.clinicaserena.app.domain.model.AccountType
import com.clinicaserena.app.domain.model.Identity
import com.clinicaserena.app.domain.model.Role
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** DataStore en memoria: hace síncronas las pruebas de sesión. */
class InMemoryPreferencesDataStore : DataStore<Preferences> {
    private val flow = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = flow
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(flow.value).also { flow.value = it }
}

/** viewModelScope usa Dispatchers.Main: en JVM se reemplaza por un dispatcher de prueba. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}

val TEST_NOW: Instant = Instant.parse("2026-10-07T06:00:00Z")
val TEST_CLOCK: Clock = Clock.fixed(TEST_NOW, ZoneOffset.UTC)

/** AuthRepository configurable que registra las llamadas. */
class FakeAuthRepository : AuthRepository {
    var loginResult: ApiResult<UnifiedLogin> =
        ApiResult.Success(UnifiedLogin("token-paciente", AccountType.PACIENTE, Role.PACIENTE, 1800))
    var identityResult: ApiResult<Identity> =
        ApiResult.Success(Identity("paciente-1", "paciente@ejemplo.invalid", Role.PACIENTE))
    var logoutResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var messageResult: ApiResult<String> = ApiResult.Success("Si la solicitud es válida, recibirás un correo.")

    val logins = mutableListOf<String>()
    val identityChecks = mutableListOf<AccountType>()
    val logouts = mutableListOf<AccountType>()
    val registrations = mutableListOf<Triple<String, String, String>>()
    val resends = mutableListOf<String>()
    val recoveries = mutableListOf<String>()

    override suspend fun loginUnified(email: String, password: String): ApiResult<UnifiedLogin> {
        logins += email
        return loginResult
    }

    override suspend fun currentIdentity(accountType: AccountType): ApiResult<Identity> {
        identityChecks += accountType
        return identityResult
    }

    override suspend fun logout(accountType: AccountType): ApiResult<Unit> {
        logouts += accountType
        return logoutResult
    }

    override suspend fun register(fullName: String, email: String, password: String): ApiResult<String> {
        registrations += Triple(fullName, email, password)
        return messageResult
    }

    override suspend fun resendVerification(email: String): ApiResult<String> {
        resends += email
        return messageResult
    }

    override suspend fun requestPasswordRecovery(email: String): ApiResult<String> {
        recoveries += email
        return messageResult
    }
}

class SessionFixture(scope: CoroutineScope) {
    val cipher = FakeTokenCipher()
    val store = SessionStore(InMemoryPreferencesDataStore(), cipher)
    val auth = FakeAuthRepository()
    val manager = SessionManager(store, { auth }, TEST_CLOCK, scope)
}
