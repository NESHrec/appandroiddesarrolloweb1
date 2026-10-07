package com.clinicaserena.app.core.network

/**
 * Resultado tipado de una llamada a Spring. Cada fallo conserva el `code` del backend para que las
 * pantallas distingan los casos (por ejemplo `SLOT_NOT_AVAILABLE` frente a `ATTENTION_ALREADY_RECORDED`)
 * en lugar de reducirlos a un error genérico.
 */
sealed interface ApiResult<out T> {
    data class Success<out T>(val data: T) : ApiResult<T>

    sealed interface Failure : ApiResult<Nothing>

    /** 401 `UNAUTHENTICATED`: token ausente, vencido o revocado. */
    data class Unauthenticated(val code: String, val message: String?) : Failure

    /** 401 `AUTHENTICATION_FAILED` en el login: credenciales incorrectas. No es una sesión vencida. */
    data class InvalidCredentials(val message: String?) : Failure

    /** 403, por ejemplo `FORBIDDEN` o `PRACTITIONER_LINK_REQUIRED`. */
    data class Forbidden(val code: String, val message: String?) : Failure

    /** 404, por ejemplo `APPOINTMENT_NOT_FOUND` (también para recursos ajenos). */
    data class NotFound(val code: String, val message: String?) : Failure

    /** 409 de negocio, por ejemplo `SLOT_NOT_AVAILABLE` o `ARRIVAL_NOT_REGISTERED`. */
    data class Conflict(val code: String, val message: String?) : Failure

    /** 400 `VALIDATION_ERROR` con errores por campo. */
    data class Validation(val message: String?, val fieldErrors: List<FieldError>) : Failure

    /** Otros 4xx con código de dominio (`SLOT_IN_PAST`, `INVALID_PARAMETER`, 429…). */
    data class BadRequest(val status: Int, val code: String?, val message: String?) : Failure

    /** 5xx: el servidor falló o no está disponible. */
    data class ServerError(val status: Int) : Failure

    /** Sin conexión, tiempo de espera agotado o servidor inalcanzable. */
    data object NetworkError : Failure

    /** Respuesta que no corresponde al contrato (cuerpo ilegible, estado no previsto). */
    data class Unexpected(val status: Int?) : Failure
}
