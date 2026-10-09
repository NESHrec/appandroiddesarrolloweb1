package com.clinicaserena.app.feature.patient.record

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clinicaserena.app.R
import com.clinicaserena.app.core.time.ClinicTime
import com.clinicaserena.app.core.ui.FailureContent
import com.clinicaserena.app.core.ui.LoadState
import com.clinicaserena.app.core.ui.components.EmptyState
import com.clinicaserena.app.core.ui.components.LoadingState
import com.clinicaserena.app.core.ui.components.StatusBanner
import com.clinicaserena.app.core.ui.theme.LocalStatusColors
import com.clinicaserena.app.domain.model.Attention
import com.clinicaserena.app.domain.model.Checkup
import com.clinicaserena.app.domain.model.ClinicalProfile
import com.clinicaserena.app.domain.model.ClinicalRecord
import com.clinicaserena.app.domain.model.Prescription
import com.clinicaserena.app.domain.model.PrescriptionItem
import com.clinicaserena.app.feature.clinical.AttentionCard
import com.clinicaserena.app.feature.clinical.ClinicalCard
import com.clinicaserena.app.feature.clinical.PrescriptionItems
import com.clinicaserena.app.feature.clinical.ProfileFields
import com.clinicaserena.app.feature.clinical.SectionTitle
import com.clinicaserena.app.feature.common.InfoRow

// ---------------------------------------------------------------- Recetas

@Composable
fun PrescriptionsScreen(viewModel: PrescriptionsViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (val load = state) {
        LoadState.Loading -> LoadingState()
        is LoadState.Failed -> FailureContent(load.failure, onRetry = viewModel::load)
        is LoadState.Loaded -> if (load.data.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.recetas_vacio_titulo),
                message = stringResource(R.string.recetas_vacio_mensaje),
                modifier = Modifier.testTag("prescriptions_empty"),
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize().testTag("prescriptions_list"),
            ) {
                item(key = "actualizar") {
                    OutlinedButton(onClick = viewModel::load) { Text(stringResource(R.string.perfil_actualizar)) }
                }
                items(load.data, key = { it.id }) { PrescriptionCard(it) }
            }
        }
    }
}

