package com.clinicaserena.app.feature.patient.appointments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clinicaserena.app.core.network.ApiResult
import com.clinicaserena.app.core.security.SessionManager
import com.clinicaserena.app.core.ui.LoadState
import com.clinicaserena.app.data.catalog.CatalogRepository
import com.clinicaserena.app.data.patient.AppointmentsRepository
import com.clinicaserena.app.domain.model.AppointmentStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock

data class AppointmentsUiState(
    val load: LoadState<List<AppointmentItem>> = LoadState.Loading,
    val tab: AppointmentTab = AppointmentTab.UPCOMING,
    val statusFilter: AppointmentStatus? = null,
)

/**
 * Citas del paciente, compartidas por Inicio, la lista y el detalle (no hay endpoint de detalle).
 * Vive en el almacén de la sesión: se libera al cerrar sesión y no se guarda en disco.
 */
class PatientAppointmentsViewModel(
    private val appointmentsRepository: AppointmentsRepository,
    private val catalogRepository: CatalogRepository,
    sessionManager: SessionManager,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(AppointmentsUiState())
    val state: StateFlow<AppointmentsUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        load()
        viewModelScope.launch {
            sessionManager.reauthenticated.collect {
                if ((_state.value.load as? LoadState.Failed)?.failure is ApiResult.Unauthenticated) load()
            }
        }
    }

    fun now() = clock.instant()

    fun load() {
        loadJob?.cancel()
        _state.update { it.copy(load = LoadState.Loading) }
        loadJob = viewModelScope.launch {
            val practitioners = async { catalogRepository.allPractitioners() }
            val specialties = async { catalogRepository.specialties() }
            val result = appointmentsRepository.listOwn()
            val load = when (result) {
                is ApiResult.Failure -> LoadState.Failed(result)
                is ApiResult.Success -> {
                    // Si el catálogo falla, las citas se muestran igual, sin nombres.
                    val practitionerNames = (practitioners.await() as? ApiResult.Success)?.data
                        ?.associate { it.id to it.fullName }.orEmpty()
                    val specialtyNames = (specialties.await() as? ApiResult.Success)?.data
                        ?.associate { it.id to it.name }.orEmpty()
                    LoadState.Loaded(
                        result.data.map {
                            AppointmentItem(it, practitionerNames[it.practitionerId], specialtyNames[it.specialtyId])
                        },
                    )
                }
            }
            _state.update { it.copy(load = load) }
        }
    }

    fun selectTab(tab: AppointmentTab) = _state.update { it.copy(tab = tab) }

    fun selectStatus(status: AppointmentStatus?) = _state.update { it.copy(statusFilter = status) }

    fun find(id: String): AppointmentItem? =
        (_state.value.load as? LoadState.Loaded)?.data?.firstOrNull { it.appointment.id == id }
}
