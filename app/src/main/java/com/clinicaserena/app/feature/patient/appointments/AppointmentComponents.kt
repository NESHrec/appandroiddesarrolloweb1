package com.clinicaserena.app.feature.patient.appointments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clinicaserena.app.R
import com.clinicaserena.app.core.time.ClinicTime
import com.clinicaserena.app.core.ui.components.GradientCard
import com.clinicaserena.app.core.ui.components.GradientStrip
import com.clinicaserena.app.core.ui.components.GroupedRow
import com.clinicaserena.app.core.ui.components.PractitionerAvatar
import com.clinicaserena.app.core.ui.theme.SectionHeaderStyle
import com.clinicaserena.app.feature.common.StatusChip

/**
 * Próxima cita en Inicio: tarjeta con el degradado, la fecha, la hora grande y el médico en una franja.
 * Toda la tarjeta abre el detalle y se lee como una sola unidad.
 */
@Composable
fun NextAppointmentCard(item: AppointmentItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val appointment = item.appointment
    GradientCard(
        modifier = modifier.semantics(mergeDescendants = true) {},
        onClick = onClick,
        onClickLabel = stringResource(R.string.cita_ver_detalle),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.inicio_proxima_cita).uppercase(),
                style = SectionHeaderStyle,
                modifier = Modifier.weight(1f),
            )
            StatusChip(appointment.status, appointment.statusRaw, onGradient = true)
        }
        Text(
            ClinicTime.formatDay(appointment.scheduledAt).replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(ClinicTime.formatTime(appointment.scheduledAt), style = MaterialTheme.typography.displayMedium)
        GradientStrip(modifier = Modifier.padding(top = 8.dp)) {
            item.practitionerName?.let { PractitionerAvatar(name = it, photoUrl = item.practitionerPhotoUrl, size = 44.dp) }
            Column(Modifier.weight(1f)) {
                Text(
                    item.practitionerName ?: stringResource(R.string.cita_medico_desconocido),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    item.specialtyName ?: stringResource(R.string.cita_especialidad_desconocida),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/**
 * Fila de cita en la lista agrupada por fecha: avatar del médico, «hora · médico», especialidad,
 * pastilla de estado y chevron. Toda la fila abre el detalle.
 */
@Composable
fun AppointmentRow(item: AppointmentItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val appointment = item.appointment
    val doctor = item.practitionerName ?: stringResource(R.string.cita_medico_desconocido)
    // Con letra grande la pastilla baja debajo del texto para no apretar el nombre del médico.
    val largeText = LocalDensity.current.fontScale >= 1.3f
    GroupedRow(
        modifier = modifier,
        onClick = onClick,
        onClickLabel = stringResource(R.string.cita_ver_detalle),
        testTag = "appointment_${appointment.id}",
        leading = item.practitionerName?.let { name -> { PractitionerAvatar(name = name, photoUrl = item.practitionerPhotoUrl, size = 44.dp) } },
        trailing = if (largeText) null else { { StatusChip(appointment.status, appointment.statusRaw) } },
    ) {
        Text(
            "${ClinicTime.formatTime(appointment.scheduledAt)} · $doctor",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            item.specialtyName ?: stringResource(R.string.cita_especialidad_desconocida),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (largeText) StatusChip(appointment.status, appointment.statusRaw, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Día (en Guatemala) por el que se agrupan las citas en la lista. */
fun appointmentDayKey(item: AppointmentItem) = ClinicTime.localDate(item.appointment.scheduledAt)
