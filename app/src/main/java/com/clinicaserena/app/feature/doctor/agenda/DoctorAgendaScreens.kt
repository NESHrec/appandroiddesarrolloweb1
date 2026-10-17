package com.clinicaserena.app.feature.doctor.agenda

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clinicaserena.app.R
import com.clinicaserena.app.core.time.ClinicTime
import com.clinicaserena.app.core.ui.LoadState
import com.clinicaserena.app.core.ui.components.BottomActionBar
import com.clinicaserena.app.core.ui.components.EmptyState
import com.clinicaserena.app.core.ui.components.GradientCard
import com.clinicaserena.app.core.ui.components.GroupedCard
import com.clinicaserena.app.core.ui.components.GroupedRow
import com.clinicaserena.app.core.ui.components.GroupedSectionHeader
import com.clinicaserena.app.core.ui.components.HairlineDivider
import com.clinicaserena.app.core.ui.components.InitialsAvatar
import com.clinicaserena.app.core.ui.components.LoadingState
import com.clinicaserena.app.core.ui.components.PrimaryButton
import com.clinicaserena.app.core.ui.components.SecondaryButton
import com.clinicaserena.app.core.ui.components.SegmentedControl
import com.clinicaserena.app.core.ui.components.SelectableFilterChip
import com.clinicaserena.app.core.ui.components.StatusBanner
import com.clinicaserena.app.core.ui.components.StatusTone
import com.clinicaserena.app.core.ui.components.tone
import com.clinicaserena.app.core.ui.theme.LocalStatusColors
import com.clinicaserena.app.domain.model.AppointmentStatus
import com.clinicaserena.app.domain.model.DoctorAppointment
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
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().testTag("doctor_home"),
    ) {
        item(key = "periodos") {
            // Hoy/Esta semana/Este mes: control segmentado con semántica de pestañas.
            val periods = listOf(
                HomePeriod.TODAY to R.string.medico_filtro_hoy,
                HomePeriod.WEEK to R.string.medico_filtro_semana,
                HomePeriod.MONTH to R.string.medico_filtro_mes,
            )
            SegmentedControl(
                options = periods.map { stringResource(it.second) },
                selectedIndex = periods.indexOfFirst { it.first == state.period },
                onSelect = { viewModel.selectPeriod(periods[it].first) },
                testTags = periods.map { "period_${it.first.name}" },
            )
        }
        when (val load = state.load) {
            LoadState.Loading -> item(key = "cargando") { LoadingState(modifier = Modifier.padding(vertical = 32.dp)) }
            is LoadState.Failed -> item(key = "error") {
                DoctorFailureContent(load.failure, onRetry = viewModel::load, scrollable = false)
            }
            is LoadState.Loaded -> {
                val appointments = load.data
                item(key = "conteo") { StatCards(appointments, periodLabel) }
                if (DoctorStats.mayBeIncomplete(appointments)) item(key = "incompleto") { IncompleteResultBanner() }

                val ready = appointments.filter { it.canRecordAttention }
                item(key = "listas-titulo") { GroupedSectionHeader(stringResource(R.string.medico_listas_para_documentar)) }
                if (ready.isEmpty()) {
                    item(key = "listas-vacio") {
                        Text(stringResource(R.string.medico_sin_listas), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    item(key = "listas") {
                        GroupedCard {
                            ready.forEachIndexed { index, it ->
                                if (index > 0) HairlineDivider(startIndent = 72.dp)
                                ReadyAppointmentRow(it, { onOpenAppointment(it.id) })
                            }
                        }
                    }
                }

                val now = viewModel.now()
                val upcoming = appointments
                    .filter { it.status != AppointmentStatus.CANCELADA && !it.scheduledAt.toInstant().isBefore(now) }
                    .take(5)
                item(key = "proximas-titulo") { GroupedSectionHeader(stringResource(R.string.medico_proximas_citas)) }
                if (upcoming.isEmpty()) {
                    item(key = "proximas-vacio") {
                        Text(stringResource(R.string.medico_sin_proximas), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    item(key = "proximas") {
                        GroupedCard {
                            upcoming.forEachIndexed { index, it ->
                                if (index > 0) HairlineDivider(startIndent = 84.dp)
                                DoctorAppointmentRow(it, { onOpenAppointment(it.id) }, timeColumn = true)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Estadísticas del período: pacientes distintos (degradado principal) y citas del período (alterno), con
 * los anillos decorativos. Con letra grande van una debajo de la otra.
 */
@Composable
private fun StatCards(appointments: List<DoctorAppointment>, periodLabel: String) {
    val stacked = LocalDensity.current.fontScale >= 1.3f
    val patients: @Composable (Modifier) -> Unit = { modifier ->
        GradientCard(modifier = modifier.testTag("patients_count_card")) {
            Text(
                DoctorStats.distinctPatients(appointments).toString(),
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.testTag("patients_count"),
            )
            Text(stringResource(R.string.medico_pacientes_distintos, periodLabel), style = MaterialTheme.typography.bodyMedium)
        }
    }
    val total: @Composable (Modifier) -> Unit = { modifier ->
        GradientCard(modifier = modifier, alt = true) {
            Text(appointments.size.toString(), style = MaterialTheme.typography.displaySmall)
            Text(stringResource(R.string.medico_citas_en_periodo, appointments.size), style = MaterialTheme.typography.bodyMedium)
        }
    }
    if (stacked) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            patients(Modifier)
            total(Modifier)
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.height(IntrinsicSize.Max)) {
            patients(Modifier.weight(1f).fillMaxHeight())
            total(Modifier.weight(1f).fillMaxHeight())
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
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize().testTag("doctor_agenda"),
    ) {
        item(key = "rangos") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    AgendaRange.TODAY to R.string.medico_filtro_hoy,
                    AgendaRange.NEXT_7 to R.string.medico_filtro_proximos_7,
                    AgendaRange.LAST_7 to R.string.medico_filtro_ultimos_7,
                    AgendaRange.NEXT_30 to R.string.medico_filtro_proximos_30,
                ).forEach { (range, label) ->
                    SelectableFilterChip(
                        selected = state.range == range,
                        label = stringResource(label),
                        onClick = { viewModel.selectRange(range) },
                        modifier = Modifier.testTag("range_${range.name}"),
                    )
                }
            }
        }
        item(key = "estados") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    null to R.string.cita_filtro_todos,
                    AppointmentStatus.PENDIENTE to R.string.cita_estado_pendiente,
                    AppointmentStatus.CONFIRMADA to R.string.cita_estado_confirmada,
                    AppointmentStatus.COMPLETADA to R.string.cita_estado_completada,
                    AppointmentStatus.CANCELADA to R.string.cita_estado_cancelada,
                ).forEach { (status, label) ->
                    SelectableFilterChip(
                        selected = state.status == status,
                        label = stringResource(label),
                        onClick = { viewModel.selectStatus(status) },
                        modifier = Modifier.testTag("agenda_status_${status?.name ?: "TODOS"}"),
                    )
                }
            }
        }
        item(key = "actualizar") {
            SecondaryButton(
                text = stringResource(R.string.perfil_actualizar),
                onClick = viewModel::load,
                modifier = Modifier.testTag("agenda_refresh"),
                fillWidth = false,
            )
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
                            GroupedSectionHeader(ClinicTime.formatDay(appointments.first().scheduledAt), modifier = Modifier.padding(top = 6.dp))
                        }
                        item(key = "grupo-$day") {
                            GroupedCard {
                                appointments.forEachIndexed { index, it ->
                                    if (index > 0) HairlineDivider(startIndent = 72.dp)
                                    DoctorAppointmentRow(it, { onOpenAppointment(it.id) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Detalle

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DoctorAppointmentDetailScreen(
    viewModel: DoctorAppointmentViewModel,
    onBack: () -> Unit,
    onRecordAttention: () -> Unit,
    onOpenRecord: () -> Unit,
    onOpenOdontogram: () -> Unit,
    onAddAddendum: (attentionId: String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loaded = (state as? LoadState.Loaded)?.data

    BackScaffold(
        title = stringResource(R.string.medico_cita_titulo),
        onBack = onBack,
        bottomBar = {
            // Barra fija: el servidor vuelve a validar al guardar; el botón solo refleja canRecordAttention
            // (si está deshabilitado, el aviso de arriba explica por qué).
            if (loaded != null) {
                BottomActionBar {
                    PrimaryButton(
                        text = stringResource(R.string.medico_registrar_atencion),
                        onClick = onRecordAttention,
                        enabled = loaded.appointment.canRecordAttention,
                        modifier = Modifier.testTag("detail_record_attention"),
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (val load = state) {
                LoadState.Loading -> LoadingState(modifier = Modifier.padding(vertical = 32.dp))
                is LoadState.Failed -> DoctorFailureContent(load.failure, onRetry = viewModel::load, scrollable = false)
                is LoadState.Loaded -> {
                    val appointment = load.data.appointment
                    // Paciente arriba: avatar, nombre, fecha y especialidad, estado y llegada.
                    Column(
                        modifier = Modifier.fillMaxWidth().testTag("doctor_appointment_detail"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        InitialsAvatar(appointment.patientName, 72.dp)
                        val patient = appointment.patientName ?: stringResource(R.string.medico_paciente_sin_nombre)
                        Text(
                            patient,
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(
                            listOfNotNull(ClinicTime.formatDateTime(appointment.scheduledAt), appointment.specialtyName).joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            StatusChip(appointment.status, appointment.statusRaw)
                            appointment.arrivalAt?.let { ArrivalPill(it) }
                        }
                        if (appointment.arrivalAt == null) {
                            Text(
                                arrivalText(appointment),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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

                    GroupedCard {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            InfoRow(
                                stringResource(R.string.cita_notas),
                                appointment.notes?.takeIf { it.isNotBlank() } ?: stringResource(R.string.cita_sin_notas),
                            )
                        }
                    }

                    // Expediente y odontograma: filas agrupadas con ícono de color y chevron.
                    GroupedCard {
                        GroupedRow(
                            onClick = onOpenRecord,
                            testTag = "detail_open_record",
                            leading = { IconTile(R.drawable.ic_folder, StatusTone.INFO) },
                        ) { Text(stringResource(R.string.medico_ver_expediente), style = MaterialTheme.typography.bodyLarge) }
                        HairlineDivider(startIndent = 64.dp)
                        GroupedRow(
                            onClick = onOpenOdontogram,
                            testTag = "detail_open_odontogram",
                            leading = { IconTile(R.drawable.ic_medical_services, StatusTone.WARNING) },
                        ) { Text(stringResource(R.string.medico_ver_odontograma), style = MaterialTheme.typography.bodyLarge) }
                    }

                    load.data.attention?.let {
                        GroupedSectionHeader(stringResource(R.string.medico_atencion_registrada))
                        AttentionCard(it)
                        // La atención es inmutable: las correcciones se agregan como adendas.
                        SecondaryButton(
                            text = stringResource(R.string.adenda_agregar),
                            onClick = { onAddAddendum(it.id) },
                            modifier = Modifier.testTag("detail_add_addendum"),
                        )
                    }
                }
            }
        }
    }
}

/** Ícono decorativo en un cuadro redondeado del tono indicado (ícono ≥ 6:1 sobre su fondo). */
@Composable
private fun IconTile(@DrawableRes iconRes: Int, tone: StatusTone) {
    val colors = LocalStatusColors.current.tone(tone)
    Box(
        Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(colors.background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(iconRes), contentDescription = null, tint = colors.content, modifier = Modifier.size(20.dp))
    }
}
