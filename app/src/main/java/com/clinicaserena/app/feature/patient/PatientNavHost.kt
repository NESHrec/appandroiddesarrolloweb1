package com.clinicaserena.app.feature.patient

import androidx.compose.runtime.Composable
import androidx.navigation.compose.composable
import com.clinicaserena.app.R
import com.clinicaserena.app.feature.common.PendingFeatureScreen
import com.clinicaserena.app.feature.common.RoleNavScaffold
import com.clinicaserena.app.feature.common.TopLevelDestination
import kotlinx.serialization.Serializable

@Serializable data object PatientHomeRoute
@Serializable data object PatientAppointmentsRoute
@Serializable data object PatientRecordRoute
@Serializable data object PatientPrescriptionsRoute

private val patientDestinations = listOf(
    TopLevelDestination(PatientHomeRoute, R.string.nav_inicio, R.drawable.ic_home),
    TopLevelDestination(PatientAppointmentsRoute, R.string.nav_citas, R.drawable.ic_calendar),
    TopLevelDestination(PatientRecordRoute, R.string.nav_expediente, R.drawable.ic_folder),
    TopLevelDestination(PatientPrescriptionsRoute, R.string.nav_recetas, R.drawable.ic_receipt),
)

/** Navegación del PACIENTE: Inicio, Citas, Mi expediente y Recetas (contenido en Fase B). */
@Composable
fun PatientNavHost() {
    RoleNavScaffold(destinations = patientDestinations, startDestination = PatientHomeRoute) {
        composable<PatientHomeRoute> { PendingFeatureScreen(R.string.fase_b) }
        composable<PatientAppointmentsRoute> { PendingFeatureScreen(R.string.fase_b) }
        composable<PatientRecordRoute> { PendingFeatureScreen(R.string.fase_b) }
        composable<PatientPrescriptionsRoute> { PendingFeatureScreen(R.string.fase_b) }
    }
}
