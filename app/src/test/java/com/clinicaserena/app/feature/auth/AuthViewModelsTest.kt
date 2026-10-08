package com.clinicaserena.app.feature.auth

import com.clinicaserena.app.R
import com.clinicaserena.app.core.network.ApiResult
import com.clinicaserena.app.core.network.FieldError
import com.clinicaserena.app.core.security.LogoutReason
import com.clinicaserena.app.core.security.SessionState
import com.clinicaserena.app.core.ui.UserMessage
import com.clinicaserena.app.data.auth.UnifiedLogin
import com.clinicaserena.app.domain.model.AccountType
import com.clinicaserena.app.domain.model.Role
import com.clinicaserena.app.testing.FakeAuthRepository
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

class LoginViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private fun TestScope.setup(): Pair<SessionFixture, LoginViewModel> {
        val fixture = SessionFixture(backgroundScope)
        return fixture to LoginViewModel(fixture.manager)
    }

    @Test
    fun `valida correo y contrasena antes de llamar a Spring`() = runTest {
        val (f, vm) = setup()
        vm.onEmailChange("no-es-correo")

        vm.submit()

        assertEquals(R.string.validacion_correo_invalido, vm.state.value.emailError)
        assertEquals(R.string.validacion_contrasena_obligatoria, vm.state.value.passwordError)
        assertTrue(f.auth.logins.isEmpty())
    }

    @Test
    fun `credenciales incorrectas muestran un mensaje unico y borran la contrasena`() = runTest {
        val (f, vm) = setup()
        f.auth.loginResult = ApiResult.InvalidCredentials("Credenciales inválidas")
        vm.onEmailChange("paciente@ejemplo.invalid")
        vm.onPasswordChange("mala")

        vm.submit()

        assertEquals(UserMessage.Local(R.string.error_credenciales), vm.state.value.message)
        assertEquals("", vm.state.value.password)
        assertFalse(vm.state.value.submitting)
    }

    @Test
    fun `sin conexion muestra error de red`() = runTest {
        val (f, vm) = setup()
        f.auth.loginResult = ApiResult.NetworkError
        vm.onEmailChange("paciente@ejemplo.invalid")
        vm.onPasswordChange("clave")

        vm.submit()

        assertEquals(UserMessage.Local(R.string.error_sin_conexion), vm.state.value.message)
    }

    @Test
    fun `login exitoso activa la sesion sin mensaje`() = runTest {
        val (f, vm) = setup()
        vm.onEmailChange("paciente@ejemplo.invalid")
        vm.onPasswordChange("clave")

        vm.submit()

        assertTrue(f.manager.state.value is SessionState.Active)
        assertNull(vm.state.value.message)
        assertEquals("", vm.state.value.password)
    }

    @Test
    fun `rol no soportado deja el aviso en el estado de sesion`() = runTest {
        val (f, vm) = setup()
        f.auth.loginResult = ApiResult.Success(UnifiedLogin("t", AccountType.PERSONAL, Role.ADMIN, 1800))
        vm.onEmailChange("admin@ejemplo.invalid")
        vm.onPasswordChange("clave")

        vm.submit()

        assertEquals(SessionState.LoggedOut(LogoutReason.UNSUPPORTED_ROLE), f.manager.state.value)
        assertTrue(vm.state.value.unsupportedRole)
        assertNull(vm.state.value.message)

        // Un segundo intento con otra cuenta quita el aviso.
        f.auth.loginResult = ApiResult.InvalidCredentials(null)
        vm.onPasswordChange("otra")
        vm.submit()
        assertFalse(vm.state.value.unsupportedRole)
    }

    @Test
    fun `al volver a intentar se oculta el aviso del cierre anterior`() = runTest {
        val (f, vm) = setup()
        f.auth.loginResult = ApiResult.InvalidCredentials(null)
        assertFalse(vm.state.value.reasonDismissed)
        vm.onEmailChange("paciente@ejemplo.invalid")
        vm.onPasswordChange("mala")

        vm.submit()

        assertTrue(vm.state.value.reasonDismissed)
    }

    @Test
    fun `en re-login el correo queda fijo`() = runTest {
        val f = SessionFixture(backgroundScope)
        val vm = LoginViewModel(f.manager, lockedEmail = "paciente@ejemplo.invalid")

        vm.onEmailChange("otro@ejemplo.invalid")

        assertEquals("paciente@ejemplo.invalid", vm.state.value.email)
    }

    @Test
    fun `el estado no expone la contrasena en toString`() = runTest {
        val (_, vm) = setup()
        vm.onPasswordChange("secreta-123")

        assertFalse(vm.state.value.toString().contains("secreta-123"))
    }
}

class RegisterViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val auth = FakeAuthRepository()
    private val vm = RegisterViewModel(auth)

    private fun fill(password: String = "clave-segura", confirmation: String = password) {
        vm.onNameChange("  Ana Prueba ")
        vm.onEmailChange(" ana@ejemplo.invalid ")
        vm.onPasswordChange(password)
        vm.onConfirmationChange(confirmation)
    }

    @Test
    fun `exige contrasena de 8 a 72 caracteres`() {
        fill(password = "corta")
        vm.submit()
        assertEquals(UserMessage.Local(R.string.validacion_contrasena_longitud), vm.state.value.passwordError)

        fill(password = "x".repeat(73))
        vm.submit()
        assertEquals(UserMessage.Local(R.string.validacion_contrasena_longitud), vm.state.value.passwordError)
        assertTrue(auth.registrations.isEmpty())
    }

    @Test
    fun `las contrasenas deben coincidir`() {
        fill(password = "clave-segura", confirmation = "otra-clave")
        vm.submit()

        assertEquals(UserMessage.Local(R.string.validacion_contrasenas_distintas), vm.state.value.confirmationError)
        assertTrue(auth.registrations.isEmpty())
    }

    @Test
    fun `nombre obligatorio y de hasta 160`() {
        vm.onNameChange("x".repeat(161))
        vm.onEmailChange("ana@ejemplo.invalid")
        vm.onPasswordChange("clave-segura")
        vm.onConfirmationChange("clave-segura")
        vm.submit()

        assertEquals(UserMessage.Local(R.string.validacion_nombre_largo), vm.state.value.nameError)
    }

    @Test
    fun `202 muestra que la verificacion sigue por correo y limpia el formulario`() {
        fill()
        vm.submit()

        assertEquals(listOf(Triple("Ana Prueba", "ana@ejemplo.invalid", "clave-segura")), auth.registrations)
        assertTrue(vm.state.value.sent)
        assertEquals("", vm.state.value.password)
        assertEquals("", vm.state.value.email)
    }

    @Test
    fun `errores por campo de Spring se muestran en su campo`() {
        auth.messageResult = ApiResult.Validation("Datos inválidos", listOf(FieldError("email", "correo inválido")))
        fill()
        vm.submit()

        assertEquals(UserMessage.Server("correo inválido"), vm.state.value.emailError)
        assertFalse(vm.state.value.sent)
    }
}

class EmailRequestViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val auth = FakeAuthRepository()

    @Test
    fun `recuperacion solicita el correo y confirma con mensaje generico`() {
        val vm = EmailRequestViewModel(EmailRequestMode.PASSWORD_RECOVERY, auth)
        vm.onEmailChange(" ana@ejemplo.invalid ")

        vm.submit()

        assertEquals(listOf("ana@ejemplo.invalid"), auth.recoveries)
        assertTrue(auth.resends.isEmpty())
        assertTrue(vm.state.value.sent)
    }

    @Test
    fun `reenvio de verificacion usa su endpoint`() {
        val vm = EmailRequestViewModel(EmailRequestMode.RESEND_VERIFICATION, auth)
        vm.onEmailChange("ana@ejemplo.invalid")

        vm.submit()

        assertEquals(listOf("ana@ejemplo.invalid"), auth.resends)
    }

    @Test
    fun `correo invalido no llama a Spring`() {
        val vm = EmailRequestViewModel(EmailRequestMode.PASSWORD_RECOVERY, auth)
        vm.onEmailChange("ana")

        vm.submit()

        assertEquals(R.string.validacion_correo_invalido, vm.state.value.emailError)
        assertTrue(auth.recoveries.isEmpty())
    }

    @Test
    fun `error del servidor se muestra sin marcar enviado`() {
        auth.messageResult = ApiResult.ServerError(500)
        val vm = EmailRequestViewModel(EmailRequestMode.RESEND_VERIFICATION, auth)
        vm.onEmailChange("ana@ejemplo.invalid")

        vm.submit()

        assertFalse(vm.state.value.sent)
        assertEquals(UserMessage.Local(R.string.error_servidor), vm.state.value.message)
    }
}
