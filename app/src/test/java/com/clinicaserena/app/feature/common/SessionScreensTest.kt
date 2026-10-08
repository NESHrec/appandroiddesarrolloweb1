package com.clinicaserena.app.feature.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.clinicaserena.app.R
import com.clinicaserena.app.core.network.ApiResult
import com.clinicaserena.app.core.security.LogoutReason
import com.clinicaserena.app.core.security.SessionState
import com.clinicaserena.app.core.ui.LoadState
import com.clinicaserena.app.core.ui.ScopedStoresViewModel
import com.clinicaserena.app.core.ui.UserMessage
import com.clinicaserena.app.data.patient.PatientProfile
import com.clinicaserena.app.data.patient.PatientProfileRepository
import com.clinicaserena.app.feature.patient.profile.PatientProfileViewModel
import com.clinicaserena.app.testing.MainDispatcherRule
import com.clinicaserena.app.testing.SessionFixture
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.reflect.KClass

class LogoutViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @Test
    fun `logout sin red muestra el fallo y permite cerrar solo aqui`() = runTest {
        val f = SessionFixture(backgroundScope)
        f.manager.login("paciente@ejemplo.invalid", "clave")
        f.auth.logoutResult = ApiResult.NetworkError
        val vm = LogoutViewModel(f.manager)

        vm.logout()

        assertEquals(LogoutUiState.Failed(UserMessage.Local(R.string.error_sin_conexion)), vm.state.value)
        assertTrue(f.manager.state.value is SessionState.Active)

        vm.logoutLocalOnly()

        assertEquals(SessionState.LoggedOut(LogoutReason.LOCAL_ONLY), f.manager.state.value)
    }

    @Test
    fun `logout confirmado lleva al acceso`() = runTest {
        val f = SessionFixture(backgroundScope)
        f.manager.login("paciente@ejemplo.invalid", "clave")
        val vm = LogoutViewModel(f.manager)

        vm.logout()

        assertEquals(LogoutUiState.Idle, vm.state.value)
        assertEquals(SessionState.LoggedOut(LogoutReason.NONE), f.manager.state.value)
    }
}

class PatientProfileViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private class FakeProfileRepository : PatientProfileRepository {
        val results = ArrayDeque<ApiResult<PatientProfile>>()
        var calls = 0
        override suspend fun getOwn(): ApiResult<PatientProfile> {
            calls++
            return results.removeFirst()
        }
    }

    private val profile = PatientProfile("Paciente Prueba", "paciente@ejemplo.invalid", "ACTIVA", null)

    @Test
    fun `tras re-login de la misma persona reintenta la carga pendiente`() = runTest {
        val f = SessionFixture(backgroundScope)
        f.manager.login("paciente@ejemplo.invalid", "clave")
        val repo = FakeProfileRepository().apply {
            results += ApiResult.Unauthenticated("UNAUTHENTICATED", null)
            results += ApiResult.Success(profile)
        }
        val vm = PatientProfileViewModel(repo, f.manager)
        assertTrue((vm.state.value as LoadState.Failed).failure is ApiResult.Unauthenticated)

        f.manager.expire()
        f.manager.login("paciente@ejemplo.invalid", "clave")

        assertEquals(2, repo.calls)
        assertEquals(LoadState.Loaded(profile), vm.state.value)
    }
}

class ScopedStoresViewModelTest {

    private class Probe : ViewModel() {
        var cleared = false
        override fun onCleared() {
            cleared = true
        }
    }

    private val factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T = Probe() as T
    }

    @Test
    fun `cambiar de clave limpia los ViewModels de la sesion anterior`() {
        val holder = ScopedStoresViewModel()
        val first = ViewModelProvider.create(holder.storeFor("sesion:a"), factory)[Probe::class]

        assertSame(first, ViewModelProvider.create(holder.storeFor("sesion:a"), factory)[Probe::class])
        assertFalse(first.cleared)

        val other = ViewModelProvider.create(holder.storeFor("acceso"), factory)[Probe::class]

        assertTrue(first.cleared)
        assertNotSame(first, other)
    }
}
