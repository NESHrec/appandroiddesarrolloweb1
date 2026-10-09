package com.clinicaserena.app.feature.doctor.agenda

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.clinicaserena.app.R
import com.clinicaserena.app.core.network.ApiResult
import com.clinicaserena.app.core.time.ClinicTime
import com.clinicaserena.app.core.ui.FailureContent
import com.clinicaserena.app.core.ui.components.ForbiddenState
import com.clinicaserena.app.core.ui.components.StatusBanner
import com.clinicaserena.app.core.ui.theme.LocalStatusColors
import com.clinicaserena.app.domain.model.AttentionBlocker
import com.clinicaserena.app.domain.model.DoctorAppointment
import com.clinicaserena.app.feature.common.StatusChip

/** Fallos del médico: la vinculación pendiente y la cita ajena tienen su propio texto. */
@Composable
fun DoctorFailureContent(failure: ApiResult.Failure, onRetry: () -> Unit, scrollable: Boolean = true) {
    when {
        // La vinculación la hace administración: se ofrece volver a comprobar.
        failure is ApiResult.Forbidden && failure.code == "PRACTITIONER_LINK_REQUIRED" -> ForbiddenState(
            message = stringResource(R.string.vinculacion_pendiente_mensaje),
            scrollable = scrollable,
            onRetry = onRetry,
            modifier = Modifier.testTag("doctor_link_required"),
        )
        failure is ApiResult.NotFound && failure.code == "APPOINTMENT_NOT_FOUND" -> ForbiddenState(
            message = stringResource(R.string.medico_cita_no_encontrada),
            scrollable = scrollable,
            modifier = Modifier.testTag("doctor_appointment_not_found"),
        )
        else -> FailureContent(failure, onRetry = onRetry, scrollable = scrollable)
    }
}

/** Aviso de resultado posiblemente incompleto (Spring devuelve como máximo 100 citas y no pagina). */
@Composable
fun IncompleteResultBanner(modifier: Modifier = Modifier) {
    StatusBanner(
        iconRes = R.drawable.ic_warning,
        title = stringResource(R.string.medico_resultado_incompleto_titulo),
        message = stringResource(R.string.medico_resultado_incompleto_mensaje),
        background = LocalStatusColors.current.warning,
        modifier = modifier.testTag("agenda_incomplete"),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DoctorAppointmentCard(appointment: DoctorAppointment, onClick: () -> Unit, showDate: Boolean = false) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("doctor_appointment_${appointment.id}")
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.cita_ver_detalle), onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (showDate) ClinicTime.formatDateTime(appointment.scheduledAt) else ClinicTime.formatTime(appointment.scheduledAt),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                appointment.patientName ?: stringResource(R.string.medico_paciente_sin_nombre),
                style = MaterialTheme.typography.bodyLarge,
            )
            appointment.specialtyName?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(arrivalText(appointment), style = MaterialTheme.typography.bodyMedium)
            // FlowRow: con letra grande el segundo chip baja de línea en lugar de partir su texto.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusChip(appointment.status, appointment.statusRaw)
                if (appointment.canRecordAttention) ReadyChip()
            }
        }
    }
}

@Composable
fun arrivalText(appointment: DoctorAppointment): String =
    appointment.arrivalAt?.let { stringResource(R.string.medico_llegada_registrada, ClinicTime.formatTime(it)) }
        ?: stringResource(R.string.medico_llegada_sin_registrar)

/** Indicador con icono y texto (no solo color) de que la cita se puede documentar ahora. */
@Composable
fun ReadyChip() {
    val colors = LocalStatusColors.current
    Row(
        modifier = Modifier
            .background(colors.success, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_check_circle), contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.onStatus)
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.medico_lista_para_documentar), style = MaterialTheme.typography.labelLarge, color = colors.onStatus)
    }
}

/** Explicación de cada motivo por el que no se puede documentar, en el orden que envía Spring. */
@Composable
fun blockerText(blocker: AttentionBlocker, raw: String): String = when (blocker) {
    AttentionBlocker.ATTENTION_ALREADY_RECORDED -> stringResource(R.string.bloqueo_atencion_registrada)
    AttentionBlocker.STATUS_NOT_DOCUMENTABLE -> stringResource(R.string.bloqueo_estado_no_documentable)
    AttentionBlocker.NOT_STARTED -> stringResource(R.string.bloqueo_no_iniciada)
    AttentionBlocker.ARRIVAL_NOT_REGISTERED -> stringResource(R.string.bloqueo_sin_llegada)
    AttentionBlocker.DESCONOCIDO -> stringResource(R.string.bloqueo_desconocido, raw)
}
