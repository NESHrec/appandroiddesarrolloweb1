package com.clinicaserena.app.core.debug

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.clinicaserena.app.R
import com.clinicaserena.app.core.ui.LocalAppContainer
import com.clinicaserena.app.core.ui.components.LinkButton

/**
 * Solo debug: invalida el token en memoria para que la siguiente petición reciba un 401 real de
 * Spring y se pruebe el re-login sin esperar 30 minutos. No muestra ni copia el token.
 */
@Composable
fun DebugSessionTools() {
    val sessionManager = LocalAppContainer.current.sessionManager
    LinkButton(
        text = stringResource(R.string.debug_simular_vencida),
        onClick = sessionManager::debugInvalidateToken,
        modifier = Modifier.testTag("debug_expire_session"),
        muted = true,
    )
}
