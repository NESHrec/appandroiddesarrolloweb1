package com.clinicaserena.app.feature.patient.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clinicaserena.app.R
import com.clinicaserena.app.core.network.ApiResult
import com.clinicaserena.app.core.security.SessionManager
import com.clinicaserena.app.core.ui.LoadState
import com.clinicaserena.app.core.ui.UserMessage
import com.clinicaserena.app.core.ui.toLoadState
import com.clinicaserena.app.core.ui.toUserMessage
import com.clinicaserena.app.data.patient.PatientProfile
import com.clinicaserena.app.data.patient.PatientProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PatientProfileUiState(
    val load: LoadState<PatientProfile> = LoadState.Loading,
    val editing: Boolean = false,
    val nameDraft: String = "",
    val nameError: UserMessage? = null,
    val saving: Boolean = false,
    /** Spring confirmó el cambio (200): solo entonces se muestra el éxito. */
    val saved: Boolean = false,
    val saveError: UserMessage? = null,
    /** 401 al guardar: el borrador se conserva y se reintenta una vez tras el re-login. */
    val awaitingReauth: Boolean = false,
)

/** Mi perfil (`/pacientes/me/perfil`): ver y cambiar el nombre completo. */
class PatientProfileViewModel(
    private val repository: PatientProfileRepository,
    sessionManager: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(PatientProfileUiState())
    val state: StateFlow<PatientProfileUiState> = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            sessionManager.reauthenticated.collect {
                val current = _state.value
                when {
                    current.awaitingReauth -> {
                        _state.update { it.copy(awaitingReauth = false) }
                        save()
                    }
                    (current.load as? LoadState.Failed)?.failure is ApiResult.Unauthenticated -> load()
                }
            }
        }
    }

    fun load() {
        _state.update { it.copy(load = LoadState.Loading) }
        viewModelScope.launch {
            val result = repository.getOwn().toLoadState()
            _state.update { it.copy(load = result) }
        }
    }

    fun startEditing() {
        val name = (_state.value.load as? LoadState.Loaded)?.data?.fullName ?: return
        _state.update { it.copy(editing = true, nameDraft = name, nameError = null, saveError = null, saved = false) }
    }

    fun cancelEditing() = _state.update { it.copy(editing = false, nameError = null, saveError = null) }

    fun onNameChange(value: String) = _state.update { it.copy(nameDraft = value, nameError = null, saveError = null) }

    fun save() {
        val current = _state.value
        if (current.saving || !current.editing) return
        val name = current.nameDraft.trim()
        val localError = when {
            name.length < NAME_MIN -> R.string.perfil_nombre_corto
            name.length > NAME_MAX -> R.string.validacion_nombre_largo
            else -> null
        }
        if (localError != null) {
            _state.update { it.copy(nameError = UserMessage.Local(localError)) }
            return
        }

        _state.update { it.copy(saving = true, saveError = null, saved = false) }
        viewModelScope.launch {
            when (val result = repository.updateName(name)) {
                is ApiResult.Success -> _state.update {
                    it.copy(load = LoadState.Loaded(result.data), editing = false, saving = false, saved = true)
                }
                is ApiResult.Unauthenticated -> _state.update { it.copy(saving = false, awaitingReauth = true) }
                is ApiResult.Validation -> _state.update {
                    it.copy(
                        saving = false,
                        nameError = result.fieldErrors.firstOrNull { e -> e.field == "fullName" }
                            ?.let { e -> UserMessage.Server(e.message) },
                        saveError = result.toUserMessage(),
                    )
                }
                is ApiResult.Failure -> _state.update { it.copy(saving = false, saveError = result.toUserMessage()) }
            }
        }
    }

    companion object {
        const val NAME_MIN = 2
        const val NAME_MAX = 160
    }
}
