package com.clinicaserena.app.feature.doctor.odontogram

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clinicaserena.app.R
import com.clinicaserena.app.core.network.ApiResult
import com.clinicaserena.app.core.security.SessionManager
import com.clinicaserena.app.core.ui.LoadState
import com.clinicaserena.app.core.ui.UserMessage
import com.clinicaserena.app.core.ui.toUserMessage
import com.clinicaserena.app.data.doctor.DentalObservationRequest
import com.clinicaserena.app.data.doctor.DoctorRepository
import com.clinicaserena.app.domain.model.DentalObservation
import com.clinicaserena.app.domain.model.DoctorAppointment
import com.clinicaserena.app.domain.model.FdiTeeth
import com.clinicaserena.app.domain.model.Odontogram
import com.clinicaserena.app.domain.model.ToothHistory
import com.clinicaserena.app.domain.model.ToothSurface
import com.clinicaserena.app.domain.model.groupObservations
import com.clinicaserena.app.feature.doctor.care.FormBack
import com.clinicaserena.app.feature.doctor.care.FormStep
import com.clinicaserena.app.feature.doctor.care.formBack
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Historial del paciente agrupado y la cita desde la que se abrió (decide si se puede agregar). */
data class OdontogramContent(
    val odontogram: Odontogram,
    val history: List<ToothHistory>,
    val appointment: DoctorAppointment,
)

/** Historial de observaciones del paciente de una cita propia. Sin caché: solo vive en este ViewModel. */
class OdontogramViewModel(
    private val repository: DoctorRepository,
    sessionManager: SessionManager,
    private val appointmentId: String,
    private val patientId: String,
) : ViewModel() {

    private val _state = MutableStateFlow<LoadState<OdontogramContent>>(LoadState.Loading)
    val state: StateFlow<LoadState<OdontogramContent>> = _state.asStateFlow()

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
        viewModelScope.launch {
            val odontogram = async { repository.odontogram(patientId) }
            val detail = async { repository.appointment(appointmentId) }
            val o = odontogram.await()
            val d = detail.await()
            _state.value = when {
                o is ApiResult.Failure -> LoadState.Failed(o)
                d is ApiResult.Failure -> LoadState.Failed(d)
                else -> {
                    val page = (o as ApiResult.Success).data
                    LoadState.Loaded(OdontogramContent(page, groupObservations(page.observations), (d as ApiResult.Success).data.appointment))
                }
            }
        }
    }
}

data class ObservationFormUiState(
    val step: FormStep = FormStep.FORM,
    val tooth: Int? = null,
    val surface: ToothSurface? = null,
    val observation: String = "",
    val toothError: UserMessage? = null,
    val surfaceError: UserMessage? = null,
    val observationError: UserMessage? = null,
    val submitting: Boolean = false,
    val error: UserMessage? = null,
    val saved: DentalObservation? = null,
    val awaitingReauth: Boolean = false,
    val confirmDiscard: Boolean = false,
) {
    val hasChanges: Boolean get() = tooth != null || surface != null || observation.isNotBlank()

    override fun toString(): String = "ObservationFormUiState(step=$step, submitting=$submitting)"
}

/**
 * Nueva observación del odontograma (inmutable). Spring solo la acepta mientras la cita es documentable,
 * es decir, antes de registrar la atención. Ante un resultado incierto se consulta el historial antes de
 * permitir reenviar.
 */
