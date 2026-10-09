package com.clinicaserena.app.feature.doctor.care

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clinicaserena.app.R
import com.clinicaserena.app.core.debug.DebugSessionTools
import com.clinicaserena.app.core.ui.LoadState
import com.clinicaserena.app.core.ui.UserMessage
import com.clinicaserena.app.core.ui.asString
import com.clinicaserena.app.core.ui.components.EmptyState
import com.clinicaserena.app.core.ui.components.LoadingState
import com.clinicaserena.app.core.ui.components.StatusBanner
import com.clinicaserena.app.core.ui.theme.LocalStatusColors
import com.clinicaserena.app.domain.model.PrescriptionItem
import com.clinicaserena.app.feature.auth.SubmitButton
import com.clinicaserena.app.feature.clinical.AttentionCard
import com.clinicaserena.app.feature.clinical.ClinicalCard
import com.clinicaserena.app.feature.clinical.PrescriptionItems
import com.clinicaserena.app.feature.clinical.ProfileFields
import com.clinicaserena.app.feature.clinical.SectionTitle
import com.clinicaserena.app.feature.common.BackScaffold
import com.clinicaserena.app.feature.common.InfoRow
import com.clinicaserena.app.feature.doctor.agenda.DoctorFailureContent

/** Campo de texto clínico multilínea con etiqueta visible, error y contador. */
@Composable
fun ClinicalTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    max: Int,
    error: UserMessage?,
    testTag: String,
    enabled: Boolean = true,
    singleLine: Boolean = false,
) {
    val errorText = error?.asString()
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = { Text(errorText ?: stringResource(R.string.reserva_notas_contador, value.length, max)) },
        singleLine = singleLine,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (singleLine) Modifier else Modifier.heightIn(min = 96.dp))
            .testTag(testTag)
            .semantics { if (errorText != null) error(errorText) },
    )
}

@Composable
private fun ImmutableNotice(message: String, testTag: String) {
    StatusBanner(
        iconRes = R.drawable.ic_lock,
        title = stringResource(R.string.atencion_inmutable_titulo),
        message = message,
        background = LocalStatusColors.current.warning,
        modifier = Modifier.testTag(testTag),
    )
}

@Composable
private fun ErrorNotice(message: UserMessage, testTag: String) {
    StatusBanner(
        iconRes = R.drawable.ic_error,
        title = stringResource(R.string.atencion_no_registrada_titulo),
        message = message.asString(),
        background = LocalStatusColors.current.error,
        modifier = Modifier.testTag(testTag),
    )
}

// ---------------------------------------------------------------- Atención

@Composable
fun AttentionFormScreen(viewModel: AttentionFormViewModel, onExit: () -> Unit, onDone: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val goBack = {
        when (state.step) {
            FormStep.CONFIRM -> viewModel.backToForm()
            FormStep.DONE -> onDone()
            FormStep.FORM -> onExit()
        }
    }
    BackHandler(onBack = goBack)

    BackScaffold(
        title = stringResource(
            when (state.step) {
                FormStep.FORM -> R.string.medico_registrar_atencion
                FormStep.CONFIRM -> R.string.atencion_confirmar_titulo
                FormStep.DONE -> R.string.atencion_registrada_titulo
            },
        ),
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
                FormStep.FORM -> AttentionForm(state, viewModel)
                FormStep.CONFIRM -> AttentionConfirm(state, viewModel, onExit = onDone)
                FormStep.DONE -> {
                    StatusBanner(
                        iconRes = R.drawable.ic_check_circle,
                        title = stringResource(R.string.atencion_registrada_titulo),
                        message = stringResource(R.string.atencion_registrada_mensaje),
                        background = LocalStatusColors.current.success,
                        modifier = Modifier.testTag("attention_done"),
                    )
                    state.recorded?.let { AttentionCard(it) }
                    Button(onClick = onDone, modifier = Modifier.fillMaxWidth().testTag("attention_back_to_appointment")) {
                        Text(stringResource(R.string.atencion_volver_a_la_cita))
                    }
                }
            }
        }
    }
}

