package com.clinicaserena.app.feature.patient.appointments

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.clinicaserena.app.core.time.ClinicTime
import com.clinicaserena.app.core.ui.theme.LocalStatusColors
import com.clinicaserena.app.feature.common.StatusChip

@Composable
fun AppointmentCard(item: AppointmentItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val appointment = item.appointment
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("appointment_${appointment.id}")
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.cita_ver_detalle), onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                ClinicTime.formatDateTime(appointment.scheduledAt),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                item.specialtyName ?: stringResource(R.string.cita_especialidad_desconocida),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                item.practitionerName ?: stringResource(R.string.cita_medico_desconocido),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StatusChip(appointment.status, appointment.statusRaw)
        }
    }
}
