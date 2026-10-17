package com.clinicaserena.app.feature.doctor.agenda

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clinicaserena.app.R
import com.clinicaserena.app.core.network.ApiResult
import com.clinicaserena.app.core.time.ClinicTime
import com.clinicaserena.app.core.ui.FailureContent
import com.clinicaserena.app.core.ui.components.ForbiddenState
import com.clinicaserena.app.core.ui.components.GroupedRow
import com.clinicaserena.app.core.ui.components.InitialsAvatar
import com.clinicaserena.app.core.ui.components.StatusBanner
import com.clinicaserena.app.core.ui.components.StatusPill
import com.clinicaserena.app.core.ui.components.StatusTone
import com.clinicaserena.app.core.ui.theme.GradienteListoFin
import com.clinicaserena.app.core.ui.theme.GradienteListoInicio
import com.clinicaserena.app.core.ui.theme.LocalStatusColors
import com.clinicaserena.app.domain.model.AttentionBlocker
import com.clinicaserena.app.domain.model.DoctorAppointment
import com.clinicaserena.app.feature.common.StatusChip
import java.time.format.DateTimeFormatter
import java.util.Locale

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
        failure is ApiResult.NotFound && failure.code == "PATIENT_NOT_FOUND" -> ForbiddenState(
            message = stringResource(R.string.odontograma_paciente_no_encontrado),
            scrollable = scrollable,
            modifier = Modifier.testTag("doctor_patient_not_found"),
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

/**
 * Cita en una lista agrupada (Agenda): avatar del paciente, «hora · paciente», especialidad y llegada,
 * estado y chevron. Con [timeColumn] (Próximas citas del Inicio) la hora y el día van en una columna a la
 * izquierda. Con dos pastillas o letra grande, el estado baja debajo del texto.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DoctorAppointmentRow(appointment: DoctorAppointment, onClick: () -> Unit, timeColumn: Boolean = false) {
    val patient = appointment.patientName ?: stringResource(R.string.medico_paciente_sin_nombre)
    // A la derecha solo cabe una pastilla con letra normal; con dos (o letra grande) bajan debajo del texto.
    val pillsBelow = appointment.canRecordAttention || LocalDensity.current.fontScale >= 1.3f
    val pills: @Composable () -> Unit = {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusChip(appointment.status, appointment.statusRaw)
            if (appointment.canRecordAttention) ReadyChip()
        }
    }
    GroupedRow(
        onClick = onClick,
        onClickLabel = stringResource(R.string.cita_ver_detalle),
        testTag = "doctor_appointment_${appointment.id}",
        leading = {
            if (timeColumn) {
                Column(Modifier.widthIn(min = 56.dp)) {
                    Text(ClinicTime.formatTime(appointment.scheduledAt), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(shortDay(appointment), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                InitialsAvatar(appointment.patientName, 44.dp)
            }
        },
        trailing = if (pillsBelow) null else pills,
        showChevron = true,
    ) {
        Text(
            if (timeColumn) patient else "${ClinicTime.formatTime(appointment.scheduledAt)} · $patient",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        appointment.specialtyName?.takeIf { !timeColumn }?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(arrivalText(appointment), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (pillsBelow) Box(Modifier.padding(top = 4.dp)) { pills() }
    }
}

/**
 * Cita lista para documentar (Inicio): check en degradado verde → turquesa (blanco ≥ 3.3:1), paciente,
 * fecha y hora, y la pastilla violeta de llegada.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReadyAppointmentRow(appointment: DoctorAppointment, onClick: () -> Unit) {
    GroupedRow(
        onClick = onClick,
        onClickLabel = stringResource(R.string.cita_ver_detalle),
        testTag = "doctor_appointment_${appointment.id}",
        leading = {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(Brush.linearGradient(listOf(GradienteListoInicio, GradienteListoFin))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_check_circle), contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
        },
    ) {
        Text(
            appointment.patientName ?: stringResource(R.string.medico_paciente_sin_nombre),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "${shortDay(appointment)} · ${ClinicTime.formatTime(appointment.scheduledAt)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 4.dp),
        ) {
            appointment.arrivalAt?.let { ArrivalPill(it) }
            ReadyChip()
        }
    }
}

/** Llegada registrada: pastilla violeta con la hora (texto, no solo color). */
@Composable
fun ArrivalPill(arrivalAt: java.time.OffsetDateTime, modifier: Modifier = Modifier) {
    StatusPill(
        text = stringResource(R.string.medico_llegada_registrada, ClinicTime.formatTime(arrivalAt)),
        tone = StatusTone.VIOLET,
        modifier = modifier,
    )
}

/** Día corto en Guatemala, por ejemplo «Sáb 17». */
fun shortDay(appointment: DoctorAppointment): String =
    ClinicTime.localDate(appointment.scheduledAt).format(ShortDay).replace(".", "").replaceFirstChar { it.uppercase() }

private val ShortDay = DateTimeFormatter.ofPattern("EEE d", Locale.forLanguageTag("es-GT"))

@Composable
fun arrivalText(appointment: DoctorAppointment): String =
    appointment.arrivalAt?.let { stringResource(R.string.medico_llegada_registrada, ClinicTime.formatTime(it)) }
        ?: stringResource(R.string.medico_llegada_sin_registrar)

/** Indicador con icono y texto (no solo color) de que la cita se puede documentar ahora. */
@Composable
fun ReadyChip() {
    StatusPill(text = stringResource(R.string.medico_lista_para_documentar), tone = StatusTone.SUCCESS, iconRes = R.drawable.ic_check_circle)
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
