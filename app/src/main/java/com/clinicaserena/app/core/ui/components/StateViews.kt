package com.clinicaserena.app.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.clinicaserena.app.R
import com.clinicaserena.app.core.ui.theme.LocalStatusColors

/*
 * Estados comunes de toda pantalla con datos: carga, vacío, error recuperable, sesión vencida,
 * permiso denegado y éxito. Cada estado combina icono, título y texto; el color de fondo es apoyo,
 * nunca la única señal.
 */

@Composable
fun LoadingState(modifier: Modifier = Modifier, message: String = stringResource(R.string.estado_cargando)) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.size(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
fun EmptyState(
    message: String,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.estado_vacio_titulo),
    scrollable: Boolean = true,
) {
    StatusPanel(
        iconRes = R.drawable.ic_info,
        title = title,
        message = message,
        background = LocalStatusColors.current.info,
        modifier = modifier,
        scrollable = scrollable,
    )
}

@Composable
fun ErrorState(
    message: String,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.estado_error_titulo),
    scrollable: Boolean = true,
) {
    StatusPanel(
        iconRes = R.drawable.ic_error,
        title = title,
        message = message,
        background = LocalStatusColors.current.error,
        modifier = modifier,
        scrollable = scrollable,
        actionLabel = onRetry?.let { stringResource(R.string.estado_reintentar) },
        onAction = onRetry,
    )
}

@Composable
fun SessionExpiredState(onLogin: () -> Unit, modifier: Modifier = Modifier, scrollable: Boolean = true) {
    StatusPanel(
        iconRes = R.drawable.ic_schedule,
        title = stringResource(R.string.estado_sesion_vencida_titulo),
        message = stringResource(R.string.estado_sesion_vencida_mensaje),
        background = LocalStatusColors.current.warning,
        modifier = modifier,
        scrollable = scrollable,
        actionLabel = stringResource(R.string.estado_iniciar_sesion),
        onAction = onLogin,
    )
}

@Composable
fun ForbiddenState(
    message: String,
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    onRetry: (() -> Unit)? = null,
) {
    StatusPanel(
        iconRes = R.drawable.ic_lock,
        title = stringResource(R.string.estado_permiso_denegado_titulo),
        message = message,
        background = LocalStatusColors.current.error,
        modifier = modifier,
        actionLabel = onRetry?.let { stringResource(R.string.estado_reintentar) },
        onAction = onRetry,
        scrollable = scrollable,
    )
}

@Composable
fun SuccessBanner(message: String, modifier: Modifier = Modifier) {
    StatusBanner(
        iconRes = R.drawable.ic_check_circle,
        title = stringResource(R.string.estado_exito_titulo),
        message = message,
        background = LocalStatusColors.current.success,
        modifier = modifier,
    )
}

/** Aviso en línea (dentro de una pantalla), con icono y texto. */
@Composable
fun StatusBanner(
    @DrawableRes iconRes: Int,
    title: String,
    message: String,
    background: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        colors = CardDefaults.cardColors(
            containerColor = background,
            contentColor = LocalStatusColors.current.onStatus,
        ),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(painterResource(iconRes), contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(message, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * Estado de pantalla completa, desplazable para fuentes grandes y pantallas pequeñas. Dentro de una
 * columna que ya se desplaza se usa `scrollable = false` (dos scroll verticales anidados fallan).
 */
@Composable
private fun StatusPanel(
    @DrawableRes iconRes: Int,
    title: String,
    message: String,
    background: Color,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    scrollable: Boolean = true,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = background,
                contentColor = LocalStatusColors.current.onStatus,
            ),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(painterResource(iconRes), contentDescription = null, modifier = Modifier.size(40.dp))
                Spacer(Modifier.size(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                if (actionLabel != null && onAction != null) {
                    Spacer(Modifier.size(16.dp))
                    Button(onClick = onAction) { Text(actionLabel) }
                }
            }
        }
    }
}
