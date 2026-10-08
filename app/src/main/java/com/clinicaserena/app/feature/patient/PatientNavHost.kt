package com.clinicaserena.app.feature.patient

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.clinicaserena.app.R
import com.clinicaserena.app.core.ui.LocalAppContainer
import com.clinicaserena.app.feature.common.PendingFeatureScreen
import com.clinicaserena.app.feature.common.RoleNavScaffold
import com.clinicaserena.app.feature.common.TopLevelDestination
import com.clinicaserena.app.feature.patient.profile.PatientProfileScreen
import com.clinicaserena.app.feature.patient.profile.PatientProfileViewModel
import kotlinx.serialization.Serializable

@Serializable data object PatientHomeRoute
@Serializable data object PatientAppointmentsRoute
@Serializable data object PatientRecordRoute
@Serializable data object PatientPrescriptionsRoute
@Serializable data object PatientProfileRoute

private val patientDestinations = listOf(
    TopLevelDestination(PatientHomeRoute, R.string.nav_inicio, R.drawable.ic_home),
    TopLevelDestination(PatientAppointmentsRoute, R.string.nav_citas, R.drawable.ic_calendar),
    TopLevelDestination(PatientRecordRoute, R.string.nav_expediente, R.drawable.ic_folder),
    TopLevelDestination(PatientPrescriptionsRoute, R.string.nav_recetas, R.drawable.ic_receipt),
)

/** Navegación del PACIENTE: Inicio, Citas, Mi expediente y Recetas (contenido en B2/B3), más Mi perfil. */
@Composable
fun PatientNavHost() {
    val container = LocalAppContainer.current
    val navController = rememberNavController()

    RoleNavScaffold(
        destinations = patientDestinations,
        startDestination = PatientHomeRoute,
        navController = navController,
        topBarActions = {
            IconButton(
                onClick = { navController.navigate(PatientProfileRoute) { launchSingleTop = true } },
                modifier = Modifier.testTag("patient_profile_action"),
            ) {
                Icon(painterResource(R.drawable.ic_person), contentDescription = stringResource(R.string.perfil_titulo))
            }
        },
    ) {
        composable<PatientHomeRoute> { PendingFeatureScreen(R.string.fase_b) }
        composable<PatientAppointmentsRoute> { PendingFeatureScreen(R.string.fase_b) }
        composable<PatientRecordRoute> { PendingFeatureScreen(R.string.fase_b) }
        composable<PatientPrescriptionsRoute> { PendingFeatureScreen(R.string.fase_b) }
        composable<PatientProfileRoute> {
            PatientProfileScreen(
                viewModel = viewModel {
                    PatientProfileViewModel(container.patientProfileRepository, container.sessionManager)
                },
                onBack = { navController.popBackStack() },
            )
        }
    }
}
