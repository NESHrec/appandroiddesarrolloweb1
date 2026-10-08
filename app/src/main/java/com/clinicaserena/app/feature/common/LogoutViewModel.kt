package com.clinicaserena.app.feature.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clinicaserena.app.core.security.LogoutOutcome
import com.clinicaserena.app.core.security.SessionManager
import com.clinicaserena.app.core.ui.UserMessage
import com.clinicaserena.app.core.ui.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface LogoutUiState {
    data object Idle : LogoutUiState
    data object InProgress : LogoutUiState

    /** Spring no confirmó el cierre: se ofrece reintentar o cerrar solo en este dispositivo. */
    data class Failed(val message: UserMessage) : LogoutUiState
}

class LogoutViewModel(private val sessionManager: SessionManager) : ViewModel() {

    private val _state = MutableStateFlow<LogoutUiState>(LogoutUiState.Idle)
    val state: StateFlow<LogoutUiState> = _state.asStateFlow()

    fun logout() {
        if (_state.value == LogoutUiState.InProgress) return
        _state.value = LogoutUiState.InProgress
        viewModelScope.launch {
            _state.value = when (val outcome = sessionManager.logout()) {
                LogoutOutcome.Done -> LogoutUiState.Idle
                is LogoutOutcome.Failed -> LogoutUiState.Failed(outcome.failure.toUserMessage())
            }
        }
    }

    fun logoutLocalOnly() {
        viewModelScope.launch { sessionManager.logoutLocalOnly() }
    }
}
