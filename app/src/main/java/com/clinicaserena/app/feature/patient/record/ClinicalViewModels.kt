package com.clinicaserena.app.feature.patient.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clinicaserena.app.core.network.ApiResult
import com.clinicaserena.app.core.security.SessionManager
import com.clinicaserena.app.core.ui.LoadState
import com.clinicaserena.app.core.ui.toLoadState
import com.clinicaserena.app.data.patient.PatientClinicalRepository
import com.clinicaserena.app.domain.model.Checkup
import com.clinicaserena.app.domain.model.ClinicalRecord
import com.clinicaserena.app.domain.model.Prescription
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private fun LoadState<*>.isUnauthenticated() = (this as? LoadState.Failed)?.failure is ApiResult.Unauthenticated

/** Recetas persistidas del paciente (`/pacientes/me/recetas`), más recientes primero. Solo en memoria. */
class PrescriptionsViewModel(
    private val repository: PatientClinicalRepository,
    sessionManager: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow<LoadState<List<Prescription>>>(LoadState.Loading)
    val state: StateFlow<LoadState<List<Prescription>>> = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            sessionManager.reauthenticated.collect { if (_state.value.isUnauthenticated()) load() }
        }
    }

    fun load() {
        _state.value = LoadState.Loading
        viewModelScope.launch { _state.value = repository.prescriptions().toLoadState() }
    }
}

enum class RecordTab { SUMMARY, ATTENTIONS, CHECKUPS }

data class MyRecordUiState(
    val record: LoadState<ClinicalRecord> = LoadState.Loading,
    val checkups: LoadState<List<Checkup>> = LoadState.Loading,
    val tab: RecordTab = RecordTab.SUMMARY,
)

/** Mi expediente (solo lectura) y seguimientos. Ambos se cargan a la vez y fallan por separado. */
class MyRecordViewModel(
    private val repository: PatientClinicalRepository,
    sessionManager: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(MyRecordUiState())
    val state: StateFlow<MyRecordUiState> = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            sessionManager.reauthenticated.collect {
                val current = _state.value
                if (current.record.isUnauthenticated() || current.checkups.isUnauthenticated()) load()
            }
        }
    }

    fun load() {
        _state.update { it.copy(record = LoadState.Loading, checkups = LoadState.Loading) }
        viewModelScope.launch { _state.update { it.copy(record = repository.record().toLoadState()) } }
        viewModelScope.launch { _state.update { it.copy(checkups = repository.checkups().toLoadState()) } }
    }

    fun selectTab(tab: RecordTab) = _state.update { it.copy(tab = tab) }
}
