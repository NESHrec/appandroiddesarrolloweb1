package com.clinicaserena.app.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.clinicaserena.app.core.network.ApiResult
import com.clinicaserena.app.core.ui.components.ErrorState
import com.clinicaserena.app.core.ui.components.ForbiddenState
import com.clinicaserena.app.core.ui.components.SessionExpiredState

/** Estado de una pantalla que carga datos de Spring. */
sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Loaded<out T>(val data: T) : LoadState<T>
    data class Failed(val failure: ApiResult.Failure) : LoadState<Nothing>
}

fun <T> ApiResult<T>.toLoadState(): LoadState<T> = when (this) {
    is ApiResult.Success -> LoadState.Loaded(data)
    is ApiResult.Failure -> LoadState.Failed(this)
}

/**
 * Fallo de carga según su tipo: sesión vencida (la hoja de re-login ya está encima), permiso
 * denegado o error recuperable con Reintentar.
 */
@Composable
fun FailureContent(
    failure: ApiResult.Failure,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
) {
    when (failure) {
        is ApiResult.Unauthenticated -> SessionExpiredState(onLogin = onRetry, modifier = modifier, scrollable = scrollable)
        is ApiResult.Forbidden ->
            ForbiddenState(message = failure.toUserMessage().asString(), modifier = modifier, scrollable = scrollable)
        else -> ErrorState(
            message = failure.toUserMessage().asString(),
            onRetry = onRetry,
            modifier = modifier,
            scrollable = scrollable,
        )
    }
}
