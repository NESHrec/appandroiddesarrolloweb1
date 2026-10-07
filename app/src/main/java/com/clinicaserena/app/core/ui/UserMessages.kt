package com.clinicaserena.app.core.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.clinicaserena.app.R
import com.clinicaserena.app.core.network.ApiResult

/**
 * Mensaje para el usuario. Los mensajes de Spring ya vienen en español y sin detalles internos, así
 * que se usan cuando existen; si no, un texto local según el tipo de fallo.
 */
sealed interface UserMessage {
    data class Server(val text: String) : UserMessage
    data class Local(@StringRes val resId: Int) : UserMessage
}

fun ApiResult.Failure.toUserMessage(): UserMessage = when (this) {
    is ApiResult.NetworkError -> UserMessage.Local(R.string.error_sin_conexion)
    is ApiResult.ServerError -> UserMessage.Local(R.string.error_servidor)
    is ApiResult.Unexpected -> UserMessage.Local(R.string.error_inesperado)
    is ApiResult.InvalidCredentials -> UserMessage.Local(R.string.error_credenciales)
    is ApiResult.Unauthenticated -> UserMessage.Local(R.string.estado_sesion_vencida_mensaje)
    is ApiResult.Forbidden -> serverOr(message, R.string.error_permiso)
    is ApiResult.NotFound -> serverOr(message, R.string.error_no_encontrado)
    is ApiResult.Conflict -> serverOr(message, R.string.error_inesperado)
    is ApiResult.Validation -> serverOr(message, R.string.error_datos_invalidos)
    is ApiResult.BadRequest -> serverOr(message, R.string.error_datos_invalidos)
}

@Composable
fun UserMessage.asString(): String = when (this) {
    is UserMessage.Server -> text
    is UserMessage.Local -> stringResource(resId)
}

private fun serverOr(message: String?, @StringRes fallback: Int): UserMessage =
    if (message.isNullOrBlank()) UserMessage.Local(fallback) else UserMessage.Server(message)
