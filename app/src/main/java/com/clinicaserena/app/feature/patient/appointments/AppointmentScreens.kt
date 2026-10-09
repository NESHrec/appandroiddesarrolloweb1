package com.clinicaserena.app.feature.patient.appointments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clinicaserena.app.R
import com.clinicaserena.app.core.time.ClinicTime
import com.clinicaserena.app.core.ui.FailureContent
import com.clinicaserena.app.core.ui.LoadState
import com.clinicaserena.app.core.ui.components.EmptyState
import com.clinicaserena.app.core.ui.components.LoadingState
import com.clinicaserena.app.core.ui.components.StatusBanner
import com.clinicaserena.app.core.ui.theme.LocalStatusColors
import com.clinicaserena.app.domain.model.AppointmentStatus
import com.clinicaserena.app.feature.common.BackScaffold
import com.clinicaserena.app.feature.common.InfoRow
import com.clinicaserena.app.feature.common.StatusChip
import java.util.Locale

// ---------------------------------------------------------------- Inicio

@Composable
fun PatientHomeScreen(
    viewModel: PatientAppointmentsViewModel,
    onOpenAppointment: (String) -> Unit,
    onBook: () -> Unit,
    onSeeAll: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.inicio_proxima_cita), style = MaterialTheme.typography.titleMedium)
        when (val load = state.load) {
            LoadState.Loading -> LoadingState(modifier = Modifier.padding(vertical = 24.dp))
            is LoadState.Failed -> FailureContent(load.failure, onRetry = viewModel::load, scrollable = false)
            is LoadState.Loaded -> {
                val next = AppointmentsLogic.next(load.data, viewModel.now())
                if (next == null) {
                    EmptyState(
                        title = stringResource(R.string.inicio_sin_proxima_titulo),
                        message = stringResource(R.string.inicio_sin_proxima_mensaje),
                        scrollable = false,
                        modifier = Modifier.testTag("home_no_next"),
                    )
                } else {
                    AppointmentCard(next, onClick = { onOpenAppointment(next.appointment.id) }, modifier = Modifier.testTag("home_next"))
                }
            }
        }
        Button(onClick = onBook, modifier = Modifier.fillMaxWidth().testTag("home_book")) {
            Text(stringResource(R.string.reservar_cita))
        }
        OutlinedButton(onClick = onSeeAll, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.ver_mis_citas))
        }
    }
}

