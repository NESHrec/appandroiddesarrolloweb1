package com.clinicaserena.app.feature.patient.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clinicaserena.app.R
import com.clinicaserena.app.core.debug.DebugSessionTools
import com.clinicaserena.app.core.time.ClinicTime
import com.clinicaserena.app.core.ui.FailureContent
import com.clinicaserena.app.core.ui.LoadState
import com.clinicaserena.app.core.ui.components.LoadingState
import com.clinicaserena.app.feature.common.BackScaffold
import com.clinicaserena.app.feature.common.InfoRow
import com.clinicaserena.app.feature.common.LogoutSection

/** Mi perfil (solo lectura en B1; el cambio de nombre llega en B3) y cierre de sesión. */
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
            when (val current = state) {
                LoadState.Loading -> LoadingState(modifier = Modifier.padding(vertical = 32.dp))
                is LoadState.Failed -> FailureContent(current.failure, onRetry = viewModel::load, scrollable = false)
                is LoadState.Loaded -> {
                    val profile = current.data
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
                    OutlinedButton(onClick = viewModel::load, modifier = Modifier.testTag("profile_refresh")) {
                        Text(stringResource(R.string.perfil_actualizar))
                    }
                }
            }
            LogoutSection()
            DebugSessionTools()
        }
    }
}
