package com.clinicaserena.app.feature.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.clinicaserena.app.R
import com.clinicaserena.app.core.ui.components.EmptyState

/** Destino aún sin implementar. Muestra un estado honesto; nunca datos de ejemplo. */
@Composable
fun PendingFeatureScreen(modifier: Modifier = Modifier) {
    EmptyState(
        title = stringResource(R.string.pendiente_titulo),
        message = stringResource(R.string.pendiente_mensaje),
        modifier = modifier,
    )
}
