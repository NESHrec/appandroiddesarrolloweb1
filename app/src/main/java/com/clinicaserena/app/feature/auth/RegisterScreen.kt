package com.clinicaserena.app.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clinicaserena.app.R
import com.clinicaserena.app.core.ui.asString
import com.clinicaserena.app.core.ui.components.FormTextField
import com.clinicaserena.app.core.ui.components.PasswordField
import com.clinicaserena.app.core.ui.components.StatusBanner
import com.clinicaserena.app.core.ui.theme.LocalStatusColors
import com.clinicaserena.app.feature.common.BackScaffold

@Composable
fun RegisterScreen(viewModel: RegisterViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    BackScaffold(title = stringResource(R.string.registro_titulo), onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.sent) {
                StatusBanner(
                    iconRes = R.drawable.ic_mail,
                    title = stringResource(R.string.registro_enviado_titulo),
                    message = stringResource(R.string.registro_enviado_mensaje),
                    background = LocalStatusColors.current.success,
                    modifier = Modifier.testTag("register_sent"),
                )
                OutlinedButton(onClick = onBack, modifier = Modifier.testTag("register_back_to_login")) {
                    Text(stringResource(R.string.volver_a_inicio_sesion))
                }
                return@Column
            }

            Text(stringResource(R.string.registro_explicacion), style = MaterialTheme.typography.bodyLarge)
            FormTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = stringResource(R.string.campo_nombre_completo),
                error = state.nameError?.asString(),
                testTag = "register_name",
                capitalization = KeyboardCapitalization.Words,
                enabled = !state.submitting,
            )
            FormTextField(
                value = state.email,
                onValueChange = viewModel::onEmailChange,
                label = stringResource(R.string.campo_correo),
                error = state.emailError?.asString(),
                testTag = "register_email",
                keyboardType = KeyboardType.Email,
                contentType = ContentType.NewUsername + ContentType.EmailAddress,
                enabled = !state.submitting,
            )
            PasswordField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                label = stringResource(R.string.campo_contrasena),
                visible = state.passwordVisible,
                onToggleVisible = viewModel::togglePasswordVisible,
                error = state.passwordError?.asString(),
                testTag = "register_password",
                imeAction = ImeAction.Next,
                contentType = ContentType.NewPassword,
                enabled = !state.submitting,
            )
            PasswordField(
                value = state.confirmation,
                onValueChange = viewModel::onConfirmationChange,
                label = stringResource(R.string.campo_confirmar_contrasena),
                visible = state.passwordVisible,
                onToggleVisible = viewModel::togglePasswordVisible,
                error = state.confirmationError?.asString(),
                testTag = "register_confirmation",
                contentType = ContentType.NewPassword,
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
                text = stringResource(R.string.registro_boton),
                submitting = state.submitting,
                onClick = viewModel::submit,
                testTag = "register_submit",
            )
        }
    }
}
