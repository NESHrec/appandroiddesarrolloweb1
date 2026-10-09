package com.clinicaserena.app.feature.doctor.care

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clinicaserena.app.R
import com.clinicaserena.app.core.network.ApiResult
import com.clinicaserena.app.core.security.SessionManager
import com.clinicaserena.app.core.ui.UserMessage
import com.clinicaserena.app.core.ui.toUserMessage
import com.clinicaserena.app.data.doctor.DoctorRepository
import com.clinicaserena.app.data.doctor.PrescriptionItemRequest
import com.clinicaserena.app.data.doctor.RecordAttentionRequest
import com.clinicaserena.app.domain.model.Attention
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FormStep { FORM, CONFIRM, DONE }

data class PrescriptionItemDraft(
    val medicine: String = "",
    val dose: String = "",
    val frequency: String = "",
    val duration: String = "",
    val instructions: String = "",
)

enum class ItemField { MEDICINE, DOSE, FREQUENCY, DURATION, INSTRUCTIONS }

data class AttentionFormUiState(
    val step: FormStep = FormStep.FORM,
    val reason: String = "",
    val findings: String = "",
    val diagnosis: String = "",
    val treatmentPlan: String = "",
    val items: List<PrescriptionItemDraft> = emptyList(),
    /** Errores por campo: `reason`, `diagnosis`, `prescription[0].medicine`… (mismos nombres que Spring). */
    val fieldErrors: Map<String, UserMessage> = emptyMap(),
    val submitting: Boolean = false,
    val error: UserMessage? = null,
    /** 409 ATTENTION_ALREADY_RECORDED: la cita ya tiene atención; se ofrece verla en lugar de reintentar. */
    val alreadyRecorded: Boolean = false,
    val recorded: Attention? = null,
    /** 401 al confirmar: el borrador se conserva y se reintenta una vez tras el re-login. */
    val awaitingReauth: Boolean = false,
) {
    override fun toString(): String = "AttentionFormUiState(step=$step, items=${items.size}, submitting=$submitting)"
}

/**
 * Registro de la atención de una cita propia. La atención es inmutable y única por cita: se revisa en
 * una confirmación antes de enviarla, el botón no admite doble envío y, si el resultado es incierto
 * (red o 5xx), se consulta el detalle de la cita antes de permitir reintentar.
 */
