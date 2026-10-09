package com.clinicaserena.app.feature.doctor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
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
import com.clinicaserena.app.feature.doctor.care.AttentionFormScreen
import com.clinicaserena.app.feature.doctor.care.AttentionFormViewModel
import com.clinicaserena.app.feature.doctor.care.ClinicalProfileFormScreen
import com.clinicaserena.app.feature.doctor.care.ClinicalProfileFormViewModel
import com.clinicaserena.app.feature.doctor.care.DoctorRecordScreen
import com.clinicaserena.app.feature.doctor.care.DoctorRecordViewModel
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
@Serializable data class ClinicalProfileFormRoute(val appointmentId: String)

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
            RefreshWhenFlagged(entry) { viewModel.load() }
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
        composable<AttentionFormRoute> { entry ->
            val appointmentId = entry.toRoute<AttentionFormRoute>().appointmentId
            AttentionFormScreen(
                viewModel = viewModel { AttentionFormViewModel(container.doctorRepository, container.sessionManager, appointmentId) },
                onExit = { navController.popBackStack() },
                onDone = {
                    navController.previousBackStackEntry?.savedStateHandle?.set(REFRESH_KEY, true)
                    navController.popBackStack()
                },
            )
        }
        composable<DoctorRecordRoute> { entry ->
            val appointmentId = entry.toRoute<DoctorRecordRoute>().appointmentId
            val viewModel = viewModel { DoctorRecordViewModel(container.doctorRepository, container.sessionManager, appointmentId) }
            RefreshWhenFlagged(entry) { viewModel.load() }
            DoctorRecordScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onNewProfile = { navController.navigate(ClinicalProfileFormRoute(appointmentId)) },
            )
        }
        composable<ClinicalProfileFormRoute> { entry ->
            val appointmentId = entry.toRoute<ClinicalProfileFormRoute>().appointmentId
            ClinicalProfileFormScreen(
                viewModel = viewModel { ClinicalProfileFormViewModel(container.doctorRepository, container.sessionManager, appointmentId) },
                onExit = {
                    navController.previousBackStackEntry?.savedStateHandle?.set(REFRESH_KEY, true)
                    navController.popBackStack()
                },
            )
        }
        // El odontograma se implementa en C3.
        composable<OdontogramRoute> { PendingSecondary(R.string.medico_ver_odontograma) { navController.popBackStack() } }
    }
}

private const val REFRESH_KEY = "refresh"

/** Recarga una pantalla cuando la siguiente avisa que guardó algo (atención o perfil clínico). */
@Composable
private fun RefreshWhenFlagged(entry: NavBackStackEntry, reload: () -> Unit) {
    val flagged by entry.savedStateHandle.getStateFlow(REFRESH_KEY, false).collectAsStateWithLifecycle()
    LaunchedEffect(flagged) {
        if (flagged) {
            entry.savedStateHandle[REFRESH_KEY] = false
            reload()
        }
    }
}

@Composable
private fun PendingSecondary(titleRes: Int, onBack: () -> Unit) {
    BackScaffold(title = stringResource(titleRes), onBack = onBack) { padding ->
        PendingFeatureScreen(modifier = Modifier.padding(padding))
    }
}
