package com.clinicaserena.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clinicaserena.app.core.security.SessionState
import com.clinicaserena.app.core.ui.asString
import com.clinicaserena.app.core.ui.components.ErrorState
import com.clinicaserena.app.core.ui.components.LoadingState
import com.clinicaserena.app.core.ui.theme.ClinicaSerenaTheme
import com.clinicaserena.app.core.ui.toUserMessage
import com.clinicaserena.app.domain.model.Role
import com.clinicaserena.app.feature.auth.DiagnosticsCard
import com.clinicaserena.app.feature.auth.DiagnosticsViewModel
import com.clinicaserena.app.feature.auth.LoginScreen
import com.clinicaserena.app.feature.doctor.DoctorNavHost
import com.clinicaserena.app.feature.patient.PatientNavHost

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as ClinicaSerenaApp).container
        setContent {
            ClinicaSerenaTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot(container)
                }
            }
        }
    }
}

/** Decide el grafo según la sesión: acceso, PACIENTE o MEDICO. */
@Composable
private fun AppRoot(container: AppContainer) {
    val sessionState by container.sessionManager.state.collectAsStateWithLifecycle()

    when (val state = sessionState) {
        SessionState.Checking -> LoadingState(message = stringResource(R.string.estado_verificando_sesion))
        is SessionState.RestoreFailed -> ErrorState(
            title = stringResource(R.string.error_inicio_sesion_titulo),
            message = state.failure.toUserMessage().asString(),
            onRetry = container.sessionManager::retryRestore,
        )
        is SessionState.LoggedOut -> LoginScreen(
            reason = state.reason,
            diagnostics = if (BuildConfig.DEBUG) {
                {
                    val diagnosticsViewModel = viewModel { DiagnosticsViewModel(container.healthRepository) }
                    DiagnosticsCard(diagnosticsViewModel, container.apiBaseUrl)
                }
            } else {
                null
            },
        )
        is SessionState.Active -> when (state.session.role) {
            Role.PACIENTE -> PatientNavHost()
            Role.MEDICO -> DoctorNavHost()
            // SessionManager nunca activa estos roles; si llegaran, no se abre ningún grafo.
            Role.ADMIN, Role.RECEPCION -> LoadingState()
        }
    }
}
