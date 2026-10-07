package com.clinicaserena.app.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clinicaserena.app.core.network.ApiResult
import com.clinicaserena.app.data.health.HealthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface HealthUiState {
    data object Loading : HealthUiState
    data class Up(val status: String, val service: String) : HealthUiState
    data class Down(val failure: ApiResult.Failure) : HealthUiState
}

/** Solo debug: consulta `/health` para comprobar que el emulador alcanza el backend local. */
class DiagnosticsViewModel(private val healthRepository: HealthRepository) : ViewModel() {

    private val _state = MutableStateFlow<HealthUiState>(HealthUiState.Loading)
    val state: StateFlow<HealthUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.value = HealthUiState.Loading
        viewModelScope.launch {
            _state.value = when (val result = healthRepository.check()) {
                is ApiResult.Success -> HealthUiState.Up(result.data.status, result.data.service)
                is ApiResult.Failure -> HealthUiState.Down(result)
            }
        }
    }
}
