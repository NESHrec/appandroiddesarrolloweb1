package com.clinicaserena.app.feature.doctor

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.clinicaserena.app.R
import com.clinicaserena.app.core.ui.LoadState
import com.clinicaserena.app.core.ui.LocalAppContainer
import com.clinicaserena.app.feature.common.BackScaffold
import com.clinicaserena.app.feature.common.PendingFeatureScreen
import com.clinicaserena.app.feature.common.RoleNavScaffold
import com.clinicaserena.app.feature.common.TopLevelDestination
import com.clinicaserena.app.feature.doctor.agenda.DoctorAgendaScreen
import com.clinicaserena.app.feature.doctor.agenda.DoctorAgendaViewModel
import com.clinicaserena.app.feature.doctor.agenda.DoctorAppointmentDetailScreen
import com.clinicaserena.app.feature.doctor.agenda.DoctorAppointmentViewModel
import com.clinicaserena.app.feature.doctor.agenda.DoctorHomeScreen
import com.clinicaserena.app.feature.doctor.agenda.DoctorHomeViewModel
import com.clinicaserena.app.feature.doctor.profile.DoctorProfileScreen
import com.clinicaserena.app.feature.doctor.profile.DoctorProfileViewModel
import kotlinx.serialization.Serializable

@Serializable data object DoctorHomeRoute
@Serializable data object DoctorAgendaRoute
@Serializable data object DoctorProfileRoute
@Serializable data class DoctorAppointmentRoute(val appointmentId: String)
@Serializable data class AttentionFormRoute(val appointmentId: String)
@Serializable data class DoctorRecordRoute(val appointmentId: String)
@Serializable data class OdontogramRoute(val appointmentId: String, val patientId: String)

private val doctorDestinations = listOf(
    TopLevelDestination(DoctorHomeRoute, R.string.nav_inicio, R.drawable.ic_home),
    TopLevelDestination(DoctorAgendaRoute, R.string.nav_agenda, R.drawable.ic_calendar),
    TopLevelDestination(DoctorProfileRoute, R.string.nav_perfil, R.drawable.ic_person),
)

/** Navegación del MEDICO: Inicio, Agenda, Perfil y el detalle de cada cita propia. */
@Composable
fun DoctorNavHost() {
    val container = LocalAppContainer.current
    val navController = rememberNavController()
    val openAppointment: (String) -> Unit = { navController.navigate(DoctorAppointmentRoute(it)) }

    RoleNavScaffold(destinations = doctorDestinations, startDestination = DoctorHomeRoute, navController = navController) {
        composable<DoctorHomeRoute> {
            DoctorHomeScreen(
                viewModel = viewModel { DoctorHomeViewModel(container.doctorRepository, container.sessionManager, container.clock) },
                onOpenAppointment = openAppointment,
            )
        }
        composable<DoctorAgendaRoute> {
            DoctorAgendaScreen(
                viewModel = viewModel { DoctorAgendaViewModel(container.doctorRepository, container.sessionManager, container.clock) },
                onOpenAppointment = openAppointment,
            )
        }
        composable<DoctorProfileRoute> {
            DoctorProfileScreen(
                viewModel = viewModel { DoctorProfileViewModel(container.authRepository, container.sessionManager) },
            )
        }
        composable<DoctorAppointmentRoute> { entry ->
            val appointmentId = entry.toRoute<DoctorAppointmentRoute>().appointmentId
            val viewModel = viewModel { DoctorAppointmentViewModel(container.doctorRepository, container.sessionManager, appointmentId) }
            DoctorAppointmentDetailScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onRecordAttention = { navController.navigate(AttentionFormRoute(appointmentId)) },
                onOpenRecord = { navController.navigate(DoctorRecordRoute(appointmentId)) },
                onOpenOdontogram = {
                    val patientId = (viewModel.state.value as? LoadState.Loaded)
                        ?.data?.appointment?.patientId ?: return@DoctorAppointmentDetailScreen
                    navController.navigate(OdontogramRoute(appointmentId, patientId))
                },
            )
        }
        // Registro de atención, expediente y odontograma se implementan en C2 y C3.
        composable<AttentionFormRoute> { PendingSecondary(R.string.medico_registrar_atencion) { navController.popBackStack() } }
        composable<DoctorRecordRoute> { PendingSecondary(R.string.medico_ver_expediente) { navController.popBackStack() } }
        composable<OdontogramRoute> { PendingSecondary(R.string.medico_ver_odontograma) { navController.popBackStack() } }
    }
}

@Composable
private fun PendingSecondary(titleRes: Int, onBack: () -> Unit) {
    BackScaffold(title = stringResource(titleRes), onBack = onBack) { padding ->
        PendingFeatureScreen(modifier = Modifier.padding(padding))
    }
}