class AttentionFormViewModel(
    private val repository: DoctorRepository,
    sessionManager: SessionManager,
    private val appointmentId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(AttentionFormUiState())
    val state: StateFlow<AttentionFormUiState> = _state.asStateFlow()

    /** Hubo un envío sin respuesta clara: antes de reenviar se consulta si la atención ya existe. */
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

    fun onReasonChange(value: String) = edit { copy(reason = value) }.also { clearError("reason") }
    fun onFindingsChange(value: String) = edit { copy(findings = value) }.also { clearError("findings") }
    fun onDiagnosisChange(value: String) = edit { copy(diagnosis = value) }.also { clearError("diagnosis") }
    fun onTreatmentPlanChange(value: String) = edit { copy(treatmentPlan = value) }.also { clearError("treatmentPlan") }

    fun addItem() {
        if (_state.value.items.size >= MAX_ITEMS) return
        edit { copy(items = items + PrescriptionItemDraft()) }
    }

    fun removeItem(index: Int) {
        // Los errores por ítem dependen del índice: se recalculan al revisar.
        _state.update { it.copy(items = it.items.filterIndexed { i, _ -> i != index }, fieldErrors = emptyMap()) }
    }

    fun onItemChange(index: Int, field: ItemField, value: String) {
        edit {
            copy(items = items.mapIndexed { i, item ->
                if (i != index) item else when (field) {
                    ItemField.MEDICINE -> item.copy(medicine = value)
                    ItemField.DOSE -> item.copy(dose = value)
                    ItemField.FREQUENCY -> item.copy(frequency = value)
                    ItemField.DURATION -> item.copy(duration = value)
                    ItemField.INSTRUCTIONS -> item.copy(instructions = value)
                }
            })
        }
        clearError(itemKey(index, field))
    }

    /** Valida y pasa a la confirmación con el resumen. */
    fun review() {
        val errors = validate(_state.value)
        _state.update { it.copy(fieldErrors = errors, error = null, step = if (errors.isEmpty()) FormStep.CONFIRM else FormStep.FORM) }
    }

    fun backToForm() {
        if (_state.value.submitting) return
        _state.update { it.copy(step = FormStep.FORM, error = null) }
    }

    fun submit() {
        val current = _state.value
        // Un solo envío a la vez: un doble toque no crea dos atenciones.
        if (current.submitting || current.step != FormStep.CONFIRM) return
        if (validate(current).isNotEmpty()) {
            review()
            return
        }
        _state.update { it.copy(submitting = true, error = null, alreadyRecorded = false) }
        viewModelScope.launch {
            if (uncertainAttempt) {
                when (val check = repository.appointment(appointmentId)) {
                    is ApiResult.Success -> check.data.attention?.let { return@launch done(it) }
                    is ApiResult.Failure -> {
                        _state.update { it.copy(submitting = false, error = UserMessage.Local(R.string.atencion_incierta)) }
                        return@launch
                    }
                }
            }
            handle(repository.recordAttention(appointmentId, current.toRequest()))
        }
    }

    private suspend fun handle(result: ApiResult<Attention>) {
        when (result) {
            is ApiResult.Success -> done(result.data)
            is ApiResult.Unauthenticated -> _state.update { it.copy(submitting = false, awaitingReauth = true) }
            is ApiResult.Conflict -> _state.update {
                it.copy(
                    submitting = false,
                    error = UserMessage.Local(conflictMessage(result.code)),
                    alreadyRecorded = result.code == "ATTENTION_ALREADY_RECORDED",
                )
            }
            is ApiResult.Validation -> _state.update {
                it.copy(
                    submitting = false,
                    step = FormStep.FORM,
                    fieldErrors = result.fieldErrors.associate { e -> e.field to UserMessage.Server(e.message) },
                    error = UserMessage.Local(R.string.error_datos_invalidos),
                )
            }
            is ApiResult.NetworkError, is ApiResult.ServerError, is ApiResult.Unexpected -> {
                uncertainAttempt = true
                when (val check = repository.appointment(appointmentId)) {
                    is ApiResult.Success -> {
                        val existing = check.data.attention
                        if (existing != null) {
                            done(existing)
                        } else {
                            _state.update { it.copy(submitting = false, error = UserMessage.Local(R.string.atencion_no_registrada_reintentar)) }
                        }
                    }
                    is ApiResult.Failure ->
                        _state.update { it.copy(submitting = false, error = UserMessage.Local(R.string.atencion_incierta)) }
                }
            }
            is ApiResult.Failure -> _state.update { it.copy(submitting = false, error = result.toUserMessage()) }
        }
    }

    private fun done(attention: Attention) {
        uncertainAttempt = false
        _state.update { it.copy(step = FormStep.DONE, recorded = attention, submitting = false, error = null) }
    }

    private inline fun edit(block: AttentionFormUiState.() -> AttentionFormUiState) {
        if (_state.value.submitting || _state.value.step != FormStep.FORM) return
        _state.update { it.block() }
    }

    private fun clearError(key: String) = _state.update { it.copy(fieldErrors = it.fieldErrors - key) }

    companion object {
        const val MAX_ITEMS = 10

        fun itemKey(index: Int, field: ItemField): String = "prescription[$index]." + when (field) {
            ItemField.MEDICINE -> "medicine"
            ItemField.DOSE -> "dose"
            ItemField.FREQUENCY -> "frequency"
            ItemField.DURATION -> "duration"
            ItemField.INSTRUCTIONS -> "instructions"
        }

        /** Límites de `RecordAttentionRequest` (Spring valida lo mismo con el texto recortado). */
        fun validate(state: AttentionFormUiState): Map<String, UserMessage> {
            val errors = mutableMapOf<String, UserMessage>()
            fun check(key: String, value: String, min: Int, max: Int, @StringRes message: Int) {
                val length = value.trim().length
                if ((min > 0 && length < min) || length > max) errors[key] = UserMessage.Local(message)
            }
            check("reason", state.reason, 3, 1000, R.string.atencion_validacion_motivo)
            check("findings", state.findings, 0, 2000, R.string.atencion_validacion_2000)
            check("diagnosis", state.diagnosis, 3, 1000, R.string.atencion_validacion_diagnostico)
            check("treatmentPlan", state.treatmentPlan, 0, 2000, R.string.atencion_validacion_2000)
            state.items.forEachIndexed { i, item ->
                check(itemKey(i, ItemField.MEDICINE), item.medicine, 2, 160, R.string.atencion_validacion_medicamento)
                check(itemKey(i, ItemField.DOSE), item.dose, 1, 80, R.string.atencion_validacion_80)
                check(itemKey(i, ItemField.FREQUENCY), item.frequency, 1, 80, R.string.atencion_validacion_80)
                check(itemKey(i, ItemField.DURATION), item.duration, 1, 80, R.string.atencion_validacion_80)
                check(itemKey(i, ItemField.INSTRUCTIONS), item.instructions, 0, 500, R.string.atencion_validacion_500)
            }
            return errors
        }

        @StringRes
        fun conflictMessage(code: String): Int = when (code) {
            "ATTENTION_ALREADY_RECORDED" -> R.string.atencion_409_ya_registrada
            "APPOINTMENT_NOT_DOCUMENTABLE" -> R.string.atencion_409_no_documentable
            "APPOINTMENT_NOT_STARTED" -> R.string.atencion_409_no_iniciada
            "ARRIVAL_NOT_REGISTERED" -> R.string.atencion_409_sin_llegada
            "PATIENT_RECORD_UNAVAILABLE" -> R.string.atencion_409_sin_paciente
            else -> R.string.atencion_409_generico
        }
    }
}

private fun String.trimOrNull(): String? = trim().ifEmpty { null }

/** Texto recortado; los opcionales vacíos se envían como `null` (se omiten en el JSON). */
fun AttentionFormUiState.toRequest() = RecordAttentionRequest(
    reason = reason.trim(),
    findings = findings.trimOrNull(),
    diagnosis = diagnosis.trim(),
    treatmentPlan = treatmentPlan.trimOrNull(),
    prescription = items.map {
        PrescriptionItemRequest(it.medicine.trim(), it.dose.trim(), it.frequency.trim(), it.duration.trim(), it.instructions.trimOrNull())
    },
)
