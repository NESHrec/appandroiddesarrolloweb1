package com.clinicaserena.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clinicaserena.app.core.security.SessionState
import com.clinicaserena.app.core.ui.LocalAppContainer
import com.clinicaserena.app.core.ui.ScopedViewModels
import com.clinicaserena.app.core.ui.asString
import com.clinicaserena.app.core.ui.components.ErrorState
import com.clinicaserena.app.core.ui.components.LoadingState
import com.clinicaserena.app.core.ui.theme.ClinicaSerenaTheme
import com.clinicaserena.app.core.ui.toUserMessage
import com.clinicaserena.app.domain.model.Role
import com.clinicaserena.app.feature.auth.AuthNavHost
import com.clinicaserena.app.feature.auth.DiagnosticsCard
import com.clinicaserena.app.feature.auth.DiagnosticsViewModel
import com.clinicaserena.app.feature.auth.ReauthDialog
import com.clinicaserena.app.feature.doctor.DoctorNavHost
import com.clinicaserena.app.feature.patient.PatientNavHost

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as ClinicaSerenaApp).container
        setContent {
            ClinicaSerenaTheme {
                CompositionLocalProvider(LocalAppContainer provides container) {
                    Surface(
                        // testTagsAsResourceId: permite ubicar elementos con `uiautomator dump` en el emulador.
                        modifier = Modifier.fillMaxSize().semantics { testTagsAsResourceId = true },
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        AppRoot(container)
                    }
                }
            }
        }
    }
}

/**
 * Decide qué se muestra según la sesión. Cada etapa tiene su propio almacén de ViewModels
 * ([ScopedViewModels]): al pasar del acceso a una sesión, o de una persona a otra, el anterior se
 * limpia y no quedan en memoria contraseñas escritas ni datos clínicos.
 */
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
        is SessionState.LoggedOut -> ScopedViewModels(key = "acceso") {
            AuthNavHost(
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
        }
        // Una sola rama para ambos estados: así el grafo ocupa el mismo lugar en la composición y, si la
        // sesión vence en uso, se conservan la pantalla actual y sus borradores mientras se pide la contraseña.
        is SessionState.Active, is SessionState.ReauthRequired -> {
            val session = (state as? SessionState.Active)?.session ?: (state as SessionState.ReauthRequired).previous
            RoleGraph(
                subjectId = session.subjectId,
                role = session.role,
                reauthEmail = if (state is SessionState.ReauthRequired) session.email else null,
            )
        }
    }
}

@Composable
private fun RoleGraph(subjectId: String, role: Role, reauthEmail: String?) {
    ScopedViewModels(key = "sesion:$subjectId") {
        when (role) {
            Role.PACIENTE -> PatientNavHost()
            Role.MEDICO -> DoctorNavHost()
            // SessionManager nunca activa estos roles; si llegaran, no se abre ningún grafo.
            Role.ADMIN, Role.RECEPCION -> LoadingState()
        }
        if (reauthEmail != null) ReauthDialog(email = reauthEmail)
    }
}
