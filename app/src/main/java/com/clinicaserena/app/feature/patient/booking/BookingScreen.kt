package com.clinicaserena.app.feature.patient.booking

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clinicaserena.app.R
import com.clinicaserena.app.core.debug.DebugSessionTools
import com.clinicaserena.app.core.time.ClinicTime
import com.clinicaserena.app.core.ui.FailureContent
import com.clinicaserena.app.core.ui.LoadState
import com.clinicaserena.app.core.ui.asString
import com.clinicaserena.app.core.ui.components.EmptyState
import com.clinicaserena.app.core.ui.components.FieldShape
import com.clinicaserena.app.core.ui.components.GroupedCard
import com.clinicaserena.app.core.ui.components.GroupedRow
import com.clinicaserena.app.core.ui.components.GroupedSectionHeader
import com.clinicaserena.app.core.ui.components.HairlineDivider
import com.clinicaserena.app.core.ui.components.LoadingState
import com.clinicaserena.app.core.ui.components.PractitionerAvatar
import com.clinicaserena.app.core.ui.components.PrimaryButton
import com.clinicaserena.app.core.ui.components.SecondaryButton
import com.clinicaserena.app.core.ui.components.StatusBanner
import com.clinicaserena.app.core.ui.components.serenaFieldColors
import com.clinicaserena.app.core.ui.theme.LocalSerenaColors
import com.clinicaserena.app.core.ui.theme.LocalStatusColors
import com.clinicaserena.app.domain.model.Practitioner
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
            GroupedCard {
                load.data.forEachIndexed { index, specialty ->
                    if (index > 0) HairlineDivider(startIndent = 16.dp)
                    ChoiceRow(
                        title = specialty.name,
                        subtitle = specialty.description,
                        testTag = "specialty_${specialty.id}",
                        onClick = { viewModel.selectSpecialty(specialty) },
                    )
                }
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
            GroupedCard {
                load.data.forEachIndexed { index, practitioner ->
                    if (index > 0) HairlineDivider(startIndent = 76.dp)
                    ChoiceRow(
                        title = practitioner.fullName,
                        subtitle = practitioner.specialtyName,
                        testTag = "practitioner_${practitioner.id}",
                        onClick = { viewModel.selectPractitioner(practitioner) },
                        leading = { PractitionerAvatar(practitioner.fullName, practitioner.photoUrl, 48.dp) },
                    )
                }
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
            SecondaryButton(text = stringResource(R.string.perfil_actualizar), onClick = viewModel::loadSlots)
        } else {
            Text(
                stringResource(R.string.reserva_horarios_zona),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            load.data.groupBy { ClinicTime.localDate(it.startAt) }.forEach { (_, slots) ->
                GroupedSectionHeader(ClinicTime.formatDay(slots.first().startAt))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    slots.forEach { slot ->
                        SlotButton(slotRange(slot), onClick = { viewModel.selectSlot(slot) }, testTag = "slot_${slot.id}")
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfirmStep(state: BookingUiState, viewModel: BookingViewModel) {
    val slot = state.slot ?: return
    GroupedCard(modifier = Modifier.testTag("booking_summary")) {
        Column(Modifier.padding(16.dp)) {
            state.specialty?.let { InfoRow(stringResource(R.string.cita_especialidad), it.name) }
            state.practitioner?.let { PractitionerSummaryRow(it) }
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
        shape = FieldShape,
        colors = serenaFieldColors(),
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
    GroupedCard {
        Column(Modifier.padding(16.dp)) {
            state.specialty?.let { InfoRow(stringResource(R.string.cita_especialidad), it.name) }
            state.practitioner?.let { PractitionerSummaryRow(it) }
            InfoRow(stringResource(R.string.cita_fecha_hora), ClinicTime.formatDateTime(booked.scheduledAt))
            booked.notes?.takeIf { it.isNotBlank() }?.let { InfoRow(stringResource(R.string.cita_notas), it) }
        }
    }
    PrimaryButton(
        text = stringResource(R.string.ver_mis_citas),
        onClick = onViewAppointments,
        modifier = Modifier.testTag("booking_view_appointments"),
    )
    SecondaryButton(text = stringResource(R.string.reserva_volver_inicio), onClick = onExit)
}

/** Opción de la reserva (especialidad o médico): fila de lista agrupada con chevron; toda la fila es un botón. */
@Composable
private fun ChoiceRow(
    title: String,
    subtitle: String?,
    testTag: String,
    onClick: () -> Unit,
    leading: (@Composable () -> Unit)? = null,
) {
    GroupedRow(onClick = onClick, testTag = testTag, leading = leading) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        subtitle?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Horario libre: botón de tarjeta (48 dp mínimo) con el rango; tocarlo abre la confirmación. */
@Composable
private fun SlotButton(text: String, onClick: () -> Unit, testTag: String) {
    val colors = LocalSerenaColors.current
    val shape = RoundedCornerShape(14.dp)
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = colors.text,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .shadow(4.dp, shape, ambientColor = colors.cardShadow, spotColor = colors.cardShadow)
            .clip(shape)
            .background(colors.card)
            .border(1.dp, colors.separator, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(testTag)
            .padding(horizontal = 16.dp, vertical = 13.dp),
    )
}

/** Fila «Médico» del resumen con su foto pública (o iniciales). */
@Composable
private fun PractitionerSummaryRow(practitioner: Practitioner) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        PractitionerAvatar(practitioner.fullName, practitioner.photoUrl, 64.dp)
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f)) { InfoRow(stringResource(R.string.cita_medico), practitioner.fullName) }
    }
}

private fun slotRange(slot: Slot): String = "${ClinicTime.formatTime(slot.startAt)} – ${ClinicTime.formatTime(slot.endAt)}"
