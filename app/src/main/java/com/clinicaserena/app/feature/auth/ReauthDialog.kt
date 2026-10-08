package com.clinicaserena.app.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clinicaserena.app.R
import com.clinicaserena.app.core.ui.LocalAppContainer
import com.clinicaserena.app.core.ui.asString
import com.clinicaserena.app.core.ui.components.PasswordField
import com.clinicaserena.app.core.ui.components.StatusBanner
import com.clinicaserena.app.core.ui.theme.LocalStatusColors
import kotlinx.coroutines.launch

/**
 * Sesión vencida con la app en uso: se pide la contraseña sobre la pantalla actual, que sigue
 * compuesta con sus borradores. No se puede descartar sin elegir Continuar o Cerrar sesión.
 */
@Composable
fun ReauthDialog(email: String) {
    val container = LocalAppContainer.current
    val viewModel = viewModel(key = "reauth") { LoginViewModel(container.sessionManager, lockedEmail = email) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Card(
            // El Dialog es otra ventana: necesita su propio testTagsAsResourceId para `uiautomator`.
            modifier = Modifier
                .fillMaxWidth()
                .semantics { testTagsAsResourceId = true }
                .testTag("reauth_dialog"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_schedule), contentDescription = null, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.estado_sesion_vencida_titulo),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() },
                    )
                }
                Text(stringResource(R.string.reauth_mensaje), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.reauth_cuenta, email), style = MaterialTheme.typography.bodySmall)
                PasswordField(
                    value = state.password,
                    onValueChange = viewModel::onPasswordChange,
                    label = stringResource(R.string.campo_contrasena),
                    visible = state.passwordVisible,
                    onToggleVisible = viewModel::togglePasswordVisible,
                    error = state.passwordError?.let { stringResource(it) },
                    testTag = "reauth_password",
                    enabled = !state.submitting,
                    onImeAction = viewModel::submit,
                )
                state.message?.let {
                    StatusBanner(
                        iconRes = R.drawable.ic_error,
                        title = stringResource(R.string.estado_error_titulo),
                        message = it.asString(),
                        background = LocalStatusColors.current.error,
                    )
                }
                SubmitButton(
                    text = stringResource(R.string.reauth_continuar),
                    submitting = state.submitting,
                    onClick = viewModel::submit,
                    testTag = "reauth_submit",
                )
                TextButton(
                    onClick = { scope.launch { container.sessionManager.abandonExpiredSession() } },
                    enabled = !state.submitting,
                    modifier = Modifier.testTag("reauth_logout"),
                ) {
                    Text(stringResource(R.string.reauth_salir))
                }
            }
        }
    }
}