@Composable
private fun AttentionForm(state: AttentionFormUiState, viewModel: AttentionFormViewModel) {
    val e = state.fieldErrors
    state.error?.let { ErrorNotice(it, "attention_form_error") }
    ClinicalTextField(state.reason, viewModel::onReasonChange, stringResource(R.string.expediente_motivo), 1000, e["reason"], "attention_reason")
    ClinicalTextField(state.findings, viewModel::onFindingsChange, stringResource(R.string.atencion_hallazgos_opcional), 2000, e["findings"], "attention_findings")
    ClinicalTextField(state.diagnosis, viewModel::onDiagnosisChange, stringResource(R.string.expediente_diagnostico), 1000, e["diagnosis"], "attention_diagnosis")
    ClinicalTextField(state.treatmentPlan, viewModel::onTreatmentPlanChange, stringResource(R.string.atencion_plan_opcional), 2000, e["treatmentPlan"], "attention_plan")

    Text(stringResource(R.string.atencion_receta_titulo, state.items.size, AttentionFormViewModel.MAX_ITEMS), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
    if (state.items.isEmpty()) Text(stringResource(R.string.atencion_sin_receta), style = MaterialTheme.typography.bodyMedium)
    state.items.forEachIndexed { index, item ->
        Card(
            modifier = Modifier.fillMaxWidth().testTag("attention_item_$index"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.atencion_item_numero, index + 1), style = MaterialTheme.typography.titleSmall)
                ItemField.entries.forEach { field ->
                    val (label, max, value) = when (field) {
                        ItemField.MEDICINE -> Triple(R.string.atencion_item_medicamento, 160, item.medicine)
                        ItemField.DOSE -> Triple(R.string.atencion_item_dosis, 80, item.dose)
                        ItemField.FREQUENCY -> Triple(R.string.atencion_item_frecuencia, 80, item.frequency)
                        ItemField.DURATION -> Triple(R.string.atencion_item_duracion, 80, item.duration)
                        ItemField.INSTRUCTIONS -> Triple(R.string.atencion_item_indicaciones, 500, item.instructions)
                    }
                    ClinicalTextField(
                        value = value,
                        onValueChange = { viewModel.onItemChange(index, field, it) },
                        label = stringResource(label),
                        max = max,
                        error = e[AttentionFormViewModel.itemKey(index, field)],
                        testTag = "attention_item_${index}_${field.name.lowercase()}",
                        singleLine = field != ItemField.INSTRUCTIONS,
                    )
                }
                TextButton(onClick = { viewModel.removeItem(index) }, modifier = Modifier.testTag("attention_item_${index}_remove")) {
                    Text(stringResource(R.string.atencion_quitar_item))
                }
            }
        }
    }
    OutlinedButton(
        onClick = viewModel::addItem,
        enabled = state.items.size < AttentionFormViewModel.MAX_ITEMS,
        modifier = Modifier.fillMaxWidth().testTag("attention_add_item"),
    ) { Text(stringResource(R.string.atencion_agregar_item)) }
    Button(onClick = viewModel::review, modifier = Modifier.fillMaxWidth().testTag("attention_review")) {
        Text(stringResource(R.string.atencion_revisar))
    }
}

@Composable
private fun AttentionConfirm(state: AttentionFormUiState, viewModel: AttentionFormViewModel, onExit: () -> Unit) {
    val request = state.toRequest()
    ClinicalCard(testTag = "attention_summary") {
        SectionTitle(stringResource(R.string.atencion_resumen))
        InfoRow(stringResource(R.string.expediente_motivo), request.reason)
        request.findings?.let { InfoRow(stringResource(R.string.expediente_hallazgos), it) }
        InfoRow(stringResource(R.string.expediente_diagnostico), request.diagnosis)
        request.treatmentPlan?.let { InfoRow(stringResource(R.string.expediente_plan), it) }
        Text(stringResource(R.string.expediente_receta), style = MaterialTheme.typography.titleSmall)
        PrescriptionItems(request.prescription.mapIndexed { i, it -> PrescriptionItem(i + 1, it.medicine, it.dose, it.frequency, it.duration, it.instructions) })
    }
    ImmutableNotice(stringResource(R.string.atencion_inmutable_mensaje), "attention_immutable_notice")
    StatusBanner(
        iconRes = R.drawable.ic_warning,
        title = stringResource(R.string.atencion_odontograma_titulo),
        message = stringResource(R.string.atencion_aviso_odontograma_despues),
        background = LocalStatusColors.current.warning,
        modifier = Modifier.testTag("attention_odontogram_notice"),
    )
    if (state.awaitingReauth) {
        StatusBanner(
            iconRes = R.drawable.ic_schedule,
            title = stringResource(R.string.estado_sesion_vencida_titulo),
            message = stringResource(R.string.atencion_esperando_sesion),
            background = LocalStatusColors.current.warning,
        )
    }
    state.error?.let { ErrorNotice(it, "attention_error") }
    if (state.alreadyRecorded) {
        Button(onClick = onExit, modifier = Modifier.fillMaxWidth().testTag("attention_view_existing")) {
            Text(stringResource(R.string.atencion_ver_existente))
        }
    } else {
        // Deshabilitado mientras se envía: un doble toque no crea dos atenciones.
        SubmitButton(
            text = stringResource(R.string.atencion_confirmar),
            submitting = state.submitting,
            onClick = viewModel::submit,
            testTag = "attention_confirm",
        )
    }
    Row {
        TextButton(onClick = viewModel::backToForm, enabled = !state.submitting, modifier = Modifier.testTag("attention_edit")) {
            Text(stringResource(R.string.atencion_volver_a_editar))
        }
    }
    DebugSessionTools()
}

// ---------------------------------------------------------------- Perfil clínico