// ---------------------------------------------------------------- Lista

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppointmentListScreen(
    viewModel: PatientAppointmentsViewModel,
    onOpenAppointment: (String) -> Unit,
    onBook: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = state.tab.ordinal, containerColor = MaterialTheme.colorScheme.background) {
            Tab(
                selected = state.tab == AppointmentTab.UPCOMING,
                onClick = { viewModel.selectTab(AppointmentTab.UPCOMING) },
                text = { Text(stringResource(R.string.citas_proximas)) },
                modifier = Modifier.testTag("tab_upcoming"),
            )
            Tab(
                selected = state.tab == AppointmentTab.HISTORY,
                onClick = { viewModel.selectTab(AppointmentTab.HISTORY) },
                text = { Text(stringResource(R.string.citas_historial)) },
                modifier = Modifier.testTag("tab_history"),
            )
        }
        // Filtros, botones y citas se desplazan juntos (solo las pestañas quedan fijas): con fuente grande
        // o pantalla pequeña la lista sigue teniendo espacio. FlowRow evita el scroll horizontal.
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().testTag("appointments_list"),
        ) {
            item(key = "filtros") {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val filters = listOf(
                        null to R.string.cita_filtro_todos,
                        AppointmentStatus.PENDIENTE to R.string.cita_estado_pendiente,
                        AppointmentStatus.CONFIRMADA to R.string.cita_estado_confirmada,
                        AppointmentStatus.COMPLETADA to R.string.cita_estado_completada,
                        AppointmentStatus.CANCELADA to R.string.cita_estado_cancelada,
                    )
                    filters.forEach { (status, label) ->
                        FilterChip(
                            selected = state.statusFilter == status,
                            onClick = { viewModel.selectStatus(status) },
                            label = { Text(stringResource(label)) },
                            modifier = Modifier.testTag("filter_${status?.name ?: "TODOS"}"),
                        )
                    }
                }
            }
            item(key = "acciones") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(onClick = onBook, modifier = Modifier.weight(1f).testTag("list_book")) {
                        Text(stringResource(R.string.reservar_cita))
                    }
                    OutlinedButton(onClick = viewModel::load, modifier = Modifier.testTag("list_refresh")) {
                        Text(stringResource(R.string.perfil_actualizar))
                    }
                }
            }
            when (val load = state.load) {
                LoadState.Loading -> item(key = "cargando") { LoadingState(modifier = Modifier.padding(vertical = 32.dp)) }
                is LoadState.Failed -> item(key = "error") {
                    FailureContent(load.failure, onRetry = viewModel::load, scrollable = false)
                }
                is LoadState.Loaded -> {
                    val items = AppointmentsLogic.filterByStatus(
                        AppointmentsLogic.forTab(load.data, state.tab, viewModel.now()),
                        state.statusFilter,
                    )
                    if (items.isEmpty()) {
                        item(key = "vacio") {
                            EmptyState(
                                message = stringResource(
                                    when {
                                        state.statusFilter != null -> R.string.citas_vacio_filtro
                                        state.tab == AppointmentTab.UPCOMING -> R.string.citas_vacio_proximas
                                        else -> R.string.citas_vacio_historial
                                    },
                                ),
                                scrollable = false,
                                modifier = Modifier.testTag("appointments_empty"),
                            )
                        }
                    } else {
                        items(items, key = { it.appointment.id }) { item ->
                            AppointmentCard(item, onClick = { onOpenAppointment(item.appointment.id) })
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Detalle

/**
 * Detalle construido desde la lista (Spring no tiene endpoint de detalle de cita para el paciente).
 * Si la cita no está en memoria se recarga la lista una vez.
 */
@Composable
fun AppointmentDetailScreen(viewModel: PatientAppointmentsViewModel, appointmentId: String, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val item = (state.load as? LoadState.Loaded)?.data?.firstOrNull { it.appointment.id == appointmentId }

    LaunchedEffect(appointmentId) {
        if (state.load is LoadState.Loaded && item == null) viewModel.load()
    }

    BackScaffold(title = stringResource(R.string.cita_detalle_titulo), onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when {
                item != null -> AppointmentDetailContent(item)
                state.load is LoadState.Failed ->
                    FailureContent((state.load as LoadState.Failed).failure, onRetry = viewModel::load, scrollable = false)
                state.load is LoadState.Loading -> LoadingState(modifier = Modifier.padding(vertical = 32.dp))
                else -> EmptyState(
                    title = stringResource(R.string.cita_no_encontrada_titulo),
                    message = stringResource(R.string.cita_no_encontrada_mensaje),
                    scrollable = false,
                )
            }
        }
    }
}

@Composable
private fun AppointmentDetailContent(item: AppointmentItem) {
    val appointment = item.appointment
    Card(
        modifier = Modifier.fillMaxWidth().testTag("appointment_detail"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            StatusChip(appointment.status, appointment.statusRaw)
            InfoRow(stringResource(R.string.cita_fecha_hora), ClinicTime.formatDateTime(appointment.scheduledAt))
            InfoRow(
                stringResource(R.string.cita_especialidad),
                item.specialtyName ?: stringResource(R.string.cita_especialidad_desconocida),
            )
            InfoRow(
                stringResource(R.string.cita_medico),
                item.practitionerName ?: stringResource(R.string.cita_medico_desconocido),
            )
            InfoRow(
                stringResource(R.string.cita_notas),
                appointment.notes?.takeIf { it.isNotBlank() } ?: stringResource(R.string.cita_sin_notas),
            )
            appointment.amountCents?.let {
                InfoRow(stringResource(R.string.cita_monto), String.format(Locale.US, "Q %.2f", it / 100.0))
            }
        }
    }
    StatusBanner(
        iconRes = R.drawable.ic_info,
        title = stringResource(R.string.cita_cambios_titulo),
        message = stringResource(R.string.cita_cambios_mensaje),
        background = LocalStatusColors.current.info,
    )
}
