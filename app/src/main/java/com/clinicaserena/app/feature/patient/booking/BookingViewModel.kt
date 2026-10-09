package com.clinicaserena.app.feature.patient.booking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clinicaserena.app.R
import com.clinicaserena.app.core.network.ApiResult
import com.clinicaserena.app.core.security.SessionManager
import com.clinicaserena.app.core.ui.LoadState
import com.clinicaserena.app.core.ui.UserMessage
import com.clinicaserena.app.core.ui.toLoadState
import com.clinicaserena.app.core.ui.toUserMessage
import com.clinicaserena.app.data.catalog.CatalogRepository
import com.clinicaserena.app.data.patient.AppointmentsRepository
import com.clinicaserena.app.domain.model.Appointment
import com.clinicaserena.app.domain.model.AppointmentStatus
import com.clinicaserena.app.domain.model.Practitioner
import com.clinicaserena.app.domain.model.Slot
import com.clinicaserena.app.domain.model.Specialty
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class BookingStep { SPECIALTY, PRACTITIONER, SLOT, CONFIRM, DONE }

data class BookingUiState(
    val step: BookingStep = BookingStep.SPECIALTY,
    val specialties: LoadState<List<Specialty>> = LoadState.Loading,
    val specialty: Specialty? = null,
    val practitioners: LoadState<List<Practitioner>> = LoadState.Loading,
    val practitioner: Practitioner? = null,
    val slots: LoadState<List<Slot>> = LoadState.Loading,
    val slot: Slot? = null,
    val notes: String = "",
    val notesError: UserMessage? = null,
    val submitting: Boolean = false,
    /** El horario elegido dejó de estar disponible: se volvió a la lista de horarios refrescada. */
    val slotUnavailable: Boolean = false,
    /** Error de la confirmación que no implica que el horario esté ocupado. */
    val error: UserMessage? = null,
    /** Reserva confirmada por Spring (201): solo entonces se muestra el éxito. */
    val booked: Appointment? = null,
    /** 401 al confirmar: el borrador se conserva y se reintenta una vez tras el re-login. */
    val awaitingReauth: Boolean = false,
)

/**
 * Reserva: especialidad → médico → horario → confirmación. Reenvía el `startAt` del bloque tal como
 * llegó. Solo 409 `SLOT_NOT_AVAILABLE`, 400 `SLOT_IN_PAST` y 404 `SLOT_NOT_FOUND` significan que el
 * horario ya no sirve; los demás errores se explican sin tocar la lista de horarios.
 */
