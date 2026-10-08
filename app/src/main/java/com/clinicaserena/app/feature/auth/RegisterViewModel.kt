package com.clinicaserena.app.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clinicaserena.app.R
import com.clinicaserena.app.core.network.ApiResult
import com.clinicaserena.app.core.ui.UserMessage
import com.clinicaserena.app.core.ui.toUserMessage
import com.clinicaserena.app.data.auth.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RegisterUiState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val confirmation: String = "",
    val passwordVisible: Boolean = false,
    val nameError: UserMessage? = null,
    val emailError: UserMessage? = null,
    val passwordError: UserMessage? = null,
    val confirmationError: UserMessage? = null,
    val submitting: Boolean = false,
    val message: UserMessage? = null,
    /** 202 de Spring: se pidió el correo de verificación. */
    val sent: Boolean = false,
) {
    override fun toString(): String = "RegisterUiState(<datos ocultos>, submitting=$submitting, sent=$sent)"
}

/** Registro de paciente. La verificación del correo se completa desde el enlace del correo. */
class RegisterViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(RegisterUiState())
    val state: StateFlow<RegisterUiState> = _state.asStateFlow()

    fun onNameChange(value: String) = _state.update { it.copy(name = value, nameError = null, message = null) }
    fun onEmailChange(value: String) = _state.update { it.copy(email = value, emailError = null, message = null) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, passwordError = null, message = null) }
    fun onConfirmationChange(value: String) =
        _state.update { it.copy(confirmation = value, confirmationError = null, message = null) }

    fun togglePasswordVisible() = _state.update { it.copy(passwordVisible = !it.passwordVisible) }

    fun submit() {
        val current = _state.value
        if (current.submitting) return
        val nameError = AuthValidation.nameError(current.name)
        val emailError = AuthValidation.emailError(current.email)
        val passwordError = AuthValidation.newPasswordError(current.password)
        val confirmationError = if (passwordError == null && current.confirmation != current.password) {
            R.string.validacion_contrasenas_distintas
        } else {
            null
        }
        if (listOf(nameError, emailError, passwordError, confirmationError).any { it != null }) {
            _state.update {
                it.copy(
                    nameError = nameError?.let(UserMessage::Local),
                    emailError = emailError?.let(UserMessage::Local),
                    passwordError = passwordError?.let(UserMessage::Local),
                    confirmationError = confirmationError?.let(UserMessage::Local),
                )
            }
            return
        }

        _state.update { it.copy(submitting = true, message = null) }
        viewModelScope.launch {
            val result = authRepository.register(current.name.trim(), current.email.trim(), current.password)
            _state.update {
                when (result) {
                    is ApiResult.Success -> RegisterUiState(sent = true)
                    is ApiResult.Validation -> it.copy(
                        submitting = false,
                        nameError = result.fieldMessage("nombre"),
                        emailError = result.fieldMessage("email"),
                        passwordError = result.fieldMessage("password"),
                        message = result.toUserMessage(),
                    )
                    is ApiResult.Failure -> it.copy(submitting = false, message = result.toUserMessage())
                }
            }
        }
    }

    private fun ApiResult.Validation.fieldMessage(field: String): UserMessage? =
        fieldErrors.firstOrNull { it.field == field }?.let { UserMessage.Server(it.message) }
}