@Composable
fun ClinicalProfileFormScreen(viewModel: ClinicalProfileFormViewModel, onExit: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val goBack = { if (state.step == FormStep.CONFIRM) viewModel.backToForm() else onExit() }
    BackHandler(onBack = goBack)
    val labels = mapOf(
        ProfileField.ALLERGIES to R.string.expediente_alergias,
        ProfileField.CONDITIONS to R.string.expediente_condiciones,
        ProfileField.MEDICATIONS to R.string.expediente_medicamentos_actuales,
        ProfileField.DENTAL_HISTORY to R.string.expediente_antecedentes_odontologicos,
    )

    BackScaffold(title = stringResource(R.string.perfil_clinico_titulo), onBack = goBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val values = mapOf(
                ProfileField.ALLERGIES to state.allergies,
                ProfileField.CONDITIONS to state.relevantConditions,
                ProfileField.MEDICATIONS to state.currentMedications,
                ProfileField.DENTAL_HISTORY to state.dentalHistory,
            )
            when (state.step) {
                FormStep.FORM -> {
                    Text(stringResource(R.string.perfil_clinico_explicacion), style = MaterialTheme.typography.bodyMedium)
                    state.error?.let { ErrorNotice(it, "profile_form_error") }
                    ProfileField.entries.forEach { field ->
                        ClinicalTextField(
                            value = values.getValue(field),
                            onValueChange = { viewModel.onChange(field, it) },
                            label = stringResource(labels.getValue(field)),
                            max = ClinicalProfileFormViewModel.MAX,
                            error = state.fieldErrors[field],
                            testTag = "profile_${field.name.lowercase()}",
                        )
                    }
                    Button(onClick = viewModel::review, modifier = Modifier.fillMaxWidth().testTag("profile_review")) {
                        Text(stringResource(R.string.atencion_revisar))
                    }
                }
                FormStep.CONFIRM -> {
                    val none = stringResource(R.string.expediente_sin_dato)
                    ClinicalCard(testTag = "profile_summary") {
                        SectionTitle(stringResource(R.string.atencion_resumen))
                        ProfileField.entries.forEach { field ->
                            InfoRow(stringResource(labels.getValue(field)), values.getValue(field).trim().ifEmpty { none })
                        }
                    }
                    ImmutableNotice(stringResource(R.string.perfil_clinico_inmutable), "profile_immutable_notice")
                    if (state.awaitingReauth) {
                        StatusBanner(
                            iconRes = R.drawable.ic_schedule,
                            title = stringResource(R.string.estado_sesion_vencida_titulo),
                            message = stringResource(R.string.atencion_esperando_sesion),
                            background = LocalStatusColors.current.warning,
                        )
                    }
                    state.error?.let { ErrorNotice(it, "profile_error") }
                    SubmitButton(
                        text = stringResource(R.string.perfil_clinico_confirmar),
                        submitting = state.submitting,
                        onClick = viewModel::submit,
                        testTag = "profile_confirm",
                    )
                    TextButton(onClick = viewModel::backToForm, enabled = !state.submitting) {
                        Text(stringResource(R.string.atencion_volver_a_editar))
                    }
                    DebugSessionTools()
                }
                FormStep.DONE -> {
                    StatusBanner(
                        iconRes = R.drawable.ic_check_circle,
                        title = stringResource(R.string.estado_exito_titulo),
                        message = stringResource(R.string.perfil_clinico_guardado),
                        background = LocalStatusColors.current.success,
                        modifier = Modifier.testTag("profile_done"),
                    )
                    state.saved?.let { ClinicalCard { ProfileFields(it) } }
                    Button(onClick = onExit, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.perfil_clinico_volver))
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Expediente (médico)

@Composable
fun DoctorRecordScreen(viewModel: DoctorRecordViewModel, onBack: () -> Unit, onNewProfile: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    BackScaffold(title = stringResource(R.string.medico_ver_expediente), onBack = onBack) { padding ->
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
                    val record = load.data
                    record.patientName?.let { InfoRow(stringResource(R.string.medico_paciente), it) }
                    OutlinedButton(onClick = onNewProfile, modifier = Modifier.fillMaxWidth().testTag("record_new_profile")) {
                        Text(stringResource(R.string.perfil_clinico_nueva_version))
                    }
                    SectionTitle(stringResource(R.string.expediente_perfil_vigente))
                    val profile = record.profile
                    if (profile == null) {
                        EmptyState(message = stringResource(R.string.expediente_sin_perfil), scrollable = false)
                    } else {
                        ClinicalCard(testTag = "doctor_record_profile") { ProfileFields(profile) }
                    }
                    val previous = record.profileHistory.drop(if (profile != null) 1 else 0)
                    if (previous.isNotEmpty()) {
                        SectionTitle(stringResource(R.string.expediente_versiones_anteriores))
                        previous.forEach { ClinicalCard { ProfileFields(it) } }
                    }
                    SectionTitle(stringResource(R.string.expediente_tab_atenciones))
                    if (record.attentions.isEmpty()) {
                        Text(stringResource(R.string.expediente_sin_atenciones))
                    } else {
                        record.attentions.forEach { AttentionCard(it) }
                    }
                }
            }
        }
    }
}
