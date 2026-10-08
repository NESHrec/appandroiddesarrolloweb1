package com.clinicaserena.app.domain.model

import java.time.Instant

/** Tipo de cuenta según `login-unified`: decide qué endpoints de `me` y `logout` usar. */
enum class AccountType { PACIENTE, PERSONAL }

/** Roles que devuelve Spring. La app solo admite PACIENTE y MEDICO. */
enum class Role {
    PACIENTE, MEDICO, ADMIN, RECEPCION;

    val supportedByApp: Boolean get() = this == PACIENTE || this == MEDICO
}

/**
 * Sesión activa. El token es un Bearer opaco de Spring; nunca debe escribirse en logs ni mostrarse.
 * `subjectId` es el `patientId` o el `accountId` según `/auth/me` o `/staff/auth/me`: identifica a la
 * persona para decidir si un re-login conserva los borradores. `toString` oculta token y correo.
 */
data class Session(
    val token: String,
    val accountType: AccountType,
    val role: Role,
    val expiresAt: Instant,
    val subjectId: String,
    val email: String,
) {
    override fun toString(): String =
        "Session(token=<oculto>, accountType=$accountType, role=$role, expiresAt=$expiresAt, " +
            "subjectId=$subjectId, email=<oculto>)"
}

/** Estado de vinculación de una cuenta MEDICO con un profesional del catálogo. */
enum class PractitionerLinkStatus { VINCULADA, PENDIENTE_VINCULACION, NO_APLICA }

/** Identidad vigente según Spring (`/auth/me` o `/staff/auth/me`). */
data class Identity(
    val subjectId: String,
    val email: String,
    val role: Role,
    val fullName: String? = null,
    val practitionerLinkStatus: PractitionerLinkStatus? = null,
)
