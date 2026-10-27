package com.clinicaserena.app.feature.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.clinicaserena.app.R
import com.clinicaserena.app.core.ui.components.StatusPill
import com.clinicaserena.app.core.ui.components.StatusTone
import com.clinicaserena.app.domain.model.AppointmentStatus

/**
 * Estado de la cita: pastilla con punto de color y texto; el color es solo apoyo. Pendiente ámbar,
 * Confirmada verde, Completada turquesa y Cancelada rosa. [onGradient]: dentro de una tarjeta con degradado.
 */
@Composable
fun StatusChip(status: AppointmentStatus, raw: String, modifier: Modifier = Modifier, onGradient: Boolean = false) {
    val (tone, label) = when (status) {
        AppointmentStatus.PENDIENTE -> StatusTone.WARNING to stringResource(R.string.cita_estado_pendiente)
        AppointmentStatus.CONFIRMADA -> StatusTone.SUCCESS to stringResource(R.string.cita_estado_confirmada)
        AppointmentStatus.COMPLETADA -> StatusTone.TEAL to stringResource(R.string.cita_estado_completada)
        AppointmentStatus.CANCELADA -> StatusTone.ERROR to stringResource(R.string.cita_estado_cancelada)
        AppointmentStatus.DESCONOCIDO -> StatusTone.INFO to raw
    }
    StatusPill(text = label, tone = tone, modifier = modifier, onGradient = onGradient)
}
