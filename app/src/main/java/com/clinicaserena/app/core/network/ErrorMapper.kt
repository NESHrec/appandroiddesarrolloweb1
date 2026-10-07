package com.clinicaserena.app.core.network

import kotlinx.serialization.json.Json

/** Convierte una respuesta de error HTTP de Spring en un [ApiResult.Failure] tipado. */
class ErrorMapper(private val json: Json) {

    fun map(status: Int, body: String?): ApiResult.Failure {
        val error = parse(body)
        val code = error?.code
        val message = error?.message
        return when {
            status == 401 && code == CODE_AUTHENTICATION_FAILED -> ApiResult.InvalidCredentials(message)
            status == 401 -> ApiResult.Unauthenticated(code ?: CODE_UNAUTHENTICATED, message)
            status == 403 -> ApiResult.Forbidden(code ?: "FORBIDDEN", message)
            status == 404 -> ApiResult.NotFound(code ?: "RESOURCE_NOT_FOUND", message)
            status == 409 -> ApiResult.Conflict(code ?: "CONFLICT", message)
            status == 400 && error != null && error.code == CODE_VALIDATION_ERROR ->
                ApiResult.Validation(message, error.fieldErrors)
            status in 400..499 -> ApiResult.BadRequest(status, code, message)
            status in 500..599 -> ApiResult.ServerError(status)
            else -> ApiResult.Unexpected(status)
        }
    }

    private fun parse(body: String?): ApiError? {
        if (body.isNullOrBlank()) return null
        return try {
            json.decodeFromString(ApiError.serializer(), body)
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        const val CODE_UNAUTHENTICATED = "UNAUTHENTICATED"
        const val CODE_AUTHENTICATION_FAILED = "AUTHENTICATION_FAILED"
        const val CODE_VALIDATION_ERROR = "VALIDATION_ERROR"
    }
}
