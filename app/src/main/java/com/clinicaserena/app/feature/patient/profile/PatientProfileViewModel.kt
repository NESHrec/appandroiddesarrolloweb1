package com.clinicaserena.app.feature.patient.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clinicaserena.app.core.network.ApiResult
import com.clinicaserena.app.core.security.SessionManager
import com.clinicaserena.app.core.ui.LoadState
import com.clinicaserena.app.core.ui.toLoadState
import com.clinicaserena.app.data.patient.PatientProfile
import com.clinicaserena.app.data.patient.PatientProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Perfil del paciente (`/pacientes/me/perfil`). Tras un re-login de la misma persona, reintenta. */
class PatientProfileViewModel(
    private val repository: PatientProfileRepository,
    sessionManager: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow<LoadState<PatientProfile>>(LoadState.Loading)
    val state: StateFlow<LoadState<PatientProfile>> = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            sessionManager.reauthenticated.collect {
                if ((_state.value as? LoadState.Failed)?.failure is ApiResult.Unauthenticated) load()
            }
        }
    }

    fun load() {
        _state.value = LoadState.Loading
        viewModelScope.launch { _state.value = repository.getOwn().toLoadState() }
    }
}
