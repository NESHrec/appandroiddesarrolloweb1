package com.clinicaserena.app.feature.auth

import androidx.annotation.StringRes
import com.clinicaserena.app.R

/** Validación local previa; Spring vuelve a validar todo (`RegisterRequest`, `EmailRequest`). */
object AuthValidation {
    private val emailPattern = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    const val PASSWORD_MIN = 8
    const val PASSWORD_MAX = 72
    const val NAME_MAX = 160
    const val EMAIL_MAX = 254

    @StringRes
    fun emailError(email: String): Int? {
        val value = email.trim()
        return when {
            value.isEmpty() -> R.string.validacion_correo_obligatorio
            value.length > EMAIL_MAX -> R.string.validacion_correo_largo
            !emailPattern.matches(value) -> R.string.validacion_correo_invalido
            else -> null
        }
    }

    /** En el login solo se exige que no esté vacía: la longitud la decide la cuenta (8 o 12 mínimo). */
    @StringRes
    fun loginPasswordError(password: String): Int? =
        if (password.isEmpty()) R.string.validacion_contrasena_obligatoria else null

    @StringRes
    fun newPasswordError(password: String): Int? =
        if (password.length !in PASSWORD_MIN..PASSWORD_MAX) R.string.validacion_contrasena_longitud else null

    @StringRes
    fun nameError(name: String): Int? {
        val value = name.trim()
        return when {
            value.isEmpty() -> R.string.validacion_nombre_obligatorio
            value.length > NAME_MAX -> R.string.validacion_nombre_largo
            else -> null
        }
    }
}
