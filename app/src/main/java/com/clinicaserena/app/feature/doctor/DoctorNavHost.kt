package com.clinicaserena.app.feature.doctor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.clinicaserena.app.R
import com.clinicaserena.app.core.ui.LoadState
import com.clinicaserena.app.core.ui.LocalAppContainer
import com.clinicaserena.app.feature.common.RoleNavScaffold
import com.clinicaserena.app.feature.common.TopLevelDestination
import com.clinicaserena.app.feature.doctor.agenda.DoctorAgendaScreen
import com.clinicaserena.app.feature.doctor.care.AddendumFormScreen
import com.clinicaserena.app.feature.doctor.care.AddendumFormViewModel
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
import com.clinicaserena.app.feature.doctor.odontogram.ObservationFormScreen
import com.clinicaserena.app.feature.doctor.odontogram.ObservationFormViewModel
import com.clinicaserena.app.feature.doctor.odontogram.OdontogramScreen
import com.clinicaserena.app.feature.doctor.odontogram.OdontogramViewModel
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
@Serializable data class AddendumFormRoute(val appointmentId: String, val attentionId: String)
@Serializable data class ObservationFormRoute(val appointmentId: String, val patientId: String)

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
    // Tras un registro exitoso, la pantalla anterior (cita, expediente u odontograma) se recarga.
    val finishAndRefresh: () -> Unit = {
        navController.previousBackStackEntry?.savedStateHandle?.set(REFRESH_KEY, true)
        navController.popBackStack()
    }

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
                onAddAddendum = { attentionId -> navController.navigate(AddendumFormRoute(appointmentId, attentionId)) },
            )
        }
        composable<AttentionFormRoute> { entry ->
            val appointmentId = entry.toRoute<AttentionFormRoute>().appointmentId
            AttentionFormScreen(
                viewModel = viewModel { AttentionFormViewModel(container.doctorRepository, container.sessionManager, appointmentId) },
                onExit = { navController.popBackStack() },
                onDone = finishAndRefresh,
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
                onExit = { navController.popBackStack() },
                onDone = finishAndRefresh,
            )
        }
        composable<AddendumFormRoute> { entry ->
            val route = entry.toRoute<AddendumFormRoute>()
            AddendumFormScreen(
                viewModel = viewModel {
                    AddendumFormViewModel(container.doctorRepository, container.sessionManager, route.appointmentId, route.attentionId)
                },
                onExit = { navController.popBackStack() },
                onDone = finishAndRefresh,
            )
        }
        composable<OdontogramRoute> { entry ->
            val route = entry.toRoute<OdontogramRoute>()
            val viewModel = viewModel {
                OdontogramViewModel(container.doctorRepository, container.sessionManager, route.appointmentId, route.patientId)
            }
            RefreshWhenFlagged(entry) { viewModel.load() }
            OdontogramScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onAddObservation = { navController.navigate(ObservationFormRoute(route.appointmentId, route.patientId)) },
            )
        }
        composable<ObservationFormRoute> { entry ->
            val route = entry.toRoute<ObservationFormRoute>()
            ObservationFormScreen(
                viewModel = viewModel {
                    ObservationFormViewModel(container.doctorRepository, container.sessionManager, route.appointmentId, route.patientId)
                },
                onExit = { navController.popBackStack() },
                onDone = finishAndRefresh,
            )
        }
    }
}

private const val REFRESH_KEY = "refresh"

/** Recarga una pantalla cuando la siguiente avisa que guardó algo (atención, perfil, adenda u observación). */
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
