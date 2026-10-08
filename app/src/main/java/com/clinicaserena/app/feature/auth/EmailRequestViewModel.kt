package com.clinicaserena.app.feature.auth

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clinicaserena.app.core.network.ApiResult
import com.clinicaserena.app.core.ui.UserMessage
import com.clinicaserena.app.core.ui.toUserMessage
import com.clinicaserena.app.data.auth.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Solicitudes por correo. En ambas el proceso se completa desde el enlace del correo, no en la app. */
enum class EmailRequestMode { PASSWORD_RECOVERY, RESEND_VERIFICATION }

data class EmailRequestUiState(
    val email: String = "",
    @StringRes val emailError: Int? = null,
    val submitting: Boolean = false,
    val message: UserMessage? = null,
    val sent: Boolean = false,
) {
    override fun toString(): String = "EmailRequestUiState(email=<oculto>, submitting=$submitting, sent=$sent)"
}

class EmailRequestViewModel(
    private val mode: EmailRequestMode,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(EmailRequestUiState())
    val state: StateFlow<EmailRequestUiState> = _state.asStateFlow()

    fun onEmailChange(value: String) = _state.update { it.copy(email = value, emailError = null, message = null) }

    fun submit() {
        val current = _state.value
        if (current.submitting) return
        val emailError = AuthValidation.emailError(current.email)
        if (emailError != null) {
            _state.update { it.copy(emailError = emailError) }
            return
        }
        _state.update { it.copy(submitting = true, message = null) }
        viewModelScope.launch {
            val email = current.email.trim()
            val result = when (mode) {
                EmailRequestMode.PASSWORD_RECOVERY -> authRepository.requestPasswordRecovery(email)
                EmailRequestMode.RESEND_VERIFICATION -> authRepository.resendVerification(email)
            }
            _state.update {
                when (result) {
                    is ApiResult.Success -> EmailRequestUiState(sent = true)
                    is ApiResult.Failure -> it.copy(submitting = false, message = result.toUserMessage())
                }
            }
        }
    }
}
