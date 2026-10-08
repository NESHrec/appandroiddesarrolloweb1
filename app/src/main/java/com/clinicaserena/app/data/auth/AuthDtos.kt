package com.clinicaserena.app.data.auth

import kotlinx.serialization.Serializable

/** `POST /auth/login-unified`. Contraseña de 8–72 (paciente) o 12–72 (personal); la valida Spring. */
@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
) {
    override fun toString(): String = "LoginRequest(email=<oculto>, password=<oculto>)"
}

/** Respuesta de `login-unified`: `accountType` PACIENTE|PERSONAL y `role` PACIENTE|ADMIN|RECEPCION|MEDICO. */
@Serializable
data class UnifiedLoginResponse(
    val accessToken: String,
    val tokenType: String,
    val expiresInSeconds: Long,
    val accountType: String,
    val role: String,
) {
    override fun toString(): String =
        "UnifiedLoginResponse(accessToken=<oculto>, tokenType=$tokenType, " +
            "expiresInSeconds=$expiresInSeconds, accountType=$accountType, role=$role)"
}

/** `GET /auth/me` (solo token de paciente). No trae `role`: el rol es PACIENTE. */
@Serializable
data class PatientIdentityResponse(
    val patientId: String,
    val email: String,
    val accountStatus: String,
)

/** `GET /staff/auth/me` (solo token de personal). */
@Serializable
data class StaffIdentityResponse(
    val accountId: String,
    val email: String,
    val fullName: String,
    val role: String,
    val practitionerLinkStatus: String? = null,
    val practitionerId: String? = null,
)

/** `POST /auth/register` (`RegisterRequest` de Spring: nombre ≤160, email ≤254, password 8–72). */
@Serializable
data class RegisterRequest(
    val nombre: String,
    val email: String,
    val password: String,
) {
    override fun toString(): String = "RegisterRequest(nombre=<oculto>, email=<oculto>, password=<oculto>)"
}

/** `POST /auth/resend-verification` y `POST /auth/password-recovery` (`EmailRequest` de Spring). */
@Serializable
data class EmailRequest(val email: String) {
    override fun toString(): String = "EmailRequest(email=<oculto>)"
}

/** Respuesta 202 genérica de registro, reenvío y recuperación: no revela si la cuenta existe. */
@Serializable
data class GenericMessageResponse(val message: String)
