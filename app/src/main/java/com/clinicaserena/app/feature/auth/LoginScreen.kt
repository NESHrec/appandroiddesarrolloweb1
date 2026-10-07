package com.clinicaserena.app.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clinicaserena.app.R
import com.clinicaserena.app.core.security.LogoutReason
import com.clinicaserena.app.core.ui.asString
import com.clinicaserena.app.core.ui.components.StatusBanner
import com.clinicaserena.app.core.ui.theme.LocalStatusColors
import com.clinicaserena.app.core.ui.toUserMessage

/**
 * Acceso. En la Fase A solo muestra el motivo de cierre de sesión y, en debug, el diagnóstico del
 * backend. El formulario de login unificado se implementa en la Fase B.
 */
@Composable
fun LoginScreen(
    reason: LogoutReason,
    diagnostics: (@Composable () -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.login_titulo),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.login_subtitulo),
            style = MaterialTheme.typography.bodyLarge,
        )

        when (reason) {
            LogoutReason.EXPIRED -> StatusBanner(
                iconRes = R.drawable.ic_schedule,
                title = stringResource(R.string.estado_sesion_vencida_titulo),
                message = stringResource(R.string.estado_sesion_vencida_mensaje),
                background = LocalStatusColors.current.warning,
            )
            LogoutReason.UNSUPPORTED_ROLE -> StatusBanner(
                iconRes = R.drawable.ic_lock,
                title = stringResource(R.string.estado_permiso_denegado_titulo),
                message = stringResource(R.string.login_rol_no_soportado),
                background = LocalStatusColors.current.error,
            )
            LogoutReason.NONE -> Unit
        }

        StatusBanner(
            iconRes = R.drawable.ic_info,
            title = stringResource(R.string.pendiente_titulo),
            message = stringResource(R.string.login_pendiente),
            background = LocalStatusColors.current.info,
        )

        diagnostics?.invoke()
    }
}

@Composable
fun DiagnosticsCard(viewModel: DiagnosticsViewModel, apiBaseUrl: String) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.diagnostico_titulo), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.diagnostico_url, apiBaseUrl), style = MaterialTheme.typography.bodySmall)
            when (val current = state) {
                HealthUiState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.diagnostico_verificando))
                }
                is HealthUiState.Up -> StatusBanner(
                    iconRes = R.drawable.ic_check_circle,
                    title = current.status,
                    message = stringResource(R.string.diagnostico_ok, current.service),
                    background = LocalStatusColors.current.success,
                )
                is HealthUiState.Down -> StatusBanner(
                    iconRes = R.drawable.ic_error,
                    title = stringResource(R.string.estado_error_titulo),
                    message = current.failure.toUserMessage().asString(),
                    background = LocalStatusColors.current.error,
                )
            }
            OutlinedButton(onClick = viewModel::refresh) { Text(stringResource(R.string.estado_reintentar)) }
        }
    }
}
