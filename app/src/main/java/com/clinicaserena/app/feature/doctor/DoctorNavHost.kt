package com.clinicaserena.app.feature.doctor

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.composable
import com.clinicaserena.app.R
import com.clinicaserena.app.core.ui.LocalAppContainer
import com.clinicaserena.app.feature.common.PendingFeatureScreen
import com.clinicaserena.app.feature.common.RoleNavScaffold
import com.clinicaserena.app.feature.common.TopLevelDestination
import com.clinicaserena.app.feature.doctor.profile.DoctorProfileScreen
import com.clinicaserena.app.feature.doctor.profile.DoctorProfileViewModel
import kotlinx.serialization.Serializable

@Serializable data object DoctorHomeRoute
@Serializable data object DoctorAgendaRoute
@Serializable data object DoctorProfileRoute

private val doctorDestinations = listOf(
    TopLevelDestination(DoctorHomeRoute, R.string.nav_inicio, R.drawable.ic_home),
    TopLevelDestination(DoctorAgendaRoute, R.string.nav_agenda, R.drawable.ic_calendar),
    TopLevelDestination(DoctorProfileRoute, R.string.nav_perfil, R.drawable.ic_person),
)

/** Navegación del MEDICO: Inicio y Agenda (contenido en Fase C) y Perfil con cierre de sesión. */
@Composable
fun DoctorNavHost() {
    val container = LocalAppContainer.current
    RoleNavScaffold(destinations = doctorDestinations, startDestination = DoctorHomeRoute) {
        composable<DoctorHomeRoute> { PendingFeatureScreen() }
        composable<DoctorAgendaRoute> { PendingFeatureScreen() }
        composable<DoctorProfileRoute> {
            DoctorProfileScreen(
                viewModel = viewModel { DoctorProfileViewModel(container.authRepository, container.sessionManager) },
            )
        }
    }
}
