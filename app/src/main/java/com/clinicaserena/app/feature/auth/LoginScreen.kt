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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clinicaserena.app.R
import com.clinicaserena.app.core.security.LogoutReason
import com.clinicaserena.app.core.ui.asString
import com.clinicaserena.app.core.ui.components.FormTextField
import com.clinicaserena.app.core.ui.components.PasswordField
import com.clinicaserena.app.core.ui.components.StatusBanner
import com.clinicaserena.app.core.ui.theme.LocalStatusColors
import com.clinicaserena.app.core.ui.toUserMessage

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    reason: LogoutReason,
    onRegister: () -> Unit,
    onForgotPassword: () -> Unit,
    onResendVerification: () -> Unit,
    diagnostics: (@Composable () -> Unit)?,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

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
        Text(text = stringResource(R.string.login_subtitulo), style = MaterialTheme.typography.bodyLarge)

        if (!state.reasonDismissed) LogoutReasonBanner(reason)
        if (state.unsupportedRole) UnsupportedRoleBanner()

        FormTextField(
            value = state.email,
            onValueChange = viewModel::onEmailChange,
            label = stringResource(R.string.campo_correo),
            error = state.emailError?.let { stringResource(it) },
            testTag = "login_email",
            keyboardType = KeyboardType.Email,
            contentType = ContentType.Username + ContentType.EmailAddress,
            enabled = !state.submitting,
        )
        PasswordField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            label = stringResource(R.string.campo_contrasena),
            visible = state.passwordVisible,
            onToggleVisible = viewModel::togglePasswordVisible,
            error = state.passwordError?.let { stringResource(it) },
            testTag = "login_password",
            imeAction = ImeAction.Done,
            enabled = !state.submitting,
            onImeAction = viewModel::submit,
        )

        state.message?.let { message ->
            StatusBanner(
                iconRes = R.drawable.ic_error,
                title = stringResource(R.string.estado_error_titulo),
                message = message.asString(),
                background = LocalStatusColors.current.error,
                modifier = Modifier.testTag("login_error"),
            )
        }

        SubmitButton(
            text = stringResource(R.string.login_boton),
            submitting = state.submitting,
            onClick = viewModel::submit,
            testTag = "login_submit",
        )

        TextButton(onClick = onRegister, modifier = Modifier.testTag("login_register")) {
            Text(stringResource(R.string.login_crear_cuenta))
        }
        TextButton(onClick = onForgotPassword, modifier = Modifier.testTag("login_forgot")) {
            Text(stringResource(R.string.login_olvide_contrasena))
        }
        TextButton(onClick = onResendVerification, modifier = Modifier.testTag("login_resend")) {
            Text(stringResource(R.string.login_reenviar_verificacion))
        }

        if (diagnostics != null) {
            var expanded by rememberSaveable { mutableStateOf(false) }
            TextButton(onClick = { expanded = !expanded }) {
                Text(stringResource(if (expanded) R.string.diagnostico_ocultar else R.string.diagnostico_mostrar))
            }
            if (expanded) diagnostics()
        }
    }
}

@Composable
private fun LogoutReasonBanner(reason: LogoutReason) {
    when (reason) {
        LogoutReason.EXPIRED -> StatusBanner(
            iconRes = R.drawable.ic_schedule,
            title = stringResource(R.string.estado_sesion_vencida_titulo),
            message = stringResource(R.string.estado_sesion_vencida_mensaje),
            background = LocalStatusColors.current.warning,
        )
        LogoutReason.UNSUPPORTED_ROLE -> UnsupportedRoleBanner()
        LogoutReason.LOCAL_ONLY -> StatusBanner(
            iconRes = R.drawable.ic_warning,
            title = stringResource(R.string.login_cerrado_local_titulo),
            message = stringResource(R.string.login_cerrado_local_mensaje),
            background = LocalStatusColors.current.warning,
        )
        LogoutReason.NONE -> Unit
    }
}

@Composable
private fun UnsupportedRoleBanner() {
    StatusBanner(
        iconRes = R.drawable.ic_lock,
        title = stringResource(R.string.estado_permiso_denegado_titulo),
        message = stringResource(R.string.login_rol_no_soportado),
        background = LocalStatusColors.current.error,
        modifier = Modifier.testTag("login_unsupported_role"),
    )
}

/** Botón principal que se deshabilita y muestra progreso mientras se envía. */
@Composable
fun SubmitButton(text: String, submitting: Boolean, onClick: () -> Unit, testTag: String) {
    Button(
        onClick = onClick,
        enabled = !submitting,
        modifier = Modifier.fillMaxWidth().testTag(testTag),
    ) {
        if (submitting) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(text)
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
