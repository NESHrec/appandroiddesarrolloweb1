package com.clinicaserena.app.feature.patient.booking

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clinicaserena.app.R
import com.clinicaserena.app.core.debug.DebugSessionTools
import com.clinicaserena.app.core.time.ClinicTime
import com.clinicaserena.app.core.ui.FailureContent
import com.clinicaserena.app.core.ui.LoadState
import com.clinicaserena.app.core.ui.asString
import com.clinicaserena.app.core.ui.components.EmptyState
import com.clinicaserena.app.core.ui.components.LoadingState
import com.clinicaserena.app.core.ui.components.StatusBanner
import com.clinicaserena.app.core.ui.theme.LocalStatusColors
import com.clinicaserena.app.domain.model.Slot
import com.clinicaserena.app.feature.auth.SubmitButton
import com.clinicaserena.app.feature.common.BackScaffold
import com.clinicaserena.app.feature.common.InfoRow

@Composable
fun BookingScreen(
    viewModel: BookingViewModel,
    onExit: () -> Unit,
    onBooked: () -> Unit,
    onViewAppointments: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val goBack = { if (!viewModel.back()) onExit() }
    BackHandler(onBack = goBack)

    LaunchedEffect(state.booked) { if (state.booked != null) onBooked() }

    val title = stringResource(
        when (state.step) {
            BookingStep.SPECIALTY -> R.string.reserva_paso_especialidad
            BookingStep.PRACTITIONER -> R.string.reserva_paso_medico
            BookingStep.SLOT -> R.string.reserva_paso_horario
            BookingStep.CONFIRM -> R.string.reserva_paso_confirmar
            BookingStep.DONE -> R.string.reserva_paso_listo
        },
    )

    BackScaffold(title = title, onBack = goBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (state.step) {
                BookingStep.SPECIALTY -> SpecialtyStep(state, viewModel)
                BookingStep.PRACTITIONER -> PractitionerStep(state, viewModel)
                BookingStep.SLOT -> SlotStep(state, viewModel)
                BookingStep.CONFIRM -> ConfirmStep(state, viewModel)
                BookingStep.DONE -> DoneStep(state, onViewAppointments, onExit)
            }
        }
    }
}

@Composable
private fun SpecialtyStep(state: BookingUiState, viewModel: BookingViewModel) {
    when (val load = state.specialties) {
        LoadState.Loading -> LoadingState(modifier = Modifier.padding(vertical = 32.dp))
        is LoadState.Failed -> FailureContent(load.failure, onRetry = viewModel::loadSpecialties, scrollable = false)
        is LoadState.Loaded -> if (load.data.isEmpty()) {
            EmptyState(message = stringResource(R.string.reserva_sin_especialidades), scrollable = false)
        } else {
            load.data.forEach { specialty ->
                ChoiceCard(
                    title = specialty.name,
                    subtitle = specialty.description,
                    testTag = "specialty_${specialty.id}",
                    onClick = { viewModel.selectSpecialty(specialty) },
                )
            }
        }
    }
}

