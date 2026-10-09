package com.clinicaserena.app.feature.patient

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.clinicaserena.app.R
import com.clinicaserena.app.core.ui.LocalAppContainer
import com.clinicaserena.app.feature.common.RoleNavScaffold
import com.clinicaserena.app.feature.common.TopLevelDestination
import com.clinicaserena.app.feature.patient.appointments.AppointmentDetailScreen
import com.clinicaserena.app.feature.patient.appointments.AppointmentListScreen
import com.clinicaserena.app.feature.patient.appointments.AppointmentTab
import com.clinicaserena.app.feature.patient.appointments.PatientAppointmentsViewModel
import com.clinicaserena.app.feature.patient.appointments.PatientHomeScreen
import com.clinicaserena.app.feature.patient.booking.BookingScreen
import com.clinicaserena.app.feature.patient.booking.BookingViewModel
import com.clinicaserena.app.feature.patient.profile.PatientProfileScreen
import com.clinicaserena.app.feature.patient.profile.PatientProfileViewModel
import com.clinicaserena.app.feature.patient.record.MyRecordScreen
import com.clinicaserena.app.feature.patient.record.MyRecordViewModel
import com.clinicaserena.app.feature.patient.record.PrescriptionsScreen
import com.clinicaserena.app.feature.patient.record.PrescriptionsViewModel
import kotlinx.serialization.Serializable

@Serializable data object PatientHomeRoute
@Serializable data object PatientAppointmentsRoute
@Serializable data object PatientRecordRoute
@Serializable data object PatientPrescriptionsRoute
@Serializable data object PatientProfileRoute
@Serializable data class AppointmentDetailRoute(val appointmentId: String)
@Serializable data object BookingRoute

private val patientDestinations = listOf(
    TopLevelDestination(PatientHomeRoute, R.string.nav_inicio, R.drawable.ic_home),
    TopLevelDestination(PatientAppointmentsRoute, R.string.nav_citas, R.drawable.ic_calendar),
    TopLevelDestination(PatientRecordRoute, R.string.nav_expediente, R.drawable.ic_folder),
    TopLevelDestination(PatientPrescriptionsRoute, R.string.nav_recetas, R.drawable.ic_receipt),
)

/** Navegación del PACIENTE: Inicio, Citas (lista, detalle y reserva), Mi expediente, Recetas y Mi perfil. */
@Composable
fun PatientNavHost() {
    val container = LocalAppContainer.current
    val navController = rememberNavController()
    // Compartido por Inicio, la lista y el detalle; vive en el almacén de la sesión.
    val appointments = viewModel {
        PatientAppointmentsViewModel(
            container.appointmentsRepository,
            container.catalogRepository,
            container.sessionManager,
            container.clock,
        )
    }

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
        composable<PatientHomeRoute> {
            PatientHomeScreen(
                viewModel = appointments,
                onOpenAppointment = { navController.navigate(AppointmentDetailRoute(it)) },
                onBook = { navController.navigate(BookingRoute) },
                onSeeAll = { navController.navigateToTab(PatientAppointmentsRoute) },
            )
        }
        composable<PatientAppointmentsRoute> {
            AppointmentListScreen(
                viewModel = appointments,
                onOpenAppointment = { navController.navigate(AppointmentDetailRoute(it)) },
                onBook = { navController.navigate(BookingRoute) },
            )
        }
        composable<AppointmentDetailRoute> { entry ->
            AppointmentDetailScreen(
                viewModel = appointments,
                appointmentId = entry.toRoute<AppointmentDetailRoute>().appointmentId,
                onBack = { navController.popBackStack() },
            )
        }
        composable<BookingRoute> {
            BookingScreen(
                viewModel = viewModel {
                    BookingViewModel(container.catalogRepository, container.appointmentsRepository, container.sessionManager)
                },
                onExit = { navController.popBackStack() },
                onBooked = appointments::load,
                onViewAppointments = {
                    // La cita recién reservada es futura: se muestra la pestaña Próximas.
                    appointments.selectTab(AppointmentTab.UPCOMING)
                    navController.popBackStack()
                    navController.navigateToTab(PatientAppointmentsRoute)
                },
            )
        }
        composable<PatientRecordRoute> {
            MyRecordScreen(viewModel = viewModel { MyRecordViewModel(container.patientClinicalRepository, container.sessionManager) })
        }
        composable<PatientPrescriptionsRoute> {
            PrescriptionsScreen(
                viewModel = viewModel { PrescriptionsViewModel(container.patientClinicalRepository, container.sessionManager) },
            )
        }
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

private fun NavHostController.navigateToTab(route: Any) {
    navigate(route) {
        popUpTo(PatientHomeRoute) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
