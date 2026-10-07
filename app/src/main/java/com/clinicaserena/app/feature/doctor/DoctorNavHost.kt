package com.clinicaserena.app.feature.doctor

import androidx.compose.runtime.Composable
import androidx.navigation.compose.composable
import com.clinicaserena.app.R
import com.clinicaserena.app.feature.common.PendingFeatureScreen
import com.clinicaserena.app.feature.common.RoleNavScaffold
import com.clinicaserena.app.feature.common.TopLevelDestination
import kotlinx.serialization.Serializable

@Serializable data object DoctorHomeRoute
@Serializable data object DoctorAgendaRoute
@Serializable data object DoctorProfileRoute

private val doctorDestinations = listOf(
    TopLevelDestination(DoctorHomeRoute, R.string.nav_inicio, R.drawable.ic_home),
    TopLevelDestination(DoctorAgendaRoute, R.string.nav_agenda, R.drawable.ic_calendar),
    TopLevelDestination(DoctorProfileRoute, R.string.nav_perfil, R.drawable.ic_person),
)

/** Navegación del MEDICO: Inicio, Agenda y Perfil (contenido en Fase C). */
@Composable
fun DoctorNavHost() {
    RoleNavScaffold(destinations = doctorDestinations, startDestination = DoctorHomeRoute) {
        composable<DoctorHomeRoute> { PendingFeatureScreen(R.string.fase_c) }
        composable<DoctorAgendaRoute> { PendingFeatureScreen(R.string.fase_c) }
        composable<DoctorProfileRoute> { PendingFeatureScreen(R.string.fase_c) }
    }
}
