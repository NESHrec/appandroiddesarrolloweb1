package com.clinicaserena.app.feature.doctor.agenda

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clinicaserena.app.core.network.ApiResult
import com.clinicaserena.app.core.security.SessionManager
import com.clinicaserena.app.core.ui.LoadState
import com.clinicaserena.app.core.ui.toLoadState
import com.clinicaserena.app.data.doctor.DoctorRepository
import com.clinicaserena.app.domain.model.AppointmentStatus
import com.clinicaserena.app.domain.model.DoctorAppointment
import com.clinicaserena.app.domain.model.DoctorAppointmentDetail
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock

private fun LoadState<*>.isUnauthenticated() = (this as? LoadState.Failed)?.failure is ApiResult.Unauthenticated

data class DoctorHomeUiState(
    val period: HomePeriod = HomePeriod.WEEK,
    val load: LoadState<List<DoctorAppointment>> = LoadState.Loading,
)

/** Inicio del médico: conteo de pacientes distintos del período, citas listas y próximas. */
class DoctorHomeViewModel(
    private val repository: DoctorRepository,
    sessionManager: SessionManager,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(DoctorHomeUiState())
    val state: StateFlow<DoctorHomeUiState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        load()
        viewModelScope.launch {
            sessionManager.reauthenticated.collect { if (_state.value.load.isUnauthenticated()) load() }
        }
    }

    fun now() = clock.instant()

    fun selectPeriod(period: HomePeriod) {
        if (period == _state.value.period) return
        _state.update { it.copy(period = period) }
        load()
    }

    fun load() {
        job?.cancel()
        _state.update { it.copy(load = LoadState.Loading) }
        val range = AgendaPeriods.home(_state.value.period, clock.instant())
        job = viewModelScope.launch {
            val result = repository.agenda(range).toLoadState()
            _state.update { it.copy(load = result) }
        }
    }
}

data class DoctorAgendaUiState(
    val range: AgendaRange = AgendaRange.NEXT_7,
    val status: AppointmentStatus? = null,
    val load: LoadState<List<DoctorAppointment>> = LoadState.Loading,
)

/** Agenda propia con filtros de rango y estado (el estado se filtra en Spring con `status`). */
class DoctorAgendaViewModel(
    private val repository: DoctorRepository,
    sessionManager: SessionManager,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(DoctorAgendaUiState())
    val state: StateFlow<DoctorAgendaUiState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        load()
        viewModelScope.launch {
            sessionManager.reauthenticated.collect { if (_state.value.load.isUnauthenticated()) load() }
        }
    }

    fun selectRange(range: AgendaRange) {
        if (range == _state.value.range) return
        _state.update { it.copy(range = range) }
        load()
    }

    fun selectStatus(status: AppointmentStatus?) {
        if (status == _state.value.status) return
        _state.update { it.copy(status = status) }
        load()
    }

    fun load() {
        job?.cancel()
        _state.update { it.copy(load = LoadState.Loading) }
        val current = _state.value
        val range = AgendaPeriods.agenda(current.range, clock.instant())
        job = viewModelScope.launch {
            val result = repository.agenda(range, current.status).toLoadState()
            _state.update { it.copy(load = result) }
        }
    }
}

/** Detalle de una cita propia (`/medico/citas/{id}`), con su atención si existe. */
class DoctorAppointmentViewModel(
    private val repository: DoctorRepository,
    sessionManager: SessionManager,
    private val appointmentId: String,
) : ViewModel() {

    private val _state = MutableStateFlow<LoadState<DoctorAppointmentDetail>>(LoadState.Loading)
    val state: StateFlow<LoadState<DoctorAppointmentDetail>> = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            sessionManager.reauthenticated.collect { if (_state.value.isUnauthenticated()) load() }
        }
    }

    fun load() {
        _state.value = LoadState.Loading
        viewModelScope.launch { _state.value = repository.appointment(appointmentId).toLoadState() }
    }
}