@Composable
private fun PrescriptionCard(prescription: Prescription) {
    ClinicalCard(testTag = "prescription_${prescription.id}") {
        Text(
            stringResource(R.string.receta_emitida, ClinicTime.formatDateTime(prescription.issuedAt)),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        prescription.practitionerName?.let { InfoRow(stringResource(R.string.cita_medico), it) }
        prescription.appointmentScheduledAt?.let {
            InfoRow(stringResource(R.string.receta_cita), ClinicTime.formatDateTime(it))
        }
        PrescriptionItems(prescription.items)
    }
}

// ---------------------------------------------------------------- Mi expediente

@Composable
fun MyRecordScreen(viewModel: MyRecordViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        // Pestañas desplazables: con fuente grande cada etiqueta se lee completa en vez de recortarse.
        PrimaryScrollableTabRow(
            selectedTabIndex = state.tab.ordinal,
            containerColor = MaterialTheme.colorScheme.background,
            edgePadding = 0.dp,
        ) {
            listOf(
                RecordTab.SUMMARY to R.string.expediente_tab_resumen,
                RecordTab.ATTENTIONS to R.string.expediente_tab_atenciones,
                RecordTab.CHECKUPS to R.string.expediente_tab_seguimientos,
            ).forEach { (tab, label) ->
                Tab(
                    selected = state.tab == tab,
                    onClick = { viewModel.selectTab(tab) },
                    text = { Text(stringResource(label), maxLines = 1) },
                    modifier = Modifier.testTag("record_tab_${tab.name}"),
                )
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().testTag("record_content"),
        ) {
            item(key = "solo-lectura") {
                StatusBanner(
                    iconRes = R.drawable.ic_info,
                    title = stringResource(R.string.expediente_solo_lectura_titulo),
                    message = stringResource(R.string.expediente_solo_lectura_mensaje),
                    background = LocalStatusColors.current.info,
                )
            }
            when (state.tab) {
                RecordTab.SUMMARY -> recordSection(state.record, viewModel::load) { summary(it) }
                RecordTab.ATTENTIONS -> recordSection(state.record, viewModel::load) { attentions(it) }
                RecordTab.CHECKUPS -> checkups(state.checkups, viewModel::load)
            }
        }
    }
}

private fun LazyListScope.recordSection(
    load: LoadState<ClinicalRecord>,
    onRetry: () -> Unit,
    content: LazyListScope.(ClinicalRecord) -> Unit,
) {
    when (load) {
        LoadState.Loading -> item(key = "cargando") { LoadingState(modifier = Modifier.padding(vertical = 32.dp)) }
        is LoadState.Failed -> item(key = "error") { FailureContent(load.failure, onRetry = onRetry, scrollable = false) }
        is LoadState.Loaded -> content(load.data)
    }
}

private fun LazyListScope.summary(record: ClinicalRecord) {
    if (record.recordId == null && record.profile == null && record.attentions.isEmpty()) {
        item(key = "sin-expediente") {
            EmptyState(
                title = stringResource(R.string.expediente_vacio_titulo),
                message = stringResource(R.string.expediente_vacio_mensaje),
                scrollable = false,
                modifier = Modifier.testTag("record_empty"),
            )
        }
        return
    }
    item(key = "perfil") {
        val profile = record.profile
        if (profile == null) {
            EmptyState(message = stringResource(R.string.expediente_sin_perfil), scrollable = false)
        } else {
            ClinicalCard(testTag = "record_profile") {
                SectionTitle(stringResource(R.string.expediente_perfil_vigente))
                ProfileFields(profile)
            }
        }
    }
    val previous = record.profileHistory.drop(if (record.profile != null) 1 else 0)
    if (previous.isNotEmpty()) {
        item(key = "historial-titulo") { SectionTitle(stringResource(R.string.expediente_versiones_anteriores)) }
        items(previous.size) { index -> ClinicalCard { ProfileFields(previous[index]) } }
    }
}

private fun LazyListScope.attentions(record: ClinicalRecord) {
    if (record.attentions.isEmpty()) {
        item(key = "sin-atenciones") {
            EmptyState(message = stringResource(R.string.expediente_sin_atenciones), scrollable = false)
        }
        return
    }
    items(record.attentions, key = { it.id }) { AttentionCard(it) }
}

private fun LazyListScope.checkups(load: LoadState<List<Checkup>>, onRetry: () -> Unit) {
    when (load) {
        LoadState.Loading -> item(key = "cargando") { LoadingState(modifier = Modifier.padding(vertical = 32.dp)) }
        is LoadState.Failed -> item(key = "error") { FailureContent(load.failure, onRetry = onRetry, scrollable = false) }
        is LoadState.Loaded -> if (load.data.isEmpty()) {
            item(key = "sin-seguimientos") {
                EmptyState(
                    message = stringResource(R.string.expediente_sin_seguimientos),
                    scrollable = false,
                    modifier = Modifier.testTag("checkups_empty"),
                )
            }
        } else {
            items(load.data, key = { it.id }) { checkup ->
                ClinicalCard(testTag = "checkup_${checkup.id}") {
                    SectionTitle(ClinicTime.formatDateTime(checkup.appointmentScheduledAt ?: checkup.recordedAt))
                    checkup.practitionerName?.let { InfoRow(stringResource(R.string.cita_medico), it) }
                    InfoRow(stringResource(R.string.expediente_motivo), checkup.reason)
                    InfoRow(stringResource(R.string.expediente_diagnostico), checkup.diagnosis)
                    checkup.treatmentPlan?.let { InfoRow(stringResource(R.string.expediente_plan), it) }
                }
            }
        }
    }
}
