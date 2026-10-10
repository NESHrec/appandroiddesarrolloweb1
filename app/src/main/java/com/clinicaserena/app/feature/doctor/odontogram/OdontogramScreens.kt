package com.clinicaserena.app.feature.doctor.odontogram

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clinicaserena.app.R
import com.clinicaserena.app.core.debug.DebugSessionTools
import com.clinicaserena.app.core.time.ClinicTime
import com.clinicaserena.app.core.ui.LoadState
import com.clinicaserena.app.core.ui.UserMessage
import com.clinicaserena.app.core.ui.asString
import com.clinicaserena.app.core.ui.components.EmptyState
import com.clinicaserena.app.core.ui.components.LoadingState
import com.clinicaserena.app.core.ui.components.StatusBanner
import com.clinicaserena.app.core.ui.theme.LocalStatusColors
import com.clinicaserena.app.domain.model.AttentionBlocker
import com.clinicaserena.app.domain.model.Dentition
import com.clinicaserena.app.domain.model.DoctorAppointment
import com.clinicaserena.app.domain.model.FdiTeeth
import com.clinicaserena.app.domain.model.ToothHistory
import com.clinicaserena.app.domain.model.ToothSurface
import com.clinicaserena.app.feature.auth.SubmitButton
import com.clinicaserena.app.feature.clinical.ClinicalCard
import com.clinicaserena.app.feature.clinical.SectionTitle
import com.clinicaserena.app.feature.common.BackScaffold
import com.clinicaserena.app.feature.common.InfoRow
import com.clinicaserena.app.feature.doctor.agenda.DoctorFailureContent
import com.clinicaserena.app.feature.doctor.agenda.blockerText
import com.clinicaserena.app.feature.doctor.care.ClinicalTextField
import com.clinicaserena.app.feature.doctor.care.DiscardDraftDialog
import com.clinicaserena.app.feature.doctor.care.ErrorNotice
import com.clinicaserena.app.feature.doctor.care.FormStep
import com.clinicaserena.app.feature.doctor.care.ImmutableNotice
import com.clinicaserena.app.feature.doctor.care.rememberFormBack

@StringRes
fun ToothSurface.labelRes(): Int = when (this) {
    ToothSurface.MESIAL -> R.string.superficie_mesial
    ToothSurface.DISTAL -> R.string.superficie_distal
    ToothSurface.VESTIBULAR -> R.string.superficie_vestibular
    ToothSurface.LINGUAL -> R.string.superficie_lingual
    ToothSurface.PALATINA -> R.string.superficie_palatina
    ToothSurface.OCLUSAL -> R.string.superficie_oclusal
    ToothSurface.INCISAL -> R.string.superficie_incisal
}

// ---------------------------------------------------------------- Historial

