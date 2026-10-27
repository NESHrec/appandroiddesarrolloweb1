package com.clinicaserena.app.feature.patient.push

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clinicaserena.app.R
import com.clinicaserena.app.core.push.PushRegistrationManager
import com.clinicaserena.app.core.push.PushStatus
import com.clinicaserena.app.core.ui.LocalAppContainer
import com.clinicaserena.app.core.ui.asString
import com.clinicaserena.app.core.ui.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class PushRemindersUiState(
    /** El usuario ya respondió al diálogo de permiso en esta sesión y lo rechazó. */
    val permissionDenied: Boolean = false,
    /** "Ahora no" en Inicio: la invitación no vuelve a aparecer en esta sesión (sigue en Mi perfil). */
    val dismissedOnHome: Boolean = false,
)

/** Vive en el almacén de la sesión: al cerrar sesión se descarta junto con el resto. */
class PushRemindersViewModel(private val manager: PushRegistrationManager) : ViewModel() {
    val status: StateFlow<PushStatus> = manager.status

    private val _ui = MutableStateFlow(PushRemindersUiState())
    val ui: StateFlow<PushRemindersUiState> = _ui.asStateFlow()

    fun refresh() = manager.refresh()

    fun retry() = manager.retry()

    fun onPermissionResult(granted: Boolean) {
        _ui.update { it.copy(permissionDenied = !granted) }
        manager.refresh()
    }

    fun dismissOnHome() = _ui.update { it.copy(dismissedOnHome = true) }
}

@Composable
private fun pushRemindersViewModel(): PushRemindersViewModel {
    val container = LocalAppContainer.current
    return viewModel(key = "recordatorios_push") { PushRemindersViewModel(container.pushRegistration) }
}

/** Invitación en Inicio: solo aparece mientras falta el permiso. */
@Composable
fun PushRemindersInvitation(modifier: Modifier = Modifier) {
    val viewModel = pushRemindersViewModel()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    if (status != PushStatus.PermissionRequired || ui.dismissedOnHome) return
    PushRemindersCard(viewModel, status, ui, onDismiss = viewModel::dismissOnHome, modifier = modifier.testTag("push_invitation"))
}

/** Sección de Mi perfil: siempre visible con el estado actual. */
@Composable
fun PushRemindersSection(modifier: Modifier = Modifier) {
    val viewModel = pushRemindersViewModel()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    PushRemindersCard(viewModel, status, ui, onDismiss = null, modifier = modifier.testTag("push_section"))
}

@Composable
private fun PushRemindersCard(
    viewModel: PushRemindersViewModel,
    status: PushStatus,
    ui: PushRemindersUiState,
    onDismiss: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onPermissionResult(granted)
    }
    val openSettings = {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        )
    }
    // Android 13+ pide el permiso; antes, las notificaciones solo se apagan desde los ajustes.
    val canAskPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !ui.permissionDenied

    Card(
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val content = statusContent(status, ui)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (status == PushStatus.Registering) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Icon(painterResource(content.iconRes), contentDescription = null, modifier = Modifier.size(24.dp))
                }
                Column {
                    Text(stringResource(R.string.push_titulo), style = MaterialTheme.typography.titleMedium)
                    Text(content.label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.testTag("push_status"))
                }
            }
            Text(content.message, style = MaterialTheme.typography.bodyMedium)

            when (status) {
                PushStatus.PermissionRequired -> {
                    if (canAskPermission) {
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                            modifier = Modifier.fillMaxWidth().testTag("push_enable"),
                        ) { Text(stringResource(R.string.push_activar)) }
                    } else {
                        Button(onClick = openSettings, modifier = Modifier.fillMaxWidth().testTag("push_settings")) {
                            Text(stringResource(R.string.push_abrir_ajustes))
                        }
                    }
                    if (onDismiss != null) {
                        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().testTag("push_dismiss")) {
                            Text(stringResource(R.string.push_ahora_no))
                        }
                    }
                }
                PushStatus.TokenUnavailable, is PushStatus.Failed -> OutlinedButton(
                    onClick = viewModel::retry,
                    modifier = Modifier.fillMaxWidth().testTag("push_retry"),
                ) { Text(stringResource(R.string.estado_reintentar)) }
                else -> Unit
            }
        }
    }
}

private class StatusContent(@param:DrawableRes val iconRes: Int, val label: String, val message: String)

/** Cada estado se comunica con ícono y texto, no solo con color. */
@Composable
private fun statusContent(status: PushStatus, ui: PushRemindersUiState): StatusContent = when (status) {
    PushStatus.PermissionRequired -> if (ui.permissionDenied) {
        StatusContent(R.drawable.ic_warning, stringResource(R.string.push_estado_desactivados), stringResource(R.string.push_permiso_denegado))
    } else {
        StatusContent(R.drawable.ic_info, stringResource(R.string.push_estado_desactivados), stringResource(R.string.push_explicacion))
    }
    PushStatus.Registering ->
        StatusContent(R.drawable.ic_schedule, stringResource(R.string.push_estado_activando), stringResource(R.string.push_activando))
    PushStatus.Registered ->
        StatusContent(R.drawable.ic_check_circle, stringResource(R.string.push_estado_activados), stringResource(R.string.push_activados))
    PushStatus.ServerDisabled ->
        StatusContent(R.drawable.ic_info, stringResource(R.string.push_estado_no_disponibles), stringResource(R.string.push_no_disponibles))
    PushStatus.AwaitingReauth ->
        StatusContent(R.drawable.ic_lock, stringResource(R.string.estado_sesion_vencida_titulo), stringResource(R.string.push_esperando_sesion))
    PushStatus.TokenUnavailable ->
        StatusContent(R.drawable.ic_error, stringResource(R.string.push_estado_error), stringResource(R.string.push_sin_token))
    is PushStatus.Failed ->
        StatusContent(R.drawable.ic_error, stringResource(R.string.push_estado_error), status.failure.toUserMessage().asString())
    PushStatus.Inactive ->
        StatusContent(R.drawable.ic_schedule, stringResource(R.string.push_estado_activando), stringResource(R.string.push_activando))
}
