package com.clinicaserena.app.feature.clinical

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.clinicaserena.app.R
import com.clinicaserena.app.core.time.ClinicTime
import com.clinicaserena.app.domain.model.Attention
import com.clinicaserena.app.domain.model.ClinicalProfile
import com.clinicaserena.app.domain.model.PrescriptionItem
import com.clinicaserena.app.feature.common.InfoRow

/*
 * Componentes del expediente compartidos por el paciente (solo lectura) y el médico: receta,
 * perfil clínico y atención con sus adendas. Solo muestran nombres, nunca IDs de cuentas.
 */

/** Ítems en orden: medicamento, dosis, frecuencia, duración e instrucciones. */
@Composable
fun PrescriptionItems(items: List<PrescriptionItem>) {
    if (items.isEmpty()) {
        Text(stringResource(R.string.receta_sin_medicamentos), style = MaterialTheme.typography.bodyMedium)
        return
    }
    items.forEachIndexed { index, item ->
        if (index > 0) HorizontalDivider()
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text("${item.order}. ${item.medicine}", style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.receta_item_detalle, item.dose, item.frequency, item.duration),
                style = MaterialTheme.typography.bodyMedium,
            )
            item.instructions?.takeIf { it.isNotBlank() }?.let {
                Text(
                    stringResource(R.string.receta_instrucciones, it),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun ProfileFields(profile: ClinicalProfile) {
    val none = stringResource(R.string.expediente_sin_dato)
    InfoRow(stringResource(R.string.expediente_alergias), profile.allergies ?: none)
    InfoRow(stringResource(R.string.expediente_condiciones), profile.relevantConditions ?: none)
    InfoRow(stringResource(R.string.expediente_medicamentos_actuales), profile.currentMedications ?: none)
    InfoRow(stringResource(R.string.expediente_antecedentes_odontologicos), profile.dentalHistory ?: none)
    Text(
        stringResource(
            R.string.expediente_registrado_por,
            ClinicTime.formatDateTime(profile.recordedAt),
            profile.authorName ?: stringResource(R.string.cita_medico_desconocido),
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun AttentionCard(attention: Attention) {
    ClinicalCard(testTag = "attention_${attention.id}") {
        SectionTitle(
            stringResource(
                R.string.expediente_atencion_del,
                ClinicTime.formatDateTime(attention.appointmentScheduledAt ?: attention.recordedAt),
            ),
        )
        attention.practitionerName?.let { InfoRow(stringResource(R.string.cita_medico), it) }
        InfoRow(stringResource(R.string.expediente_motivo), attention.reason)
        attention.findings?.let { InfoRow(stringResource(R.string.expediente_hallazgos), it) }
        InfoRow(stringResource(R.string.expediente_diagnostico), attention.diagnosis)
        attention.treatmentPlan?.let { InfoRow(stringResource(R.string.expediente_plan), it) }
        Text(stringResource(R.string.expediente_receta), style = MaterialTheme.typography.titleSmall)
        PrescriptionItems(attention.prescription)
        if (attention.addenda.isNotEmpty()) {
            HorizontalDivider()
            Text(stringResource(R.string.expediente_adendas), style = MaterialTheme.typography.titleSmall)
            attention.addenda.forEach { addendum ->
                Column(Modifier.padding(vertical = 4.dp)) {
                    Text(addendum.text, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.expediente_adenda_motivo, addendum.reason),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        stringResource(
                            R.string.expediente_registrado_por,
                            ClinicTime.formatDateTime(addendum.recordedAt),
                            addendum.authorName ?: stringResource(R.string.cita_medico_desconocido),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Text(
            stringResource(
                R.string.expediente_registrado_por,
                ClinicTime.formatDateTime(attention.recordedAt),
                attention.authorName ?: attention.practitionerName ?: stringResource(R.string.cita_medico_desconocido),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}


@Composable
fun ClinicalCard(testTag: String? = null, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { content() }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
}
