package com.clinicaserena.app.feature.doctor.care

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clinicaserena.app.R
import com.clinicaserena.app.core.network.ApiResult
import com.clinicaserena.app.core.security.SessionManager
import com.clinicaserena.app.core.ui.UserMessage
import com.clinicaserena.app.core.ui.toUserMessage
import com.clinicaserena.app.data.doctor.AddendumRequest
import com.clinicaserena.app.data.doctor.DoctorRepository
import com.clinicaserena.app.domain.model.Addendum
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AddendumField { TEXT, REASON }

data class AddendumFormUiState(
    val step: FormStep = FormStep.FORM,
    val text: String = "",
    val reason: String = "",
    val fieldErrors: Map<AddendumField, UserMessage> = emptyMap(),
    val submitting: Boolean = false,
    val error: UserMessage? = null,
    val saved: Addendum? = null,
    val awaitingReauth: Boolean = false,
    val confirmDiscard: Boolean = false,
) {
    val hasChanges: Boolean get() = text.isNotBlank() || reason.isNotBlank()

    override fun toString(): String = "AddendumFormUiState(step=$step, submitting=$submitting)"
}

/**
 * Adenda a la atención de una cita propia: es la única forma de corregir una atención, y también es
 * inmutable. Mismo patrón que la atención: confirmación, un solo envío, re-login con un reintento y,
 * ante un resultado incierto, consulta de la cita antes de reenviar.
 */
class AddendumFormViewModel(
    private val repository: DoctorRepository,
    sessionManager: SessionManager,
    private val appointmentId: String,
    private val attentionId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(AddendumFormUiState())
    val state: StateFlow<AddendumFormUiState> = _state.asStateFlow()
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

    fun onChange(field: AddendumField, value: String) {
        if (_state.value.step != FormStep.FORM || _state.value.submitting) return
        _state.update {
            when (field) {
                AddendumField.TEXT -> it.copy(text = value)
                AddendumField.REASON -> it.copy(reason = value)
            }.copy(fieldErrors = it.fieldErrors - field, error = null)
        }
    }

    fun review() {
        val errors = validate(_state.value)
        _state.update { it.copy(fieldErrors = errors, error = null, step = if (errors.isEmpty()) FormStep.CONFIRM else FormStep.FORM) }
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
        _state.value = AddendumFormUiState()
    }

    fun submit() {
        val current = _state.value
        if (current.submitting || current.step != FormStep.CONFIRM) return
        if (validate(current).isNotEmpty()) {
            review()
            return
        }
        val request = current.toRequest()
        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            if (uncertainAttempt) {
                when (val check = findSaved(request)) {
                    is ApiResult.Success -> check.data?.let { return@launch done(it) }
                    is ApiResult.Failure -> {
                        _state.update { it.copy(submitting = false, error = UserMessage.Local(R.string.adenda_incierta)) }
                        return@launch
                    }
                }
            }
            when (val result = repository.addAddendum(appointmentId, attentionId, request)) {
                is ApiResult.Success -> done(result.data)
                is ApiResult.Unauthenticated -> _state.update { it.copy(submitting = false, awaitingReauth = true) }
                is ApiResult.NotFound -> _state.update {
                    it.copy(submitting = false, error = UserMessage.Local(R.string.adenda_404))
                }
                is ApiResult.Validation -> _state.update {
                    it.copy(
                        submitting = false,
                        step = FormStep.FORM,
                        fieldErrors = result.fieldErrors.mapNotNull { e ->
                            when (e.field) {
                                "text" -> AddendumField.TEXT
                                "reason" -> AddendumField.REASON
                                else -> null
                            }?.let { field -> field to UserMessage.Server(e.message) }
                        }.toMap(),
                        error = UserMessage.Local(R.string.error_datos_invalidos),
                    )
                }
                is ApiResult.NetworkError, is ApiResult.ServerError, is ApiResult.Unexpected -> {
                    uncertainAttempt = true
                    when (val check = findSaved(request)) {
                        is ApiResult.Success -> {
                            val saved = check.data
                            if (saved != null) done(saved) else _state.update {
                                it.copy(submitting = false, error = UserMessage.Local(R.string.adenda_no_guardada))
                            }
                        }
                        is ApiResult.Failure ->
                            _state.update { it.copy(submitting = false, error = UserMessage.Local(R.string.adenda_incierta)) }
                    }
                }
                is ApiResult.Failure -> _state.update { it.copy(submitting = false, error = result.toUserMessage()) }
            }
        }
    }

    /** Tras un envío incierto: ¿la atención ya tiene una adenda con este mismo texto y motivo? */
    private suspend fun findSaved(request: AddendumRequest): ApiResult<Addendum?> =
        when (val detail = repository.appointment(appointmentId)) {
            is ApiResult.Success -> ApiResult.Success(
                detail.data.attention?.addenda?.lastOrNull { it.text == request.text && it.reason == request.reason },
            )
            is ApiResult.Failure -> detail
        }

    private fun done(addendum: Addendum) {
        uncertainAttempt = false
        _state.update { it.copy(step = FormStep.DONE, saved = addendum, submitting = false, error = null) }
    }

    companion object {
        const val TEXT_MAX = 2000
        const val REASON_MAX = 500

        /** Límites de `CreateAddendumRequest` (Spring vuelve a comprobar el texto recortado). */
        fun validate(state: AddendumFormUiState): Map<AddendumField, UserMessage> = buildMap {
            if (state.text.trim().length !in 3..TEXT_MAX) put(AddendumField.TEXT, UserMessage.Local(R.string.adenda_validacion_texto))
            if (state.reason.trim().length !in 3..REASON_MAX) put(AddendumField.REASON, UserMessage.Local(R.string.adenda_validacion_motivo))
        }
    }
}

fun AddendumFormUiState.toRequest() = AddendumRequest(text = text.trim(), reason = reason.trim())
