package com.clinicaserena.app.feature.auth

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clinicaserena.app.core.security.LoginOutcome
import com.clinicaserena.app.core.security.SessionManager
import com.clinicaserena.app.core.ui.UserMessage
import com.clinicaserena.app.core.ui.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val passwordVisible: Boolean = false,
    @StringRes val emailError: Int? = null,
    @StringRes val passwordError: Int? = null,
    val submitting: Boolean = false,
    val message: UserMessage? = null,
    /** Tras un nuevo intento se oculta el aviso del cierre anterior (rol no soportado, sesión vencida…). */
    val reasonDismissed: Boolean = false,
    /** El último intento fue de una cuenta ADMIN o RECEPCION (su sesión ya se cerró en Spring). */
    val unsupportedRole: Boolean = false,
) {
    override fun toString(): String =
        "LoginUiState(email=<oculto>, password=<oculto>, submitting=$submitting, message=$message)"
}

/**
 * Login unificado (sin selector de rol). También sirve para el re-login: [lockedEmail] fija el
 * correo de la sesión vencida. La contraseña se borra del estado tras cada intento.
 */
class LoginViewModel(
    private val sessionManager: SessionManager,
    private val lockedEmail: String? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState(email = lockedEmail.orEmpty()))
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onEmailChange(value: String) {
        if (lockedEmail != null) return
        _state.update { it.copy(email = value, emailError = null, message = null) }
    }

    fun onPasswordChange(value: String) {
        _state.update { it.copy(password = value, passwordError = null, message = null) }
    }

    fun togglePasswordVisible() {
        _state.update { it.copy(passwordVisible = !it.passwordVisible) }
    }

    fun submit() {
        val current = _state.value
        if (current.submitting) return
        val emailError = AuthValidation.emailError(current.email)
        val passwordError = AuthValidation.loginPasswordError(current.password)
        if (emailError != null || passwordError != null) {
            _state.update { it.copy(emailError = emailError, passwordError = passwordError) }
            return
        }

        _state.update { it.copy(submitting = true, message = null, reasonDismissed = true, unsupportedRole = false) }
        viewModelScope.launch {
            val outcome = sessionManager.login(current.email, current.password)
            _state.update {
                it.copy(
                    submitting = false,
                    password = "",
                    passwordVisible = false,
                    // Un 401 del login (credenciales, cuenta sin verificar o bloqueo por intentos) llega
                    // igual desde Spring; `error_credenciales` cubre los tres casos.
                    message = (outcome as? LoginOutcome.Failed)?.failure?.toUserMessage(),
                    unsupportedRole = outcome == LoginOutcome.UnsupportedRole,
                )
            }
        }
    }
}
