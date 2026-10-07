package com.clinicaserena.app.core.network

import kotlinx.serialization.Serializable

/** Cuerpo de error de Spring (`GlobalExceptionHandler` y `SecurityConfig`). */
@Serializable
data class ApiError(
    val status: Int,
    val code: String,
    val message: String,
    val fieldErrors: List<FieldError> = emptyList(),
)

@Serializable
data class FieldError(
    val field: String,
    val message: String,
)