class ObservationFormViewModel(
    private val repository: DoctorRepository,
    sessionManager: SessionManager,
    private val appointmentId: String,
    private val patientId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(ObservationFormUiState())
    val state: StateFlow<ObservationFormUiState> = _state.asStateFlow()
    private var uncertainAttempt = false

    init {
        viewModelScope.launch {
            sessionManager.reauthenticated.collect {
                if (_state.value.awaitingReauth) {
                    _state.update { it.copy(awaitingReauth = false) }
                    submit()
                }
            }
        }
    }

    /** Solo piezas FDI válidas; una pieza fuera del catálogo (19, 56, 86…) se rechaza sin enviarla. */
    fun selectTooth(tooth: Int) {
        if (!editable()) return
        if (!FdiTeeth.isValid(tooth)) {
            _state.update { it.copy(tooth = null, toothError = UserMessage.Local(R.string.odontograma_validacion_pieza)) }
            return
        }
        _state.update { it.copy(tooth = tooth, toothError = null, error = null) }
    }

    fun selectSurface(surface: ToothSurface) {
        if (!editable()) return
        _state.update { it.copy(surface = surface, surfaceError = null, error = null) }
    }

    fun onObservationChange(value: String) {
        if (!editable()) return
        _state.update { it.copy(observation = value, observationError = null, error = null) }
    }

    fun review() {
        val checked = validate(_state.value)
        val valid = checked.toothError == null && checked.surfaceError == null && checked.observationError == null
        _state.value = checked.copy(error = null, step = if (valid) FormStep.CONFIRM else FormStep.FORM)
    }

    fun backToForm() {
        if (_state.value.submitting) return
        _state.update { it.copy(step = FormStep.FORM, error = null) }
    }

    fun onBack(): FormBack {
        val current = _state.value
        val action = formBack(current.step, current.hasChanges)
        if (current.step == FormStep.CONFIRM) backToForm()
        if (current.step == FormStep.FORM && action == FormBack.STAY) _state.update { it.copy(confirmDiscard = true) }
        return action
    }

    fun keepEditing() = _state.update { it.copy(confirmDiscard = false) }

    fun discardDraft() {
        uncertainAttempt = false
        _state.value = ObservationFormUiState()
    }

    fun submit() {
        val current = _state.value
        if (current.submitting || current.step != FormStep.CONFIRM) return
        val request = current.toRequestOrNull() ?: return review()
        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            if (uncertainAttempt) {
                when (val check = findSaved(request)) {
                    is ApiResult.Success -> check.data?.let { return@launch done(it) }
                    is ApiResult.Failure -> {
                        _state.update { it.copy(submitting = false, error = UserMessage.Local(R.string.odontograma_incierto)) }
                        return@launch
                    }
                }
            }
            when (val result = repository.addObservation(appointmentId, request)) {
                is ApiResult.Success -> done(result.data)
                is ApiResult.Unauthenticated -> _state.update { it.copy(submitting = false, awaitingReauth = true) }
                is ApiResult.Conflict -> _state.update {
                    it.copy(submitting = false, error = UserMessage.Local(conflictMessage(result.code)))
                }
                is ApiResult.BadRequest -> _state.update {
                    // 400 de dominio: se vuelve al formulario con el error en su campo.
                    val local = UserMessage.Local(badRequestMessage(result.code))
                    it.copy(
                        submitting = false,
                        step = FormStep.FORM,
                        toothError = local.takeIf { result.code == "TOOTH_INVALID" },
                        surfaceError = local.takeIf { result.code == "SURFACE_INVALID" },
                        observationError = local.takeIf { result.code == "OBSERVATION_INVALID" },
                        error = UserMessage.Local(R.string.error_datos_invalidos),
                    )
                }
                is ApiResult.Validation -> _state.update {
                    val byField = result.fieldErrors.associate { e -> e.field to UserMessage.Server(e.message) }
                    it.copy(
                        submitting = false,
                        step = FormStep.FORM,
                        toothError = byField["toothNumber"],
                        surfaceError = byField["surface"],
                        observationError = byField["observation"],
                        error = UserMessage.Local(R.string.error_datos_invalidos),
                    )
                }
                is ApiResult.NotFound -> _state.update {
                    it.copy(submitting = false, error = UserMessage.Local(R.string.odontograma_404))
                }
                is ApiResult.NetworkError, is ApiResult.ServerError, is ApiResult.Unexpected -> {
                    uncertainAttempt = true
                    when (val check = findSaved(request)) {
                        is ApiResult.Success -> {
                            val saved = check.data
                            if (saved != null) done(saved) else _state.update {
                                it.copy(submitting = false, error = UserMessage.Local(R.string.odontograma_no_guardada))
                            }
                        }
                        is ApiResult.Failure ->
                            _state.update { it.copy(submitting = false, error = UserMessage.Local(R.string.odontograma_incierto)) }
                    }
                }
                is ApiResult.Failure -> _state.update { it.copy(submitting = false, error = result.toUserMessage()) }
            }
        }
    }

    /** ¿El historial ya tiene esta misma observación de esta cita? */
    private suspend fun findSaved(request: DentalObservationRequest): ApiResult<DentalObservation?> =
        when (val page = repository.odontogram(patientId)) {
            is ApiResult.Success -> ApiResult.Success(
                page.data.observations.firstOrNull {
                    it.appointmentId == appointmentId && it.toothNumber == request.toothNumber &&
                        it.surfaceRaw == request.surface && it.observation == request.observation
                },
            )
            is ApiResult.Failure -> page
        }

    private fun done(observation: DentalObservation) {
        uncertainAttempt = false
        _state.update { it.copy(step = FormStep.DONE, saved = observation, submitting = false, error = null) }
    }

    private fun editable() = _state.value.step == FormStep.FORM && !_state.value.submitting

    companion object {
        const val MAX = 500

        fun validate(state: ObservationFormUiState): ObservationFormUiState = state.copy(
            toothError = if (state.tooth == null || !FdiTeeth.isValid(state.tooth)) UserMessage.Local(R.string.odontograma_validacion_pieza) else null,
            surfaceError = if (state.surface == null) UserMessage.Local(R.string.odontograma_validacion_superficie) else null,
            observationError = if (state.observation.trim().length !in 1..MAX) UserMessage.Local(R.string.odontograma_validacion_observacion) else null,
        )

        /** Mismo orden que `OdontogramaService`: no documentable, no iniciada, sin llegada. */
        @StringRes
        fun conflictMessage(code: String): Int = when (code) {
            "APPOINTMENT_NOT_DOCUMENTABLE" -> R.string.odontograma_409_no_documentable
            "APPOINTMENT_NOT_STARTED" -> R.string.odontograma_409_no_iniciada
            "ARRIVAL_NOT_REGISTERED" -> R.string.odontograma_409_sin_llegada
            "PATIENT_RECORD_UNAVAILABLE" -> R.string.atencion_409_sin_paciente
            else -> R.string.odontograma_409_generico
        }

        @StringRes
        fun badRequestMessage(code: String?): Int = when (code) {
            "TOOTH_INVALID" -> R.string.odontograma_validacion_pieza
            "SURFACE_INVALID" -> R.string.odontograma_validacion_superficie
            "OBSERVATION_INVALID" -> R.string.odontograma_validacion_observacion
            else -> R.string.error_datos_invalidos
        }
    }
}

fun ObservationFormUiState.toRequestOrNull(): DentalObservationRequest? {
    val tooth = tooth ?: return null
    val surface = surface ?: return null
    return DentalObservationRequest(toothNumber = tooth, surface = surface.name, observation = observation.trim())
}
