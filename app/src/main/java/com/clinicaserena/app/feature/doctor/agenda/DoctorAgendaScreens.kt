package com.clinicaserena.app.feature.doctor.agenda

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clinicaserena.app.R
import com.clinicaserena.app.core.time.ClinicTime
import com.clinicaserena.app.core.ui.LoadState
import com.clinicaserena.app.core.ui.components.EmptyState
import com.clinicaserena.app.core.ui.components.LoadingState
import com.clinicaserena.app.core.ui.components.StatusBanner
import com.clinicaserena.app.core.ui.theme.LocalStatusColors
import com.clinicaserena.app.domain.model.AppointmentStatus
import com.clinicaserena.app.feature.clinical.AttentionCard
import com.clinicaserena.app.feature.common.BackScaffold
import com.clinicaserena.app.feature.common.InfoRow
import com.clinicaserena.app.feature.common.StatusChip

// ---------------------------------------------------------------- Inicio

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DoctorHomeScreen(viewModel: DoctorHomeViewModel, onOpenAppointment: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Al volver a la pestaña se recarga (la primera carga la hace el ViewModel): refleja atenciones
    // recién registradas y reintenta cargas fallidas, como la vinculación pendiente.
    LaunchedEffect(Unit) { if (state.load !is LoadState.Loading) viewModel.load() }
    val periodLabel = stringResource(
        when (state.period) {
            HomePeriod.TODAY -> R.string.medico_periodo_hoy
            HomePeriod.WEEK -> R.string.medico_periodo_semana
            HomePeriod.MONTH -> R.string.medico_periodo_mes
        },
    )

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().testTag("doctor_home"),
    ) {
        item(key = "periodos") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    HomePeriod.TODAY to R.string.medico_filtro_hoy,
                    HomePeriod.WEEK to R.string.medico_filtro_semana,
                    HomePeriod.MONTH to R.string.medico_filtro_mes,
                ).forEach { (period, label) ->
                    FilterChip(
                        selected = state.period == period,
                        onClick = { viewModel.selectPeriod(period) },
                        label = { Text(stringResource(label)) },
                        modifier = Modifier.testTag("period_${period.name}"),
                    )
                }
            }
        }
        when (val load = state.load) {
            LoadState.Loading -> item(key = "cargando") { LoadingState(modifier = Modifier.padding(vertical = 32.dp)) }
            is LoadState.Failed -> item(key = "error") {
                DoctorFailureContent(load.failure, onRetry = viewModel::load, scrollable = false)
            }
            is LoadState.Loaded -> {
                val appointments = load.data
                item(key = "conteo") {
                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("patients_count_card"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                DoctorStats.distinctPatients(appointments).toString(),
                                style = MaterialTheme.typography.displaySmall,
                                modifier = Modifier.testTag("patients_count"),
                            )
                            Text(
                                stringResource(R.string.medico_pacientes_distintos, periodLabel),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                stringResource(R.string.medico_citas_en_periodo, appointments.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (DoctorStats.mayBeIncomplete(appointments)) item(key = "incompleto") { IncompleteResultBanner() }

                val ready = appointments.filter { it.canRecordAttention }
                item(key = "listas-titulo") { SectionHeader(stringResource(R.string.medico_listas_para_documentar)) }
                if (ready.isEmpty()) {
                    item(key = "listas-vacio") { Text(stringResource(R.string.medico_sin_listas)) }
                } else {
                    items(ready, key = { "lista-${it.id}" }) { DoctorAppointmentCard(it, { onOpenAppointment(it.id) }, showDate = true) }
                }

                val now = viewModel.now()
                val upcoming = appointments
                    .filter { it.status != AppointmentStatus.CANCELADA && !it.scheduledAt.toInstant().isBefore(now) }
                    .take(5)
                item(key = "proximas-titulo") { SectionHeader(stringResource(R.string.medico_proximas_citas)) }
                if (upcoming.isEmpty()) {
                    item(key = "proximas-vacio") { Text(stringResource(R.string.medico_sin_proximas)) }
                } else {
                    items(upcoming, key = { "proxima-${it.id}" }) { DoctorAppointmentCard(it, { onOpenAppointment(it.id) }, showDate = true) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Agenda

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DoctorAgendaScreen(viewModel: DoctorAgendaViewModel, onOpenAppointment: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Al volver a la pestaña se recarga (la primera carga la hace el ViewModel): refleja atenciones
    // recién registradas y reintenta cargas fallidas, como la vinculación pendiente.
    LaunchedEffect(Unit) { if (state.load !is LoadState.Loading) viewModel.load() }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().testTag("doctor_agenda"),
    ) {
        item(key = "rangos") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    AgendaRange.TODAY to R.string.medico_filtro_hoy,
                    AgendaRange.NEXT_7 to R.string.medico_filtro_proximos_7,
                    AgendaRange.LAST_7 to R.string.medico_filtro_ultimos_7,
                    AgendaRange.NEXT_30 to R.string.medico_filtro_proximos_30,
                ).forEach { (range, label) ->
                    FilterChip(
                        selected = state.range == range,
                        onClick = { viewModel.selectRange(range) },
                        label = { Text(stringResource(label)) },
                        modifier = Modifier.testTag("range_${range.name}"),
                    )
                }
            }
        }
        item(key = "estados") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    null to R.string.cita_filtro_todos,
                    AppointmentStatus.PENDIENTE to R.string.cita_estado_pendiente,
                    AppointmentStatus.CONFIRMADA to R.string.cita_estado_confirmada,
                    AppointmentStatus.COMPLETADA to R.string.cita_estado_completada,
                    AppointmentStatus.CANCELADA to R.string.cita_estado_cancelada,
                ).forEach { (status, label) ->
                    FilterChip(
                        selected = state.status == status,
                        onClick = { viewModel.selectStatus(status) },
                        label = { Text(stringResource(label)) },
                        modifier = Modifier.testTag("agenda_status_${status?.name ?: "TODOS"}"),
                    )
                }
            }
        }
        item(key = "actualizar") {
            OutlinedButton(onClick = viewModel::load, modifier = Modifier.testTag("agenda_refresh")) {
                Text(stringResource(R.string.perfil_actualizar))
            }
        }
        when (val load = state.load) {
            LoadState.Loading -> item(key = "cargando") { LoadingState(modifier = Modifier.padding(vertical = 32.dp)) }
            is LoadState.Failed -> item(key = "error") {
                DoctorFailureContent(load.failure, onRetry = viewModel::load, scrollable = false)
            }
            is LoadState.Loaded -> {
                if (DoctorStats.mayBeIncomplete(load.data)) item(key = "incompleto") { IncompleteResultBanner() }
                if (load.data.isEmpty()) {
                    item(key = "vacio") {
                        EmptyState(
                            message = stringResource(R.string.medico_agenda_vacia),
                            scrollable = false,
                            modifier = Modifier.testTag("agenda_empty"),
                        )
                    }
                } else {
                    // Spring las devuelve en orden ascendente; se agrupan por día en Guatemala.
                    load.data.groupBy { ClinicTime.localDate(it.scheduledAt) }.forEach { (day, appointments) ->
                        item(key = "dia-$day") {
                            SectionHeader(ClinicTime.formatDay(appointments.first().scheduledAt).replaceFirstChar { it.uppercase() })
                        }
                        items(appointments, key = { it.id }) { DoctorAppointmentCard(it, { onOpenAppointment(it.id) }) }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Detalle

@Composable
fun DoctorAppointmentDetailScreen(
    viewModel: DoctorAppointmentViewModel,
    onBack: () -> Unit,
    onRecordAttention: () -> Unit,
    onOpenRecord: () -> Unit,
    onOpenOdontogram: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    BackScaffold(title = stringResource(R.string.medico_cita_titulo), onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (val load = state) {
                LoadState.Loading -> LoadingState(modifier = Modifier.padding(vertical = 32.dp))
                is LoadState.Failed -> DoctorFailureContent(load.failure, onRetry = viewModel::load, scrollable = false)
                is LoadState.Loaded -> {
                    val appointment = load.data.appointment
                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("doctor_appointment_detail"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            StatusChip(appointment.status, appointment.statusRaw)
                            InfoRow(
                                stringResource(R.string.medico_paciente),
                                appointment.patientName ?: stringResource(R.string.medico_paciente_sin_nombre),
                            )
                            InfoRow(stringResource(R.string.cita_fecha_hora), ClinicTime.formatDateTime(appointment.scheduledAt))
                            appointment.specialtyName?.let { InfoRow(stringResource(R.string.cita_especialidad), it) }
                            InfoRow(stringResource(R.string.medico_llegada), arrivalText(appointment))
                            InfoRow(
                                stringResource(R.string.cita_notas),
                                appointment.notes?.takeIf { it.isNotBlank() } ?: stringResource(R.string.cita_sin_notas),
                            )
                        }
                    }

                    if (appointment.canRecordAttention) {
                        StatusBanner(
                            iconRes = R.drawable.ic_check_circle,
                            title = stringResource(R.string.medico_lista_para_documentar),
                            message = stringResource(R.string.medico_aviso_odontograma_antes),
                            background = LocalStatusColors.current.success,
                            modifier = Modifier.testTag("detail_ready"),
                        )
                    } else if (appointment.blockers.isNotEmpty()) {
                        StatusBanner(
                            iconRes = R.drawable.ic_lock,
                            title = stringResource(R.string.medico_no_documentable_titulo),
                            message = appointment.blockers.zip(appointment.blockersRaw)
                                .map { (blocker, raw) -> "• " + blockerText(blocker, raw) }
                                .joinToString("\n"),
                            background = LocalStatusColors.current.warning,
                            modifier = Modifier.testTag("detail_blockers"),
                        )
                    }
                    // El servidor vuelve a validar al guardar; el botón solo refleja canRecordAttention.
                    Button(
                        onClick = onRecordAttention,
                        enabled = appointment.canRecordAttention,
                        modifier = Modifier.fillMaxWidth().testTag("detail_record_attention"),
                    ) { Text(stringResource(R.string.medico_registrar_atencion)) }
                    OutlinedButton(onClick = onOpenRecord, modifier = Modifier.fillMaxWidth().testTag("detail_open_record")) {
                        Text(stringResource(R.string.medico_ver_expediente))
                    }
                    OutlinedButton(onClick = onOpenOdontogram, modifier = Modifier.fillMaxWidth().testTag("detail_open_odontogram")) {
                        Text(stringResource(R.string.medico_ver_odontograma))
                    }

                    load.data.attention?.let {
                        SectionHeader(stringResource(R.string.medico_atencion_registrada))
                        AttentionCard(it)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 4.dp).semantics { heading() },
    )
}
