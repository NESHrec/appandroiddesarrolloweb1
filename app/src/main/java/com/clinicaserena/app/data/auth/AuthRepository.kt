package com.clinicaserena.app.data.auth

import com.clinicaserena.app.core.network.ApiCaller
import com.clinicaserena.app.core.network.ApiResult
import com.clinicaserena.app.domain.model.AccountType
import com.clinicaserena.app.domain.model.Role

/** Resultado de `login-unified` ya traducido a tipos de dominio. */
data class UnifiedLogin(
    val token: String,
    val accountType: AccountType,
    val role: Role,
    val expiresInSeconds: Long,
) {
    override fun toString(): String =
        "UnifiedLogin(token=<oculto>, accountType=$accountType, role=$role, expiresInSeconds=$expiresInSeconds)"
}

interface AuthRepository {
    suspend fun loginUnified(email: String, password: String): ApiResult<UnifiedLogin>

    /** Valida el token guardado contra `/auth/me` o `/staff/auth/me` y devuelve el rol vigente. */
    suspend fun currentRole(accountType: AccountType): ApiResult<Role>

    /** Revoca la sesión en Spring (`/auth/logout` o `/staff/auth/logout`). */
    suspend fun logout(accountType: AccountType): ApiResult<Unit>
}

class RemoteAuthRepository(
    private val api: AuthApi,
    private val apiCaller: ApiCaller,
) : AuthRepository {

    override suspend fun loginUnified(email: String, password: String): ApiResult<UnifiedLogin> =
        when (val result = apiCaller.call(authenticated = false) { api.loginUnified(LoginRequest(email, password)) }) {
            is ApiResult.Success -> result.data.toDomain()
            is ApiResult.Failure -> result
        }

    override suspend fun currentRole(accountType: AccountType): ApiResult<Role> = when (accountType) {
        AccountType.PACIENTE ->
            when (val result = apiCaller.call(authenticated = true) { api.patientMe() }) {
                is ApiResult.Success -> ApiResult.Success(Role.PACIENTE)
                is ApiResult.Failure -> result
            }
        AccountType.PERSONAL ->
            when (val result = apiCaller.call(authenticated = true) { api.staffMe() }) {
                is ApiResult.Success -> parseRole(result.data.role)
                    ?.let { ApiResult.Success(it) }
                    ?: ApiResult.Unexpected(null)
                is ApiResult.Failure -> result
            }
    }

    override suspend fun logout(accountType: AccountType): ApiResult<Unit> = when (accountType) {
        AccountType.PACIENTE -> apiCaller.callNoContent(authenticated = true) { api.patientLogout() }
        AccountType.PERSONAL -> apiCaller.callNoContent(authenticated = true) { api.staffLogout() }
    }

    private fun UnifiedLoginResponse.toDomain(): ApiResult<UnifiedLogin> {
        val type = AccountType.entries.firstOrNull { it.name == accountType }
        val parsedRole = parseRole(role)
        if (type == null || parsedRole == null || !tokenType.equals("Bearer", ignoreCase = true)) {
            return ApiResult.Unexpected(null)
        }
        return ApiResult.Success(UnifiedLogin(accessToken, type, parsedRole, expiresInSeconds))
    }

    private fun parseRole(value: String): Role? = Role.entries.firstOrNull { it.name == value }
}