@Composable
fun OdontogramScreen(viewModel: OdontogramViewModel, onBack: () -> Unit, onAddObservation: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    BackScaffold(title = stringResource(R.string.odontograma_titulo), onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (val load = state) {
                LoadState.Loading -> LoadingState(modifier = Modifier.padding(vertical = 32.dp))
                is LoadState.Failed -> DoctorFailureContent(load.failure, onRetry = viewModel::load, scrollable = false)
                is LoadState.Loaded -> {
                    val content = load.data
                    content.odontogram.patientName?.let { InfoRow(stringResource(R.string.medico_paciente), it) }
                    AddObservationSection(content.appointment, onAddObservation)
                    Text(
                        stringResource(R.string.odontograma_historial),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        stringResource(R.string.odontograma_aviso_historial),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag("odontogram_history_notice"),
                    )
                    if (content.history.isEmpty()) {
                        EmptyState(message = stringResource(R.string.odontograma_vacio), scrollable = false)
                    } else {
                        Dentition.entries.forEach { dentition ->
                            val teeth = content.history.filter { it.dentition == dentition }
                            if (teeth.isNotEmpty()) {
                                SectionTitle(
                                    stringResource(if (dentition == Dentition.PERMANENT) R.string.odontograma_permanente else R.string.odontograma_temporal),
                                )
                                teeth.forEach { ToothCard(it, content.appointment.id) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Se puede agregar mientras la cita es documentable (mismos bloqueos que la atención); Spring revalida. */
@Composable
private fun AddObservationSection(appointment: DoctorAppointment, onAddObservation: () -> Unit) {
    if (appointment.canRecordAttention) {
        StatusBanner(
            iconRes = R.drawable.ic_check_circle,
            title = stringResource(R.string.medico_lista_para_documentar),
            message = stringResource(R.string.odontograma_disponible),
            background = LocalStatusColors.current.success,
            modifier = Modifier.testTag("odontogram_can_add"),
        )
    } else {
        val reasons = appointment.blockers.zip(appointment.blockersRaw).map { (blocker, raw) ->
            "• " + when (blocker) {
                AttentionBlocker.ATTENTION_ALREADY_RECORDED -> stringResource(R.string.odontograma_bloqueo_atencion)
                AttentionBlocker.STATUS_NOT_DOCUMENTABLE -> stringResource(R.string.odontograma_bloqueo_estado)
                else -> blockerText(blocker, raw)
            }
        }
        StatusBanner(
            iconRes = R.drawable.ic_lock,
            title = stringResource(R.string.odontograma_no_disponible_titulo),
            message = reasons.joinToString("\n"),
            background = LocalStatusColors.current.warning,
            modifier = Modifier.testTag("odontogram_blocked"),
        )
    }
    Button(
        onClick = onAddObservation,
        enabled = appointment.canRecordAttention,
        modifier = Modifier.fillMaxWidth().testTag("odontogram_add"),
    ) { Text(stringResource(R.string.odontograma_agregar)) }
}

@Composable
private fun ToothCard(tooth: ToothHistory, currentAppointmentId: String) {
    ClinicalCard(testTag = "odontogram_tooth_${tooth.toothNumber}") {
        SectionTitle(stringResource(R.string.odontograma_pieza, tooth.toothNumber))
        tooth.surfaces.forEachIndexed { index, surface ->
            if (index > 0) HorizontalDivider()
            Text(
                surface.surface?.let { stringResource(it.labelRes()) }
                    ?: surface.surfaceRaw
                    ?: stringResource(R.string.odontograma_sin_superficie),
                style = MaterialTheme.typography.titleSmall,
            )
            surface.observations.forEach { observation ->
                Column(Modifier.padding(vertical = 4.dp)) {
                    Text(observation.observation, style = MaterialTheme.typography.bodyMedium)
                    val date = stringResource(R.string.odontograma_registrada, ClinicTime.formatDateTime(observation.recordedAt))
                    Text(
                        if (observation.appointmentId == currentAppointmentId) {
                            date + " · " + stringResource(R.string.odontograma_esta_cita)
                        } else {
                            date
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Nueva observación

@Composable
fun ObservationFormScreen(viewModel: ObservationFormViewModel, onExit: () -> Unit, onDone: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val goBack = rememberFormBack(viewModel::onBack, onExit, onFinished = onDone)
    DiscardDraftDialog(state.confirmDiscard, onKeepEditing = viewModel::keepEditing) {
        viewModel.discardDraft()
        onExit()
    }

    BackScaffold(
        title = stringResource(if (state.step == FormStep.CONFIRM) R.string.odontograma_confirmar_titulo else R.string.odontograma_nueva_titulo),
        onBack = goBack,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (state.step) {
                FormStep.FORM -> ObservationForm(state, viewModel)
                FormStep.CONFIRM -> {
                    ClinicalCard(testTag = "observation_summary") {
                        SectionTitle(stringResource(R.string.atencion_resumen))
                        InfoRow(stringResource(R.string.odontograma_elige_pieza), state.tooth?.toString().orEmpty())
                        InfoRow(stringResource(R.string.odontograma_elige_superficie), state.surface?.let { stringResource(it.labelRes()) }.orEmpty())
                        InfoRow(stringResource(R.string.odontograma_observacion), state.observation.trim())
                    }
                    ImmutableNotice(stringResource(R.string.odontograma_inmutable), "observation_immutable_notice")
                    if (state.awaitingReauth) {
                        StatusBanner(
                            iconRes = R.drawable.ic_schedule,
                            title = stringResource(R.string.estado_sesion_vencida_titulo),
                            message = stringResource(R.string.atencion_esperando_sesion),
                            background = LocalStatusColors.current.warning,
                        )
                    }
                    state.error?.let { ErrorNotice(it, "observation_error") }
                    SubmitButton(
                        text = stringResource(R.string.odontograma_confirmar),
                        submitting = state.submitting,
                        onClick = viewModel::submit,
                        testTag = "observation_confirm",
                    )
                    TextButton(onClick = viewModel::backToForm, enabled = !state.submitting, modifier = Modifier.testTag("observation_edit")) {
                        Text(stringResource(R.string.atencion_volver_a_editar))
                    }
                    DebugSessionTools()
                }
                FormStep.DONE -> {
                    StatusBanner(
                        iconRes = R.drawable.ic_check_circle,
                        title = stringResource(R.string.estado_exito_titulo),
                        message = stringResource(R.string.odontograma_guardada),
                        background = LocalStatusColors.current.success,
                        modifier = Modifier.testTag("observation_done"),
                    )
                    Button(onClick = onDone, modifier = Modifier.fillMaxWidth().testTag("observation_back")) {
                        Text(stringResource(R.string.odontograma_volver))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ObservationForm(state: ObservationFormUiState, viewModel: ObservationFormViewModel) {
    state.error?.let { ErrorNotice(it, "observation_form_error") }

    Text(stringResource(R.string.odontograma_elige_pieza), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
    Text(
        state.tooth?.let { stringResource(R.string.odontograma_pieza_elegida, it) } ?: stringResource(R.string.odontograma_ninguna_pieza),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.testTag("observation_selected_tooth"),
    )
    FieldError(state.toothError, "observation_tooth_error")
    listOf(
        R.string.odontograma_permanente to FdiTeeth.permanentQuadrants,
        R.string.odontograma_temporal to FdiTeeth.temporaryQuadrants,
    ).forEach { (title, quadrants) ->
        Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
        quadrants.forEach { teeth ->
            Text(
                stringResource(R.string.odontograma_cuadrante, teeth.first() / 10),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                teeth.forEach { tooth ->
                    val description = stringResource(R.string.odontograma_pieza, tooth)
                    SelectableChip(
                        selected = state.tooth == tooth,
                        label = tooth.toString(),
                        onClick = { viewModel.selectTooth(tooth) },
                        modifier = Modifier.testTag("tooth_$tooth").semantics { contentDescription = description },
                    )
                }
            }
        }
    }

    Text(stringResource(R.string.odontograma_elige_superficie), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
    FieldError(state.surfaceError, "observation_surface_error")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        ToothSurface.entries.forEach { surface ->
            SelectableChip(
                selected = state.surface == surface,
                label = stringResource(surface.labelRes()),
                onClick = { viewModel.selectSurface(surface) },
                modifier = Modifier.testTag("surface_${surface.name.lowercase()}"),
            )
        }
    }

    ClinicalTextField(
        state.observation, viewModel::onObservationChange, stringResource(R.string.odontograma_observacion),
        ObservationFormViewModel.MAX, state.observationError, "observation_text",
    )
    Button(onClick = viewModel::review, modifier = Modifier.fillMaxWidth().testTag("observation_review")) {
        Text(stringResource(R.string.atencion_revisar))
    }
}

/** Chip con marca visible al elegirlo: el estado no depende solo del color. */
@Composable
private fun SelectableChip(selected: Boolean, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            { Icon(painterResource(R.drawable.ic_check_circle), contentDescription = null, modifier = Modifier.size(16.dp)) }
        } else {
            null
        },
        modifier = modifier,
    )
}

@Composable
private fun FieldError(message: UserMessage?, testTag: String) {
    val text = message?.asString() ?: return
    Text(
        text,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.testTag(testTag).semantics { error(text) },
    )
}
