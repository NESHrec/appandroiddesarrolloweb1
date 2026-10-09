package com.clinicaserena.app.feature.doctor.care

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clinicaserena.app.R
import com.clinicaserena.app.core.network.ApiResult
import com.clinicaserena.app.core.security.SessionManager
import com.clinicaserena.app.core.ui.LoadState
import com.clinicaserena.app.core.ui.UserMessage
import com.clinicaserena.app.core.ui.toLoadState
import com.clinicaserena.app.core.ui.toUserMessage
import com.clinicaserena.app.data.doctor.ClinicalProfileRequest
import com.clinicaserena.app.data.doctor.DoctorRepository
import com.clinicaserena.app.domain.model.ClinicalProfile
import com.clinicaserena.app.domain.model.ClinicalRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ProfileField { ALLERGIES, CONDITIONS, MEDICATIONS, DENTAL_HISTORY }

data class ClinicalProfileFormUiState(
    val step: FormStep = FormStep.FORM,
    val allergies: String = "",
    val relevantConditions: String = "",
    val currentMedications: String = "",
    val dentalHistory: String = "",
    val fieldErrors: Map<ProfileField, UserMessage> = emptyMap(),
    val submitting: Boolean = false,
    val error: UserMessage? = null,
    val saved: ClinicalProfile? = null,
    val awaitingReauth: Boolean = false,
) {
    override fun toString(): String = "ClinicalProfileFormUiState(step=$step, submitting=$submitting)"
}

/**
 * Nueva versión del perfil clínico (inmutable; las anteriores quedan en el historial). Al menos un
 * campo con texto y ≤2000 caracteres cada uno, igual que `UpdateClinicalProfileRequest`.
 */
class ClinicalProfileFormViewModel(
    private val repository: DoctorRepository,
    sessionManager: SessionManager,
    private val appointmentId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(ClinicalProfileFormUiState())
    val state: StateFlow<ClinicalProfileFormUiState> = _state.asStateFlow()
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

    fun onChange(field: ProfileField, value: String) {
        if (_state.value.step != FormStep.FORM || _state.value.submitting) return
        _state.update {
            when (field) {
                ProfileField.ALLERGIES -> it.copy(allergies = value)
                ProfileField.CONDITIONS -> it.copy(relevantConditions = value)
                ProfileField.MEDICATIONS -> it.copy(currentMedications = value)
                ProfileField.DENTAL_HISTORY -> it.copy(dentalHistory = value)
            }.copy(fieldErrors = it.fieldErrors - field, error = null)
        }
    }

    fun review() {
        val current = _state.value
        val errors = buildMap {
            if (current.allergies.trim().length > MAX) put(ProfileField.ALLERGIES, UserMessage.Local(R.string.atencion_validacion_2000))
            if (current.relevantConditions.trim().length > MAX) put(ProfileField.CONDITIONS, UserMessage.Local(R.string.atencion_validacion_2000))
            if (current.currentMedications.trim().length > MAX) put(ProfileField.MEDICATIONS, UserMessage.Local(R.string.atencion_validacion_2000))
            if (current.dentalHistory.trim().length > MAX) put(ProfileField.DENTAL_HISTORY, UserMessage.Local(R.string.atencion_validacion_2000))
        }
        val empty = current.toRequest().let {
            it.allergies == null && it.relevantConditions == null && it.currentMedications == null && it.dentalHistory == null
        }
        _state.update {
            it.copy(
                fieldErrors = errors,
                error = if (empty) UserMessage.Local(R.string.perfil_clinico_vacio) else null,
                step = if (errors.isEmpty() && !empty) FormStep.CONFIRM else FormStep.FORM,
            )
        }
    }

    fun backToForm() {
        if (_state.value.submitting) return
        _state.update { it.copy(step = FormStep.FORM, error = null) }
    }

    fun submit() {
        val current = _state.value
        if (current.submitting || current.step != FormStep.CONFIRM) return
        val request = current.toRequest()
        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            if (uncertainAttempt) {
                when (val check = findSaved(request)) {
                    is ApiResult.Success -> check.data?.let { return@launch done(it) }
                    is ApiResult.Failure -> {
                        _state.update { it.copy(submitting = false, error = UserMessage.Local(R.string.perfil_clinico_incierto)) }
                        return@launch
                    }
                }
            }
            when (val result = repository.addClinicalProfile(appointmentId, request)) {
                is ApiResult.Success -> done(result.data)
                is ApiResult.Unauthenticated -> _state.update { it.copy(submitting = false, awaitingReauth = true) }
                is ApiResult.BadRequest -> _state.update {
                    it.copy(
                        submitting = false,
                        step = if (result.code == "CLINICAL_PROFILE_EMPTY") FormStep.FORM else it.step,
                        error = UserMessage.Local(
                            if (result.code == "CLINICAL_PROFILE_EMPTY") R.string.perfil_clinico_vacio else R.string.error_datos_invalidos,
                        ),
                    )
                }
                is ApiResult.NetworkError, is ApiResult.ServerError, is ApiResult.Unexpected -> {
                    uncertainAttempt = true
                    when (val check = findSaved(request)) {
                        is ApiResult.Success -> {
                            val saved = check.data
                            if (saved != null) done(saved) else _state.update {
                                it.copy(submitting = false, error = UserMessage.Local(R.string.perfil_clinico_no_guardado))
                            }
                        }
                        is ApiResult.Failure ->
                            _state.update { it.copy(submitting = false, error = UserMessage.Local(R.string.perfil_clinico_incierto)) }
                    }
                }
                is ApiResult.Failure -> _state.update { it.copy(submitting = false, error = result.toUserMessage()) }
            }
        }
    }

    /** Tras un envío incierto: ¿el perfil vigente ya es exactamente el que se envió? */
    private suspend fun findSaved(request: ClinicalProfileRequest): ApiResult<ClinicalProfile?> =
        when (val record = repository.record(appointmentId)) {
            is ApiResult.Success -> ApiResult.Success(
                record.data.profile?.takeIf {
                    it.allergies == request.allergies && it.relevantConditions == request.relevantConditions &&
                        it.currentMedications == request.currentMedications && it.dentalHistory == request.dentalHistory
                },
            )
            is ApiResult.Failure -> record
        }

    private fun done(profile: ClinicalProfile) {
        uncertainAttempt = false
        _state.update { it.copy(step = FormStep.DONE, saved = profile, submitting = false, error = null) }
    }

    companion object {
        const val MAX = 2000
    }
}

private fun String.trimOrNull(): String? = trim().ifEmpty { null }

fun ClinicalProfileFormUiState.toRequest() = ClinicalProfileRequest(
    allergies = allergies.trimOrNull(),
    relevantConditions = relevantConditions.trimOrNull(),
    currentMedications = currentMedications.trimOrNull(),
    dentalHistory = dentalHistory.trimOrNull(),
)

/** Expediente del paciente de una cita propia. */
class DoctorRecordViewModel(
    private val repository: DoctorRepository,
    sessionManager: SessionManager,
    private val appointmentId: String,
) : ViewModel() {

    private val _state = MutableStateFlow<LoadState<ClinicalRecord>>(LoadState.Loading)
    val state: StateFlow<LoadState<ClinicalRecord>> = _state.asStateFlow()

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
        viewModelScope.launch { _state.value = repository.record(appointmentId).toLoadState() }
    }
}
