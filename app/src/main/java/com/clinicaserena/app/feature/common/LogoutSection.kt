package com.clinicaserena.app.feature.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clinicaserena.app.R
import com.clinicaserena.app.core.ui.LocalAppContainer
import com.clinicaserena.app.core.ui.asString
import com.clinicaserena.app.core.ui.components.LinkButton
import com.clinicaserena.app.core.ui.components.SecondaryButton
import com.clinicaserena.app.core.ui.components.StatusBanner
import com.clinicaserena.app.core.ui.theme.LocalStatusColors

/** Botón de cierre de sesión con el caso de servidor inalcanzable explicado sin simular éxito. */
@Composable
fun LogoutSection(modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val viewModel = viewModel { LogoutViewModel(container.sessionManager) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val failed = state as? LogoutUiState.Failed
        if (failed != null) {
            StatusBanner(
                iconRes = R.drawable.ic_error,
                title = stringResource(R.string.logout_fallo_titulo),
                message = failed.message.asString() + "\n" + stringResource(R.string.logout_fallo_explicacion),
                background = LocalStatusColors.current.error,
                modifier = Modifier.testTag("logout_failed"),
            )
        }
        SecondaryButton(
            text = stringResource(if (failed != null) R.string.estado_reintentar else R.string.logout_boton),
            onClick = viewModel::logout,
            loading = state == LogoutUiState.InProgress,
            iconRes = R.drawable.ic_logout,
            modifier = Modifier.testTag("logout_button"),
        )
        if (failed != null) {
            LinkButton(
                text = stringResource(R.string.logout_solo_local),
                onClick = viewModel::logoutLocalOnly,
                modifier = Modifier.fillMaxWidth().testTag("logout_local_only"),
            )
        }
    }
}