@Composable
private fun PractitionerStep(state: BookingUiState, viewModel: BookingViewModel) {
    state.specialty?.let { InfoRow(stringResource(R.string.cita_especialidad), it.name) }
    when (val load = state.practitioners) {
        LoadState.Loading -> LoadingState(modifier = Modifier.padding(vertical = 32.dp))
        is LoadState.Failed -> FailureContent(load.failure, onRetry = viewModel::loadPractitioners, scrollable = false)
        is LoadState.Loaded -> if (load.data.isEmpty()) {
            EmptyState(message = stringResource(R.string.reserva_sin_medicos), scrollable = false)
        } else {
            load.data.forEach { practitioner ->
                ChoiceCard(
                    title = practitioner.fullName,
                    subtitle = practitioner.specialtyName,
                    testTag = "practitioner_${practitioner.id}",
                    onClick = { viewModel.selectPractitioner(practitioner) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SlotStep(state: BookingUiState, viewModel: BookingViewModel) {
    state.practitioner?.let { InfoRow(stringResource(R.string.cita_medico), it.fullName) }
    if (state.slotUnavailable) {
        StatusBanner(
            iconRes = R.drawable.ic_warning,
            title = stringResource(R.string.reserva_horario_ocupado_titulo),
            message = stringResource(R.string.reserva_horario_ocupado_mensaje),
            background = LocalStatusColors.current.warning,
            modifier = Modifier.testTag("slot_unavailable"),
        )
    }
    when (val load = state.slots) {
        LoadState.Loading -> LoadingState(modifier = Modifier.padding(vertical = 32.dp))
        is LoadState.Failed -> FailureContent(load.failure, onRetry = viewModel::loadSlots, scrollable = false)
        is LoadState.Loaded -> if (load.data.isEmpty()) {
            EmptyState(message = stringResource(R.string.reserva_sin_horarios), scrollable = false)
            OutlinedButton(onClick = viewModel::loadSlots) { Text(stringResource(R.string.perfil_actualizar)) }
        } else {
            Text(stringResource(R.string.reserva_horarios_zona), style = MaterialTheme.typography.bodySmall)
            load.data.groupBy { ClinicTime.localDate(it.startAt) }.forEach { (_, slots) ->
                Text(
                    ClinicTime.formatDay(slots.first().startAt).replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.semantics { heading() },
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    slots.forEach { slot ->
                        OutlinedButton(
                            onClick = { viewModel.selectSlot(slot) },
                            modifier = Modifier.testTag("slot_${slot.id}"),
                        ) { Text(slotRange(slot)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfirmStep(state: BookingUiState, viewModel: BookingViewModel) {
    val slot = state.slot ?: return
    Card(
        modifier = Modifier.fillMaxWidth().testTag("booking_summary"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            state.specialty?.let { InfoRow(stringResource(R.string.cita_especialidad), it.name) }
            state.practitioner?.let { InfoRow(stringResource(R.string.cita_medico), it.fullName) }
            InfoRow(stringResource(R.string.cita_fecha_hora), ClinicTime.formatDateTime(slot.startAt))
            InfoRow(stringResource(R.string.reserva_duracion), slotRange(slot))
        }
    }
    OutlinedTextField(
        value = state.notes,
        onValueChange = viewModel::onNotesChange,
        label = { Text(stringResource(R.string.reserva_notas)) },
        supportingText = {
            Text(
                state.notesError?.asString()
                    ?: stringResource(R.string.reserva_notas_contador, state.notes.length, BookingViewModel.NOTES_MAX),
            )
        },
        isError = state.notesError != null,
        enabled = !state.submitting,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp).testTag("booking_notes"),
    )
    if (state.awaitingReauth) {
        StatusBanner(
            iconRes = R.drawable.ic_schedule,
            title = stringResource(R.string.estado_sesion_vencida_titulo),
            message = stringResource(R.string.reserva_esperando_sesion),
            background = LocalStatusColors.current.warning,
        )
    }
    state.error?.let {
        StatusBanner(
            iconRes = R.drawable.ic_error,
            title = stringResource(R.string.reserva_no_completada_titulo),
            message = it.asString(),
            background = LocalStatusColors.current.error,
            modifier = Modifier.testTag("booking_error"),
        )
    }
    SubmitButton(
        text = stringResource(R.string.reserva_confirmar),
        submitting = state.submitting,
        onClick = viewModel::submit,
        testTag = "booking_confirm",
    )
    DebugSessionTools()
}

@Composable
private fun DoneStep(state: BookingUiState, onViewAppointments: () -> Unit, onExit: () -> Unit) {
    val booked = state.booked ?: return
    StatusBanner(
        iconRes = R.drawable.ic_check_circle,
        title = stringResource(R.string.reserva_exito_titulo),
        message = stringResource(R.string.reserva_exito_mensaje),
        background = LocalStatusColors.current.success,
        modifier = Modifier.testTag("booking_done"),
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            state.specialty?.let { InfoRow(stringResource(R.string.cita_especialidad), it.name) }
            state.practitioner?.let { InfoRow(stringResource(R.string.cita_medico), it.fullName) }
            InfoRow(stringResource(R.string.cita_fecha_hora), ClinicTime.formatDateTime(booked.scheduledAt))
            booked.notes?.takeIf { it.isNotBlank() }?.let { InfoRow(stringResource(R.string.cita_notas), it) }
        }
    }
    Button(onClick = onViewAppointments, modifier = Modifier.fillMaxWidth().testTag("booking_view_appointments")) {
        Text(stringResource(R.string.ver_mis_citas))
    }
    OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.reserva_volver_inicio))
    }
}

@Composable
private fun ChoiceCard(title: String, subtitle: String?, testTag: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
            .clickable(role = Role.Button, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun slotRange(slot: Slot): String = "${ClinicTime.formatTime(slot.startAt)} – ${ClinicTime.formatTime(slot.endAt)}"
