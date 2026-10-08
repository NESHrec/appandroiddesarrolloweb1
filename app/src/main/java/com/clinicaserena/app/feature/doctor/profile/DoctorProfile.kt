package com.clinicaserena.app.feature.doctor.profile

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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.clinicaserena.app.R
import com.clinicaserena.app.core.debug.DebugSessionTools
import com.clinicaserena.app.core.network.ApiResult
import com.clinicaserena.app.core.security.SessionManager
import com.clinicaserena.app.core.ui.FailureContent
import com.clinicaserena.app.core.ui.LoadState
import com.clinicaserena.app.core.ui.components.LoadingState
import com.clinicaserena.app.core.ui.components.StatusBanner
import com.clinicaserena.app.core.ui.theme.LocalStatusColors
import com.clinicaserena.app.core.ui.toLoadState
import com.clinicaserena.app.data.auth.AuthRepository
import com.clinicaserena.app.domain.model.AccountType
import com.clinicaserena.app.domain.model.Identity
import com.clinicaserena.app.domain.model.PractitionerLinkStatus
import com.clinicaserena.app.feature.common.InfoRow
import com.clinicaserena.app.feature.common.LogoutSection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Perfil del médico desde `/staff/auth/me`: datos de cuenta y estado de vinculación. */
class DoctorProfileViewModel(
    private val authRepository: AuthRepository,
    sessionManager: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow<LoadState<Identity>>(LoadState.Loading)
    val state: StateFlow<LoadState<Identity>> = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            sessionManager.reauthenticated.collect {
                if ((_state.value as? LoadState.Failed)?.failure is ApiResult.Unauthenticated) load()
            }
        }
    }

    fun load() {
        _state.value = LoadState.Loading
        viewModelScope.launch { _state.value = authRepository.currentIdentity(AccountType.PERSONAL).toLoadState() }
    }
}

@Composable
fun DoctorProfileScreen(viewModel: DoctorProfileViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (val current = state) {
            LoadState.Loading -> LoadingState(modifier = Modifier.padding(vertical = 32.dp))
            is LoadState.Failed -> FailureContent(current.failure, onRetry = viewModel::load, scrollable = false)
            is LoadState.Loaded -> {
                val identity = current.data
                when (identity.practitionerLinkStatus) {
                    PractitionerLinkStatus.VINCULADA -> StatusBanner(
                        iconRes = R.drawable.ic_check_circle,
                        title = stringResource(R.string.vinculacion_ok_titulo),
                        message = stringResource(R.string.vinculacion_ok_mensaje),
                        background = LocalStatusColors.current.success,
                        modifier = Modifier.testTag("doctor_link_ok"),
                    )
                    PractitionerLinkStatus.PENDIENTE_VINCULACION -> StatusBanner(
                        iconRes = R.drawable.ic_warning,
                        title = stringResource(R.string.vinculacion_pendiente_titulo),
                        message = stringResource(R.string.vinculacion_pendiente_mensaje),
                        background = LocalStatusColors.current.warning,
                        modifier = Modifier.testTag("doctor_link_pending"),
                    )
                    else -> Unit
                }
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("doctor_profile_card"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        identity.fullName?.let { InfoRow(stringResource(R.string.perfil_nombre), it) }
                        InfoRow(stringResource(R.string.perfil_correo), identity.email)
                        InfoRow(stringResource(R.string.perfil_rol), stringResource(R.string.rol_medico))
                    }
                }
                OutlinedButton(onClick = viewModel::load) { Text(stringResource(R.string.perfil_actualizar)) }
            }
        }
        LogoutSection()
        DebugSessionTools()
    }
}
