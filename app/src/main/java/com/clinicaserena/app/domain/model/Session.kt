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
 * `toString` lo oculta para que un log accidental del objeto no lo filtre.
 */
data class Session(
    val token: String,
    val accountType: AccountType,
    val role: Role,
    val expiresAt: Instant,
) {
    override fun toString(): String =
        "Session(token=<oculto>, accountType=$accountType, role=$role, expiresAt=$expiresAt)"
}