class BookingViewModel(
    private val catalogRepository: CatalogRepository,
    private val appointmentsRepository: AppointmentsRepository,
    sessionManager: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(BookingUiState())
    val state: StateFlow<BookingUiState> = _state.asStateFlow()

    /** Hubo un envío con resultado incierto (red o 5xx): antes de reenviar se consultan las citas. */
    private var uncertainAttempt = false

    init {
        loadSpecialties()
        viewModelScope.launch {
            sessionManager.reauthenticated.collect {
                if (_state.value.awaitingReauth) {
                    _state.update { it.copy(awaitingReauth = false) }
                    submit()
                }
            }
        }
    }

    fun loadSpecialties() {
        _state.update { it.copy(specialties = LoadState.Loading) }
        viewModelScope.launch {
            val result = catalogRepository.specialties().toLoadState()
            _state.update { it.copy(specialties = result) }
        }
    }

    fun selectSpecialty(specialty: Specialty) {
        _state.update {
            it.copy(step = BookingStep.PRACTITIONER, specialty = specialty, practitioner = null, slot = null)
        }
        loadPractitioners()
    }

    fun loadPractitioners() {
        val specialty = _state.value.specialty ?: return
        _state.update { it.copy(practitioners = LoadState.Loading) }
        viewModelScope.launch {
            val result = catalogRepository.practitioners(specialty.id).toLoadState()
            _state.update { it.copy(practitioners = result) }
        }
    }

    fun selectPractitioner(practitioner: Practitioner) {
        _state.update { it.copy(step = BookingStep.SLOT, practitioner = practitioner, slot = null, slotUnavailable = false) }
        loadSlots()
    }

    fun loadSlots() {
        val practitioner = _state.value.practitioner ?: return
        _state.update { it.copy(slots = LoadState.Loading) }
        viewModelScope.launch {
            val result = catalogRepository.availability(practitioner.id).toLoadState()
            _state.update { it.copy(slots = result) }
        }
    }

    fun selectSlot(slot: Slot) {
        _state.update { it.copy(step = BookingStep.CONFIRM, slot = slot, error = null, slotUnavailable = false) }
    }

    fun onNotesChange(value: String) {
        _state.update { it.copy(notes = value, notesError = null, error = null) }
    }

    /** Paso anterior. Devuelve `false` si ya no hay paso anterior (salir de la reserva). */
    fun back(): Boolean {
        val current = _state.value
        if (current.submitting) return true
        val previous = when (current.step) {
            BookingStep.SPECIALTY, BookingStep.DONE -> return false
            BookingStep.PRACTITIONER -> BookingStep.SPECIALTY
            BookingStep.SLOT -> BookingStep.PRACTITIONER
            BookingStep.CONFIRM -> BookingStep.SLOT
        }
        _state.update { it.copy(step = previous, error = null, slotUnavailable = false) }
        return true
    }

    fun submit() {
        val current = _state.value
        if (current.submitting || current.step != BookingStep.CONFIRM) return
        val slot = current.slot ?: return
        val practitioner = current.practitioner ?: return
        val specialty = current.specialty ?: return
        if (current.notes.length > NOTES_MAX) {
            _state.update { it.copy(notesError = UserMessage.Local(R.string.reserva_notas_largas)) }
            return
        }

        _state.update { it.copy(submitting = true, error = null, notesError = null) }
        viewModelScope.launch {
            if (uncertainAttempt) {
                when (val check = findBooked(slot, practitioner)) {
                    is ApiResult.Success -> check.data?.let { return@launch done(it) }
                    is ApiResult.Failure -> {
                        _state.update { it.copy(submitting = false, error = UserMessage.Local(R.string.reserva_incierta)) }
                        return@launch
                    }
                }
            }
            val notes = current.notes.trim().ifEmpty { null }
            val result = appointmentsRepository.book(practitioner.id, specialty.id, slot.startAtRaw, notes)
            handleResult(result, slot, practitioner)
        }
    }

    private suspend fun handleResult(result: ApiResult<Appointment>, slot: Slot, practitioner: Practitioner) {
        when {
            result is ApiResult.Success -> done(result.data)

            result is ApiResult.Unauthenticated ->
                _state.update { it.copy(submitting = false, awaitingReauth = true) }

            result.isSlotGone() -> {
                // Tras un intento incierto, un 409 puede ser nuestra propia reserva: se comprueba primero.
                val own = if (uncertainAttempt) (findBooked(slot, practitioner) as? ApiResult.Success)?.data else null
                if (own != null) done(own) else slotGone()
            }

            result is ApiResult.NetworkError || result is ApiResult.ServerError || result is ApiResult.Unexpected -> {
                uncertainAttempt = true
                when (val check = findBooked(slot, practitioner)) {
                    is ApiResult.Success -> {
                        val own = check.data
                        if (own != null) {
                            done(own)
                        } else {
                            _state.update {
                                it.copy(submitting = false, error = UserMessage.Local(R.string.reserva_no_confirmada))
                            }
                        }
                    }
                    is ApiResult.Failure ->
                        _state.update { it.copy(submitting = false, error = UserMessage.Local(R.string.reserva_incierta)) }
                }
            }

            result is ApiResult.BadRequest && result.code == "NOTES_TOO_LONG" ->
                _state.update { it.copy(submitting = false, notesError = UserMessage.Local(R.string.reserva_notas_largas)) }

            result is ApiResult.Validation -> {
                val notesError = result.fieldErrors.firstOrNull { it.field == "notes" }?.let { UserMessage.Server(it.message) }
                _state.update {
                    it.copy(
                        submitting = false,
                        notesError = notesError,
                        error = if (notesError == null) UserMessage.Local(R.string.reserva_error_generico) else null,
                    )
                }
            }

            else -> _state.update { it.copy(submitting = false, error = (result as ApiResult.Failure).toBookingMessage()) }
        }
    }

    private fun slotGone() {
        _state.update {
            it.copy(step = BookingStep.SLOT, slot = null, slotUnavailable = true, submitting = false, error = null)
        }
        loadSlots()
    }

    private fun done(appointment: Appointment) {
        uncertainAttempt = false
        _state.update { it.copy(step = BookingStep.DONE, booked = appointment, submitting = false, error = null) }
    }

    /** Busca en las citas propias una reserva activa para ese médico y ese instante. */
    private suspend fun findBooked(slot: Slot, practitioner: Practitioner): ApiResult<Appointment?> =
        when (val result = appointmentsRepository.listOwn()) {
            is ApiResult.Success -> ApiResult.Success(
                result.data.firstOrNull {
                    it.practitionerId == practitioner.id &&
                        it.scheduledAt.toInstant() == slot.startAt.toInstant() &&
                        it.status != AppointmentStatus.CANCELADA
                },
            )
            is ApiResult.Failure -> result
        }

    private fun ApiResult<*>.isSlotGone(): Boolean =
        (this is ApiResult.Conflict && code == "SLOT_NOT_AVAILABLE") ||
            (this is ApiResult.BadRequest && code == "SLOT_IN_PAST") ||
            (this is ApiResult.NotFound && code == "SLOT_NOT_FOUND")

    private fun ApiResult.Failure.toBookingMessage(): UserMessage = when {
        this is ApiResult.BadRequest && (code == "SPECIALTY_PRACTITIONER_MISMATCH" || code == "SLOT_PRACTITIONER_MISMATCH") ->
            UserMessage.Local(R.string.reserva_datos_no_coinciden)
        this is ApiResult.BadRequest && code == "SCHEDULED_AT_INVALID" -> UserMessage.Local(R.string.reserva_fecha_invalida)
        this is ApiResult.NotFound && (code == "PRACTITIONER_NOT_FOUND" || code == "SPECIALTY_NOT_FOUND") ->
            UserMessage.Local(R.string.reserva_medico_no_disponible)
        this is ApiResult.Forbidden -> toUserMessage()
        else -> UserMessage.Local(R.string.reserva_error_generico)
    }

    companion object {
        const val NOTES_MAX = 1000
    }
}
