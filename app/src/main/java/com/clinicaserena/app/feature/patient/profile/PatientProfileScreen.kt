package com.clinicaserena.app.feature.patient.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clinicaserena.app.R
import com.clinicaserena.app.core.debug.DebugSessionTools
import com.clinicaserena.app.core.time.ClinicTime
import com.clinicaserena.app.core.ui.FailureContent
import com.clinicaserena.app.core.ui.LoadState
import com.clinicaserena.app.core.ui.asString
import com.clinicaserena.app.core.ui.components.FormTextField
import com.clinicaserena.app.core.ui.components.LoadingState
import com.clinicaserena.app.core.ui.components.StatusBanner
import com.clinicaserena.app.core.ui.theme.LocalStatusColors
import com.clinicaserena.app.feature.auth.SubmitButton
import com.clinicaserena.app.feature.common.BackScaffold
import com.clinicaserena.app.feature.common.InfoRow
import com.clinicaserena.app.feature.common.LogoutSection

/** Mi perfil: datos de la cuenta, cambio de nombre y cierre de sesión. */
@Composable
fun PatientProfileScreen(viewModel: PatientProfileViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    BackScaffold(title = stringResource(R.string.perfil_titulo), onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (val current = state.load) {
                LoadState.Loading -> LoadingState(modifier = Modifier.padding(vertical = 32.dp))
                is LoadState.Failed -> FailureContent(current.failure, onRetry = viewModel::load, scrollable = false)
                is LoadState.Loaded -> {
                    val profile = current.data
                    if (state.saved) {
                        StatusBanner(
                            iconRes = R.drawable.ic_check_circle,
                            title = stringResource(R.string.estado_exito_titulo),
                            message = stringResource(R.string.perfil_nombre_actualizado),
                            background = LocalStatusColors.current.success,
                            modifier = Modifier.testTag("profile_saved"),
                        )
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("profile_card"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            InfoRow(stringResource(R.string.perfil_nombre), profile.fullName)
                            InfoRow(stringResource(R.string.perfil_correo), profile.email)
                            InfoRow(
                                stringResource(R.string.perfil_estado_cuenta),
                                if (profile.accountStatus == "ACTIVA") stringResource(R.string.perfil_cuenta_activa) else profile.accountStatus,
                            )
                            profile.registeredAt?.let {
                                InfoRow(stringResource(R.string.perfil_registrado), ClinicTime.formatDateTime(it))
                            }
                        }
                    }
                    if (state.editing) {
                        NameEditor(state, viewModel)
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = viewModel::startEditing, modifier = Modifier.testTag("profile_edit_name")) {
                                Text(stringResource(R.string.perfil_cambiar_nombre))
                            }
                            OutlinedButton(onClick = viewModel::load, modifier = Modifier.testTag("profile_refresh")) {
                                Text(stringResource(R.string.perfil_actualizar))
                            }
                        }
                    }
                }
            }
            LogoutSection()
            DebugSessionTools()
        }
    }
}

@Composable
private fun NameEditor(state: PatientProfileUiState, viewModel: PatientProfileViewModel) {
    FormTextField(
        value = state.nameDraft,
        onValueChange = viewModel::onNameChange,
        label = stringResource(R.string.campo_nombre_completo),
        error = state.nameError?.asString(),
        testTag = "profile_name_field",
        capitalization = KeyboardCapitalization.Words,
        imeAction = ImeAction.Done,
        enabled = !state.saving,
        contentType = ContentType.PersonFullName,
        onImeAction = viewModel::save,
    )
    if (state.awaitingReauth) {
        StatusBanner(
            iconRes = R.drawable.ic_schedule,
            title = stringResource(R.string.estado_sesion_vencida_titulo),
            message = stringResource(R.string.perfil_esperando_sesion),
            background = LocalStatusColors.current.warning,
        )
    }
    state.saveError?.let {
        StatusBanner(
            iconRes = R.drawable.ic_error,
            title = stringResource(R.string.estado_error_titulo),
            message = it.asString(),
            background = LocalStatusColors.current.error,
            modifier = Modifier.testTag("profile_save_error"),
        )
    }
    SubmitButton(
        text = stringResource(R.string.perfil_guardar_nombre),
        submitting = state.saving,
        onClick = viewModel::save,
        testTag = "profile_save_name",
    )
    TextButton(onClick = viewModel::cancelEditing, enabled = !state.saving) {
        Text(stringResource(R.string.perfil_cancelar))
    }
}
